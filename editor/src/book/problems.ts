// What stops a chapter saving (red) and what only looks wrong (amber), in a side sheet. Each row names a quest
// when the message does; clicking it opens that quest's card at the part the message is about. Save and Save all
// go through here: a chapter with problems opens the sheet instead of posting.
import type { Chapter } from "../types";
import { h } from "./dom";
import type { Store } from "./store";

export interface SaveDeps {
  /** Where the sheet opens. */
  host: HTMLElement;
  post: (body: string) => Promise<Response>;
  tell: (text: string) => Promise<void>;
  notice: (text: string) => void;
}

export interface ProblemTarget {
  tileId: string;
  part: "task" | "reward" | "description" | "title";
  index: number;
}

/**
 * The quest a problem is about, and which part of its card. validate() starts messages "<title> (<id>)" (or the id
 * when untitled); id checks start "<title> · …" and stage checks "<title> requires …". The longest name wins, so
 * "Mine more" is never read as "Mine".
 */
export function problemTarget(message: string, chapter: Chapter): ProblemTarget | null {
  const prefixes = (chapter.tiles ?? []).flatMap((t) => [
    ...(t.title ? [{ tile: t, text: `${t.title} (${t.id})` }] : []),
    { tile: t, text: t.title || t.id },
  ]).sort((a, b) => b.text.length - a.text.length);
  const hit = prefixes.find((p) => message.startsWith(p.text) && /^($| |:)/.test(message.slice(p.text.length)));
  if (!hit) return null;
  const rest = message.slice(hit.text.length).replace(/^ ·/, "");
  const numbered = /^ (task|reward) (\d+)/.exec(rest);
  if (numbered) return { tileId: hit.tile.id, part: numbered[1] as "task" | "reward", index: Number(numbered[2]) - 1 };
  if (rest.startsWith(" description")) return { tileId: hit.tile.id, part: "description", index: 0 };
  return { tileId: hit.tile.id, part: "title", index: 0 };
}

/** Focus the part of the open card a problem is about (after the card has drawn). */
function focusCardPart(target: ProblemTarget) {
  setTimeout(() => {
    const card = document.querySelector<HTMLElement>(".card-layer .quest-card, .card-layer [role=dialog], .card-layer > *");
    if (!card) return;
    let el: HTMLElement | null = null;
    if (target.part === "task") el = card.querySelectorAll<HTMLElement>(".card-tasks > :not(.card-caption)")[target.index] ?? null;
    if (target.part === "reward") el = card.querySelectorAll<HTMLElement>(".card-rewards > :not(.card-caption)")[target.index] ?? null;
    if (target.part === "description") el = card.querySelector<HTMLElement>(".card-desc");
    if (target.part === "title") el = card.querySelector<HTMLElement>(".card-title");
    const focusable = el?.matches("input, select, button, [tabindex], [contenteditable=true]") ? el : el?.querySelector<HTMLElement>("input, select, button, [tabindex]") ?? el;
    if (!focusable) return;
    if (!focusable.hasAttribute("tabindex") && !focusable.matches("input, select, button")) focusable.tabIndex = -1;
    focusable.focus();
    focusable.scrollIntoView?.({ block: "nearest" });
  }, 0);
}

/** Open (or refresh) the sheet for a chapter. Empty: says so. */
export function openProblems(host: HTMLElement, store: Store, chapterId: string = store.get().openId): void {
  const chapter = store.get().chapters.find((c) => c.id === chapterId);
  host.querySelector(".problems-sheet")?.remove();
  if (!chapter) return;
  const errors = store.problems(chapterId);
  const warnings = store.warnings(chapterId);
  const row = (message: string, kind: "error" | "warning") => {
    const target = problemTarget(message, chapter);
    return h("button", {
      type: "button",
      class: `problem-row ${kind}`,
      title: target ? "Open this quest" : undefined,
      onclick: () => {
        if (!target) return;
        if (store.get().openId !== chapterId) store.open(chapterId);
        store.select(target.tileId);
        focusCardPart(target);
      },
    }, h("span", { class: "problem-kind" }, kind === "error" ? "Must fix" : "Check"), message);
  };
  const close = h("button", { type: "button", class: "card-close sheet-close", "aria-label": "Close problems", onclick: () => sheet.remove() }, "×");
  const sheet = h("aside", { class: "problems-sheet", role: "complementary", "aria-label": `Problems in ${chapter.title || chapter.id}` },
    close,
    h("h2", { class: "sheet-title" }, chapter.title || chapter.id),
    errors.length || warnings.length
      ? h("p", { class: "sheet-sub" }, errors.length
        ? `${errors.length} to fix before it can be saved${warnings.length ? `, ${warnings.length} to check` : ""}.`
        : `Nothing stops saving. ${warnings.length} to check.`)
      : h("p", { class: "sheet-sub" }, "No problems."),
    ...errors.map((m) => row(m, "error")),
    ...warnings.map((m) => row(m, "warning")));
  sheet.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      event.stopPropagation();
      sheet.remove();
    }
  });
  host.append(sheet);
}

/** Save one chapter: problems open the sheet, a refusal says why, success is noticed. */
export async function saveChapter(store: Store, chapterId: string, deps: SaveDeps): Promise<boolean> {
  const chapter = store.get().chapters.find((c) => c.id === chapterId);
  if (!chapter) return false;
  if (store.problems(chapterId).length) {
    openProblems(deps.host, store, chapterId);
    return false;
  }
  const result = await store.save(chapterId, deps.post);
  if (!result.ok) {
    await deps.tell(`Minecraft refused the save: ${result.error}`);
    return false;
  }
  deps.host.querySelector(".problems-sheet")?.remove();
  deps.notice(`Saved · ${chapter.title || chapter.id}`);
  return true;
}

/** Save every unsaved chapter in list order; stop at the first that can't be saved. */
export async function saveAll(store: Store, deps: SaveDeps): Promise<boolean> {
  for (const chapter of store.get().chapters.filter((c) => store.dirty(c.id))) {
    if (!(await saveChapter(store, chapter.id, deps))) return false;
  }
  return true;
}
