// Stages as a book page: the stage list on the left page, the stage editor on the right. The editor is the same
// component the old UI used (stageEditor.ts) with the shared bridge context (stageContext.ts); only its frame and
// colors are the book's. Giving a stage from a quest edits that quest's chapter, so the page says which chapter
// changed and offers to save it instead of changing it silently.
import { iconUrl } from "../bridge";
import { stageEditorContext, type StageHost } from "../stageContext";
import { renderStageEditor } from "../stageEditor";
import { lockCount, stageUsage, type StageInfo } from "../stages";
import { h } from "./dom";
import { loadStages } from "./pack";
import type { Store } from "./store";

const STAGE_ICON = "minecraft:amethyst_shard";

export interface StagesPageDeps {
  fetcher: (path: string) => Promise<Response>;
  connected: () => boolean;
  /** Save a chapter (the problems/save flow); resolves whether it saved. */
  save: (chapterId: string) => Promise<boolean>;
  /** Leave the page and go back to the board. */
  close: () => void;
}

function hostFor(page: HTMLElement, store: Store, deps: StagesPageDeps, popup = false): StageHost {
  return {
    catalog: () => ({ ...store.get().catalog, stageLock: store.get().stages.stageLock }),
    stages: () => store.get().stages.stages,
    chapters: () => store.get().chapters,
    refreshStages: async () => store.load({ stages: await loadStages(deps.fetcher) }, "stages"),
    connected: deps.connected,
    grant: (stageId, chapterId, tileId) => grantStage(page, store, deps, stageId, chapterId, tileId),
    jump: (chapterId, tileId) => {
      page.querySelector(".stage-popup")?.remove();
      if (!popup) deps.close();
      if (store.get().openId !== chapterId) store.open(chapterId);
      if (tileId) store.select(tileId);
    },
    open: popup ? undefined : (id) => openStages(page, store, deps, id),
  };
}

/** Put "gives this stage" on a quest, and say which chapter that changed with a Save button. */
export function grantStage(page: HTMLElement, store: Store, deps: StagesPageDeps, stageId: string, chapterId: string, tileId: string): void {
  const chapter = store.get().chapters.find((c) => c.id === chapterId);
  if (!chapter?.tiles?.some((t) => t.id === tileId)) return;
  store.edit(chapterId, "give stage", (c) => {
    const tile = c.tiles!.find((t) => t.id === tileId)!;
    tile.rewards = [...(tile.rewards ?? []), { type: "stage", stage: stageId }];
  });
  page.querySelector(`.changed-chip[data-chapter="${CSS.escape(chapterId)}"]`)?.remove();
  const chip = h("div", { class: "changed-chip", role: "status", dataset: { chapter: chapterId } },
    `${chapter.title || chapterId} changed · `,
    h("button", { type: "button", class: "chip-save", onclick: async () => {
      if (await deps.save(chapterId)) chip.remove();
    } }, "Save it"));
  (page.querySelector(".page-chips") ?? page).append(chip);
}

function stageCard(info: StageInfo, store: Store, active: boolean, pick: () => void): HTMLElement {
  const use = stageUsage(store.get().chapters, info.id);
  const parts = [
    use.grants.length ? `given by ${use.grants.length}` : "no quest gives it",
    ...(use.needs.length ? [`needed by ${use.needs.length}`] : []),
    ...(info.stage ? [`locks ${lockCount(info.stage)}`] : []),
  ];
  return h("button", { type: "button", class: `stage-card${active ? " active" : ""}`, dataset: { stageId: info.id }, "aria-pressed": String(active), onclick: pick },
    h("img", { alt: "", src: iconUrl(info.stage?.icon || STAGE_ICON) }),
    h("strong", {}, info.name),
    h("span", { class: `stage-badge ${info.authored ? "yours" : "pack"}` }, info.authored ? "yours" : "pack"),
    h("small", {}, parts.join(" · ")));
}

/**
 * Show the stages page in `page`: the list, and the editor for `stageId` ("" for a new stage, undefined for the
 * page's welcome). Calling it again redraws in place and keeps the search and the save chips.
 */
export function openStages(page: HTMLElement, store: Store, deps: StagesPageDeps, stageId?: string, notice = ""): void {
  const state = store.get();
  page.hidden = false;
  page.classList.add("stages-page", "stage-scope");
  let chips = page.querySelector<HTMLElement>(".page-chips");
  let search = page.querySelector<HTMLInputElement>(".page-stage-search");
  if (!chips || !search) {
    chips = h("div", { class: "page-chips" });
    search = h("input", { class: "page-stage-search", type: "search", placeholder: "Find a stage", "aria-label": "Find a stage" }) as HTMLInputElement;
    page.replaceChildren(
      h("button", { type: "button", class: "card-close stages-close", "aria-label": "Back to the quests", onclick: () => deps.close() }, "×"),
      h("div", { class: "stage-pages" },
        h("section", { class: "stage-left", "aria-label": "Stages" },
          h("h2", { class: "stage-heading" }, "Stages"),
          h("div", { class: "stage-tools" }, search,
            h("button", { type: "button", class: "chip-save stage-new", onclick: () => openStages(page, store, deps, "") }, "+ New stage")),
          state.stages.stageLock ? null : h("p", { class: "stage-note" }, "StageLock isn't installed. Stages still work as quest flags, but they can't lock items, blocks or places."),
          h("div", { class: "stage-list" })),
        h("section", { class: "stage-detail", "aria-label": "Stage" })),
      chips);
    search.addEventListener("input", () => openStages(page, store, deps, page.dataset.stage === undefined ? undefined : page.dataset.stage));
  }
  if (stageId === undefined) delete page.dataset.stage;
  else page.dataset.stage = stageId;
  const needle = search.value.trim().toLowerCase();
  const list = page.querySelector<HTMLElement>(".stage-list")!;
  const shown = state.stages.stages.filter((s) => !needle || s.id.includes(needle) || s.name.toLowerCase().includes(needle));
  list.replaceChildren(...(state.stages.stages.length
    ? shown.length ? shown.map((info) => stageCard(info, store, info.id === stageId, () => openStages(page, store, deps, info.id)))
      : [h("p", { class: "stage-list-empty" }, `No stage matches “${needle}”.`)]
    : [h("p", { class: "stage-list-empty" }, "No stages yet. A stage is a milestone a quest gives: until players have it, what it locks stays locked.")]));
  const detail = page.querySelector<HTMLElement>(".stage-detail")!;
  if (stageId === undefined) {
    detail.replaceChildren(h("div", { class: "stage-empty" },
      h("h2", {}, "Stages"),
      h("p", {}, "A stage is a milestone players earn, usually by finishing a quest. Until they have it, the items, blocks, mods, dimensions or mobs it locks stay out of reach."),
      h("p", {}, h("button", { type: "button", class: "chip-save", onclick: () => openStages(page, store, deps, "") }, "+ Make a stage"))));
    return;
  }
  const info = stageId === "" ? null : state.stages.stages.find((s) => s.id === stageId) ?? null;
  renderStageEditor(detail, info, stageEditorContext(hostFor(page, store, deps), (id) => {
    openStages(page, store, deps, id || undefined, id ? "Saved. The game has it, and its locks apply now." : "");
  }), { notice });
}

/** The same editor in a pop-up over the book (a quest's "New stage…"): resolves the saved stage id, or null. */
export function openStagePopup(host: HTMLElement, store: Store, deps: StagesPageDeps, prefillName: string): Promise<string | null> {
  return new Promise((resolve) => {
    const body = h("div", { class: "stage-popup-body" });
    const popup = h("div", { class: "stage-popup dlg-backdrop stage-scope" }, h("div", { class: "dlg stage-popup-panel", role: "dialog", "aria-label": "New stage" }, body));
    const close = (id: string | null) => {
      popup.remove();
      resolve(id);
    };
    popup.addEventListener("mousedown", (event) => { if (event.target === popup) close(null); });
    host.append(popup);
    renderStageEditor(body, null, stageEditorContext(hostFor(host, store, deps, true), (id) => close(id || null), () => close(null)), { modal: true, prefillName });
  });
}
