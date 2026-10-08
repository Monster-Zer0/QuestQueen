// Pure chapter-level rules the editor shares with the mod. No DOM here, so every rule is unit tested.
import type { Chapter, Reward, Task, Tile } from "./types";

/**
 * An act intro is a parent chapter with no quests of its own (the mod's ClientQuestState.isIntroChapter). A parent
 * that has quests keeps its quest board.
 */
export function isIntroChapter(chapters: Chapter[], chapter: Chapter): boolean {
  return (chapter.tiles ?? []).length === 0 && chapters.some((entry) => entry.parent === chapter.id);
}

/** Every chapter below {@code id} in the parent tree. A chapter may not pick one of these (or itself) as parent. */
export function descendantIds(chapters: Chapter[], id: string): Set<string> {
  const out = new Set<string>();
  const queue = [id];
  while (queue.length) {
    const current = queue.shift()!;
    for (const entry of chapters) {
      if (entry.parent === current && !out.has(entry.id) && entry.id !== id) {
        out.add(entry.id);
        queue.push(entry.id);
      }
    }
  }
  return out;
}

/** The pack's own namespace: the most common one among its chapters that is not the mod's reserved one. */
export function packNamespace(chapters: Chapter[]): string {
  const counts = new Map<string, number>();
  for (const chapter of chapters) {
    const ns = chapter.id.split(":")[0];
    if (ns && ns !== "questqueen") counts.set(ns, (counts.get(ns) ?? 0) + 1);
  }
  let best = "";
  let bestCount = 0;
  for (const [ns, count] of counts) {
    if (count > bestCount) {
      best = ns;
      bestCount = count;
    }
  }
  return best || "questqueen";
}

/** A chapter id in {@code ns} that no chapter uses yet: {@code ns:base}, then {@code ns:base_2}, … */
export function uniqueChapterId(chapters: Chapter[], ns: string, base: string): string {
  const taken = new Set(chapters.map((chapter) => chapter.id));
  let id = `${ns}:${base}`;
  for (let n = 2; taken.has(id); n++) id = `${ns}:${base}_${n}`;
  return id;
}

/** Smallest grid that still holds every quest: a shrink below this would push quests off the board. */
export function minGrid(chapter: Chapter): { width: number; height: number } {
  let width = 1;
  let height = 1;
  for (const tile of chapter.tiles ?? []) {
    width = Math.max(width, tile.pos.x + 1);
    height = Math.max(height, tile.pos.y + 1);
  }
  return { width, height };
}

/** A new quest: never task-less, since a quest with no tasks can only be completed by a grant command. */
export function newTile(id: string, x: number, y: number): Tile {
  return { id, pos: { x, y }, title: "New tile", description: "", tasks: [{ type: "checkmark" }], rewards: [] };
}

function stripBlank<T extends object>(value: T): T {
  const out: Record<string, unknown> = {};
  for (const [key, field] of Object.entries(value)) {
    if (field === "" || field === undefined) continue;
    out[key] = field;
  }
  return out as T;
}

/**
 * The chapter as it should be written: optional fields left blank are dropped rather than sent as "" (a kill task
 * with "entity": "" next to its "tag" made the mod log a contract violation on every load). Required fields that
 * are blank are left for validation to report.
 */
export function cleanChapter(chapter: Chapter): Chapter {
  const copy = structuredClone(chapter);
  for (const tile of copy.tiles ?? []) {
    if (tile.tasks) tile.tasks = tile.tasks.map((task) => stripBlank<Task>(task));
    if (tile.rewards) {
      tile.rewards = tile.rewards.map((reward) => {
        const clean = stripBlank<Reward>(reward);
        if (clean.options) clean.options = clean.options.map((option) => stripBlank<Reward>(option));
        return clean;
      });
    }
    if (tile.required_stage === "") delete tile.required_stage;
    if (tile.description === "") delete tile.description;
  }
  return copy;
}
