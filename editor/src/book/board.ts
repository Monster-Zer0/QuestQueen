// The quest board as the book draws it: recessed grid cells, ledger tiles (QuestTileWidget) and the path arrows at
// the ports (QuestBookScreen.tilePorts + MockChrome.pathArrowFilled). Positions are book pixels times the zoom's
// CSS px per book px; the game's own tile layout (ledgerTile.ts) places everything inside a tile.
//
// Edit mode differs from the game in three deliberate ways: the selected quest reads EDIT with an EDIT rail, hidden
// quests are drawn (dimmed) so they can be edited, and every straight link gets its arrow plus a gate tag, since an
// author has to see links a fresh player would not. Preview draws exactly what the simulated player would see.
import { bridgeBase } from "../bridge";
import { glyphSvg } from "../glyphs";
import { iconUrl } from "../itemSelect";
import { chapterGridHeight, chapterGridWidth, gateOp, type Chapter, type GridPos, type Link, type Reward, type Tile } from "../types";
import { h, keptImage, reconcile, releaseImages } from "./dom";
import { textWidth } from "./font";
import * as L from "./ledgerTile";
import { concealed, unlockedChapters, visual, type Visual } from "./progress";
import { GAP, TILE, ZOOM_STEPS } from "./scale";
import type { BookState, Store } from "./store";

export interface Geometry {
  /** Tile size in book px (the game's tile at this rung). */
  size: number;
  /** CSS px per book px. */
  px: number;
  /** CSS px from one cell to the next. */
  stride: number;
}

/** Whole zooms draw the 64px tile bigger; a half zoom draws the game's 32px tile at 1:1, as its small rungs do. */
export function tileGeometry(zoom: number): Geometry {
  const size = zoom < 1 ? Math.round(TILE * zoom) : TILE;
  const px = Math.max(1, zoom);
  return { size, px, stride: (size + (GAP * size) / TILE) * px };
}

const GATE_LABEL: Record<string, string> = { and: "AND", or: "OR", not: "NOT", xor: "FORK" };
const GATE_VAR: Record<string, string> = { and: "--gate-and", or: "--gate-or", not: "--gate-not", xor: "--gate-xor" };
const STATE_VAR: Record<Visual | "EDIT", string> = {
  CURRENT: "--current", NEW: "--neu", COMPLETED: "--completed", FAILED: "--failed", CLOSED: "--closed",
  LOCKED: "--port-red", EDIT: "--edit",
};

const bp = (n: number) => `calc(var(--px) * ${n}px)`;

function hasXorBadge(ch: Chapter, tile: Tile): boolean {
  return (ch.links ?? []).some((link) => (link.from === tile.id || link.to === tile.id) && gateOp(link) === "xor");
}

/** The item each reward shows on the tile (RewardFaces), with its count. */
function rewardFaces(rewards: Reward[] = []): { item: string; count: number }[] {
  const faces: { item: string; count: number }[] = [];
  for (const reward of rewards) {
    if (reward.type === "item" && reward.item) {
      faces.push({ item: reward.item.split("[")[0], count: reward.count ?? 1 });
    } else if (reward.type === "choice") {
      for (const o of reward.options ?? []) if (o.item) faces.push({ item: o.item.split("[")[0], count: o.count ?? 1 });
    } else if (reward.type === "command" && reward.icon && !reward.icon.startsWith("glyph:") && !reward.icon.includes("textures/")) {
      faces.push({ item: reward.icon.split("[")[0], count: 1 });
    } else if (reward.type === "xp" || reward.type === "xp_levels") {
      faces.push({ item: "minecraft:experience_bottle", count: 1 });
    } else if (reward.type === "loot") {
      faces.push({ item: "minecraft:chest", count: 1 });
    }
  }
  return faces;
}

/** ClientQuestState.tileCountText for a player with no task progress yet. */
function countText(tile: Tile): string {
  const tasks = tile.tasks ?? [];
  if (tasks.length > 1) return `0/${tasks.length}`;
  const need = tasks[0]?.count ?? tasks[0]?.levels ?? 1;
  return tasks.length === 1 && need > 1 ? `0/${need}` : "";
}

function tinyText(cls: string, text: string, x: number, y: number, color: string, scale = L.TINY_SCALE): HTMLElement {
  return h("span", {
    class: `tiny ${cls}`,
    style: `left:${bp(x)};top:${bp(y)};color:${color};font-size:${bp(8 * scale)};text-shadow:${bp(scale)} ${bp(scale)} 0 color-mix(in srgb, ${color} 25%, black)`,
  }, text);
}

function iconEl(cls: string, item: string | undefined, glyph: string | undefined, x: number, y: number, size: number, scope?: HTMLElement): HTMLElement | null {
  const style = `left:${bp(x)};top:${bp(y)};width:${bp(size)};height:${bp(size)}`;
  if (glyph) {
    const el = h("span", { class: `icon ${cls}`, style, "aria-hidden": "true" });
    el.innerHTML = glyphSvg(glyph);
    return el;
  }
  if (!item) return null;
  const src = iconUrl(bridgeBase(), item.split("[")[0]);
  return scope ? keptImage(scope, src, { class: `icon ${cls}`, style, alt: "" }) : h("img", { class: `icon ${cls}`, style, alt: "", src });
  return null;
}

const EXPAND_BOXES: [number, number, number, number][] = [[1, 3, 4, 1], [1, 6, 4, 1], [1, 3, 1, 4], [4, 3, 1, 4], [4, 1, 1, 1], [5, 1, 1, 1], [6, 1, 1, 1], [6, 2, 1, 1], [6, 3, 1, 1], [5, 2, 1, 1]];
let expandTemplate: HTMLElement | null = null;

/** MockChrome.expandIcon in the tile's state colour (fill: currentColor). */
function expandGlyph(size: number, color: string): HTMLElement {
  if (!expandTemplate) {
    expandTemplate = h("span", { class: "expand", "aria-hidden": "true" });
    expandTemplate.innerHTML = `<svg viewBox="0 0 7 7" width="100%" height="100%" shape-rendering="crispEdges" fill="currentColor"><path d="${EXPAND_BOXES.map(([x, y, w, hh]) => `M${x} ${y}h${w}v${hh}h-${w}z`).join("")}"/></svg>`;
  }
  const el = expandTemplate.cloneNode(true) as HTMLElement;
  el.style.cssText = `left:${bp(size - 4 - 7)};top:${bp(2)};width:${bp(7)};height:${bp(7)};color:${color}`;
  return el;
}

interface TileModel {
  tile: Tile;
  visual: Visual;
  word: string;
  edge: string;
  selected: boolean;
  hidden: boolean;
  claimable: boolean;
}

function fillTile(el: HTMLElement, m: TileModel, ch: Chapter, g: Geometry) {
  const { tile, size } = { tile: m.tile, size: g.size };
  const lay = L.layout(size, Boolean(tile.icon?.item || tile.icon?.glyph));
  const locked = m.visual === "LOCKED";
  const hasIcon = Boolean(tile.icon?.item || tile.icon?.glyph);
  const faces = locked ? [] : rewardFaces(tile.rewards);
  const xor = !locked && hasXorBadge(ch, tile);
  el.dataset.visual = m.visual;
  el.className = "tile";
  el.classList.toggle("locked", locked);
  el.classList.toggle("selected", m.selected);
  el.classList.toggle("concealed", m.hidden);
  el.classList.toggle("claimable", m.claimable);
  el.setAttribute("aria-label", `${tile.title || tile.id}, ${m.word || m.visual}`);
  el.style.left = `${tile.pos.x * g.stride}px`;
  el.style.top = `${tile.pos.y * g.stride}px`;
  el.style.width = el.style.height = bp(size);
  el.style.setProperty("--edge", m.edge);

  const content = h("span", { class: "tile-content" });
  if (L.showsHeader(size) && m.word) content.append(tinyText("state-word", m.word, lay.header.x, lay.header.y, m.edge));
  const icon = iconEl("tile-icon", tile.icon?.item, tile.icon?.glyph, lay.icon.x, lay.icon.y, lay.icon.size, el);
  if (icon) content.append(icon);
  const title = locked ? "??? COMPLETE PREVIOUS" : tile.title || tile.id;
  let budget = lay.title.lines;
  if (faces.length && size >= 40 && !L.compact(size)) {
    const labelY = L.rewardRowY(0, size) - L.LINE_STEP;
    budget = Math.min(budget, Math.max(1, Math.floor((labelY - lay.title.y) / L.LINE_STEP)));
  }
  const lines = L.wrapTiny(textWidth, title, Math.max(8, size - L.padPx(size) - lay.title.x), budget);
  const ink = locked ? "var(--locked-text)" : L.CAPTION_INK;
  const titleEl = h("span", { class: "title" });
  lines.forEach((line, i) => titleEl.append(tinyText("title-line", line, lay.title.x, lay.title.y + i * L.LINE_STEP, ink)));
  content.append(titleEl);

  const count = m.visual === "NEW" || m.visual === "CURRENT" ? countText(tile) : "";
  if (count && size >= 40) {
    content.append(h("span", { class: "bar", style: `left:${bp(lay.rail)};top:${bp(size - 3)};width:${bp(size - lay.rail - 1)};height:${bp(2)}` }));
    const countX = xor && size >= 48 ? L.contentInset(size) + 17 : L.contentInset(size);
    if (!L.compact(size) && L.countFits(countX, Math.ceil(textWidth(count) * L.TINY_SCALE), L.rewardStripLeft(0, size, faces.length))) {
      content.append(tinyText("count", count, countX, size - 10, "var(--muted)"));
    }
  }
  if (faces.length && size >= 40) {
    const show = faces.slice(0, L.REWARD_FACE_CAP);
    const rpx = L.rewardPx(size);
    const gap = show.length > 1 ? 1 : 0;
    const ry = L.rewardRowY(0, size);
    let rx = L.rewardStripLeft(0, size, faces.length);
    if (!L.compact(size)) {
      if (m.claimable) {
        const w = Math.ceil(textWidth("CLAIM") * L.TINY_SCALE) + 4;
        content.append(h("span", { class: "claim-chip", style: `left:${bp(size - L.padPx(size) - w)};top:${bp(ry - L.LINE_STEP - 2)};width:${bp(w)};height:${bp(7)}` },
          tinyText("claim-text", "CLAIM", 2, 1, "var(--card)")));
      } else {
        content.append(tinyText("rewards-label", "REWARDS", size - 26, ry - L.LINE_STEP, L.REWARDS_INK, 0.35));
      }
    }
    for (const face of show) {
      const img = iconEl("reward-icon", face.item, undefined, rx, ry, rpx, el);
      if (img) content.append(img);
      if (face.count > 1) content.append(tinyText("reward-count", `${face.count}x`, rx + 1, ry + Math.max(0, rpx - 5), "var(--text)"));
      rx += rpx + gap;
    }
  }
  if (xor && size >= 48) {
    const inner = L.contentInset(size);
    content.append(h("span", { class: "xor-badge", style: `left:${bp(inner)};top:${bp(size - 11)};width:${bp(15)};height:${bp(6)}` },
      tinyText("xor-text", "XOR", 2, 1, "#1b1322")));
  }
  if (!locked && size >= 22) content.append(expandGlyph(size, m.edge));
  el.dataset.rail = String(lay.rail);
  el.style.setProperty("--rail", String(lay.rail));
  el.replaceChildren(h("span", { class: "rail" }), content);
  releaseImages(el);
}

/** Pixels of the path arrow (MockChrome.pathArrowFilled), as offsets along (lx) and across (ly) the travel. */
const ARROW: [number, number][] = [];
for (let lx = 0; lx <= 7; lx++) {
  for (let ly = -3; ly <= 3; ly++) {
    const ay = Math.abs(ly);
    if ((lx <= 3 && ay <= 1) || (lx >= 4 && ay <= 7 - lx)) ARROW.push([lx, ly]);
  }
}

/** QuestBookScreen.sourceEdgeMid: the source tile's edge midpoint in the travel direction, in book px. */
function edgeMid(size: number, dx: number, dy: number): [number, number] {
  return [dx > 0 ? size - 1 : dx < 0 ? 0 : Math.floor(size / 2), dy > 0 ? size - 1 : dy < 0 ? 0 : Math.floor(size / 2)];
}

const arrowTemplates = new Map<string, SVGElement>();

/** The arrow's pixels for one travel direction, as one path (fill: currentColor), cached per direction. */
function arrowSvg(dx: number, dy: number): SVGElement {
  const key = `${dx},${dy}`;
  let svg = arrowTemplates.get(key);
  if (!svg) {
    const d = ARROW.map(([lx, ly]) => (dx !== 0 ? [dx * lx, ly] : [ly, dy * lx])).map(([x, y]) => `M${x} ${y}h1v1h-1z`).join("");
    const holder = document.createElement("span");
    holder.innerHTML = `<svg class="arrow-px" viewBox="-8 -8 17 17" style="left:${bp(-8)};top:${bp(-8)};width:${bp(17)};height:${bp(17)}" shape-rendering="crispEdges" fill="currentColor" aria-hidden="true"><path d="${d}"/></svg>`;
    svg = holder.firstElementChild as SVGElement;
    arrowTemplates.set(key, svg);
  }
  return svg.cloneNode(true) as SVGElement;
}

interface ArrowModel {
  link: Link;
  from: Tile;
  to: Tile;
  locked: boolean;
  showGate: boolean;
}

function fillArrow(el: HTMLElement, a: ArrowModel, g: Geometry) {
  const dx = Math.sign(a.to.pos.x - a.from.pos.x);
  const dy = Math.sign(a.to.pos.y - a.from.pos.y);
  const [mx, my] = edgeMid(g.size, dx, dy);
  const color = a.locked ? "var(--port-red)" : "var(--neu)";
  el.className = "arrow";
  el.dataset.from = a.from.id;
  el.dataset.to = a.to.id;
  el.dataset.locked = String(a.locked);
  el.style.left = `${a.from.pos.x * g.stride + mx * g.px}px`;
  el.style.top = `${a.from.pos.y * g.stride + my * g.px}px`;
  el.replaceChildren(arrowSvg(dx, dy));
  el.style.color = color;
  if (a.showGate) {
    const op = gateOp(a.link);
    // In the middle of the gap the arrow crosses, beside it (GAP 12: the gap centre is 6 past the edge).
    const half = GAP / 2;
    const pos = dx !== 0
      ? `left:${bp(dx * half)};top:${bp(5)};transform:translateX(-50%)`
      : `top:${bp(dy * half)};left:${bp(5)};transform:translateY(-50%)`;
    const tag = h("span", { class: "gate-tag", style: `color:var(${GATE_VAR[op] ?? "--gate-and"});${pos}` }, GATE_LABEL[op] ?? op.toUpperCase());
    el.append(tag);
  }
}

const patternCache = new Map<string, string>();

/** One grid cell as an SVG tile: a recessed CELL square with a CELL_LINE top/left edge and a CELL_SHADOW bottom/right. */
function cellPattern(el: HTMLElement, g: Geometry): string {
  const css = getComputedStyle(el);
  const color = (name: string, fallback: string) => css.getPropertyValue(name).trim() || fallback;
  const [cell, line, shadow] = [color("--cell", "#18101f"), color("--cell-line", "#4a3c62"), color("--cell-shadow", "#0a0610")];
  const key = `${g.stride}|${g.size}|${g.px}|${cell}|${line}|${shadow}`;
  let url = patternCache.get(key);
  if (!url) {
    const c = g.size * g.px;
    const p = g.px;
    const svg = `<svg xmlns="http://www.w3.org/2000/svg" width="${g.stride}" height="${g.stride}" shape-rendering="crispEdges">`
      + `<rect width="${c}" height="${c}" fill="${cell}"/>`
      + `<rect width="${c}" height="${p}" fill="${line}" fill-opacity="0.55"/><rect width="${p}" height="${c}" fill="${line}" fill-opacity="0.55"/>`
      + `<rect y="${c - p}" width="${c}" height="${p}" fill="${shadow}"/><rect x="${c - p}" width="${p}" height="${c}" fill="${shadow}"/></svg>`;
    url = `url("data:image/svg+xml,${encodeURIComponent(svg)}")`;
    patternCache.set(key, url);
  }
  return url;
}

const SIDES = { n: [0, -1], e: [1, 0], s: [0, 1], w: [-1, 0] } as const;
export type Side = keyof typeof SIDES;
export const SIDE_STEP = SIDES;

/**
 * Edit-mode handles on the selected quest, after the original mock: four NEW-blue diamonds at its side midpoints
 * (drag one onto a neighbour to link, or onto an empty neighbour to add a linked quest) and a dim ⊕ ADD in each
 * empty grid cell beside it.
 */
function renderAffordances(layer: HTMLElement, s: BookState, ch: Chapter, g: Geometry) {
  const tile = s.mode === "edit" && s.selected ? ch.tiles.find((t) => t.id === s.selected) : undefined;
  if (!tile) {
    layer.replaceChildren();
    return;
  }
  const w = chapterGridWidth(ch);
  const hh = chapterGridHeight(ch);
  const occupied = new Set(ch.tiles.map((t) => `${t.pos.x},${t.pos.y}`));
  const nodes: HTMLElement[] = [];
  for (const [side, [dx, dy]] of Object.entries(SIDES) as [Side, readonly [number, number]][]) {
    const nx = tile.pos.x + dx;
    const ny = tile.pos.y + dy;
    const inGrid = nx >= 0 && ny >= 0 && nx < w && ny < hh;
    const [mx, my] = edgeMid(g.size, dx, dy);
    nodes.push(h("button", {
      type: "button", class: "link-handle", dataset: { side },
      "aria-label": `Link ${tile.title || tile.id} ${({ n: "up", e: "right", s: "down", w: "left" })[side]}`,
      style: `left:${tile.pos.x * g.stride + (mx + dx) * g.px}px;top:${tile.pos.y * g.stride + (my + dy) * g.px}px`,
    }));
    if (inGrid && !occupied.has(`${nx},${ny}`)) {
      nodes.push(h("button", {
        type: "button", class: "add-cell", dataset: { x: String(nx), y: String(ny) }, "aria-label": `Add a quest ${({ n: "above", e: "right of", s: "below", w: "left of" })[side]} ${tile.title || tile.id}`,
        style: `left:${nx * g.stride}px;top:${ny * g.stride}px;width:${bp(g.size)};height:${bp(g.size)}`,
      }, h("span", { class: "add-plus" }, "⊕"), h("span", { class: "add-word" }, "ADD")));
    }
  }
  layer.replaceChildren(...nodes);
}

export interface Hit {
  cell: GridPos;
  tile?: Tile;
  /** Where in the tile, in book px from its top-left (for link handles). */
  local?: { x: number; y: number };
}

export interface Board {
  render(): void;
  geometry(): Geometry;
  hit(clientX: number, clientY: number): Hit | null;
  fit(): void;
  panBy(dx: number, dy: number): void;
  /** Zoom one step in (+1) or out (-1), keeping the board point under (clientX, clientY) still. */
  zoomAt(direction: 1 | -1, clientX: number, clientY: number): void;
  /** The part of the grid in view, in cells. */
  viewCells(): { x: number; y: number; w: number; h: number };
  /** Put the grid point (in cells) at the middle of the view. */
  centerOn(cellX: number, cellY: number): void;
  /** Scroll so a quest is in view. */
  reveal(tileId: string): void;
  plane: HTMLElement;
}

export function createBoard(el: HTMLElement, store: Store): Board {
  el.classList.add("book-board");
  el.tabIndex = 0;
  el.setAttribute("role", "grid");
  const plane = h("div", { class: "board-plane" });
  const cells = h("div", { class: "board-cells", "aria-hidden": "true" });
  const tileLayer = h("div", { class: "tile-layer", role: "row" });
  const arrowLayer = h("div", { class: "arrow-layer", "aria-hidden": "true" });
  const editLayer = h("div", { class: "edit-layer" });
  plane.append(cells, tileLayer, arrowLayer, editLayer);
  el.replaceChildren(plane);
  let pan = { x: 24, y: 24 };
  let fitted = "";

  const chapter = (s: BookState) => s.chapters.find((c) => c.id === s.openId);

  const applyPan = () => { plane.style.transform = `translate(${Math.round(pan.x)}px, ${Math.round(pan.y)}px)`; };

  /** Tile bounds of a chapter, in cells. */
  const bounds = (ch: Chapter) => {
    const tiles = ch.tiles ?? [];
    if (!tiles.length) return { minX: 0, minY: 0, maxX: chapterGridWidth(ch), maxY: chapterGridHeight(ch) };
    return {
      minX: Math.min(...tiles.map((t) => t.pos.x)), minY: Math.min(...tiles.map((t) => t.pos.y)),
      maxX: Math.max(...tiles.map((t) => t.pos.x)) + 1, maxY: Math.max(...tiles.map((t) => t.pos.y)) + 1,
    };
  };

  /** Centre the chapter's quests in the view at geometry g. */
  const center = (ch: Chapter, g: Geometry) => {
    const view = el.getBoundingClientRect();
    const b = bounds(ch);
    pan = {
      x: ((view.width || 800) - (b.maxX - b.minX) * g.stride) / 2 - b.minX * g.stride,
      y: ((view.height || 600) - (b.maxY - b.minY) * g.stride) / 2 - b.minY * g.stride,
    };
  };

  const board: Board = {
    plane,
    geometry: () => tileGeometry(store.get().zoom),
    render() {
      const s = store.get();
      const ch = chapter(s);
      if (!ch) {
        tileLayer.replaceChildren();
        arrowLayer.replaceChildren();
        return;
      }
      if (fitted !== ch.id) {
        // A chapter opens centred at the current zoom; only Fit changes the zoom.
        fitted = ch.id;
        center(ch, tileGeometry(s.zoom));
      }
      const g = tileGeometry(s.zoom);
      el.style.setProperty("--px", String(g.px));
      el.setAttribute("aria-label", `${ch.title || ch.id} quests`);
      const w = chapterGridWidth(ch);
      const hh = chapterGridHeight(ch);
      plane.style.width = `${w * g.stride}px`;
      plane.style.height = `${hh * g.stride}px`;
      cells.style.backgroundImage = cellPattern(el, g);
      applyPan();

      const editing = s.mode === "edit";
      const open = unlockedChapters(s.chapters, s.sim).has(ch.id);
      const models: TileModel[] = [];
      const visuals = new Map<string, Visual>();
      for (const tile of ch.tiles ?? []) {
        const v = visual(ch, tile, s.sim, s.chapters, open);
        visuals.set(tile.id, v);
        const hidden = concealed(ch, tile, s.sim);
        if (hidden && !editing) continue;
        const selected = s.selected === tile.id;
        const asEdit = editing && selected;
        const word = asEdit ? "EDIT" : hidden ? "HIDDEN" : v;
        models.push({ tile, visual: v, word, edge: `var(${STATE_VAR[asEdit ? "EDIT" : v]})`, selected, hidden,
          claimable: v === "COMPLETED" && (tile.rewards ?? []).length > 0 });
      }
      reconcile(tileLayer, models, (m) => m.tile.id,
        (m) => {
          const id = m.tile.id;
          return h("button", { type: "button", class: "tile", role: "gridcell", dataset: { tile: id }, onclick: () => store.select(id) });
        },
        (node, m) => fillTile(node, m, ch, g));

      const byId = new Map((ch.tiles ?? []).map((t) => [t.id, t]));
      const shown = new Set(models.map((m) => m.tile.id));
      const arrows: ArrowModel[] = [];
      for (const link of ch.links ?? []) {
        const from = byId.get(link.from);
        const to = byId.get(link.to);
        if (!from || !to || !shown.has(from.id) || !shown.has(to.id)) continue;
        const straight = (from.pos.x === to.pos.x) !== (from.pos.y === to.pos.y);
        if (!straight) continue;
        if (!editing && visuals.get(from.id) === "LOCKED") continue;
        arrows.push({ link, from, to, locked: visuals.get(to.id) === "LOCKED", showGate: editing });
      }
      reconcile(arrowLayer, arrows, (a) => `${a.from.id}>${a.to.id}`, () => h("span", { class: "arrow" }), (node, a) => fillArrow(node, a, g));
      renderAffordances(editLayer, s, ch, g);
    },
    hit(clientX, clientY) {
      const s = store.get();
      const ch = chapter(s);
      if (!ch) return null;
      const g = tileGeometry(s.zoom);
      const rect = plane.getBoundingClientRect();
      const x = clientX - rect.left;
      const y = clientY - rect.top;
      const cell = { x: Math.floor(x / g.stride), y: Math.floor(y / g.stride) };
      const inX = (x - cell.x * g.stride) / g.px;
      const inY = (y - cell.y * g.stride) / g.px;
      const tile = (ch.tiles ?? []).find((t) => t.pos.x === cell.x && t.pos.y === cell.y);
      const onTile = inX >= 0 && inY >= 0 && inX < g.size && inY < g.size;
      return { cell, tile: onTile ? tile : undefined, local: onTile ? { x: inX, y: inY } : undefined };
    },
    fit() {
      const s = store.get();
      const ch = chapter(s);
      if (!ch) return;
      const view = el.getBoundingClientRect();
      const b = bounds(ch);
      let zoom: number = ZOOM_STEPS[0];
      for (const step of ZOOM_STEPS) {
        const g = tileGeometry(step);
        if ((b.maxX - b.minX) * g.stride + 48 <= (view.width || 800) && (b.maxY - b.minY) * g.stride + 48 <= (view.height || 600)) zoom = step;
      }
      center(ch, tileGeometry(zoom));
      fitted = ch.id;
      if (s.zoom !== zoom) store.setZoom(zoom);
      board.render();
    },
    panBy(dx, dy) {
      pan = { x: pan.x + dx, y: pan.y + dy };
      applyPan();
    },
    zoomAt(direction, clientX, clientY) {
      const s = store.get();
      const steps = ZOOM_STEPS as readonly number[];
      const at = Math.max(0, steps.indexOf(s.zoom));
      const next = steps[Math.max(0, Math.min(steps.length - 1, at + direction))];
      if (next === s.zoom) return;
      const before = tileGeometry(s.zoom).stride;
      const after = tileGeometry(next).stride;
      const rect = el.getBoundingClientRect();
      const ax = clientX - rect.left;
      const ay = clientY - rect.top;
      pan = { x: ax - ((ax - pan.x) * after) / before, y: ay - ((ay - pan.y) * after) / before };
      store.setZoom(next);
    },
    viewCells() {
      const g = tileGeometry(store.get().zoom);
      const view = el.getBoundingClientRect();
      return { x: -pan.x / g.stride, y: -pan.y / g.stride, w: (view.width || 800) / g.stride, h: (view.height || 600) / g.stride };
    },
    centerOn(cellX, cellY) {
      const g = tileGeometry(store.get().zoom);
      const view = el.getBoundingClientRect();
      pan = { x: (view.width || 800) / 2 - cellX * g.stride, y: (view.height || 600) / 2 - cellY * g.stride };
      applyPan();
    },
    reveal(tileId) {
      const s = store.get();
      const tile = chapter(s)?.tiles.find((t) => t.id === tileId);
      if (!tile) return;
      const g = tileGeometry(s.zoom);
      const view = el.getBoundingClientRect();
      const x = pan.x + tile.pos.x * g.stride;
      const y = pan.y + tile.pos.y * g.stride;
      const size = g.size * g.px;
      if (x < 8) pan.x += 8 - x;
      if (y < 8) pan.y += 8 - y;
      if (x + size > view.width - 8) pan.x -= x + size - view.width + 8;
      if (y + size > view.height - 8) pan.y -= y + size - view.height + 8;
      applyPan();
    },
  };

  // Wheel zooms at the pointer; right-drag pans (as in the game and the old editor).
  el.addEventListener("wheel", (event) => {
    event.preventDefault();
    board.zoomAt(event.deltaY < 0 ? 1 : -1, event.clientX, event.clientY);
  }, { passive: false });
  let drag: { x: number; y: number } | null = null;
  el.addEventListener("contextmenu", (event) => event.preventDefault());
  el.addEventListener("pointerdown", (event) => {
    if (event.button !== 2) return;
    drag = { x: event.clientX, y: event.clientY };
    el.setPointerCapture?.(event.pointerId);
  });
  el.addEventListener("pointermove", (event) => {
    if (!drag) return;
    board.panBy(event.clientX - drag.x, event.clientY - drag.y);
    drag = { x: event.clientX, y: event.clientY };
  });
  el.addEventListener("pointerup", () => { drag = null; });
  return board;
}
