// Chapters as a whole: add (empty or with example quests), move (reorder among siblings, or nest under another),
// duplicate. A chapter list change is not an undoable board edit; each moved chapter's own fields are.
import { descendantIds, packNamespace, uniqueChapterId, uniqueChapterTitle } from "../model";
import { STARTER, type Chapter } from "../types";
import type { Store } from "./store";

function add(store: Store, chapter: Chapter): string {
  const s = store.get();
  store.load({ chapters: [...s.chapters, chapter] }, "add chapter");
  store.open(chapter.id);
  return chapter.id;
}

/** Siblings of the open chapter (same parent), in order. */
function nextOrder(chapters: Chapter[], parent: string | undefined): number {
  const siblings = chapters.filter((c) => c.parent === parent);
  return siblings.length ? Math.max(...siblings.map((c) => c.order ?? 0)) + 10 : 0;
}

/** A new chapter beside the open one, with one quest to start from. */
export function addEmptyChapter(store: Store): string {
  const s = store.get();
  const parent = s.chapters.find((c) => c.id === s.openId)?.parent;
  return add(store, {
    id: uniqueChapterId(s.chapters, packNamespace(s.chapters), "new_chapter"),
    title: uniqueChapterTitle(s.chapters, "New Chapter"),
    order: nextOrder(s.chapters, parent),
    ...(parent ? { parent } : {}),
    grid_width: 12,
    grid_height: 10,
    tiles: [{ id: "start", pos: { x: 1, y: 1 }, title: "First step", description: "", icon: { item: "minecraft:paper" }, tasks: [{ type: "checkmark" }], rewards: [] }],
    links: [],
  });
}

/** A new chapter made from the starter template's example quests. */
export function addStarterChapter(store: Store): string {
  const s = store.get();
  const parent = s.chapters.find((c) => c.id === s.openId)?.parent;
  const added = structuredClone(STARTER);
  added.id = uniqueChapterId(s.chapters, packNamespace(s.chapters), added.id.split(":")[1]);
  added.title = uniqueChapterTitle(s.chapters, added.title || "New Chapter");
  added.order = nextOrder(s.chapters, parent);
  if (parent) added.parent = parent;
  return add(store, added);
}

/** A copy of a chapter under a new id and title; it is unsaved until Save. */
export function duplicateChapter(store: Store, id: string): string {
  const s = store.get();
  const source = s.chapters.find((c) => c.id === id);
  if (!source) return "";
  const copy = structuredClone(source);
  const [ns, path] = source.id.split(":");
  copy.id = uniqueChapterId(s.chapters, ns, path);
  copy.title = uniqueChapterTitle(s.chapters, source.title || path);
  copy.order = (source.order ?? 0) + 1;
  return add(store, copy);
}

/**
 * Move a chapter: `before` puts it just before that chapter among that chapter's siblings (taking its parent);
 * `into` nests it as the last child of that chapter. Siblings are renumbered 10, 20, 30… Moving a chapter under
 * itself or one of its own descendants is refused (false).
 */
export function moveChapter(store: Store, id: string, where: { before: string } | { into: string }): boolean {
  const s = store.get();
  const moving = s.chapters.find((c) => c.id === id);
  if (!moving) return false;
  const parent = "into" in where ? where.into : s.chapters.find((c) => c.id === where.before)?.parent;
  if (parent && (parent === id || descendantIds(s.chapters, id).has(parent))) return false;
  const siblings = s.chapters
    .filter((c) => c.parent === parent && c.id !== id)
    .sort((a, b) => (a.order ?? 0) - (b.order ?? 0) || a.id.localeCompare(b.id));
  const at = "before" in where ? Math.max(0, siblings.findIndex((c) => c.id === where.before)) : siblings.length;
  siblings.splice(at, 0, moving);
  siblings.forEach((chapter, i) => {
    const order = (i + 1) * 10;
    if (chapter.order === order && (chapter.id !== id || moving.parent === parent)) return;
    store.edit(chapter.id, "move chapter", (c) => {
      c.order = order;
      if (c.id === id) {
        if (parent) c.parent = parent;
        else delete c.parent;
      }
    });
  });
  return true;
}
