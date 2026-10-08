// Undo for board edits, one stack per chapter. Each step is a snapshot of the chapter's quests, links and grid
// taken just before the change, so every kind of edit (move, delete, link, rule, layout) undoes the same way and
// keeps link conditions intact. Keyed on the chapter object, so a rename keeps its history and an undo can never
// land in a different chapter.
import type { Chapter, Link, Tile } from "./types";

type Snapshot = { tiles: Tile[]; links: Link[]; grid_width?: number; grid_height?: number };

const LIMIT = 50;
const stacks = new WeakMap<Chapter, Snapshot[]>();

/** Record the chapter as it is now; call right before changing it. */
export function snapshot(chapter: Chapter): void {
  const stack = stacks.get(chapter) ?? [];
  stack.push(structuredClone({
    tiles: chapter.tiles ?? [],
    links: chapter.links ?? [],
    grid_width: chapter.grid_width,
    grid_height: chapter.grid_height,
  }));
  if (stack.length > LIMIT) stack.shift();
  stacks.set(chapter, stack);
}

/** Restore this chapter's last snapshot. Returns false when there is nothing to undo here. */
export function undo(chapter: Chapter): boolean {
  const last = stacks.get(chapter)?.pop();
  if (!last) return false;
  chapter.tiles = last.tiles;
  chapter.links = last.links;
  if (last.grid_width === undefined) delete chapter.grid_width;
  else chapter.grid_width = last.grid_width;
  if (last.grid_height === undefined) delete chapter.grid_height;
  else chapter.grid_height = last.grid_height;
  return true;
}

export function canUndo(chapter: Chapter): boolean {
  return (stacks.get(chapter)?.length ?? 0) > 0;
}
