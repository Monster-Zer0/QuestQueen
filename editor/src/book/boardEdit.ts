// Editing on the board (Edit mode only): add a quest in an empty cell, drag a quest to move it, drag one of the
// selected quest's blue diamonds onto a neighbour to link it (or onto an empty neighbour to add a linked quest),
// click an arrow to change how its quest opens, and the keyboard: arrows move the selection, Alt+arrows move the
// quest, N adds, Ctrl+D copies, Delete removes, Ctrl+Z / Ctrl+Shift+Z undo and redo. Linked quests always share a
// row or column: a move that would put a link on a diagonal is refused with a notice.
import { newTile } from "../model";
import { chapterGridHeight, chapterGridWidth, gateOp, straightLink, upsertLink, wouldCreateCycle, removeLink, type Chapter, type GateOpName, type GridPos, type Tile } from "../types";
import { SIDE_STEP, type Board, type Side } from "./board";
import { ask, choose } from "./dialog";
import { emptyTask } from "./sentences";
import type { Store } from "./store";

const DRAG_START_PX = 4;

export const GATE_CHOICES: { label: string; hint: string; value: GateOpName }[] = [
  { label: "AND", hint: "Opens when every quest leading here is finished.", value: "and" },
  { label: "OR", hint: "Opens when any one quest leading here is finished.", value: "or" },
  { label: "NOT", hint: "Open only until a quest leading here is finished, then closed for good.", value: "not" },
  { label: "FORK", hint: "Makes the earlier quest a fork: the player picks one of its paths and the others close.", value: "xor" },
];

const name = (tile: Tile | undefined) => tile?.title || tile?.id || "this quest";
const inGrid = (ch: Chapter, p: GridPos) => p.x >= 0 && p.y >= 0 && p.x < chapterGridWidth(ch) && p.y < chapterGridHeight(ch);
const tileAt = (ch: Chapter, p: GridPos) => ch.tiles.find((t) => t.pos.x === p.x && t.pos.y === p.y);

/** Quests linked to `id` that would sit off its row and column if it stood at `at`. */
export function diagonalPartners(ch: Chapter, id: string, at: GridPos): Tile[] {
  return (ch.links ?? [])
    .filter((link) => link.from === id || link.to === id)
    .map((link) => ch.tiles.find((t) => t.id === (link.from === id ? link.to : link.from)))
    .filter((other): other is Tile => Boolean(other) && !straightLink(at, other!.pos));
}

function uniqueTileId(ch: Chapter, base: string): string {
  const taken = new Set(ch.tiles.map((t) => t.id));
  let id = base;
  for (let n = 2; taken.has(id); n++) id = `${base}_${n}`;
  return id;
}

/** The nearest empty grid cell scanning right along the row, then down the column, then outward. */
function nearestEmpty(ch: Chapter, from: GridPos): GridPos | null {
  const w = chapterGridWidth(ch);
  const hh = chapterGridHeight(ch);
  for (let r = 1; r < Math.max(w, hh) * 2; r++) {
    for (const p of [{ x: from.x + r, y: from.y }, { x: from.x, y: from.y + r }, { x: from.x - r, y: from.y }, { x: from.x, y: from.y - r }]) {
      if (inGrid(ch, p) && !tileAt(ch, p)) return p;
    }
  }
  return null;
}

/** KeyNav.next: the nearest quest strictly in the arrow's direction, by distance along + 2× sideways. */
function nextInDirection(ch: Chapter, from: Tile, dx: number, dy: number): Tile | undefined {
  let best: Tile | undefined;
  let bestScore = Infinity;
  for (const t of ch.tiles) {
    const ax = t.pos.x - from.pos.x;
    const ay = t.pos.y - from.pos.y;
    const along = dx !== 0 ? ax * dx : ay * dy;
    if (along <= 0) continue;
    const score = along + 2 * Math.abs(dx !== 0 ? ay : ax);
    if (score < bestScore) {
      bestScore = score;
      best = t;
    }
  }
  return best;
}

const editable = (target: EventTarget | null) =>
  target instanceof HTMLElement && Boolean(target.closest("input, textarea, select, [contenteditable]:not([contenteditable=false])"));

export interface BoardEditing {
  dispose(): void;
  duplicateSelected(): void;
  deleteSelected(): void;
}

export function wireBoardEdit(el: HTMLElement, board: Board, store: Store, notice: (text: string) => void): BoardEditing {
  const open = () => {
    const s = store.get();
    return s.chapters.find((c) => c.id === s.openId);
  };
  const editing = () => store.get().mode === "edit";

  const addQuest = (at: GridPos, linkFrom?: string) => {
    const ch = open();
    if (!ch || !inGrid(ch, at) || tileAt(ch, at)) return;
    const id = uniqueTileId(ch, `tile_${at.x}_${at.y}`);
    store.edit(ch.id, "add quest", (c) => {
      const tile = newTile(id, at.x, at.y);
      tile.title = "New quest";
      tile.tasks = [emptyTask("checkmark")];
      c.tiles.push(tile);
      if (linkFrom) upsertLink(c, linkFrom, id, "and");
    });
    store.select(id);
  };

  const moveQuest = (id: string, to: GridPos): boolean => {
    const ch = open();
    const tile = ch?.tiles.find((t) => t.id === id);
    if (!ch || !tile || (tile.pos.x === to.x && tile.pos.y === to.y)) return false;
    if (!inGrid(ch, to) || tileAt(ch, to)) return false;
    const bent = diagonalPartners(ch, id, to);
    if (bent.length) {
      notice(`Not moved: ${name(tile)} would be diagonal to ${bent.map(name).join(", ")}. Linked quests share a row or column.`);
      return false;
    }
    store.edit(ch.id, "move quest", (c) => { c.tiles.find((t) => t.id === id)!.pos = { ...to }; });
    return true;
  };

  const askGate = async (from: string, to: string) => {
    const ch = open();
    if (!ch) return;
    const chapterId = ch.id;
    const target = ch.tiles.find((t) => t.id === to);
    const link = (ch.links ?? []).find((l) => l.from === from && l.to === to);
    const pick = await choose<GateOpName | "remove">(`How does ${name(target)} open?`,
      [...GATE_CHOICES, ...(link ? [{ label: "Remove this link", hint: "Ctrl+Z puts it back.", value: "remove" as const, danger: true }] : [])],
      link ? `Now: ${GATE_CHOICES.find((c) => c.value === gateOp(link))?.label ?? gateOp(link).toUpperCase()}` : undefined);
    if (!pick) return;
    store.edit(chapterId, pick === "remove" ? "remove link" : "gate", (c) => {
      if (pick === "remove") removeLink(c, from, to);
      else upsertLink(c, from, to, pick);
    });
  };

  const link = (fromId: string, side: Side, dropAt: GridPos | null) => {
    const ch = open();
    const from = ch?.tiles.find((t) => t.id === fromId);
    if (!ch || !from) return;
    const [dx, dy] = SIDE_STEP[side];
    const at = { x: from.pos.x + dx, y: from.pos.y + dy };
    if (dropAt && (dropAt.x !== at.x || dropAt.y !== at.y)) {
      notice("A link joins neighbours: drop the diamond on the quest or empty cell right beside it.");
      return;
    }
    const to = tileAt(ch, at);
    if (!to) {
      addQuest(at, fromId);
      return;
    }
    const existing = (ch.links ?? []).find((l) => (l.from === fromId && l.to === to.id) || (l.from === to.id && l.to === fromId));
    if (existing) {
      void askGate(existing.from, existing.to);
      return;
    }
    if (wouldCreateCycle(ch, fromId, to.id)) {
      notice(`Not linked: ${name(to)} already leads to ${name(from)}, so this would make a loop.`);
      return;
    }
    store.edit(ch.id, "link", (c) => upsertLink(c, fromId, to.id, "and"));
  };

  const deleteSelected = () => {
    const s = store.get();
    const ch = open();
    if (!ch || !s.selected) return;
    const id = s.selected;
    store.edit(ch.id, "delete quest", (c) => {
      c.tiles = c.tiles.filter((t) => t.id !== id);
      c.links = (c.links ?? []).filter((l) => l.from !== id && l.to !== id);
    });
    store.select(null);
  };

  const duplicateSelected = () => {
    const s = store.get();
    const ch = open();
    const tile = ch?.tiles.find((t) => t.id === s.selected);
    if (!ch || !tile) return;
    const at = nearestEmpty(ch, tile.pos);
    if (!at) {
      notice("No empty cell left on this board for a copy.");
      return;
    }
    const id = uniqueTileId(ch, `${tile.id}_copy`);
    store.edit(ch.id, "duplicate quest", (c) => c.tiles.push({ ...structuredClone(tile), id, pos: at }));
    store.select(id);
  };

  // ---- pointer ----
  type Press =
    | { kind: "tile"; id: string; x: number; y: number; moved: boolean }
    | { kind: "handle"; from: string; side: Side }
    | { kind: "cell"; at: GridPos; x: number; y: number }
    | { kind: "arrow"; from: string; to: string };
  let press: Press | null = null;
  const tileNode = (id: string) => el.querySelector<HTMLElement>(`.tile[data-tile="${CSS.escape(id)}"]`);

  const down = (event: PointerEvent) => {
    if (event.button !== 0) return;
    const target = event.target as HTMLElement;
    const handle = target.closest<HTMLElement>(".link-handle");
    const selected = store.get().selected;
    if (handle && selected && editing()) {
      press = { kind: "handle", from: selected, side: handle.dataset.side as Side };
      event.preventDefault();
      return;
    }
    const arrow = target.closest<HTMLElement>(".arrow");
    if (arrow && editing()) {
      press = { kind: "arrow", from: arrow.dataset.from!, to: arrow.dataset.to! };
      return;
    }
    const add = target.closest<HTMLElement>(".add-cell");
    if (add && editing()) {
      press = { kind: "cell", at: { x: Number(add.dataset.x), y: Number(add.dataset.y) }, x: event.clientX, y: event.clientY };
      return;
    }
    const hit = board.hit(event.clientX, event.clientY);
    if (!hit) return;
    if (hit.tile) press = { kind: "tile", id: hit.tile.id, x: event.clientX, y: event.clientY, moved: false };
    else press = { kind: "cell", at: hit.cell, x: event.clientX, y: event.clientY };
  };

  const move = (event: PointerEvent) => {
    if (press?.kind !== "tile" || !editing()) return;
    const dx = event.clientX - press.x;
    const dy = event.clientY - press.y;
    if (!press.moved && Math.hypot(dx, dy) < DRAG_START_PX) return;
    press.moved = true;
    const node = tileNode(press.id);
    if (node) {
      node.classList.add("dragging");
      node.style.transform = `translate(${dx}px, ${dy}px)`;
    }
  };

  const up = (event: PointerEvent) => {
    const p = press;
    press = null;
    if (!p) return;
    if (p.kind === "tile") {
      const node = tileNode(p.id);
      node?.classList.remove("dragging");
      if (node) node.style.transform = "";
      if (p.moved && editing()) {
        const hit = board.hit(event.clientX, event.clientY);
        if (hit) moveQuest(p.id, hit.cell);
        board.render();
      } else {
        store.select(p.id);
      }
      return;
    }
    if (!editing()) return;
    if (p.kind === "handle") {
      const hit = board.hit(event.clientX, event.clientY);
      link(p.from, p.side, hit ? hit.cell : null);
    } else if (p.kind === "arrow") {
      void askGate(p.from, p.to);
    } else if (p.kind === "cell") {
      if (Math.hypot(event.clientX - p.x, event.clientY - p.y) < DRAG_START_PX) addQuest(p.at);
    }
  };

  // ---- keyboard ----
  const boardKey = (event: KeyboardEvent) => {
    if (editable(event.target)) return;
    const s = store.get();
    const ch = open();
    if (!ch) return;
    const selected = ch.tiles.find((t) => t.id === s.selected);
    const dirs: Record<string, [number, number]> = { ArrowLeft: [-1, 0], ArrowRight: [1, 0], ArrowUp: [0, -1], ArrowDown: [0, 1] };
    if (event.key in dirs) {
      event.preventDefault();
      const [dx, dy] = dirs[event.key];
      if (!selected) {
        store.select(ch.tiles[0]?.id ?? null);
      } else if (event.altKey && editing()) {
        moveQuest(selected.id, { x: selected.pos.x + dx, y: selected.pos.y + dy });
      } else {
        const next = nextInDirection(ch, selected, dx, dy);
        if (next) {
          store.select(next.id);
          board.reveal(next.id);
        }
      }
      return;
    }
    if (!editing()) return;
    if ((event.key === "Delete" || event.key === "Backspace") && selected) {
      event.preventDefault();
      deleteSelected();
    } else if ((event.key === "d" || event.key === "D") && (event.ctrlKey || event.metaKey)) {
      event.preventDefault();
      duplicateSelected();
    } else if ((event.key === "n" || event.key === "N") && !event.ctrlKey && !event.metaKey) {
      event.preventDefault();
      const at = nearestEmpty(ch, selected?.pos ?? { x: -1, y: 0 });
      if (at) addQuest(at);
    }
  };

  const undoKey = (event: KeyboardEvent) => {
    if (!(event.ctrlKey || event.metaKey) || (event.key !== "z" && event.key !== "Z" && event.key !== "y")) return;
    if (editable(event.target)) return;
    event.preventDefault();
    if (event.key === "y" || event.shiftKey) store.redo();
    else store.undo();
  };

  el.addEventListener("pointerdown", down);
  el.addEventListener("pointermove", move);
  el.addEventListener("pointerup", up);
  el.addEventListener("keydown", boardKey);
  window.addEventListener("keydown", undoKey);
  return {
    duplicateSelected,
    deleteSelected,
    dispose() {
      el.removeEventListener("pointerdown", down);
      el.removeEventListener("pointermove", move);
      el.removeEventListener("pointerup", up);
      el.removeEventListener("keydown", boardKey);
      window.removeEventListener("keydown", undoKey);
    },
  };
}

/** Exposed for the card's Delete button (it asks first; the board's Delete key relies on Ctrl+Z instead). */
export async function confirmDelete(title: string): Promise<boolean> {
  return ask(`Delete ${title}? Its links go too. Ctrl+Z brings it back.`, { ok: "Delete", danger: true });
}
