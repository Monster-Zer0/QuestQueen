// The board's map: the whole chapter in a corner box, each quest a dot in its state color, the view as a frame.
// Click to move the view there. Off by default when the board is narrow, so it never sits on top of quests.
import { chapterGridHeight, chapterGridWidth } from "../types";
import type { Board } from "./board";
import { h } from "./dom";
import { concealed, unlockedChapters, visual } from "./progress";
import type { Store } from "./store";

export const MAP_W = 180;
export const MAP_H = 120;
const NARROW = 700;

/** CSS px per cell so the grid fits the box. */
export function mapScale(gridW: number, gridH: number, boxW: number, boxH: number): number {
  return Math.min(boxW / Math.max(1, gridW), boxH / Math.max(1, gridH));
}

/** The grid cell (fractional) under a point of the map, clamped to the grid. */
export function mapToCell(x: number, y: number, gridW: number, gridH: number, boxW: number, boxH: number): { x: number; y: number } {
  const k = mapScale(gridW, gridH, boxW, boxH);
  return { x: Math.max(0, Math.min(gridW, x / k)), y: Math.max(0, Math.min(gridH, y / k)) };
}

const STATE_VAR: Record<string, string> = { CURRENT: "--current", NEW: "--neu", COMPLETED: "--completed", FAILED: "--failed", CLOSED: "--closed", LOCKED: "--port-red" };

export interface Minimap {
  render(): void;
  toggle(): void;
}

export function createMinimap(host: HTMLElement, board: Board, store: Store, boardEl: HTMLElement): Minimap {
  const canvas = h("canvas", { class: "minimap", width: String(MAP_W), height: String(MAP_H), "aria-label": "Map of the chapter: click to move there" }) as HTMLCanvasElement;
  host.append(canvas);
  let on = boardEl.getBoundingClientRect().width >= NARROW;
  const map: Minimap = {
    toggle() {
      on = !on;
      map.render();
    },
    render() {
      canvas.hidden = !on;
      const s = store.get();
      const ch = s.chapters.find((c) => c.id === s.openId);
      const ctx = canvas.getContext?.("2d");
      if (!on || !ch || !ctx) return;
      const gw = chapterGridWidth(ch);
      const gh = chapterGridHeight(ch);
      const k = mapScale(gw, gh, MAP_W, MAP_H);
      const css = getComputedStyle(boardEl);
      ctx.clearRect(0, 0, MAP_W, MAP_H);
      ctx.fillStyle = css.getPropertyValue("--cell").trim() || "#18101f";
      ctx.fillRect(0, 0, gw * k, gh * k);
      const open = unlockedChapters(s.chapters, s.sim).has(ch.id);
      for (const tile of ch.tiles ?? []) {
        if (s.mode === "preview" && concealed(ch, tile, s.sim)) continue;
        const v = visual(ch, tile, s.sim, s.chapters, open);
        ctx.fillStyle = css.getPropertyValue(STATE_VAR[v]).trim() || "#888";
        ctx.fillRect(tile.pos.x * k + 1, tile.pos.y * k + 1, Math.max(2, k - 2), Math.max(2, k - 2));
      }
      const view = board.viewCells();
      ctx.strokeStyle = css.getPropertyValue("--edit").trim() || "#ffb84a";
      ctx.strokeRect(view.x * k + 0.5, view.y * k + 0.5, view.w * k, view.h * k);
    },
  };
  canvas.addEventListener("click", (event) => {
    const s = store.get();
    const ch = s.chapters.find((c) => c.id === s.openId);
    if (!ch) return;
    const rect = canvas.getBoundingClientRect();
    const cell = mapToCell(event.clientX - rect.left, event.clientY - rect.top, chapterGridWidth(ch), chapterGridHeight(ch), MAP_W, MAP_H);
    board.centerOn(cell.x, cell.y);
    map.render();
  });
  return map;
}
