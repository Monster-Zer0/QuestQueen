// Book pixels → CSS pixels. The board is laid out in the book's own units (QuestBookScreen: TILE 64, GAP 12) and
// drawn at a zoom: CSS px per book px. Whole-number zooms keep the pixel art crisp.
export const TILE = 64;
export const GAP = 12;
export const STRIDE = TILE + GAP;

export const ZOOM_STEPS = [0.5, 1, 2, 3, 4] as const;

/** The largest zoom step at which a board of boardW×boardH book px fits a viewW×viewH view (at least the smallest). */
export function fitZoom(boardW: number, boardH: number, viewW: number, viewH: number): number {
  let best: number = ZOOM_STEPS[0];
  for (const step of ZOOM_STEPS) if (boardW * step <= viewW && boardH * step <= viewH) best = step;
  return best;
}

/** CSS px of a grid coordinate at a zoom. */
export function cellToPx(cell: number, zoom: number): number {
  return cell * STRIDE * zoom;
}

/** One zoom step in or out of the current zoom, clamped to the steps. */
export function zoomStep(zoom: number, direction: 1 | -1): number {
  const steps = ZOOM_STEPS as readonly number[];
  let at = steps.findIndex((s) => s >= zoom);
  if (at < 0) at = steps.length - 1;
  return steps[Math.max(0, Math.min(steps.length - 1, at + direction))];
}
