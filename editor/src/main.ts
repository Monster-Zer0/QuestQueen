import JSZip from "jszip";
import schema from "../../schema/questqueen.schema.json";
import {
  BLANK,
  GRID_PRESETS,
  REWARD_TYPES,
  STARTER,
  TASK_TYPES,
  chapterGridHeight,
  chapterGridWidth,
  chapterTree,
  cardinalAdjacent,
  defaultReward,
  defaultTask,
  gateOp,
  hasLink,
  inGrid,
  removeLink,
  setUnlockConditions,
  startsOf,
  unlockConditions,
  upsertLink,
  type Chapter,
  type ChapterNode,
  type GateOpName,
  type Link,
  type Reward,
  type Task,
  type Tile,
  type UnlockCondition,
} from "./types";
import { emptyCatalog, fromApi, type CatalogEntry, type PackCatalog } from "./packCatalog";
import { iconUrl, itemSelectHtml, readItemSelect, wireItemSelects } from "./itemSelect";
import { glyphPickerHtml, glyphSvg, previewIntroHtml, readGlyph, wireGlyphPicker } from "./glyphs";
import {
  DEFAULT_THEME_ID,
  STOCK_BACKGROUNDS,
  themeById,
  themeOptionHtml,
} from "./themes";

const TILE = 64;
const GAP = 12;
const STRIDE = TILE + GAP;
const CAPTION_FONT = "'Segoe UI', system-ui, -apple-system, 'Helvetica Neue', Arial, sans-serif";
const MONO_FONT = "'Cascadia Mono', Consolas, 'Courier New', monospace";
const canvas = document.getElementById("grid") as HTMLCanvasElement;
const ctx = canvas.getContext("2d")!;
const card = document.getElementById("card") as HTMLElement;
const cardBody = document.getElementById("card-body") as HTMLElement;
const cardChrome = document.getElementById("card-chrome") as HTMLElement;
const cardTitle = document.getElementById("card-title") as HTMLElement;
const cardResize = document.getElementById("card-resize") as HTMLElement | null;
const closeCardBtn = document.getElementById("close-card") as HTMLButtonElement;
const search = document.getElementById("search") as HTMLInputElement;
const fileZip = document.getElementById("file-zip") as HTMLInputElement;
const packStatus = document.getElementById("pack-status");
const lock = document.getElementById("lock");
const lockStatus = document.getElementById("lock-status");
const app = document.getElementById("app");
const linkStatus = document.getElementById("link-status");
const chapterTreeEl = document.getElementById("chapter-tree");
// Hoisted above setLocked→draw (Troi TDZ crash): must exist before first drawMinimap
const minimap = document.getElementById("minimap") as HTMLCanvasElement | null;
const minimapCtx = minimap?.getContext("2d") ?? null;

const CARD_GEOM_KEY = "questqueen.editor.card";
const CARD_MIN_W = 260;
const CARD_MIN_H = 200;

let chapter: Chapter = structuredClone(STARTER);
let packChapters: Chapter[] = [structuredClone(STARTER)];
/** Board size in CSS pixels. The backing store is this times devicePixelRatio, so text and icons stay sharp. */
let viewW = 960;
let viewH = 640;
let camX = -STRIDE;
let camY = -STRIDE;
let zoom = 1; // Slice 2: canvas zoom (LOD)
const ZOOM_MIN = 0.35;
const ZOOM_MAX = 2.5;
const FIT_ZOOM_MAX = 1.6;
let selected = "make_chest";
let unlocked = false;
type UiMode = "view" | "edit" | "link";
type Lens = "author" | "player";
let uiMode: UiMode = "view";
let lens: Lens = "author";
let authoring = false; // derived via syncAuthoring: Author + Edit
function syncAuthoring() {
  authoring = lens === "author" && uiMode === "edit";
}

function isSelectedEdge(link: Link): boolean {
  if (!selected) return false;
  return link.from === selected || link.to === selected;
}

function canLink(): boolean {
  return lens === "author" && uiMode === "link";
}

let dragging = false;
let lastX = 0;
let lastY = 0;
let hoverCell: { x: number; y: number } | null = null;
type SideName = "n" | "e" | "s" | "w";
let hoverSide: { id: string; side: SideName } | null = null;
let gateAsk: { from: string; to: string; existing: boolean } | null = null;
const SIDE_STEP: Record<SideName, [number, number]> = { n: [0, -1], e: [1, 0], s: [0, 1], w: [-1, 0] };
const SIDE_WORD: Record<SideName, string> = { n: "north", e: "east", s: "south", w: "west" };
let linkHint = "";
type BoardPress = {
  kind: "tile" | "empty";
  id?: string;
  startX: number;
  startY: number;
  moved: boolean;
};
let press: BoardPress | null = null;
type TileDrag = { id: string; ox: number; oy: number; px: number; py: number; grabX: number; grabY: number };
let tileDrag: TileDrag | null = null;
let catalog: PackCatalog = emptyCatalog();
let linkFrom = "";
let pendingGate: GateOpName = "and";
/** Slice 3: staged link awaiting confirm (unless Shift power-shortcut). */
let searchHits: Set<string> | null = null;

type LinkUndo =
  | { kind: "remove"; from: string; to: string; gate: GateOpName }
  | { kind: "gate"; from: string; to: string; prev: GateOpName; next: GateOpName }
  | { kind: "add"; from: string; to: string; gate: GateOpName }
  | { kind: "move"; id: string; fromX: number; fromY: number; toX: number; toY: number }
  | { kind: "delete"; tile: Tile; links: { from: string; to: string; gate: GateOpName }[] }
  | { kind: "spawn"; tile: Tile; from: string; gate: GateOpName };
const linkUndo: LinkUndo[] = [];

function pushLinkUndo(entry: LinkUndo) {
  linkUndo.push(entry);
  if (linkUndo.length > 50) linkUndo.shift();
}

function undoLastLinkOp() {
  const entry = linkUndo.pop();
  if (!entry) return;
  if (entry.kind === "remove") {
    upsertLink(chapter, entry.from, entry.to, entry.gate);
  } else if (entry.kind === "gate") {
    upsertLink(chapter, entry.from, entry.to, entry.prev);
  } else if (entry.kind === "add") {
    removeLink(chapter, entry.from, entry.to);
  } else if (entry.kind === "move") {
    const moved = chapter.tiles.find((t) => t.id === entry.id);
    if (moved) moved.pos = { x: entry.fromX, y: entry.fromY };
  } else if (entry.kind === "delete") {
    chapter.tiles.push(structuredClone(entry.tile));
    for (const link of entry.links) upsertLink(chapter, link.from, link.to, link.gate);
    selected = entry.tile.id;
  } else if (entry.kind === "spawn") {
    removeLink(chapter, entry.from, entry.tile.id);
    chapter.tiles = chapter.tiles.filter((t) => t.id !== entry.tile.id);
    if (selected === entry.tile.id) selected = entry.from;
  }
  rememberChapter();
  updateLinkStatus();
  syncLinkConfirmChrome();
  const tile = chapter.tiles.find((t) => t.id === selected);
  if (tile) showCard(tile);
  draw();
}

function confirmLinkDelete(from: string, to: string): boolean {
  return window.confirm(`Delete link ${from} → ${to}?\n\nYou can press Ctrl+Z to undo.`);
}

let pendingLink: { from: string; to: string; gate: GateOpName } | null = null;

type CardGeom = { left: number; top: number; width: number; height: number };
let cardGeom: CardGeom | null = loadCardGeom();

cardBody.addEventListener("mousedown", (event) => event.stopPropagation());
closeCardBtn.addEventListener("click", (event) => {
  event.stopPropagation();
  selected = "";
  setCardOpen(false);
  draw();
});

function rememberChapter() {
  const index = packChapters.findIndex((entry) => entry.id === chapter.id);
  if (index >= 0) packChapters[index] = chapter;
  else packChapters.push(chapter);
}

function ensureTiles(next: Chapter): Tile[] {
  if (!next.tiles) next.tiles = [];
  return next.tiles;
}

function chapterHasChildren(id: string): boolean {
  return packChapters.some((entry) => entry.parent === id);
}

function iconPayload(glyph: string, item: string): { item?: string; glyph?: string } | undefined {
  if (glyph) return item ? { glyph, item } : { glyph };
  if (item) return { item };
  return undefined;
}

function switchChapter(id: string) {
  rememberChapter();
  const next = packChapters.find((entry) => entry.id === id);
  if (!next) return;
  chapter = next;
  ensureTiles(chapter);
  selected = chapter.tiles[0]?.id ?? "";
  linkFrom = "";
  pendingLink = null;
  searchHits = null;
  updateLinkStatus();
  syncLinkConfirmChrome();
  syncGridSizeSelect();
  renderChapterTree();
  const finish = () => {
    fitChapterBoard();
    draw();
    if (chapterHasChildren(chapter.id)) {
      showCard();
      return;
    }
    const tile = chapter.tiles.find((entry) => entry.id === selected) ?? chapter.tiles[0];
    if (tile) showCard(tile);
    else setCardOpen(false);
  };
  requestAnimationFrame(finish);
}

function createChapter() {
  if (!unlocked) return;
  rememberChapter();
  const slug = `chapter_${packChapters.length + 1}`;
  const created: Chapter = {
    id: `questqueen:${slug}`,
    title: "New Chapter",
    order: packChapters.length * 10,
    parent: chapter.id,
    grid_width: 12,
    grid_height: 10,
    tiles: [{
      id: "start",
      pos: { x: 1, y: 1 },
      title: "First step",
      description: "",
      icon: { item: "minecraft:paper" },
      tasks: [{ type: "checkmark" }],
      rewards: [{ type: "xp", amount: 5 }],
    }],
    links: [],
    unlock: { op: "and", conditions: [] },
  };
  packChapters.push(created);
  chapter = created;
  selected = "start";
  syncGridSizeSelect();
  renderChapterTree();
  draw();
  showCard(created.tiles[0]);
}

function renderChapterTree() {
  if (!chapterTreeEl) return;
  rememberChapter();
  const renderNode = (node: ChapterNode, depth: number): string => {
    const active = node.chapter.id === chapter.id ? "active" : "";
    const title = node.chapter.title || node.chapter.id;
    const kids = node.children.map((child) => renderNode(child, depth + 1)).join("");
    return `<button type="button" class="chapter-row ${active}" data-chapter-id="${escapeAttr(node.chapter.id)}" style="padding-left:${6 + depth * 12}px">${escapeAttr(title)}</button>${kids}`;
  };
  chapterTreeEl.innerHTML = chapterTree(packChapters).map((node) => renderNode(node, 0)).join("")
    || `<p class="muted">No chapters</p>`;
  chapterTreeEl.querySelectorAll<HTMLButtonElement>("[data-chapter-id]").forEach((button) => {
    button.addEventListener("click", () => switchChapter(button.dataset.chapterId ?? ""));
  });
}

document.getElementById("new-chapter")?.addEventListener("click", () => createChapter());

function loadCardGeom(): CardGeom | null {
  try {
    const raw = localStorage.getItem(CARD_GEOM_KEY);
    if (!raw) return null;
    const parsed = JSON.parse(raw) as CardGeom;
    if (![parsed.left, parsed.top, parsed.width, parsed.height].every((n) => Number.isFinite(n))) return null;
    return parsed;
  } catch {
    return null;
  }
}

function saveCardGeom() {
  if (!cardGeom) return;
  localStorage.setItem(CARD_GEOM_KEY, JSON.stringify(cardGeom));
}

function panelBounds() {
  const main = card.parentElement ?? document.body;
  return { width: main.clientWidth, height: main.clientHeight };
}

function clampCardGeom(next: CardGeom): CardGeom {
  const bounds = panelBounds();
  const width = Math.min(Math.max(CARD_MIN_W, next.width), Math.max(CARD_MIN_W, bounds.width - 8));
  const height = Math.min(Math.max(CARD_MIN_H, next.height), Math.max(CARD_MIN_H, bounds.height - 8));
  const left = Math.min(Math.max(0, next.left), Math.max(0, bounds.width - width));
  const top = Math.min(Math.max(0, next.top), Math.max(0, bounds.height - height));
  return { left, top, width, height };
}

function applyCardGeom() {
  if (card.classList.contains("docked")) return;
  if (!cardGeom) return;
  cardGeom = clampCardGeom(cardGeom);
  card.style.left = `${cardGeom.left}px`;
  card.style.top = `${cardGeom.top}px`;
  card.style.right = "auto";
  card.style.bottom = "auto";
  card.style.width = `${cardGeom.width}px`;
  card.style.height = `${cardGeom.height}px`;
}

function captureDefaultGeom() {
  const rect = card.getBoundingClientRect();
  const parent = card.parentElement?.getBoundingClientRect();
  if (!parent) return;
  cardGeom = clampCardGeom({
    left: rect.left - parent.left,
    top: rect.top - parent.top,
    width: rect.width,
    height: rect.height,
  });
  saveCardGeom();
  applyCardGeom();
}

function wireCardWindow() {
  // Slice 1: inspector is a docked right rail — no floating drag/resize.
  card.classList.add("docked");
  card.style.left = "";
  card.style.top = "";
  card.style.right = "";
  card.style.bottom = "";
  card.style.width = "";
  card.style.height = "";
  cardGeom = null;
}

function setCardOpen(open: boolean) {
  card.hidden = false; // rail always in layout
  card.classList.toggle("collapsed", !open);
  if (!open) {
    cardTitle.textContent = "INSPECTOR";
    cardBody.innerHTML = `<p class="muted">Select a tile</p>`;
  }
}

function sizeCanvas() {
  if (canvas.clientWidth <= 0 || canvas.clientHeight <= 0) return;
  const dpr = window.devicePixelRatio || 1;
  viewW = canvas.clientWidth;
  viewH = canvas.clientHeight;
  canvas.width = Math.round(viewW * dpr);
  canvas.height = Math.round(viewH * dpr);
}

function resize() {
  sizeCanvas();
  applyCardGeom();
  draw();
}

function screen(gx: number, gy: number) {
  const stride = STRIDE * zoom;
  return { x: gx * stride - camX, y: gy * stride - camY };
}

function tileSize(): number {
  return TILE * zoom;
}

function stridePx(): number {
  return STRIDE * zoom;
}

/** LOD: 0 overview chips, 1 short title, 2 full labels */
function labelLod(): 0 | 1 | 2 {
  if (zoom < 0.55) return 0;
  if (zoom < 0.9) return 1;
  return 2;
}

function clampZoom(next: number): number {
  return Math.min(ZOOM_MAX, Math.max(ZOOM_MIN, next));
}

function setZoomAt(next: number, anchorX: number, anchorY: number) {
  const before = zoom;
  zoom = clampZoom(next);
  if (zoom === before) return;
  // keep world point under anchor stable
  const worldX = (anchorX + camX) / (STRIDE * before);
  const worldY = (anchorY + camY) / (STRIDE * before);
  camX = worldX * STRIDE * zoom - anchorX;
  camY = worldY * STRIDE * zoom - anchorY;
  syncZoomChrome();
  draw();
}


/** Fit camera to tile AABB (+pad), else chapter grid. Data S2. */
function fitChapterBoard() {
  // Ensure canvas has layout size (Data saw no auto-fit when width was 0 mid-switch)
  sizeCanvas();
  const pad = 48;
  const tiles = chapter.tiles ?? [];
  let minX = 0, minY = 0, maxX = chapterGridWidth(chapter), maxY = chapterGridHeight(chapter);
  if (tiles.length) {
    minX = Math.min(...tiles.map((t) => t.pos.x));
    minY = Math.min(...tiles.map((t) => t.pos.y));
    maxX = Math.max(...tiles.map((t) => t.pos.x)) + 1;
    maxY = Math.max(...tiles.map((t) => t.pos.y)) + 1;
  }
  const worldW = Math.max(1, maxX - minX) * STRIDE;
  const worldH = Math.max(1, maxY - minY) * STRIDE;
  const zx = (viewW - pad * 2) / worldW;
  const zy = (viewH - pad * 2) / worldH;
  // Past ~1.6x a fitted small chapter is just big tiles; leave room around it instead.
  zoom = clampZoom(Math.min(zx, zy, FIT_ZOOM_MAX));
  // Centre the content box (tile extents, not the trailing gap) in the view.
  const contentW = ((maxX - minX) * STRIDE - GAP) * zoom;
  const contentH = ((maxY - minY) * STRIDE - GAP) * zoom;
  camX = minX * STRIDE * zoom - (viewW - contentW) / 2;
  camY = minY * STRIDE * zoom - (viewH - contentH) / 2;
  syncZoomChrome();
}

function syncZoomChrome() {
  const label = document.getElementById("zoom-label");
  if (label) label.textContent = `${Math.round(zoom * 100)}%`;
}


function activeTheme() {
  const base = themeById(chapter.theme);
  const override = chapter.background?.color?.trim();
  if (override) {
    return { ...base, void: override.startsWith("#") ? override : `#${override}` };
  }
  return base;
}

function gateColor(op: GateOpName): string {
  const theme = activeTheme();
  if (op === "or") return theme.neu;
  if (op === "xor") return theme.completed;
  return "#C8C0D8";
}

function drawArrowHead(x: number, y: number, dx: number, dy: number, color: string, len = 6) {
  ctx.fillStyle = color;
  for (let i = 0; i < len; i++) {
    const px = x - dx * i;
    const py = y - dy * i;
    if (dx !== 0) ctx.fillRect(px, py - i, 1, i * 2 + 1);
    else ctx.fillRect(px - i, py, i * 2 + 1, 1);
  }
}

function drawLinkArrow(from: Tile, to: Tile, link: Link) {
  const a = tileOrigin(from);
  const b = tileOrigin(to);
  const size = tileSize();
  const x1 = a.x + size / 2;
  const y1 = a.y + size / 2;
  const x2 = b.x + size / 2;
  const y2 = b.y + size / 2;
  const op = gateOp(link);
  const color = gateColor(op);
  ctx.strokeStyle = color;
  ctx.lineWidth = 2;
  ctx.beginPath();
  ctx.moveTo(x1, y1);
  if (x1 === x2 || y1 === y2) {
    ctx.lineTo(x2, y2);
  } else {
    ctx.lineTo(x2, y1);
    ctx.lineTo(x2, y2);
  }
  ctx.stroke();
  // Direction of the final segment; the head goes on the destination's edge; at its centre the tile hid it.
  let fdx = 0;
  let fdy = 0;
  if (x1 !== x2 && y1 !== y2) fdy = Math.sign(y2 - y1) || 1;
  else if (x1 !== x2) fdx = Math.sign(x2 - x1);
  else fdy = Math.sign(y2 - y1) || 1;
  const headLen = Math.max(4, Math.round(5 * Math.min(zoom, 1.6)));
  drawArrowHead(Math.round(x2 - fdx * (size / 2 + 1)), Math.round(y2 - fdy * (size / 2 + 1)), fdx, fdy, color, headLen);
  // Edge chip: Link mode always; else only at high LOD (Data S2 — hide gate text in overview)
  // Data: no lod≥2 spray in Edit/View. Outside Link mode the chip also needs a gap wide enough to hold it,
  // or it lands on the tiles either side.
  const showChip = canLink() || (isSelectedEdge(link) && GAP * zoom >= 30);
  if (showChip) {
    const mx = x1 === x2 ? x1 + 4 : (x1 + x2) / 2;
    const my = y1 === y2 ? (y1 + y2) / 2 : y1;
    const label = op === "and" ? "∧" : op === "or" ? "∨" : op === "xor" ? "⊕" : op.toUpperCase();
    ctx.fillStyle = "#140f1c";
    ctx.fillRect(mx - 14, my - 10, 28, 16);
    ctx.strokeStyle = color;
    ctx.strokeRect(mx - 14.5, my - 10.5, 29, 17);
    ctx.fillStyle = color;
    ctx.font = `11px ${MONO_FONT}`;
    ctx.textAlign = "center";
    ctx.fillText(label, mx, my + 3);
    ctx.textAlign = "left";
  }
}


function drawMinimap() {
  if (!minimap || !minimapCtx) return;
  const mctx = minimapCtx;
  const w = minimap.width;
  const h = minimap.height;
  const theme = activeTheme();
  mctx.fillStyle = "#0c0810";
  mctx.fillRect(0, 0, w, h);
  const gw = Math.max(1, chapterGridWidth(chapter));
  const gh = Math.max(1, chapterGridHeight(chapter));
  const scale = Math.min(w / (gw * STRIDE), h / (gh * STRIDE));
  for (const tile of chapter.tiles ?? []) {
    const inbound = (chapter.links ?? []).some((l) => l.to === tile.id);
    mctx.fillStyle = tile.id === selected ? theme.text : inbound ? theme.lockedEdge : theme.current;
    mctx.fillRect(tile.pos.x * STRIDE * scale, tile.pos.y * STRIDE * scale, Math.max(2, TILE * scale), Math.max(2, TILE * scale));
  }
  // The map only earns its corner when part of the chapter is off screen.
  const stride = stridePx();
  const size = tileSize();
  const allInView = (chapter.tiles ?? []).every((t) => {
    const sx = t.pos.x * stride - camX;
    const sy = t.pos.y * stride - camY;
    return sx >= 0 && sy >= 0 && sx + size <= viewW && sy + size <= viewH;
  });
  minimap.classList.toggle("idle", allInView && !chapterHasChildren(chapter.id));
  // viewport rect
  const vx = camX * scale;
  const vy = camY * scale;
  const vw = (viewW / zoom) * scale;
  const vh = (viewH / zoom) * scale;
  mctx.strokeStyle = "#d6b25e";
  mctx.lineWidth = 1;
  mctx.strokeRect(Math.round(vx) + 0.5, Math.round(vy) + 0.5, Math.round(vw), Math.round(vh));
}

minimap?.addEventListener("click", (event) => {
  if (!minimap) return;
  const rect = minimap.getBoundingClientRect();
  const gw = Math.max(1, chapterGridWidth(chapter));
  const gh = Math.max(1, chapterGridHeight(chapter));
  const scale = Math.min(minimap.width / (gw * STRIDE), minimap.height / (gh * STRIDE));
  const mx = (event.clientX - rect.left) * (minimap.width / rect.width);
  const my = (event.clientY - rect.top) * (minimap.height / rect.height);
  camX = mx / scale - viewW / (2 * zoom);
  camY = my / scale - viewH / (2 * zoom);
  draw();
});

function setCaptionFont(px: number) {
  ctx.font = `600 ${px}px ${CAPTION_FONT}`;
}

/** Cut on a word boundary when one fits; otherwise keep as many glyphs as fit. Width stays <= maxWidth. */
function ellipsizeToWidth(text: string, maxWidth: number): string {
  if (maxWidth <= 0) return "";
  if (ctx.measureText(text).width <= maxWidth) return text;
  const ell = "…";
  if (ctx.measureText(ell).width > maxWidth) return "";
  const words = text.split(" ");
  if (words.length > 1) {
    let best = "";
    for (let n = 1; n < words.length; n++) {
      const candidate = `${words.slice(0, n).join(" ")}${ell}`;
      if (ctx.measureText(candidate).width <= maxWidth) best = candidate;
      else break;
    }
    if (best) return best;
  }
  const word = words[0] ?? text;
  let lo = 0;
  let hi = word.length;
  while (lo < hi) {
    const mid = (lo + hi + 1) >> 1;
    const candidate = word.slice(0, mid) + ell;
    if (ctx.measureText(candidate).width <= maxWidth) lo = mid;
    else hi = mid - 1;
  }
  return lo === 0 ? ell : word.slice(0, lo) + ell;
}

/** Wrap to maxLines. The last line ellipsizes; earlier lines break only on spaces. */
function fitCaptionLines(text: string, maxWidth: number, maxLines: number): string[] {
  const clean = text.trim().replace(/\s+/g, " ");
  if (!clean || maxWidth <= 0 || maxLines < 1) return [];
  const words = clean.split(" ");
  const lines: string[] = [];
  let i = 0;
  while (i < words.length && lines.length < maxLines) {
    if (lines.length === maxLines - 1) {
      const fitted = ellipsizeToWidth(words.slice(i).join(" "), maxWidth);
      if (fitted) lines.push(fitted);
      break;
    }
    if (ctx.measureText(words[i]).width > maxWidth) {
      const fitted = ellipsizeToWidth(words[i], maxWidth);
      if (fitted) lines.push(fitted);
      i += 1;
      continue;
    }
    let line = words[i];
    i += 1;
    while (i < words.length) {
      const trial = `${line} ${words[i]}`;
      if (ctx.measureText(trial).width > maxWidth) break;
      line = trial;
      i += 1;
    }
    lines.push(line);
  }
  return lines;
}

/** LOD size, stepped down only until the widest word fits. Floor stays readable. */
function captionFontPx(zoomLevel: number, maxWidth: number, text: string, preferredScale: number): number {
  const floor = 8;
  let px = Math.max(floor, Math.floor(preferredScale * zoomLevel));
  const words = text.trim().split(/\s+/).filter(Boolean);
  while (px > floor && maxWidth > 0) {
    setCaptionFont(px);
    let widest = 0;
    for (const word of words) widest = Math.max(widest, ctx.measureText(word).width);
    if (widest <= maxWidth) return px;
    px -= 1;
  }
  setCaptionFont(px);
  return px;
}

function paintCaptionLines(
  lines: string[],
  x: number,
  top: number,
  lineH: number,
  fontPx: number,
  bottom: number,
  align: CanvasTextAlign = "left",
) {
  ctx.textBaseline = "top";
  ctx.textAlign = align;
  let y = top;
  for (const line of lines) {
    if (y + fontPx > bottom + 0.5) break;
    ctx.fillText(line, x, y);
    y += lineH;
  }
  ctx.textBaseline = "alphabetic";
  ctx.textAlign = "left";
}

/*
 * Tile faces. Item icons come from Minecraft through the bridge (/api/icon renders the real item); glyphs are the
 * mod's own 16x16 pixel set rendered from SVG. Images load lazily and one coalesced redraw lands them.
 */
const iconCache = new Map<string, HTMLImageElement | null>();
let iconRedraw = 0;

function cachedImage(key: string, src: string): HTMLImageElement | null {
  const hit = iconCache.get(key);
  if (hit !== undefined) return hit && hit.complete && hit.naturalWidth > 0 ? hit : null;
  const img = new Image();
  img.decoding = "async";
  img.onload = () => {
    if (!iconRedraw) iconRedraw = requestAnimationFrame(() => { iconRedraw = 0; draw(); });
  };
  img.onerror = () => iconCache.set(key, null);
  img.src = src;
  iconCache.set(key, img);
  return null;
}

function tileIconImage(tile: Tile, color: string): HTMLImageElement | null {
  const glyph = tile.icon?.glyph;
  if (glyph) {
    const svg = glyphSvg(glyph, color);
    if (svg) return cachedImage(`g:${glyph}:${color}`, `data:image/svg+xml;charset=utf-8,${encodeURIComponent(svg)}`);
  }
  const item = tile.icon?.item;
  if (item && unlocked) return cachedImage(`i:${item}`, iconUrl(bridgeBase(), item));
  return null;
}

/** Icon edge in px: whole multiples of 8 so 16px pixel art scales without shimmer. */
function tileIconPx(size: number, band: number): number {
  const room = Math.min(size * 0.36, size - band - 18);
  return Math.max(8, Math.floor(room / 8) * 8);
}

function roundRectPath(x: number, y: number, w: number, h: number, r: number) {
  const rr = Math.max(0, Math.min(r, w / 2, h / 2));
  ctx.beginPath();
  ctx.moveTo(x + rr, y);
  ctx.arcTo(x + w, y, x + w, y + h, rr);
  ctx.arcTo(x + w, y + h, x, y + h, rr);
  ctx.arcTo(x, y + h, x, y, rr);
  ctx.arcTo(x, y, x + w, y, rr);
  ctx.closePath();
}

function tileOrigin(tile: Tile): { x: number; y: number } {
  if (tileDrag && tileDrag.id === tile.id) return { x: tileDrag.px, y: tileDrag.py };
  return screen(tile.pos.x, tile.pos.y);
}

/** Pixel padlock. Unit scales with the tile so the glyph stays inside the quiet lock. */
function drawPadlock(cx: number, cy: number, tilePx: number) {
  const u = Math.max(1, Math.round(tilePx / 16));
  ctx.fillRect(cx - 2 * u, cy - 3 * u, u, 2 * u);
  ctx.fillRect(cx + u, cy - 3 * u, u, 2 * u);
  ctx.fillRect(cx - 2 * u, cy - 4 * u, 4 * u, u);
  ctx.fillRect(cx - 3 * u, cy - u, 6 * u, 5 * u);
}

function paintBoardAffordances() {
  const theme = activeTheme();
  const size = tileSize();
  if (canLink() && linkFrom) {
    const from = chapter.tiles.find((t) => t.id === linkFrom);
    if (from) {
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]] as const) {
        const dest = chapter.tiles.find((t) => t.pos.x === from.pos.x + dx && t.pos.y === from.pos.y + dy);
        if (!dest || hasLink(chapter, from.id, dest.id)) continue;
        const p = tileOrigin(dest);
        ctx.save();
        ctx.strokeStyle = theme.edit;
        ctx.lineWidth = 2;
        ctx.setLineDash([4, 3]);
        ctx.strokeRect(p.x + 3, p.y + 3, size - 6, size - 6);
        ctx.restore();
      }
    }
  }
  if (tileDrag && hoverCell && inGrid(chapter, hoverCell.x, hoverCell.y)) {
    const blocked = chapter.tiles.some((t) => t.id !== tileDrag!.id && t.pos.x === hoverCell!.x && t.pos.y === hoverCell!.y);
    const p = screen(hoverCell.x, hoverCell.y);
    ctx.save();
    ctx.strokeStyle = blocked ? theme.lockedEdge : theme.edit;
    ctx.setLineDash([3, 3]);
    ctx.strokeRect(p.x + 2, p.y + 2, size - 4, size - 4);
    ctx.restore();
  }
  if (!hoverCell || chapterHasChildren(chapter.id) || tileDrag) return;
  const occupied = chapter.tiles.find((t) => t.pos.x === hoverCell!.x && t.pos.y === hoverCell!.y);
  if (occupied) {
    const p = tileOrigin(occupied);
    ctx.save();
    ctx.strokeStyle = theme.text;
    ctx.globalAlpha = 0.85;
    ctx.lineWidth = 2;
    ctx.strokeRect(p.x + 1, p.y + 1, size - 2, size - 2);
    ctx.restore();
    return;
  }
  if (!authoring || !inGrid(chapter, hoverCell.x, hoverCell.y)) return;
  const p = screen(hoverCell.x, hoverCell.y);
  ctx.save();
  ctx.beginPath();
  ctx.rect(p.x, p.y, size, size);
  ctx.clip();
  ctx.strokeStyle = theme.edit;
  ctx.setLineDash([4, 3]);
  ctx.lineDashOffset = -((performance.now() / 40) % 8);
  ctx.strokeRect(p.x + 2, p.y + 2, size - 4, size - 4);
  ctx.lineDashOffset = 0;
  ctx.setLineDash([]);
  ctx.fillStyle = theme.edit;
  ctx.font = `${Math.max(8, Math.floor(10 * zoom))}px ${CAPTION_FONT}`;
  ctx.textAlign = "center";
  ctx.textBaseline = "middle";
  ctx.fillText("NEW", p.x + size / 2, p.y + size / 2);
  ctx.restore();
}

function sideAt(clientX: number, clientY: number): { tile: Tile; side: SideName } | null {
  if (!canLink() || chapterHasChildren(chapter.id)) return null;
  const cell = pointerCell(clientX, clientY);
  const tile = chapter.tiles.find((t) => t.pos.x === cell.gx && t.pos.y === cell.gy);
  if (!tile) return null;
  const origin = tileOrigin(tile);
  const size = tileSize();
  const x = cell.localX - origin.x;
  const y = cell.localY - origin.y;
  if (x < 0 || y < 0 || x >= size || y >= size) return null;
  const band = Math.min(size * 0.28, Math.max(8, size * 0.2));
  const edges: [SideName, number][] = [["w", x], ["e", size - x], ["n", y], ["s", size - y]];
  edges.sort((a, b) => a[1] - b[1]);
  if (edges[0][1] > band) return null;
  return { tile, side: edges[0][0] };
}

function sideLink(from: Tile, to: Tile) {
  return (chapter.links ?? []).find((link) =>
    (link.from === from.id && link.to === to.id) || (link.from === to.id && link.to === from.id));
}

function paintLinkSides() {
  if (!canLink() || chapterHasChildren(chapter.id)) return;
  const theme = activeTheme();
  const size = tileSize();
  const band = Math.min(size * 0.28, Math.max(8, size * 0.2));
  for (const tile of chapter.tiles) {
    const underPointer = hoverCell?.x === tile.pos.x && hoverCell?.y === tile.pos.y;
    if (!underPointer) continue;
    const p = tileOrigin(tile);
    for (const side of ["n", "e", "s", "w"] as const) {
      const hot = hoverSide?.id === tile.id && hoverSide.side === side;
      const [dx, dy] = SIDE_STEP[side];
      const nx = tile.pos.x + dx;
      const ny = tile.pos.y + dy;
      const neighbor = chapter.tiles.find((t) => t.pos.x === nx && t.pos.y === ny);
      const link = neighbor ? sideLink(tile, neighbor) : undefined;
      const label = !inGrid(chapter, nx, ny) ? "END" : !neighbor ? "NEW" : link ? gateOp(link).toUpperCase() : "LINK";
      let rx = p.x;
      let ry = p.y;
      let rw = size;
      let rh = band;
      if (side === "s") ry = p.y + size - band;
      if (side === "w") { rw = band; rh = size; }
      if (side === "e") { rx = p.x + size - band; rw = band; rh = size; }
      ctx.save();
      ctx.beginPath();
      ctx.rect(rx, ry, rw, rh);
      ctx.clip();
      ctx.fillStyle = hot ? theme.edit : "rgba(255,184,74,0.28)";
      ctx.globalAlpha = hot ? 0.55 + 0.4 * fxWave() : 1;
      ctx.fillRect(rx, ry, rw, rh);
      ctx.globalAlpha = 1;
      if (hot && band >= 12) {
        ctx.fillStyle = "#140f1c";
        ctx.font = `${Math.max(8, Math.min(11, Math.floor(band - 2)))}px ${CAPTION_FONT}`;
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.fillText(label, rx + rw / 2, ry + rh / 2);
      }
      ctx.restore();
    }
  }
}

function fxWave(): number {
  return 0.5 + 0.5 * Math.sin(performance.now() / 380);
}

function pointerOnBoard(): boolean {
  return hoverCell != null || hoverSide != null;
}

function boardFxHot(): boolean {
  if (!pointerOnBoard() || chapterHasChildren(chapter.id)) return false;
  if (hoverSide) return true;
  if (selected) return true;
  if (authoring && hoverCell && inGrid(chapter, hoverCell.x, hoverCell.y)) {
    return !chapter.tiles.some((t) => t.pos.x === hoverCell!.x && t.pos.y === hoverCell!.y);
  }
  return false;
}

let boardFx = 0;
function ensureBoardFx() {
  if (boardFx || !boardFxHot()) return;
  const step = () => {
    if (!boardFxHot()) {
      boardFx = 0;
      return;
    }
    draw();
    boardFx = requestAnimationFrame(step);
  };
  boardFx = requestAnimationFrame(step);
}

function draw() {
  const theme = activeTheme();
  const lod = labelLod();
  const size = tileSize();
  const stride = stridePx();
  const dpr = window.devicePixelRatio || 1;
  ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
  ctx.imageSmoothingEnabled = false;
  ctx.fillStyle = theme.void;
  ctx.fillRect(0, 0, viewW, viewH);
  if (chapterHasChildren(chapter.id)) {
    ctx.fillStyle = theme.text;
    ctx.font = `600 15px ${CAPTION_FONT}`;
    ctx.fillText("Act intro — board is not shown in-game", 24, 48);
    ctx.fillStyle = theme.neu;
    ctx.font = `13px ${CAPTION_FONT}`;
    ctx.fillText("Child chapters still use a quest board. Edit intro image + body in the card.", 24, 70);
    return;
  }
  const gw = chapterGridWidth(chapter);
  const gh = chapterGridHeight(chapter);
  const minGx = Math.max(0, Math.floor(camX / stride) - 1);
  const minGy = Math.max(0, Math.floor(camY / stride) - 1);
  const maxGx = Math.min(gw, Math.ceil((camX + viewW) / stride) + 1);
  const maxGy = Math.min(gh, Math.ceil((camY + viewH) / stride) + 1);
  for (let gx = minGx; gx < maxGx; gx++) {
    for (let gy = minGy; gy < maxGy; gy++) {
      const p = screen(gx, gy);
      // Empty slots: faint dots only (not loud empty tiles)
      ctx.fillStyle = theme.text;
      ctx.globalAlpha = lod === 0 ? 0.06 : 0.1;
      const dot = Math.max(2, Math.round(size * 0.05));
      ctx.fillRect(Math.round(p.x + size / 2 - dot / 2), Math.round(p.y + size / 2 - dot / 2), dot, dot);
      ctx.globalAlpha = 1;
    }
  }
  const origin = screen(0, 0);
  const corner = screen(gw, gh);
  ctx.save();
  ctx.strokeStyle = theme.text;
  ctx.globalAlpha = 0.14;
  ctx.lineWidth = 1;
  ctx.setLineDash([6, 6]);
  const gap = GAP * zoom;
  ctx.strokeRect(Math.round(origin.x - gap / 2) + 0.5, Math.round(origin.y - gap / 2) + 0.5,
    Math.round(corner.x - origin.x), Math.round(corner.y - origin.y));
  ctx.restore();
  const starts = startsOf(chapter);
  for (const link of chapter.links ?? []) {
    const from = chapter.tiles.find((t) => t.id === link.from);
    const to = chapter.tiles.find((t) => t.id === link.to);
    if (!from || !to) continue;
    drawLinkArrow(from, to, link);
  }
  // Pending / armed preview
  if (pendingLink || linkFrom) {
    const fromId = pendingLink?.from ?? linkFrom;
    const toId = pendingLink?.to;
    const from = chapter.tiles.find((t) => t.id === fromId);
    if (from) {
      const a = screen(from.pos.x, from.pos.y);
      const size = tileSize();
      ctx.strokeStyle = theme.edit;
      ctx.setLineDash([4, 4]);
      ctx.strokeRect(a.x + 2, a.y + 2, size - 4, size - 4);
      if (toId) {
        const to = chapter.tiles.find((t) => t.id === toId);
        if (to) {
          const b = screen(to.pos.x, to.pos.y);
          ctx.beginPath();
          ctx.moveTo(a.x + size / 2, a.y + size / 2);
          ctx.lineTo(b.x + size / 2, b.y + size / 2);
          ctx.stroke();
          ctx.setLineDash([]);
          ctx.fillStyle = theme.edit;
          ctx.font = `600 11px ${CAPTION_FONT}`;
          ctx.fillText("CONFIRM?", (a.x + b.x) / 2 + size / 2 - 20, (a.y + b.y) / 2 + size / 2 - 8);
        }
      }
      ctx.setLineDash([]);
    }
  }
  for (const tile of chapter.tiles) {
    const p = tileOrigin(tile);
    const inbound = (chapter.links ?? []).some((l) => l.to === tile.id);
    const hasXor = (chapter.links ?? []).some((l) => (l.from === tile.id || l.to === tile.id) && gateOp(l) === "xor");
    const state = authoring && tile.id === selected ? "EDIT"
      : starts.has(tile.id) ? "CURRENT"
      : inbound ? "LOCKED"
      : "NEW";
    const locked = state === "LOCKED";
    // The player lens shows what a player sees: a locked quest is a padlock. Authors always see the quest itself.
    const hideFace = locked && lens === "player";
    const accent = state === "CURRENT" ? theme.current : state === "NEW" ? theme.neu : state === "EDIT" ? theme.edit : theme.lockedEdge;
    const isSelected = tile.id === selected;
    const radius = Math.max(2, Math.round(4 * zoom));

    // Selection ring sits outside the tile so it never covers the face.
    if (isSelected && !searchHits) {
      ctx.save();
      ctx.strokeStyle = theme.text;
      ctx.globalAlpha = pointerOnBoard() ? 0.45 + 0.4 * fxWave() : 0.75;
      ctx.lineWidth = 2;
      roundRectPath(p.x - 3, p.y - 3, size + 6, size + 6, radius + 3);
      ctx.stroke();
      ctx.restore();
    }

    ctx.save();
    roundRectPath(p.x, p.y, size, size, radius);
    ctx.fillStyle = locked ? theme.locked : theme.card;
    ctx.globalAlpha = locked && lod === 0 ? 0.6 : 1;
    ctx.fill();
    ctx.globalAlpha = 1;
    if (searchHits && !searchHits.has(tile.id)) {
      ctx.fillStyle = "rgba(8,6,14,0.72)";
      ctx.fill();
      ctx.restore();
      continue; // Data: scope board to hits — skip labels on non-matches
    }
    ctx.clip();
    const band = lod >= 1 ? Math.max(9, Math.round(12 * zoom)) : 0;
    if (lod >= 1) {
      ctx.fillStyle = accent;
      ctx.globalAlpha = locked ? 0.35 : 1;
      ctx.fillRect(p.x, p.y, size, band);
      ctx.globalAlpha = 1;
      setCaptionFont(Math.max(7, Math.min(10, Math.floor(8 * zoom))));
      ctx.fillStyle = locked ? theme.text : theme.card;
      ctx.globalAlpha = locked ? 0.75 : 1;
      ctx.textBaseline = "middle";
      const stateText = lod === 1 ? state.slice(0, 1) : state;
      ctx.fillText(ellipsizeToWidth(stateText, Math.max(0, size - 8)), p.x + Math.max(3, 4 * zoom), p.y + band / 2 + 0.5);
      ctx.textBaseline = "alphabetic";
      ctx.globalAlpha = 1;
      if (locked && !hideFace) {
        // Small lock badge in the band: the quest reads as gated without hiding what it is.
        ctx.fillStyle = theme.text;
        ctx.globalAlpha = 0.8;
        drawPadlock(p.x + size - band / 2 - 2, p.y + band / 2 - 1, band * 1.6);
        ctx.globalAlpha = 1;
      }
    }
    const pad = Math.max(3, Math.floor(5 * zoom));
    const maxW = Math.max(0, size - pad * 2);
    const iconPx = tileIconPx(size, band);
    const iconX = Math.round(p.x + (size - iconPx) / 2);
    const iconY = Math.round(p.y + band + Math.max(3, 4 * zoom));
    if (hideFace) {
      ctx.globalAlpha = 0.55;
      ctx.fillStyle = theme.neu;
      drawPadlock(p.x + size / 2, p.y + band / 2 + size / 2, size);
      ctx.globalAlpha = 1;
    } else {
      const img = tileIconImage(tile, theme.text);
      ctx.globalAlpha = locked ? 0.6 : 1;
      if (img) {
        ctx.imageSmoothingEnabled = false;
        ctx.drawImage(img, iconX, iconY, iconPx, iconPx);
      } else if (lod >= 1) {
        // No icon yet (or none set): a monogram keeps the tile identifiable.
        ctx.fillStyle = accent;
        ctx.globalAlpha = locked ? 0.25 : 0.18;
        roundRectPath(iconX, iconY, iconPx, iconPx, Math.max(2, iconPx / 6));
        ctx.fill();
        ctx.globalAlpha = locked ? 0.6 : 0.9;
        ctx.fillStyle = theme.text;
        setCaptionFont(Math.max(8, Math.floor(iconPx * 0.55)));
        ctx.textAlign = "center";
        ctx.textBaseline = "middle";
        ctx.fillText((tile.title ?? tile.id).trim().charAt(0).toUpperCase(), iconX + iconPx / 2, iconY + iconPx / 2 + 1);
        ctx.textAlign = "left";
        ctx.textBaseline = "alphabetic";
      }
      ctx.globalAlpha = 1;
    }
    if (lod === 0 && !locked) {
      // Overview: a status pip in the corner, the icon carries identity.
      ctx.fillStyle = accent;
      ctx.fillRect(p.x + size - Math.max(4, size * 0.22), p.y, Math.max(4, size * 0.22), Math.max(4, size * 0.22));
    }
    if (lod >= 1 && !hideFace && maxW >= 12) {
      const title = tile.title ?? tile.id;
      const bottomReserve = hasXor ? Math.max(7, Math.min(10, Math.floor(7 * zoom))) + 8 : pad;
      const innerTop = iconY + iconPx + Math.max(2, Math.round(2 * zoom));
      const innerBottom = p.y + size - bottomReserve;
      // Caption size tracks zoom only up to a reading size; beyond that the tile grows, not the text.
      const fontPx = captionFontPx(Math.min(zoom, 1.45), maxW, title, 8.5);
      const lineH = fontPx + Math.max(1, Math.round(fontPx * 0.12));
      const fitLines = Math.floor((innerBottom - innerTop) / lineH);
      const maxLines = lod === 1 ? Math.min(1, fitLines) : Math.min(2, fitLines);
      if (maxLines >= 1) {
        ctx.fillStyle = theme.text;
        ctx.globalAlpha = locked ? 0.6 : 1;
        paintCaptionLines(fitCaptionLines(title, maxW, maxLines), p.x + size / 2, innerTop, lineH, fontPx, innerBottom, "center");
        ctx.globalAlpha = 1;
      }
    }
    if (hasXor && lod >= 1) {
      // Badge sized from its own text so the label can never overflow it.
      const fontPx = Math.max(7, Math.min(10, Math.floor(7 * zoom)));
      setCaptionFont(fontPx);
      const bw = Math.ceil(ctx.measureText("XOR").width) + 6;
      const bh = fontPx + 4;
      const bx = p.x + 3;
      const by = p.y + size - bh - 3;
      ctx.fillStyle = theme.completed;
      roundRectPath(bx, by, bw, bh, 2);
      ctx.fill();
      ctx.fillStyle = theme.card;
      ctx.textBaseline = "middle";
      ctx.fillText("XOR", bx + 3, by + bh / 2 + 0.5);
      ctx.textBaseline = "alphabetic";
    }
    ctx.restore();
    // Outline after the clip so it is crisp on all four sides.
    ctx.save();
    roundRectPath(p.x + 0.5, p.y + 0.5, size - 1, size - 1, radius);
    ctx.strokeStyle = accent;
    ctx.globalAlpha = locked ? 0.55 : 1;
    ctx.lineWidth = 1;
    ctx.stroke();
    ctx.restore();
    if ((authoring || canLink()) && tile.id === linkFrom) {
      ctx.strokeStyle = theme.edit;
      ctx.lineWidth = 2;
      ctx.strokeRect(p.x + 2, p.y + 2, size - 4, size - 4);
      ctx.lineWidth = 1;
    }
  }
  paintBoardAffordances();
  paintLinkSides();
  drawMinimap();
  ensureBoardFx();
}

function listOptions(entries: CatalogEntry[]): string {
  return entries.map((entry) => `<option value="${escapeAttr(entry.id)}">${escapeAttr(entry.name ?? entry.id)}`).join("");
}

function taskFields(task: Task, index: number, ro: string, disabled: boolean): string {
  const count = `<label>COUNT</label><input data-task="${index}" data-field="count" type="number" min="1" value="${task.count ?? task.waves ?? task.levels ?? 1}" ${ro} />`;
  switch (task.type) {
    case "obtain":
    case "submit":
      return `${itemSelectHtml(`data-task="${index}" data-field="item"`, task.item ?? "", disabled, "ITEM")}${count}`;
    case "item_tag":
      return `<label>ITEM TAG</label><input data-task="${index}" data-field="tag" list="pack-tags" value="${escapeAttr(task.tag ?? "")}" ${ro} />${count}`;
    case "kill":
    case "interact_entity":
      return `<label>ENTITY</label><input data-task="${index}" data-field="entity" list="pack-entities" value="${escapeAttr(task.entity ?? "")}" ${ro} />${count}`;
    case "advancement":
      return `<label>ADVANCEMENT</label><input data-task="${index}" data-field="advancement" list="pack-advancements" value="${escapeAttr(task.advancement ?? "")}" ${ro} />`;
    case "visit_structure":
      return `<label>STRUCTURE</label><input data-task="${index}" data-field="structure" list="pack-structures" value="${escapeAttr(task.structure ?? "")}" ${ro} />`;
    case "visit_dimension":
      return `<label>DIMENSION</label><input data-task="${index}" data-field="dimension" value="${escapeAttr(task.dimension ?? "")}" ${ro} />`;
    case "visit_biome":
      return `<label>BIOME</label><input data-task="${index}" data-field="biome" list="pack-biomes" value="${escapeAttr(task.biome ?? "")}" ${ro} />`;
    case "observation":
    case "interact_block":
      return `<label>BLOCK</label><input data-task="${index}" data-field="block" list="pack-blocks" value="${escapeAttr(task.block ?? "")}" ${ro} />${task.type === "interact_block" ? count : ""}`;
    case "fluid":
      return `<label>FLUID</label><input data-task="${index}" data-field="fluid" value="${escapeAttr(task.fluid ?? "")}" ${ro} />`;
    case "stat":
      return `<label>STAT</label><input data-task="${index}" data-field="stat" value="${escapeAttr(task.stat ?? "")}" ${ro} />${count}`;
    case "location":
      return `<label>X Y Z / RADIUS</label>
        <input data-task="${index}" data-field="x" type="number" value="${task.x ?? 0}" ${ro} />
        <input data-task="${index}" data-field="y" type="number" value="${task.y ?? 64}" ${ro} />
        <input data-task="${index}" data-field="z" type="number" value="${task.z ?? 0}" ${ro} />
        <input data-task="${index}" data-field="radius" type="number" value="${task.radius ?? 8}" ${ro} />
        <label>DIMENSION</label><input data-task="${index}" data-field="dimension" value="${escapeAttr(task.dimension ?? "")}" ${ro} />`;
    case "npc_dialog":
      return `<label>NPC</label><input data-task="${index}" data-field="npc" value="${escapeAttr(task.npc ?? "")}" ${ro} />`;
    case "trigger":
      return `<label>TRIGGER</label><input data-task="${index}" data-field="trigger" value="${escapeAttr(task.trigger ?? "")}" ${ro} />`;
    case "xp_levels":
      return `<label>LEVELS</label><input data-task="${index}" data-field="levels" type="number" min="1" value="${task.levels ?? 1}" ${ro} />`;
    case "raid":
      return `<label>WAVES</label><input data-task="${index}" data-field="waves" type="number" min="1" value="${task.waves ?? 1}" ${ro} />`;
    default:
      return "";
  }
}

function rewardFields(reward: Reward, index: number, ro: string, disabled: boolean): string {
  switch (reward.type) {
    case "item":
      return `${itemSelectHtml(`data-reward="${index}" data-field="item"`, reward.item ?? "", disabled, "ITEM")}
        <label>COUNT</label><input data-reward="${index}" data-field="count" type="number" min="1" value="${reward.count ?? 1}" ${ro} />`;
    case "xp":
      return `<label>XP</label><input data-reward="${index}" data-field="amount" type="number" min="1" value="${reward.amount ?? 5}" ${ro} />`;
    case "xp_levels":
      return `<label>LEVELS</label><input data-reward="${index}" data-field="levels" type="number" min="1" value="${reward.levels ?? 1}" ${ro} />`;
    case "command":
      return `<label>COMMAND</label><input data-reward="${index}" data-field="command" value="${escapeAttr(reward.command ?? "")}" ${ro} />`;
    case "loot":
      return `<label>LOOT TABLE</label><input data-reward="${index}" data-field="table" list="pack-loot" value="${escapeAttr(reward.table ?? "")}" ${ro} />`;
    case "toast":
      return `<label>MESSAGE</label><input data-reward="${index}" data-field="message" value="${escapeAttr(reward.message ?? "")}" ${ro} />`;
    case "advancement":
      return `<label>ADVANCEMENT</label><input data-reward="${index}" data-field="advancement" list="pack-advancements" value="${escapeAttr(reward.advancement ?? "")}" ${ro} />`;
    case "choice":
      return `${itemSelectHtml(`data-reward="${index}" data-field="option0"`, reward.options?.[0]?.item ?? "", disabled, "OPTION A")}
        ${itemSelectHtml(`data-reward="${index}" data-field="option1"`, reward.options?.[1]?.item ?? "", disabled, "OPTION B")}`;
    default:
      return "";
  }
}

function questlineHtml(tile: Tile): string {
  const inbound = (chapter.links ?? []).filter((link) => link.to === tile.id);
  const outbound = (chapter.links ?? []).filter((link) => link.from === tile.id);
  // Data S1: mutate gates/links only in Author+Link — Edit must not cycle/remove.
  if (!canLink()) {
    const parts = [
      ...inbound.map((link) => `IN ${link.from} [${gateOp(link).toUpperCase()}]`),
      ...outbound.map((link) => `OUT ${link.to} [${gateOp(link).toUpperCase()}]`),
    ];
    const hint = lens === "author" && uiMode === "edit"
      ? `<p class="muted">Switch to LINK. Hover a side to add a quest or choose AND, OR, or XOR.</p>`
      : "";
    return `${hint}<p>${parts.length ? parts.join("<br/>") : "No links"}</p>`;
  }
  if (!inbound.length && !outbound.length) {
    return `<ol class="link-steps">
      <li>Hover a quest. The four sides light up.</li>
      <li>Click an empty side to create the next quest that way.</li>
      <li>Click a side that already meets a quest, then pick AND, OR, or XOR.</li>
    </ol>`;
  }
  const rows = [
    ...inbound.map((link) => `
      <div class="link-row">
        <span>IN ${escapeAttr(link.from)}</span>
        <button type="button" data-cycle-from="${escapeAttr(link.from)}" data-cycle-to="${escapeAttr(link.to)}">${gateOp(link).toUpperCase()}</button>
        <button type="button" data-remove-from="${escapeAttr(link.from)}" data-remove-to="${escapeAttr(link.to)}">x</button>
      </div>`),
    ...outbound.map((link) => `
      <div class="link-row">
        <span>OUT ${escapeAttr(link.to)}</span>
        <button type="button" data-cycle-from="${escapeAttr(link.from)}" data-cycle-to="${escapeAttr(link.to)}">${gateOp(link).toUpperCase()}</button>
        <button type="button" data-remove-from="${escapeAttr(link.from)}" data-remove-to="${escapeAttr(link.to)}">x</button>
      </div>`),
  ];
  return rows.join("");
}

/** Which inspector sections are expanded; kept across tile and chapter switches. */
const sectionOpen: Record<string, boolean> = { quest: true, intro: true, chapter: false };

function section(key: string, title: string, meta: string, body: string, forceOpen = false): string {
  const open = forceOpen || sectionOpen[key] ? "open" : "";
  return `<details class="section" data-section="${key}" ${open}>
    <summary>${escapeText(title)}<span class="section-meta">${escapeText(meta)}</span></summary>
    <div class="section-body">${body}</div>
  </details>`;
}

function showCard(tile?: Tile) {
  const intro = chapterHasChildren(chapter.id);
  if (!intro && !tile) {
    setCardOpen(false);
    return;
  }
  setCardOpen(true);
  card.classList.toggle("edit", authoring);
  applyCardGeom();
  const header = intro ? "ACT INTRO" : uiMode === "edit" && lens === "author" ? "EDIT" : uiMode === "link" && lens === "author" ? "LINK" : "INSPECTOR";
  const heading = intro ? (chapter.title || chapter.id) : (tile!.title ?? tile!.id);
  cardTitle.textContent = `${header} · ${heading}`;
  const hint = authoring ? "" : (lens === "player" ? "<p>Player lens — read only.</p>" : "<p>Switch to EDIT mode (Author lens) to change this tile.</p>");
  const ro = authoring ? "" : "readonly";
  const disabled = authoring ? "" : "disabled";
  const tasks = tile?.tasks ?? [];
  const rewards = tile?.rewards ?? [];
  const introFields = intro ? `
    <p class="intro-note">Act intro — board is not shown in-game. Child chapters keep a quest grid.</p>
    <label>INTRO IMAGE</label>
    <input id="intro-image" value="${escapeAttr(chapter.intro?.image ?? "")}" placeholder="skylore:textures/gui/intro/act_i.png" ${ro} />
    <label>INTRO BODY</label>
    <textarea id="intro-body" rows="8" ${ro}>${escapeText(chapter.intro?.body ?? "")}</textarea>
    <p class="intro-note"># title · ## subtitle · **bold** · *italic* · {#RRGGBB}text{/#} · {size:N}text{/size}</p>
    <label>INTRO PREVIEW</label>
    <div id="intro-preview" class="intro-preview">${previewIntroHtml(chapter.intro?.body ?? "")}</div>
  ` : `
    <label>TILE TITLE</label>
    <input id="tile-title" value="${escapeAttr(tile?.title ?? "")}" ${ro} />
    <label>DESCRIPTION</label>
    <textarea id="tile-body" rows="3" ${ro}>${escapeText(tile?.description ?? "")}</textarea>
    ${itemSelectHtml(`id="tile-item-select"`, tile?.icon?.item ?? "", !authoring, "ICON")}
    ${glyphPickerHtml(tile?.icon?.glyph ?? "", !authoring, "tile-glyph")}
    <label>QUESTLINE</label>
    ${tile ? questlineHtml(tile) : ""}
    <label>TASKS</label>
    ${tasks.map((task, index) => `
      <fieldset class="stack">
        <legend>TASK ${index + 1}</legend>
        <select data-task-type="${index}" ${disabled}>
          ${TASK_TYPES.map((type) => `<option value="${type}" ${task.type === type ? "selected" : ""}>${type}</option>`).join("")}
        </select>
        ${taskFields(task, index, ro, !authoring)}
        ${authoring ? `<button type="button" data-remove-task="${index}">REMOVE TASK</button>` : ""}
      </fieldset>
    `).join("")}
    ${authoring ? `<button type="button" id="add-task">+ TASK</button>` : ""}
    <label>REWARDS</label>
    ${rewards.map((reward, index) => `
      <fieldset class="stack">
        <legend>REWARD ${index + 1}</legend>
        <select data-reward-type="${index}" ${disabled}>
          ${REWARD_TYPES.map((type) => `<option value="${type}" ${reward.type === type ? "selected" : ""}>${type}</option>`).join("")}
        </select>
        ${rewardFields(reward, index, ro, !authoring)}
        ${authoring ? `<button type="button" data-remove-reward="${index}">REMOVE REWARD</button>` : ""}
      </fieldset>
    `).join("")}
    ${authoring ? `<button type="button" id="add-reward">+ REWARD</button>` : ""}
    ${authoring ? `<button type="button" id="remove-tile">REMOVE TILE</button>` : ""}
  `;
  const chapterFields = `
    <datalist id="pack-entities">${listOptions(catalog.entities)}</datalist>
    <datalist id="pack-blocks">${listOptions(catalog.blocks)}</datalist>
    <datalist id="pack-tags">${listOptions(catalog.tags)}</datalist>
    <datalist id="pack-advancements">${listOptions(catalog.advancements)}</datalist>
    <datalist id="pack-biomes">${listOptions(catalog.biomes)}</datalist>
    <datalist id="pack-structures">${listOptions(catalog.structures)}</datalist>
    <datalist id="pack-loot">${listOptions(catalog.loot)}</datalist>
    <label>CHAPTER TITLE</label>
    <input id="chapter-title" value="${escapeAttr(chapter.title ?? "")}" ${ro} />
    <label>CHAPTER ID</label>
    <input id="chapter-id" value="${escapeAttr(chapter.id)}" ${ro} />
    <label>PARENT CHAPTER</label>
    <select id="chapter-parent" ${disabled}>
      <option value="">(root)</option>
      ${packChapters.filter((entry) => entry.id !== chapter.id).map((entry) =>
        `<option value="${escapeAttr(entry.id)}" ${chapter.parent === entry.id ? "selected" : ""}>${escapeAttr(entry.title || entry.id)}</option>`
      ).join("")}
    </select>
    <label>ORDER</label>
    <input id="chapter-order" type="number" value="${chapter.order ?? 0}" ${ro} />
    <label>THEME</label>
    ${themeOptionHtml(chapter.theme, !authoring)}
    <div class="theme-swatch" style="display:flex;gap:2px;height:10px;margin:4px 0 8px">
      ${(() => {
        const t = themeById(chapter.theme);
        return [t.void, t.current, t.neu, t.completed, t.lockedEdge].map((c) =>
          `<span style="flex:1;background:${c}"></span>`
        ).join("");
      })()}
    </div>
    <label>BOARD BACKGROUND</label>
    <select id="chapter-bg-mode" ${disabled}>
      <option value="color" ${(chapter.background?.mode ?? "color") === "color" ? "selected" : ""}>Color</option>
      <option value="image" ${chapter.background?.mode === "image" ? "selected" : ""}>Image</option>
    </select>
    <label>BG COLOR</label>
    <div style="display:flex;gap:6px;align-items:center">
      <input id="chapter-bg-color-picker" type="color" value="${escapeAttr((chapter.background?.color ?? themeById(chapter.theme).void).replace(/^#?([0-9a-fA-F]{6}).*/, (_, h) => `#${h}`))}" ${disabled} />
      <input id="chapter-bg-color" value="${escapeAttr(chapter.background?.color ?? "")}" placeholder="#24142C" ${ro} />
    </div>
    <label>BG IMAGE</label>
    <select id="chapter-bg-stock" ${disabled}>
      <option value="">(custom / none)</option>
      ${STOCK_BACKGROUNDS.map((entry) =>
        `<option value="${escapeAttr(entry.id)}" ${chapter.background?.image === entry.id ? "selected" : ""}>${escapeAttr(entry.label)}</option>`
      ).join("")}
    </select>
    <input id="chapter-bg-image" value="${escapeAttr(chapter.background?.image ?? "")}" placeholder="questqueen:textures/gui/bg/midnight.png" ${ro} />
    <label>BG OPACITY</label>
    <input id="chapter-bg-opacity" type="number" min="0" max="1" step="0.05" value="${chapter.background?.opacity ?? 1}" ${ro} />
    ${itemSelectHtml(`id="chapter-icon-select"`, chapter.icon?.item ?? "", !authoring, "CHAPTER ICON")}
    ${glyphPickerHtml(chapter.icon?.glyph ?? "", !authoring, "chapter-glyph")}
    <label>UNLOCK CONDITIONS</label>
    ${unlockConditions(chapter).map((condition, index) => `
      <fieldset class="stack">
        <legend>CONDITION ${index + 1}</legend>
        <select data-unlock-type="${index}" ${disabled}>
          ${["quest_complete", "chapter_complete", "advancement", "trigger", "team_flag", "scoreboard"].map((type) =>
            `<option value="${type}" ${condition.type === type ? "selected" : ""}>${type}</option>`
          ).join("")}
        </select>
        <input data-unlock-id="${index}" value="${escapeAttr(condition.id)}" placeholder="questqueen:starter/go_nether" ${ro} />
        ${authoring ? `<button type="button" data-remove-unlock="${index}">REMOVE</button>` : ""}
      </fieldset>
    `).join("")}
    ${authoring ? `<button type="button" id="add-unlock">+ UNLOCK CONDITION</button>` : ""}
    <label class="check">
      <input id="chapter-hide-until" type="checkbox" ${chapter.hide_until_unlocked ? "checked" : ""} ${disabled} />
      Hide until all requirements are met
    </label>
  `;
  const primary = intro
    ? section("intro", "Act intro", "", introFields)
    : section("quest", "Quest", tile?.id ?? "", introFields);
  // A chapter with no selectable quest has nothing else to show, so its settings open by default.
  cardBody.innerHTML = `${hint}${primary}${section("chapter", "Chapter settings", chapter.id, chapterFields, !tile && !intro)}`;
  cardBody.querySelectorAll<HTMLDetailsElement>("details.section").forEach((el) => {
    el.addEventListener("toggle", () => { sectionOpen[el.dataset.section ?? ""] = el.open; });
  });
  const apply = () => {
    if (!authoring) return;
    chapter.title = (document.getElementById("chapter-title") as HTMLInputElement).value;
    const nextId = (document.getElementById("chapter-id") as HTMLInputElement).value.trim();
    if (nextId.includes(":")) chapter.id = nextId;
    const parent = (document.getElementById("chapter-parent") as HTMLSelectElement).value;
    if (parent) chapter.parent = parent;
    else delete chapter.parent;
    chapter.order = Number((document.getElementById("chapter-order") as HTMLInputElement).value || 0);
    chapter.theme = (document.getElementById("chapter-theme") as HTMLSelectElement).value || DEFAULT_THEME_ID;
    const bgMode = (document.getElementById("chapter-bg-mode") as HTMLSelectElement).value as "color" | "image";
    const bgColor = (document.getElementById("chapter-bg-color") as HTMLInputElement).value.trim();
    const bgImage = (document.getElementById("chapter-bg-image") as HTMLInputElement).value.trim();
    const bgOpacity = Number((document.getElementById("chapter-bg-opacity") as HTMLInputElement).value || 1);
    if (bgMode === "image" || bgColor || bgImage) {
      chapter.background = {
        mode: bgMode,
        ...(bgColor ? { color: bgColor } : {}),
        ...(bgImage ? { image: bgImage } : {}),
        opacity: Math.max(0, Math.min(1, bgOpacity)),
      };
    } else {
      delete chapter.background;
    }
    const chapterFace = iconPayload(readGlyph(cardBody, "#chapter-glyph"), readItemSelect(cardBody, "#chapter-icon-select"));
    if (chapterFace) chapter.icon = chapterFace;
    else delete chapter.icon;
    const conditions: UnlockCondition[] = [];
    cardBody.querySelectorAll<HTMLSelectElement>("[data-unlock-type]").forEach((select) => {
      const index = Number(select.dataset.unlockType);
      const idInput = cardBody.querySelector<HTMLInputElement>(`[data-unlock-id="${index}"]`);
      conditions.push({ type: select.value, id: idInput?.value ?? "" });
    });
    setUnlockConditions(chapter, conditions.filter((condition) => condition.id.trim()));
    const hideUntil = document.getElementById("chapter-hide-until") as HTMLInputElement | null;
    if (hideUntil?.checked) chapter.hide_until_unlocked = true;
    else delete chapter.hide_until_unlocked;
    if (intro) {
      const image = (document.getElementById("intro-image") as HTMLInputElement | null)?.value.trim() ?? "";
      const body = (document.getElementById("intro-body") as HTMLTextAreaElement | null)?.value ?? "";
      if (image || body) {
        chapter.intro = {
          ...(image ? { image } : {}),
          ...(body ? { body } : {}),
        };
      } else {
        delete chapter.intro;
      }
      const preview = document.getElementById("intro-preview");
      if (preview) preview.innerHTML = previewIntroHtml(body);
      cardTitle.textContent = `${header} · ${(chapter.title || chapter.id).toUpperCase()}`;
    } else if (tile) {
    tile.title = (document.getElementById("tile-title") as HTMLInputElement).value;
    tile.description = (document.getElementById("tile-body") as HTMLTextAreaElement).value;
    const tileFace = iconPayload(readGlyph(cardBody, "#tile-glyph"), readItemSelect(cardBody, "#tile-item-select"));
    if (tileFace) tile.icon = tileFace;
    else delete tile.icon;
    for (const input of cardBody.querySelectorAll<HTMLInputElement>("[data-task][data-field]:not(.item-select)")) {
      const task = tile.tasks?.[Number(input.dataset.task)];
      if (!task || !input.dataset.field) continue;
      const field = input.dataset.field;
      if (["count", "waves", "levels", "x", "y", "z", "radius"].includes(field)) {
        (task as unknown as Record<string, number>)[field] = Number(input.value || 0);
      } else {
        (task as unknown as Record<string, string>)[field] = input.value;
      }
    }
    for (const host of cardBody.querySelectorAll<HTMLElement>(".item-select[data-task]")) {
      const task = tile.tasks?.[Number(host.dataset.task)];
      const field = host.dataset.field;
      if (!task || !field) continue;
      (task as unknown as Record<string, string>)[field] = host.dataset.value ?? "";
    }
    for (const input of cardBody.querySelectorAll<HTMLInputElement>("[data-reward][data-field]:not(.item-select)")) {
      const reward = tile.rewards?.[Number(input.dataset.reward)];
      if (!reward || !input.dataset.field) continue;
      const field = input.dataset.field;
      if (["count", "amount", "levels"].includes(field)) {
        (reward as unknown as Record<string, number>)[field] = Number(input.value || 0);
      } else {
        (reward as unknown as Record<string, string>)[field] = input.value;
      }
    }
    for (const host of cardBody.querySelectorAll<HTMLElement>(".item-select[data-reward]")) {
      const reward = tile.rewards?.[Number(host.dataset.reward)];
      const field = host.dataset.field;
      if (!reward || !field) continue;
      const value = host.dataset.value ?? "";
      if (field === "option0" || field === "option1") {
        const slot = field === "option0" ? 0 : 1;
        reward.options = reward.options ?? [];
        reward.options[slot] = { type: "item", item: value, count: reward.options[slot]?.count ?? 1 };
      } else {
        (reward as unknown as Record<string, string>)[field] = value;
      }
    }
    cardTitle.textContent = `${header} · ${(tile.title ?? tile.id).toUpperCase()}`;
    }
    rememberChapter();
    renderChapterTree();
    draw();
  };
  wireItemSelects(cardBody, catalog.items, bridgeBase(), apply);
  wireGlyphPicker(cardBody, "#chapter-glyph", apply);
  wireGlyphPicker(cardBody, "#tile-glyph", apply);
  cardBody.querySelectorAll("input,textarea").forEach((el) => {
    el.addEventListener("input", apply);
    el.addEventListener("change", apply);
    el.addEventListener("mousedown", (event) => event.stopPropagation());
  });
  if (tile && !intro) {
  cardBody.querySelectorAll<HTMLSelectElement>("[data-task-type]").forEach((select) => {
    select.addEventListener("change", () => {
      const index = Number(select.dataset.taskType);
      tile.tasks = tile.tasks ?? [];
      tile.tasks[index] = defaultTask(select.value);
      showCard(tile);
      draw();
    });
  });
  cardBody.querySelectorAll<HTMLSelectElement>("[data-reward-type]").forEach((select) => {
    select.addEventListener("change", () => {
      const index = Number(select.dataset.rewardType);
      tile.rewards = tile.rewards ?? [];
      tile.rewards[index] = defaultReward(select.value);
      showCard(tile);
      draw();
    });
  });
  cardBody.querySelectorAll<HTMLButtonElement>("[data-remove-task]").forEach((button) => {
    button.addEventListener("click", () => {
      const index = Number(button.dataset.removeTask);
      tile.tasks?.splice(index, 1);
      showCard(tile);
      draw();
    });
  });
  cardBody.querySelectorAll<HTMLButtonElement>("[data-remove-reward]").forEach((button) => {
    button.addEventListener("click", () => {
      const index = Number(button.dataset.removeReward);
      tile.rewards?.splice(index, 1);
      showCard(tile);
      draw();
    });
  });
  document.getElementById("add-task")?.addEventListener("click", () => {
    tile.tasks = tile.tasks ?? [];
    tile.tasks.push(defaultTask("obtain"));
    showCard(tile);
    draw();
  });
  document.getElementById("add-reward")?.addEventListener("click", () => {
    tile.rewards = tile.rewards ?? [];
    tile.rewards.push(defaultReward("item"));
    showCard(tile);
    draw();
  });
  document.getElementById("remove-tile")?.addEventListener("click", () => {
    deleteSelectedTile();
  });
  cardBody.querySelectorAll<HTMLButtonElement>("[data-cycle-from]").forEach((button) => {
    button.addEventListener("click", () => {
      const from = button.dataset.cycleFrom ?? "";
      const to = button.dataset.cycleTo ?? "";
      if (!from || !to) return;
      openGateAsk(from, to, true);
    });
  });
  cardBody.querySelectorAll<HTMLButtonElement>("[data-remove-from]").forEach((button) => {
    button.addEventListener("click", () => {
      const from = button.dataset.removeFrom ?? "";
      const to = button.dataset.removeTo ?? "";
      const link = (chapter.links ?? []).find((entry) => entry.from === from && entry.to === to);
      if (!link) return;
      if (!confirmLinkDelete(from, to)) return;
      pushLinkUndo({ kind: "remove", from, to, gate: gateOp(link) });
      removeLink(chapter, from, to);
      showCard(tile);
      draw();
    });
  });
  }
  document.getElementById("add-unlock")?.addEventListener("click", () => {
    const conditions = unlockConditions(chapter);
    conditions.push({ type: "quest_complete", id: "" });
    setUnlockConditions(chapter, conditions);
    showCard(tile);
  });
  cardBody.querySelectorAll<HTMLButtonElement>("[data-remove-unlock]").forEach((button) => {
    button.addEventListener("click", () => {
      const conditions = unlockConditions(chapter);
      conditions.splice(Number(button.dataset.removeUnlock), 1);
      setUnlockConditions(chapter, conditions);
      showCard(tile);
    });
  });
  cardBody.querySelectorAll("[data-unlock-type],[data-unlock-id],#chapter-title,#chapter-id,#chapter-parent,#chapter-order,#chapter-theme,#chapter-bg-mode,#chapter-bg-color,#chapter-bg-image,#chapter-bg-opacity,#chapter-hide-until").forEach((el) => {
    el.addEventListener("change", apply);
    el.addEventListener("input", apply);
  });
  document.getElementById("chapter-theme")?.addEventListener("change", () => {
    apply();
    showCard(tile);
  });
  document.getElementById("chapter-bg-color-picker")?.addEventListener("input", (event) => {
    const hex = (event.target as HTMLInputElement).value;
    const field = document.getElementById("chapter-bg-color") as HTMLInputElement | null;
    if (field) field.value = hex;
    apply();
  });
  document.getElementById("chapter-bg-stock")?.addEventListener("change", () => {
    const stock = (document.getElementById("chapter-bg-stock") as HTMLSelectElement).value;
    const image = document.getElementById("chapter-bg-image") as HTMLInputElement | null;
    const mode = document.getElementById("chapter-bg-mode") as HTMLSelectElement | null;
    if (image && stock) image.value = stock;
    if (mode && stock) mode.value = "image";
    apply();
  });
}

function escapeAttr(value: string): string {
  return value.replace(/&/g, "&amp;").replace(/"/g, "&quot;").replace(/</g, "&lt;");
}

function escapeText(value: string): string {
  return value.replace(/&/g, "&amp;").replace(/</g, "&lt;");
}

function updatePackStatus() {
  if (packStatus) {
    packStatus.textContent = unlocked ? `Connected · ${catalog.items.length.toLocaleString()} items` : "Waiting for Minecraft";
    packStatus.classList.toggle("online", unlocked);
    packStatus.title = unlocked ? "Item catalog comes from the pack loaded in Minecraft" : "Run /questqueen editor in game";
  }
}

function bridgeBase(): string {
  return location.port === "47821" ? "" : "http://127.0.0.1:47821";
}

// QQ-2: the bridge requires the per-session nonce on every mutating request. The page holds
// the nonce itself: read it from GET /api/session and send it as X-Editor-Nonce on writes.
async function bridgeFetch(path: string, init: RequestInit = {}): Promise<Response> {
  const base = bridgeBase();
  const method = (init.method ?? "GET").toUpperCase();
  if (method !== "GET" && method !== "HEAD" && method !== "OPTIONS") {
    const session = await fetch(`${base}/api/session`);
    if (session.ok) {
      const body = (await session.json()) as { nonce?: string };
      if (body.nonce) {
        const headers = new Headers(init.headers ?? {});
        headers.set("X-Editor-Nonce", body.nonce);
        init = { ...init, headers };
      }
    }
  }
  return fetch(`${base}${path}`, init);
}

function setLocked(next: boolean, message: string) {
  unlocked = !next;
  if (next) {
    uiMode = "view"; if (typeof syncModeChrome === "function") syncModeChrome(); else syncAuthoring();
  }
  app?.classList.toggle("locked", next);
  if (lock) lock.hidden = !next;
  if (lockStatus) lockStatus.textContent = message;
  updatePackStatus();
}

async function syncFromMinecraft() {
  try {
    const status = await bridgeFetch("/api/status");
    if (!status.ok) {
      throw new Error("editor offline");
    }
    const catalogRes = await bridgeFetch("/api/catalog");
    if (!catalogRes.ok) {
      throw new Error("catalog unavailable");
    }
    catalog = fromApi(await catalogRes.json() as Record<string, unknown>);
    const firstConnect = !unlocked;
    if (firstConnect) {
      const chaptersRes = await bridgeFetch("/api/chapters");
      if (chaptersRes.ok) {
        const pack = await chaptersRes.json() as { chapters?: Chapter[] };
        if (pack.chapters?.length) {
          packChapters = pack.chapters.map((entry) => {
            const copy = structuredClone(entry);
            ensureTiles(copy);
            return copy;
          });
          chapter = packChapters[0];
          selected = chapter.tiles[0]?.id ?? selected;
          syncGridSizeSelect();
          renderChapterTree();
        }
      }
      uiMode = "edit"; lens = "author"; syncAuthoring();
    }
    setLocked(false, "");
    if (firstConnect) {
      // The header used to keep VIEW lit while the editor had switched itself to EDIT.
      syncModeChrome();
      updateLinkStatus();
      fitChapterBoard();
      draw();
      if (chapterHasChildren(chapter.id)) showCard();
      else {
        const tile = chapter.tiles.find((t) => t.id === selected) ?? chapter.tiles[0];
        if (tile) showCard(tile);
      }
    } else {
      updatePackStatus();
    }
  } catch {
    setLocked(true, "Waiting for /questqueen editor in Minecraft…");
  }
}

async function saveToWorld() {
  if (!unlocked) return;
  rememberChapter();
  const errors = validate(chapter);
  if (errors.length) {
    alert(errors.join("\n"));
    return;
  }
  const response = await bridgeFetch("/api/chapter", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(chapter),
  });
  if (!response.ok) {
    let reason = "Minecraft rejected the save. Is the editor still enabled?";
    try {
      const detail = (await response.json()) as { error?: string };
      if (detail?.error) reason = `Minecraft rejected the save: ${detail.error}`;
    } catch {
      /* non-JSON error body — keep the generic message */
    }
    alert(reason);
  }
}

function pointerCell(clientX: number, clientY: number) {
  const rect = canvas.getBoundingClientRect();
  const localX = clientX - rect.left;
  const localY = clientY - rect.top;
  const mx = localX + camX;
  const my = localY + camY;
  return { mx, my, localX, localY, gx: Math.floor(mx / stridePx()), gy: Math.floor(my / stridePx()) };
}

function placeTileAt(gx: number, gy: number) {
  let id = `tile_${gx}_${gy}`;
  if (chapter.tiles.some((t) => t.id === id)) id = `tile_${gx}_${gy}_${Date.now()}`;
  const created: Tile = { id, pos: { x: gx, y: gy }, title: "New tile", description: "", tasks: [], rewards: [] };
  chapter.tiles.push(created);
  selected = created.id;
  rememberChapter();
  showCard(created);
  draw();
}

function deleteSelectedTile() {
  if (!authoring || !selected) return;
  const tile = chapter.tiles.find((t) => t.id === selected);
  if (!tile) return;
  const label = tile.title || tile.id;
  if (!window.confirm(`Remove tile ${label}?\n\nYou can press Ctrl+Z to undo.`)) return;
  const links = (chapter.links ?? [])
    .filter((link) => link.from === tile.id || link.to === tile.id)
    .map((link) => ({ from: link.from, to: link.to, gate: gateOp(link) }));
  pushLinkUndo({ kind: "delete", tile: structuredClone(tile), links });
  chapter.links = (chapter.links ?? []).filter((link) => link.from !== tile.id && link.to !== tile.id);
  chapter.tiles = chapter.tiles.filter((entry) => entry.id !== tile.id);
  if (linkFrom === tile.id) linkFrom = "";
  if (pendingLink && (pendingLink.from === tile.id || pendingLink.to === tile.id)) pendingLink = null;
  selected = "";
  linkHint = "";
  setCardOpen(false);
  rememberChapter();
  updateLinkStatus();
  syncLinkConfirmChrome();
  draw();
}

function commitTileDrag(clientX: number, clientY: number) {
  if (!tileDrag) return;
  const drag = tileDrag;
  tileDrag = null;
  canvas.style.cursor = "";
  const tile = chapter.tiles.find((t) => t.id === drag.id);
  if (!tile) {
    draw();
    return;
  }
  const { gx, gy } = pointerCell(clientX, clientY);
  const occupied = chapter.tiles.some((t) => t.id !== tile.id && t.pos.x === gx && t.pos.y === gy);
  if ((gx !== drag.ox || gy !== drag.oy) && inGrid(chapter, gx, gy) && !occupied) {
    pushLinkUndo({ kind: "move", id: tile.id, fromX: drag.ox, fromY: drag.oy, toX: gx, toY: gy });
    tile.pos = { x: gx, y: gy };
    rememberChapter();
  }
  selected = tile.id;
  showCard(tile);
  draw();
}

function tileName(tile: Tile | undefined): string {
  return tile?.title || tile?.id || "this quest";
}

function openGateAsk(fromId: string, toId: string, existing: boolean) {
  const from = chapter.tiles.find((t) => t.id === fromId);
  const to = chapter.tiles.find((t) => t.id === toId);
  const panel = document.getElementById("gate-ask");
  const steps = document.getElementById("gate-ask-steps");
  const note = document.getElementById("gate-ask-note");
  const title = document.getElementById("gate-ask-title");
  const remove = document.getElementById("gate-ask-remove");
  if (!panel || !steps || !note || !title || !remove) return;
  gateAsk = { from: fromId, to: toId, existing };
  const current = existing
    ? gateOp((chapter.links ?? []).find((link) => link.from === fromId && link.to === toId) ?? { from: fromId, to: toId })
    : null;
  title.textContent = `How does ${tileName(to)} open?`;
  steps.innerHTML = [
    `You clicked the side between ${tileName(from)} and ${tileName(to)}.`,
    existing
      ? `This arrow already runs from ${tileName(from)} into ${tileName(to)}.`
      : `No arrow yet. Your choice draws one from ${tileName(from)} into ${tileName(to)}.`,
    `Pick a rule. Every quest that leads into ${tileName(to)} uses that same rule. Ctrl+Z undoes it.`,
  ].map((line) => `<li>${escapeText(line)}</li>`).join("");
  note.textContent = current
    ? `Current rule: ${current.toUpperCase()}.`
    : "AND is the usual start when only one quest leads in.";
  for (const button of panel.querySelectorAll<HTMLButtonElement>("[data-ask-gate]")) {
    button.classList.toggle("current", button.dataset.askGate === current);
  }
  remove.hidden = !existing;
  panel.hidden = false;
}

function closeGateAsk() {
  gateAsk = null;
  const panel = document.getElementById("gate-ask");
  if (panel) panel.hidden = true;
}

function applyGateAsk(op: GateOpName) {
  if (!gateAsk) return;
  const { from, to, existing } = gateAsk;
  if (existing) {
    const link = (chapter.links ?? []).find((entry) => entry.from === from && entry.to === to);
    const prev = link ? gateOp(link) : "and";
    if (prev !== op) {
      pushLinkUndo({ kind: "gate", from, to, prev, next: op });
      upsertLink(chapter, from, to, op);
    }
  } else {
    pushLinkUndo({ kind: "add", from, to, gate: op });
    upsertLink(chapter, from, to, op);
  }
  closeGateAsk();
  rememberChapter();
  linkHint = "";
  updateLinkStatus();
  const tile = chapter.tiles.find((t) => t.id === selected) ?? chapter.tiles.find((t) => t.id === to);
  if (tile) showCard(tile);
  draw();
}

function removeGateAsk() {
  if (!gateAsk?.existing) return;
  const { from, to } = gateAsk;
  const link = (chapter.links ?? []).find((entry) => entry.from === from && entry.to === to);
  if (!link) {
    closeGateAsk();
    return;
  }
  pushLinkUndo({ kind: "remove", from, to, gate: gateOp(link) });
  removeLink(chapter, from, to);
  closeGateAsk();
  rememberChapter();
  updateLinkStatus();
  const tile = chapter.tiles.find((t) => t.id === selected);
  if (tile) showCard(tile);
  draw();
}

function onSideClick(tile: Tile, side: SideName) {
  const [dx, dy] = SIDE_STEP[side];
  const nx = tile.pos.x + dx;
  const ny = tile.pos.y + dy;
  const way = SIDE_WORD[side];
  if (!inGrid(chapter, nx, ny)) {
    linkHint = `The board ends to the ${way}.`;
    updateLinkStatus();
    draw();
    return;
  }
  const neighbor = chapter.tiles.find((t) => t.pos.x === nx && t.pos.y === ny);
  if (!neighbor) {
    const name = tile.title || tile.id;
    if (!window.confirm(`Create a quest to the ${way} of “${name}”?\n\nIt links from this quest into the new one. Click that side again to choose AND, OR, or XOR.`)) return;
    let id = `tile_${nx}_${ny}`;
    if (chapter.tiles.some((t) => t.id === id)) id = `tile_${nx}_${ny}_${Date.now()}`;
    const created: Tile = { id, pos: { x: nx, y: ny }, title: "New tile", description: "", tasks: [], rewards: [] };
    chapter.tiles.push(created);
    pushLinkUndo({ kind: "spawn", tile: structuredClone(created), from: tile.id, gate: "and" });
    upsertLink(chapter, tile.id, created.id, "and");
    selected = created.id;
    rememberChapter();
    linkHint = "";
    updateLinkStatus();
    showCard(created);
    draw();
    return;
  }
  const outbound = (chapter.links ?? []).find((link) => link.from === tile.id && link.to === neighbor.id);
  const inbound = (chapter.links ?? []).find((link) => link.from === neighbor.id && link.to === tile.id);
  const link = outbound ?? inbound;
  selected = tile.id;
  showCard(tile);
  openGateAsk(link ? link.from : tile.id, link ? link.to : neighbor.id, Boolean(link));
}

function handleBoardClick(event: MouseEvent, tile: Tile | undefined, gx: number, gy: number) {
  if (tile) {
    selected = tile.id;
    showCard(tile);
    draw();
    return;
  }
  if (authoring && inGrid(chapter, gx, gy)) placeTileAt(gx, gy);
}

function finishPress(event: MouseEvent) {
  if (!press) return;
  const current = press;
  press = null;
  if (current.moved && tileDrag) {
    commitTileDrag(event.clientX, event.clientY);
    return;
  }
  if (current.moved) return;
  if (canLink()) {
    const hit = sideAt(event.clientX, event.clientY);
    if (hit) {
      onSideClick(hit.tile, hit.side);
      return;
    }
  }
  const { gx, gy } = pointerCell(event.clientX, event.clientY);
  const tile = chapter.tiles.find((t) => t.pos.x === gx && t.pos.y === gy);
  handleBoardClick(event, tile, gx, gy);
}

canvas.addEventListener("mousedown", (event) => {
  if (event.button === 1 || event.button === 2) {
    dragging = true;
    press = null;
    tileDrag = null;
    canvas.style.cursor = "";
    lastX = event.clientX;
    lastY = event.clientY;
    return;
  }
  if (event.button !== 0) return;
  if (chapterHasChildren(chapter.id)) {
    showCard();
    return;
  }
  ensureTiles(chapter);
  const { gx, gy } = pointerCell(event.clientX, event.clientY);
  if (canLink()) {
    const edge = hitEdgeChip(event.clientX, event.clientY);
    if (edge) {
      openGateAsk(edge.from, edge.to, true);
      return;
    }
  }
  const tile = chapter.tiles.find((t) => t.pos.x === gx && t.pos.y === gy);
  press = {
    kind: tile ? "tile" : "empty",
    id: tile?.id,
    startX: event.clientX,
    startY: event.clientY,
    moved: false,
  };
});
canvas.addEventListener("contextmenu", (e) => e.preventDefault());
canvas.addEventListener("mousemove", (event) => {
  if (dragging) {
    camX -= event.clientX - lastX;
    camY -= event.clientY - lastY;
    lastX = event.clientX;
    lastY = event.clientY;
    draw();
    return;
  }
  const cell = pointerCell(event.clientX, event.clientY);
  const next = inGrid(chapter, cell.gx, cell.gy) ? { x: cell.gx, y: cell.gy } : null;
  let dirty = next?.x !== hoverCell?.x || next?.y !== hoverCell?.y;
  hoverCell = next;
  const side = sideAt(event.clientX, event.clientY);
  const sideKey = side ? `${side.tile.id}:${side.side}` : "";
  const prevSide = hoverSide ? `${hoverSide.id}:${hoverSide.side}` : "";
  if (sideKey !== prevSide) {
    hoverSide = side ? { id: side.tile.id, side: side.side } : null;
    dirty = true;
  }
  if (press && !press.moved) {
    const dx = event.clientX - press.startX;
    const dy = event.clientY - press.startY;
    if (dx * dx + dy * dy > 16) {
      press.moved = true;
      dirty = true;
      if (press.kind === "tile" && authoring && press.id) {
        const tile = chapter.tiles.find((t) => t.id === press!.id);
        if (tile) {
          const origin = screen(tile.pos.x, tile.pos.y);
          tileDrag = {
            id: tile.id,
            ox: tile.pos.x,
            oy: tile.pos.y,
            px: origin.x,
            py: origin.y,
            grabX: cell.localX - origin.x,
            grabY: cell.localY - origin.y,
          };
          canvas.style.cursor = "grabbing";
        }
      }
    }
  }
  if (tileDrag) {
    tileDrag.px = cell.localX - tileDrag.grabX;
    tileDrag.py = cell.localY - tileDrag.grabY;
    dirty = true;
  }
  if (dirty) draw();
});
canvas.addEventListener("mouseleave", () => {
  if (tileDrag) return;
  const had = hoverCell || hoverSide;
  hoverCell = null;
  hoverSide = null;
  if (had) draw();
});
window.addEventListener("mouseup", (event) => {
  if (event.button === 0 && press) finishPress(event);
  dragging = false;
});

search.addEventListener("keydown", (event) => {
  if (event.key !== "Enter") return;
  const q = search.value.trim().toLowerCase();
  if (!q) {
    searchHits = null;
    draw();
    return;
  }
  const matches = chapter.tiles.filter((t) => (t.title ?? t.id).toLowerCase().includes(q));
  searchHits = new Set(matches.map((t) => t.id));
  const hit = matches[0];
  if (hit) {
    selected = hit.id;
    // center on hit
    camX = hit.pos.x * stridePx() - viewW / 2 + tileSize() / 2;
    camY = hit.pos.y * stridePx() - viewH / 2 + tileSize() / 2;
    showCard(hit);
  }
  draw();
});
search.addEventListener("input", () => {
  if (!search.value.trim()) {
    searchHits = null;
    draw();
  }
});


function selectedTiles(): Tile[] {
  if (!selected) return [...(chapter.tiles ?? [])];
  const one = chapter.tiles.find((t) => t.id === selected);
  return one ? [one] : [...(chapter.tiles ?? [])];
}

function layoutPack() {
  if (!authoring && !(lens === "author")) return;
  const tiles = [...(chapter.tiles ?? [])];
  if (!tiles.length) return;
  const starts = startsOf(chapter);
  const roots = tiles.filter((t) => starts.has(t.id));
  const ordered = roots.length ? roots : [tiles[0]];
  const seen = new Set<string>();
  const queue: Tile[] = [];
  for (const r of ordered) {
    queue.push(r);
    seen.add(r.id);
  }
  // BFS columns from starts
  const cols: Tile[][] = [];
  let frontier = [...ordered];
  while (frontier.length) {
    cols.push(frontier);
    const next: Tile[] = [];
    for (const node of frontier) {
      for (const link of chapter.links ?? []) {
        if (link.from !== node.id) continue;
        if (seen.has(link.to)) continue;
        const dest = tiles.find((t) => t.id === link.to);
        if (!dest) continue;
        seen.add(dest.id);
        next.push(dest);
      }
    }
    frontier = next;
  }
  for (const orphan of tiles) {
    if (!seen.has(orphan.id)) {
      cols.push([orphan]);
      seen.add(orphan.id);
    }
  }
  let x = 0;
  for (const col of cols) {
    let y = 0;
    for (const tile of col) {
      tile.pos = { x, y };
      y += 1;
    }
    x += 1;
  }
  rememberChapter();
  draw();
}

function layoutAlignSelection() {
  const tiles = selected ? chapter.tiles.filter((t) => t.id === selected) : [];
  // Align all tiles sharing selected row (y) to same y; if none selected, align each column's x
  if (selected) {
    const sel = chapter.tiles.find((t) => t.id === selected);
    if (!sel) return;
    const row = sel.pos.y;
    for (const t of chapter.tiles) {
      if (Math.abs(t.pos.y - row) <= 0) t.pos = { ...t.pos, y: row };
    }
  } else {
    // snap all to integer grid (already int) — compact x gaps
    const sorted = [...chapter.tiles].sort((a, b) => a.pos.x - b.pos.x || a.pos.y - b.pos.y);
    let x = 0;
    let last = -999;
    const map = new Map<number, number>();
    for (const t of sorted) {
      if (!map.has(t.pos.x)) {
        map.set(t.pos.x, x);
        x += 1;
      }
      t.pos = { x: map.get(t.pos.x)!, y: t.pos.y };
    }
  }
  rememberChapter();
  draw();
}

function layoutSpreadRow() {
  const tiles = [...chapter.tiles].sort((a, b) => a.pos.x - b.pos.x || a.pos.y - b.pos.y);
  if (!tiles.length) return;
  const y = selected ? (chapter.tiles.find((t) => t.id === selected)?.pos.y ?? 0) : tiles[0].pos.y;
  const row = tiles.filter((t) => t.pos.y === y);
  row.forEach((t, i) => { t.pos = { x: i, y }; });
  rememberChapter();
  draw();
}

function syncModeChrome() {
  syncAuthoring();
  for (const id of ["mode-edit", "mode-link", "mode-view"] as const) {
    const btn = document.getElementById(id);
    btn?.classList.toggle("active", btn.dataset.mode === uiMode);
  }
  for (const id of ["lens-author", "lens-player"] as const) {
    const btn = document.getElementById(id);
    btn?.classList.toggle("active", btn.dataset.lens === lens);
  }
  const gateGroup = document.getElementById("gate-group");
  if (gateGroup) gateGroup.hidden = !(lens === "author" && uiMode === "link");
  app?.classList.toggle("lens-player", lens === "player");
  app?.classList.toggle("lens-author", lens === "author");
  if (uiMode !== "link") { linkFrom = ""; pendingLink = null; linkHint = ""; closeGateAsk(); syncLinkConfirmChrome(); }
  else { /* keep */ }
  updateLinkStatus();
  const tile = chapter.tiles.find((t) => t.id === selected);
  if (tile) showCard(tile);
  else if (!chapterHasChildren(chapter.id)) setCardOpen(false);
  draw();
}

function setUiMode(next: UiMode) {
  if (!unlocked && next !== "view") return;
  if (lens === "player" && next !== "view") {
    // Player lens is proof-only
    uiMode = "view";
  } else {
    uiMode = next;
  }
  syncModeChrome();
}

function setLens(next: Lens) {
  if (!unlocked && next === "author") {
    // still allow switching UI chrome while locked? keep author default once unlocked
  }
  lens = next;
  if (lens === "player") uiMode = "view";
  syncModeChrome();
}

document.getElementById("mode-edit")?.addEventListener("click", () => setUiMode("edit"));
document.getElementById("mode-link")?.addEventListener("click", () => setUiMode("link"));
document.getElementById("mode-view")?.addEventListener("click", () => setUiMode("view"));
document.getElementById("lens-author")?.addEventListener("click", () => setLens("author"));
document.getElementById("lens-player")?.addEventListener("click", () => setLens("player"));


function setPendingGate(op: GateOpName) {
  pendingGate = op;
  syncGateButtons();
  if (gateAsk) applyGateAsk(op);
  else updateLinkStatus();
}

function syncGateButtons() {
  document.getElementById("gate-and")?.classList.toggle("active", pendingGate === "and");
  document.getElementById("gate-or")?.classList.toggle("active", pendingGate === "or");
  document.getElementById("gate-xor")?.classList.toggle("active", pendingGate === "xor");
}


function clearPendingLink() {
  pendingLink = null;
  linkFrom = "";
  syncLinkConfirmChrome();
  updateLinkStatus();
  draw();
}

function commitPendingLink() {
  if (!pendingLink) return;
  pushLinkUndo({ kind: "add", from: pendingLink.from, to: pendingLink.to, gate: pendingLink.gate });
  upsertLink(chapter, pendingLink.from, pendingLink.to, pendingLink.gate);
  pendingLink = null;
  linkFrom = "";
  syncLinkConfirmChrome();
  updateLinkStatus();
  draw();
}

type EdgeHit = { from: string; to: string };
function hitEdgeChip(clientX: number, clientY: number): EdgeHit | null {
  if (!canLink()) return null; // chips / cycle only in Link mode
  const rect = canvas.getBoundingClientRect();
  const mx = clientX - rect.left;
  const my = clientY - rect.top;
  const size = tileSize();
  for (const link of chapter.links ?? []) {
    const from = chapter.tiles.find((t) => t.id === link.from);
    const to = chapter.tiles.find((t) => t.id === link.to);
    if (!from || !to) continue;
    const a = screen(from.pos.x, from.pos.y);
    const b = screen(to.pos.x, to.pos.y);
    const x1 = a.x + size / 2;
    const y1 = a.y + size / 2;
    const x2 = b.x + size / 2;
    const y2 = b.y + size / 2;
    const mxMid = x1 === x2 ? x1 + 4 : (x1 + x2) / 2;
    const myMid = y1 === y2 ? (y1 + y2) / 2 : y1;
    if (Math.abs(mx - mxMid) <= 18 && Math.abs(my - myMid) <= 12) {
      return { from: link.from, to: link.to };
    }
  }
  return null;
}

function syncLinkConfirmChrome() {
  const chip = document.getElementById("link-confirm");
  if (!chip) return;
  if (!pendingLink) {
    chip.hidden = true;
    return;
  }
  chip.hidden = false;
  const label = document.getElementById("link-confirm-label");
  if (label) {
    label.textContent = `${pendingLink.from} → ${pendingLink.to} [${pendingLink.gate.toUpperCase()}]`;
  }
}

function updateLinkStatus() {
  if (!linkStatus) return;
  if (lens === "player") {
    linkStatus.textContent = "Player lens — proof only";
    return;
  }
  if (uiMode === "view") {
    linkStatus.textContent = "View mode — select a tile to inspect";
    return;
  }
  if (uiMode === "edit") {
    linkStatus.textContent = "Edit mode — click an empty cell to place · drag a tile to move";
    return;
  }
  if (linkHint) {
    linkStatus.textContent = linkHint;
    return;
  }
  linkStatus.textContent = "Link — hover a side. Empty side: create the next quest. Side with a quest: choose AND, OR, or XOR.";
}


document.getElementById("link-confirm-yes")?.addEventListener("click", () => commitPendingLink());
document.getElementById("link-confirm-no")?.addEventListener("click", () => clearPendingLink());
document.getElementById("gate-and")?.addEventListener("click", () => setPendingGate("and"));
document.getElementById("gate-or")?.addEventListener("click", () => setPendingGate("or"));
document.getElementById("gate-xor")?.addEventListener("click", () => setPendingGate("xor"));
document.getElementById("gate-ask")?.addEventListener("click", (event) => {
  const button = (event.target as HTMLElement).closest<HTMLButtonElement>("[data-ask-gate]");
  const op = button?.dataset.askGate;
  if (op === "and" || op === "or" || op === "xor") applyGateAsk(op);
});
document.getElementById("gate-ask-cancel")?.addEventListener("click", () => closeGateAsk());
document.getElementById("gate-ask-remove")?.addEventListener("click", () => removeGateAsk());

const gridSizeSelect = document.getElementById("grid-size") as HTMLSelectElement | null;

function syncGridSizeSelect() {
  if (!gridSizeSelect) return;
  const key = `${chapterGridWidth(chapter)}x${chapterGridHeight(chapter)}`;
  const match = GRID_PRESETS.some((preset) => `${preset.width}x${preset.height}` === key);
  gridSizeSelect.value = match ? key : "custom";
}

function applyGridSize(width: number, height: number) {
  chapter.grid_width = width;
  chapter.grid_height = height;
  rememberChapter();
  syncGridSizeSelect();
  draw();
}

gridSizeSelect?.addEventListener("change", () => {
  if (!unlocked || !gridSizeSelect) return;
  if (gridSizeSelect.value === "custom") {
    const widthRaw = window.prompt("Grid width (1-128)", String(chapterGridWidth(chapter)));
    const heightRaw = window.prompt("Grid height (1-128)", String(chapterGridHeight(chapter)));
    if (widthRaw == null || heightRaw == null) {
      syncGridSizeSelect();
      return;
    }
    const width = Math.max(1, Math.min(128, Number(widthRaw) || chapterGridWidth(chapter)));
    const height = Math.max(1, Math.min(128, Number(heightRaw) || chapterGridHeight(chapter)));
    applyGridSize(width, height);
    return;
  }
  const [widthText, heightText] = gridSizeSelect.value.split("x");
  applyGridSize(Number(widthText), Number(heightText));
});

syncGridSizeSelect();

window.addEventListener("keydown", (event) => {
  if (event.key === "Escape" && !document.getElementById("guide")?.hidden) {
    closeGuide();
    return;
  }
  if (!unlocked) return;
  if ((event.ctrlKey || event.metaKey) && (event.key === "z" || event.key === "Z")) {
    const t = event.target as HTMLElement | null;
    const tag = (t?.tagName ?? "").toLowerCase();
    if (tag === "input" || tag === "textarea" || t?.isContentEditable) return; // Worf V6
    event.preventDefault();
    undoLastLinkOp();
    return;
  }
  if ((event.key === "Delete" || event.key === "Backspace") && authoring) {
    const t = event.target as HTMLElement | null;
    const tag = (t?.tagName ?? "").toLowerCase();
    if (tag === "input" || tag === "textarea" || t?.isContentEditable) return;
    event.preventDefault();
    deleteSelectedTile();
    return;
  }
  // Data S1: gate hotkeys only when Link can fire (Author+Link)
  if (canLink()) {
    if (event.key === "1") setPendingGate("and");
    if (event.key === "2") setPendingGate("or");
    if (event.key === "3") setPendingGate("xor");
  }
  if (event.key === "Escape") {
    tileDrag = null;
    press = null;
    canvas.style.cursor = "";
    linkFrom = "";
    linkHint = "";
    pendingLink = null;
    closeGateAsk();
    searchHits = null; // Data optional: Esc clears search dim
    const searchEl = document.getElementById("search") as HTMLInputElement | null;
    if (searchEl) searchEl.value = "";
    syncLinkConfirmChrome();
    updateLinkStatus();
    draw();
  }
});

document.getElementById("template-blank")!.addEventListener("click", () => {
  if (!unlocked) return;
  chapter = structuredClone(BLANK);
  packChapters = [chapter];
  selected = "start";
  linkFrom = "";
  updateLinkStatus();
  syncGridSizeSelect();
  renderChapterTree();
  draw();
  showCard(chapter.tiles[0]);
});
document.getElementById("template-starter")!.addEventListener("click", () => {
  if (!unlocked) return;
  chapter = structuredClone(STARTER);
  packChapters = [chapter];
  selected = "make_chest";
  linkFrom = "";
  updateLinkStatus();
  syncGridSizeSelect();
  renderChapterTree();
  draw();
  showCard(chapter.tiles[0]);
});

document.getElementById("load-zip")!.addEventListener("click", () => { if (unlocked) fileZip.click(); });

canvas.addEventListener("wheel", (event) => {
  event.preventDefault();
  const rect = canvas.getBoundingClientRect();
  const ax = event.clientX - rect.left;
  const ay = event.clientY - rect.top;
  const factor = event.deltaY < 0 ? 1.1 : 1 / 1.1;
  setZoomAt(zoom * factor, ax, ay);
}, { passive: false });


document.getElementById("layout-pack")?.addEventListener("click", () => layoutPack());
document.getElementById("layout-align")?.addEventListener("click", () => layoutAlignSelection());
document.getElementById("layout-spread")?.addEventListener("click", () => layoutSpreadRow());
document.getElementById("zoom-in")?.addEventListener("click", () => {
  setZoomAt(zoom * 1.15, viewW / 2, viewH / 2);
});
document.getElementById("zoom-out")?.addEventListener("click", () => {
  setZoomAt(zoom / 1.15, viewW / 2, viewH / 2);
});
document.getElementById("zoom-fit")?.addEventListener("click", () => {
  fitChapterBoard();
  draw();
});


async function writeToPack() {
  if (!unlocked) return;
  rememberChapter();
  const problems = validate(chapter);
  if (problems.length) {
    alert(`Cannot WRITE TO PACK:\n\n${problems.join("\n")}`);
    return;
  }
  const id = chapter.id || "(unnamed)";
  const tiles = chapter.tiles?.length ?? 0;
  const links = chapter.links?.length ?? 0;
  const ok = window.confirm(
    `WRITE TO PACK?\n\nChapter: ${id}\nTiles: ${tiles}\nLinks: ${links}\n\nThis overwrites pack datapack files. Save to World only hot-reloads the open world and is safer for drafts.`,
  );
  if (!ok) return;
  // Prefer bridge write endpoint when present; else fall back to export zip with a clear note.
  try {
    const res = await bridgeFetch("/api/pack/write", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ chapter }),
    });
    if (res.ok) {
      if (packStatus) packStatus.textContent = `Wrote pack · ${id}`;
      return;
    }
  } catch {
    /* bridge may not expose pack write yet */
  }
  alert("Pack write endpoint unavailable in this session. Use EXPORT ZIP as a backup, then place files into the pack manually. Save to World remains the draft path.");
}

document.getElementById("write-pack")?.addEventListener("click", () => { void writeToPack(); });

document.getElementById("save-world")?.addEventListener("click", () => { void saveToWorld(); });
fileZip.addEventListener("change", async () => {
  if (!unlocked) return;
  const file = fileZip.files?.[0];
  if (!file) return;
  const zip = await JSZip.loadAsync(file);
  for (const [name, entry] of Object.entries(zip.files)) {
    if (name.replaceAll("\\", "/").includes("/questqueen/chapters/") && name.endsWith(".json")) {
      chapter = JSON.parse(await entry.async("string"));
      ensureTiles(chapter);
      const existing = packChapters.findIndex((entryChapter) => entryChapter.id === chapter.id);
      if (existing >= 0) packChapters[existing] = chapter;
      else packChapters.push(chapter);
      selected = chapter.tiles[0]?.id ?? "";
      syncGridSizeSelect();
      renderChapterTree();
      draw();
      if (chapter.tiles[0]) showCard(chapter.tiles[0]);
    }
  }
});

document.getElementById("export-zip")!.addEventListener("click", async () => {
  const errors = validate(chapter);
  if (errors.length) {
    alert(errors.join("\n"));
    return;
  }
  const zip = new JSZip();
  const ns = chapter.id.split(":")[0];
  const path = chapter.id.split(":")[1];
  zip.file("pack.mcmeta", JSON.stringify({ pack: { pack_format: 48, description: "Quest Queen export" } }, null, 2));
  zip.file(`data/${ns}/questqueen/chapters/${path}.json`, JSON.stringify(chapter, null, 2));
  const blob = await zip.generateAsync({ type: "blob" });
  const a = document.createElement("a");
  a.href = URL.createObjectURL(blob);
  a.download = `${path}.zip`;
  a.click();
});

function validate(next: Chapter): string[] {
  const errors: string[] = [];
  if (!next.id || !next.id.includes(":")) errors.push("Chapter id must be namespace:path");
  if (!Array.isArray(next.tiles)) errors.push("tiles[] required");
  const tiles = next.tiles ?? [];
  const links = next.links ?? [];
  const ids = new Set(tiles.map((t) => t.id));
  for (const link of links) {
    if (!ids.has(link.from) || !ids.has(link.to)) errors.push(`Link ${link.from}->${link.to} missing tile`);
  }
  // Cycles (directed)
  const adj = new Map<string, string[]>();
  for (const id of ids) adj.set(id, []);
  for (const link of links) {
    if (ids.has(link.from) && ids.has(link.to)) adj.get(link.from)!.push(link.to);
  }
  const visiting = new Set<string>();
  const visited = new Set<string>();
  const stack: string[] = [];
  const dfs = (node: string): boolean => {
    if (visiting.has(node)) {
      const i = stack.indexOf(node);
      errors.push(`Cycle: ${[...stack.slice(i), node].join(" → ")}`);
      return true;
    }
    if (visited.has(node)) return false;
    visiting.add(node);
    stack.push(node);
    for (const nextId of adj.get(node) ?? []) {
      if (dfs(nextId)) return true;
    }
    stack.pop();
    visiting.delete(node);
    visited.add(node);
    return false;
  };
  for (const id of ids) {
    if (!visited.has(id) && dfs(id)) break;
  }
  // XOR outbound fan-out must be >= 2 (Worf: fan-out < 2 is illegal)
  const xorOut = new Map<string, number>();
  for (const link of links) {
    if (gateOp(link) !== "xor") continue;
    xorOut.set(link.from, (xorOut.get(link.from) ?? 0) + 1);
  }
  for (const [from, count] of xorOut) {
    if (count < 2) errors.push(`XOR from ${from} has fan-out ${count} (need ≥ 2)`);
  }
  // Orphans: tiles with no path from any start (no inbound and not a start if chapter has links)
  if (links.length && tiles.length) {
    const inbound = new Set(links.map((l) => l.to));
    const starts = tiles.filter((t) => !inbound.has(t.id)).map((t) => t.id);
    const reach = new Set<string>();
    const q = [...starts];
    for (const s of starts) reach.add(s);
    while (q.length) {
      const n = q.shift()!;
      for (const d of adj.get(n) ?? []) {
        if (reach.has(d)) continue;
        reach.add(d);
        q.push(d);
      }
    }
    for (const t of tiles) {
      if (!reach.has(t.id)) errors.push(`Orphan tile (unreachable): ${t.id}`);
    }
  }
  void schema;
  return errors;
}

const GUIDE_KEY = "questqueen.editor.guide";
const GUIDE: { title: string; body: string; target: string }[] = [
  {
    title: "Start in Author",
    body: "AUTHOR is you, changing the quests. PLAYER is a look at what the player sees, and it does not edit. Stay on AUTHOR while you build.",
    target: "#lens-author",
  },
  {
    title: "Three tools",
    body: "EDIT places and changes quests. LINK connects them. VIEW looks without changing. The line next to these buttons always says what the current tool does.",
    target: ".mode-group",
  },
  {
    title: "Place a quest",
    body: "Switch to EDIT. An empty cell shows a dashed NEW. Click it to create a quest. Click the quest itself to type its title, tasks, and rewards in the inspector on the right. Drag the quest to another empty cell to move it. Delete removes it. Ctrl+Z puts it back.",
    target: "#mode-edit",
  },
  {
    title: "Link from the sides",
    body: "Switch to LINK and hover a quest. The four sides light up. Click an empty side and it asks to create the next quest in that direction. Click a side that already meets a quest and it asks how that quest opens.",
    target: "#mode-link",
  },
  {
    title: "AND, OR, and XOR",
    body: "AND means every quest leading in must be finished. OR means any one of them is enough. XOR means the player picks exactly one path and the others stay closed. The same rule applies to every arrow into that quest. The panel lists the steps. Ctrl+Z undoes the choice.",
    target: "#mode-link",
  },
  {
    title: "Move around the board",
    body: "Drag with the right mouse button to pan. The wheel, or − and +, zooms. FIT frames the whole chapter. The map in the corner shows where you are. SEARCH jumps to a quest by name.",
    target: ".zoom-group",
  },
  {
    title: "Save in the right place",
    body: "SAVE TO WORLD updates the game you have open. EXPORT ZIP is a backup file. WRITE TO PACK writes the chapter into the pack itself. Those are three different actions. Chapters on the left switch which board you are editing.",
    target: "#save-world",
  },
];
let guideStep = 0;

function clearGuideTarget() {
  document.querySelectorAll(".guide-target").forEach((el) => el.classList.remove("guide-target"));
}

function renderGuide() {
  const step = GUIDE[guideStep];
  const panel = document.getElementById("guide");
  const title = document.getElementById("guide-title");
  const body = document.getElementById("guide-body");
  const count = document.getElementById("guide-count");
  const back = document.getElementById("guide-back") as HTMLButtonElement | null;
  const next = document.getElementById("guide-next");
  if (!panel || !title || !body || !count || !next) return;
  clearGuideTarget();
  title.textContent = step.title;
  body.textContent = step.body;
  count.textContent = `Step ${guideStep + 1} of ${GUIDE.length}`;
  if (back) back.hidden = guideStep === 0;
  next.textContent = guideStep === GUIDE.length - 1 ? "Done" : "Next";
  const target = document.querySelector(step.target) as HTMLElement | null;
  const visible = target && !target.hidden && target.getClientRects().length > 0;
  (visible ? target : document.getElementById("show-guide"))?.classList.add("guide-target");
  panel.hidden = false;
}

function openGuide(step = 0) {
  guideStep = Math.max(0, Math.min(GUIDE.length - 1, step));
  renderGuide();
}

function closeGuide() {
  clearGuideTarget();
  const panel = document.getElementById("guide");
  if (panel) panel.hidden = true;
  localStorage.setItem(GUIDE_KEY, "seen");
}

document.getElementById("show-guide")?.addEventListener("click", () => openGuide(0));
document.getElementById("guide-next")?.addEventListener("click", () => {
  if (guideStep >= GUIDE.length - 1) closeGuide();
  else {
    guideStep += 1;
    renderGuide();
  }
});
document.getElementById("guide-back")?.addEventListener("click", () => {
  if (guideStep === 0) return;
  guideStep -= 1;
  renderGuide();
});
document.getElementById("guide-close")?.addEventListener("click", () => closeGuide());

// Startup runs last, after every module-level let/const above is initialised. It used to run near the top,
// and setLocked -> draw -> ensureBoardFx read `boardFx` in its temporal dead zone: the editor threw on load
// and sat on "Bridge offline" even with Minecraft connected.
wireCardWindow();
applyCardGeom();
setLocked(true, "Waiting for Minecraft…");
syncGateButtons();
updateLinkStatus();
renderChapterTree();
window.addEventListener("resize", resize);
resize();
void syncFromMinecraft();
window.setInterval(() => { void syncFromMinecraft(); }, 2000);
if (!localStorage.getItem(GUIDE_KEY)) openGuide(0);
