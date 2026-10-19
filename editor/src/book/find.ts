// Find (Ctrl+K): one box for quests in every chapter, chapters, stages and the editor's actions. A title that
// starts with what was typed beats one where a word starts with it, which beats one that only contains it; ids
// count too. Among equals the open chapter's quests come first.
import { h } from "./dom";
import type { Store } from "./store";

export interface Hit {
  kind: "quest" | "chapter" | "stage" | "action";
  label: string;
  sub: string;
  run: () => void;
}

export interface FindHooks {
  actions: { label: string; sub: string; run: () => void }[];
  openStage: (id: string) => void;
}

const MAX_HITS = 30;
const KIND_ORDER: Hit["kind"][] = ["quest", "chapter", "stage", "action"];

/** 3 prefix, 2 a word starts with it, 1 contains it, 0 no match. */
function rank(text: string, q: string): number {
  const lower = text.toLowerCase();
  if (lower.startsWith(q)) return 3;
  let at = lower.indexOf(q);
  let found = at >= 0;
  while (at > 0) {
    if (!/[a-z0-9]/.test(lower[at - 1])) return 2;
    at = lower.indexOf(q, at + 1);
  }
  return found ? 1 : 0;
}

export function search(store: Store, query: string, hooks: FindHooks): Hit[] {
  const q = query.trim().toLowerCase();
  if (!q) return [];
  const state = store.get();
  const scored: { hit: Hit; score: number; here: boolean; n: number }[] = [];
  const add = (hit: Hit, score: number, here = false) => {
    if (score > 0) scored.push({ hit, score, here, n: scored.length });
  };
  for (const chapter of state.chapters) {
    const chapterName = chapter.title || chapter.id;
    for (const tile of chapter.tiles ?? []) {
      add({
        kind: "quest", label: tile.title || tile.id, sub: `${chapterName} · ${tile.id}`,
        run: () => {
          if (store.get().openId !== chapter.id) store.open(chapter.id);
          store.select(tile.id);
        },
      }, Math.max(rank(tile.title ?? "", q), rank(tile.id, q)), chapter.id === state.openId);
    }
    add({ kind: "chapter", label: chapterName, sub: chapter.id, run: () => store.open(chapter.id) },
      Math.max(rank(chapter.title ?? "", q), rank(chapter.id, q)));
  }
  for (const stage of state.stages.stages) {
    add({ kind: "stage", label: stage.name || stage.id, sub: `Stage · ${stage.id}`, run: () => hooks.openStage(stage.id) },
      Math.max(rank(stage.name, q), rank(stage.id, q)));
  }
  for (const action of hooks.actions) add({ kind: "action", ...action }, rank(action.label, q));
  scored.sort((a, b) => b.score - a.score
    || KIND_ORDER.indexOf(a.hit.kind) - KIND_ORDER.indexOf(b.hit.kind)
    || Number(b.here) - Number(a.here)
    || a.n - b.n);
  return scored.slice(0, MAX_HITS).map((s) => s.hit);
}

const KIND_LABEL: Record<Hit["kind"], string> = { quest: "Quest", chapter: "Chapter", stage: "Stage", action: "Do" };

/** The Find panel over the book: type, ↑/↓, Enter runs the hit; Esc or a click outside closes. */
export function openFind(host: HTMLElement, store: Store, hooks: FindHooks): void {
  host.querySelector(".find-backdrop")?.remove();
  const before = document.activeElement as HTMLElement | null;
  let hits: Hit[] = [];
  let at = 0;
  const input = h("input", { class: "find-input", type: "search", placeholder: "Find a quest, chapter, stage or action…", "aria-label": "Find",
    role: "combobox", "aria-expanded": "true", "aria-controls": "find-hits", autocomplete: "off" }) as HTMLInputElement;
  const list = h("div", { class: "find-hits", id: "find-hits", role: "listbox" });
  const panel = h("div", { class: "find-panel", role: "dialog", "aria-label": "Find" }, input, list);
  const backdrop = h("div", { class: "find-backdrop" }, panel);
  const close = (restore = true) => {
    backdrop.remove();
    if (restore) before?.focus?.();
  };
  const run = (hit: Hit | undefined) => {
    if (!hit) return;
    close(false);
    hit.run();
  };
  const paint = () => {
    list.replaceChildren(...hits.map((hit, i) => h("div", {
      class: `find-hit${i === at ? " active" : ""}`, role: "option", id: `find-hit-${i}`, "aria-selected": String(i === at),
      onmousedown: (event: Event) => { event.preventDefault(); run(hit); },
    }, h("span", { class: `find-kind ${hit.kind}` }, KIND_LABEL[hit.kind]), h("span", { class: "find-label" }, hit.label), h("span", { class: "find-sub" }, hit.sub))));
    if (!hits.length && input.value.trim()) list.append(h("p", { class: "find-none" }, "Nothing matches."));
    input.setAttribute("aria-activedescendant", hits.length ? `find-hit-${at}` : "");
    list.querySelector(".active")?.scrollIntoView?.({ block: "nearest" });
  };
  input.addEventListener("input", () => {
    hits = search(store, input.value, hooks);
    at = 0;
    paint();
  });
  input.addEventListener("keydown", (event) => {
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      event.preventDefault();
      if (hits.length) at = (at + (event.key === "ArrowDown" ? 1 : hits.length - 1)) % hits.length;
      paint();
    } else if (event.key === "Enter") {
      event.preventDefault();
      run(hits[at]);
    } else if (event.key === "Escape") {
      event.preventDefault();
      event.stopPropagation();
      close();
    }
  });
  backdrop.addEventListener("mousedown", (event) => { if (event.target === backdrop) close(); });
  host.append(backdrop);
  input.focus();
}
