// The book's chapter sidebar, as the game draws it: the chapter tree (children indented under their parent), each
// row with its icon, title and done/total for the previewed player, a padlock on a chapter that player has not
// unlocked, a gold dot on a chapter with unsaved changes. Clicking a row opens that chapter.
import { bridgeBase } from "../bridge";
import { glyphSvg } from "../glyphs";
import { iconUrl } from "../itemSelect";
import { chapterTree, type Chapter, type ChapterNode } from "../types";
import { h, keptImage, reconcile, releaseImages } from "./dom";
import { concealed, questKey, unlockedChapters } from "./progress";
import type { Store } from "./store";

interface Row {
  chapter: Chapter;
  depth: number;
}

function flatten(nodes: ChapterNode[], depth = 0, out: Row[] = []): Row[] {
  for (const node of nodes) {
    out.push({ chapter: node.chapter, depth });
    flatten(node.children, depth + 1, out);
  }
  return out;
}

function chapterIcon(chapter: Chapter, scope: HTMLElement): HTMLElement {
  const box = h("span", { class: "chapter-icon", "aria-hidden": "true" });
  if (chapter.icon?.glyph) box.innerHTML = glyphSvg(chapter.icon.glyph);
  else if (chapter.icon?.item) box.append(keptImage(scope, iconUrl(bridgeBase(), chapter.icon.item), { class: "icon", alt: "" }));
  return box;
}

function padlock(): HTMLElement {
  const lock = h("span", { class: "padlock", "aria-hidden": "true" });
  lock.innerHTML = '<svg viewBox="0 0 8 8" width="100%" height="100%" shape-rendering="crispEdges"><path fill="var(--port-red)" d="M2 1h4v1H2zM1 2h1v2H1zM6 2h1v2H6zM1 4h6v4H1z"/><path fill="#3a0c10" d="M3 5h2v2H3z"/></svg>';
  return lock;
}

/** Make el the sidebar for the store's current state. Safe to call on every change; rows are kept by chapter id. */
export function renderSidebar(el: HTMLElement, store: Store): void {
  let list = el.querySelector<HTMLElement>(".chapter-list");
  if (!list) {
    el.replaceChildren(
      h("div", { class: "sidebar-head" },
        h("h2", { class: "qq-text sidebar-title" }, "CHAPTERS"),
        h("button", { type: "button", class: "sidebar-add", dataset: { act: "new-chapter" }, "aria-label": "New chapter", "aria-haspopup": "menu" }, "+")),
      (list = h("div", { class: "chapter-list", role: "list" })),
    );
  }
  const state = store.get();
  const open = unlockedChapters(state.chapters, state.sim);
  const rows = flatten(chapterTree(state.chapters));
  reconcile(list, rows, (row) => row.chapter.id,
    (row) => {
      const id = row.chapter.id;
      return h("button", { type: "button", class: "chapter-row", role: "listitem", onclick: () => store.open(id) });
    },
    (el, row) => {
      const { chapter, depth } = row;
      const visible = (chapter.tiles ?? []).filter((t) => !concealed(chapter, t, state.sim));
      const done = visible.filter((t) => state.sim.completed.has(questKey(chapter.id, t.id))).length;
      const locked = !open.has(chapter.id);
      el.dataset.depth = String(depth);
      el.style.paddingLeft = `calc(var(--px) * ${6 + depth * 12}px)`;
      el.classList.toggle("locked", locked);
      el.classList.toggle("dirty", store.dirty(chapter.id));
      if (chapter.id === state.openId) el.setAttribute("aria-current", "page");
      else el.removeAttribute("aria-current");
      el.replaceChildren(...[
        locked ? padlock() : chapterIcon(chapter, el),
        h("span", { class: "chapter-title qq-text" }, chapter.title || chapter.id),
        visible.length ? h("span", { class: "chapter-count qq-text qq-tiny" }, `${done}/${visible.length}`) : null,
        h("span", { class: "dirty-dot", title: "Unsaved changes", "aria-label": store.dirty(chapter.id) ? "unsaved changes" : undefined }),
      ].filter((child): child is HTMLElement => child !== null));
      releaseImages(el);
    });
}
