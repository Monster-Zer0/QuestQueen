// Living Book entry: build the font, mount the book, wait for Minecraft (the bridge), load the pack into the
// store, and redraw the parts on every change.
import "./book.css";
import { bridgeFetch } from "../bridge";
import { loadBookFont } from "./font";
import { createMinimap, type Minimap } from "./minimap";
import { unknownIdWarnings } from "./ids";
import { loadPack } from "./pack";
import { paletteFor } from "./palettes";
import { createBoard, type Board } from "./board";
import { confirmDelete, wireBoardEdit, type BoardEditing } from "./boardEdit";
import { renderCard } from "./card";
import { pickReward, pickTask } from "./taskPicker";
import { openPreviewAs, previewLabel } from "./previewAs";
import { ask, choose, setDialogHost, tell } from "./dialog";
import { h } from "./dom";
import { applyPalette, mountShell, type ShellParts } from "./shell";
import { renderSidebar, startRename } from "./sidebar";
import { addEmptyChapter, addStarterChapter, deleteChapter, duplicateChapter } from "./chapters";
import { openChapterSettings } from "./chapterSettings";
import { renderIntro } from "./intro";
import { isIntroChapter } from "../model";
import { createStore, type Store } from "./store";

const POLL_MS = 2000;

/** Is the bridge up with the editor session on? */
async function bridgeReady(): Promise<boolean> {
  try {
    const response = await bridgeFetch("/api/status");
    if (!response.ok) return false;
    return ((await response.json()) as { enabled?: boolean }).enabled === true;
  } catch {
    return false;
  }
}

let minimap: Minimap | null = null;

function openSettingsPage(shell: ShellParts, store: Store, chapterId: string) {
  const page = shell.board.parentElement!.querySelector<HTMLElement>(".settings-page")!;
  page.dataset.chapter = chapterId;
  page.hidden = false;
  openChapterSettings(page.querySelector<HTMLElement>(".settings-body")!, store, chapterId);
  page.querySelector<HTMLElement>("input, select")?.focus();
}

/** Ask, then delete: the game deletes a saved chapter first and may refuse one that a datapack defines. */
async function removeChapter(store: Store, chapterId: string) {
  const s = store.get();
  const chapter = s.chapters.find((c) => c.id === chapterId);
  if (!chapter) return;
  const quests = chapter.tiles?.length ?? 0;
  const kids = s.chapters.some((c) => c.parent === chapterId);
  const yes = await ask(`Delete ${chapter.title || chapterId}?${quests ? ` Its ${quests === 1 ? "quest goes" : `${quests} quests go`} too.` : ""}${kids ? " Child chapters move up a level." : ""}`,
    { ok: "Delete", danger: true });
  if (!yes) return;
  const result = await deleteChapter(store, chapterId, (body) =>
    bridgeFetch("/api/chapter/delete", { method: "POST", headers: { "Content-Type": "application/json" }, body }));
  if (!result.ok) await tell(result.message ?? "Minecraft refused the delete.", { title: "Not deleted" });
}

function sidebarHooks(shell: ShellParts, store: Store) {
  return {
    add: async () => {
      const kind = await choose("New chapter", [
        { label: "Empty chapter", hint: "One quest to start from", value: "empty" },
        { label: "With example quests", hint: "A few linked quests to change", value: "example" },
      ]);
      if (!kind) return;
      const id = kind === "example" ? addStarterChapter(store) : addEmptyChapter(store);
      startRename(shell.sidebar, store, id);
    },
    menu: async (chapterId: string) => {
      const chapter = store.get().chapters.find((c) => c.id === chapterId);
      const pick = await choose(chapter?.title || chapterId, [
        { label: "Rename", value: "rename" },
        { label: "Settings", hint: "Theme, background, icon, unlocks, board size", value: "settings" },
        { label: "Duplicate", hint: "A copy, unsaved until you save it", value: "duplicate" },
        { label: "Delete", hint: "Removes the chapter and its quests", value: "delete", danger: true },
      ]);
      if (pick === "rename") startRename(shell.sidebar, store, chapterId);
      if (pick === "settings") openSettingsPage(shell, store, chapterId);
      if (pick === "duplicate") duplicateChapter(store, chapterId);
      if (pick === "delete") await removeChapter(store, chapterId);
    },
  };
}
let editing: BoardEditing | null = null;

function render(shell: ShellParts, store: Store, board: Board) {
  const state = store.get();
  const open = state.chapters.find((c) => c.id === state.openId);
  applyPalette(shell.root, paletteFor(open?.theme));
  shell.root.classList.toggle("mode-edit", state.mode === "edit");
  shell.root.classList.toggle("mode-preview", state.mode === "preview");
  renderSidebar(shell.sidebar, store, sidebarHooks(shell, store));
  const intro = open && isIntroChapter(state.chapters, open);
  const introHost = shell.board.parentElement!.querySelector<HTMLElement>(".intro-host")!;
  introHost.hidden = !intro;
  shell.board.hidden = Boolean(intro);
  shell.root.classList.toggle("intro-open", Boolean(intro));
  if (intro && open) renderIntro(introHost, store, open);
  const settings = shell.board.parentElement!.querySelector<HTMLElement>(".settings-page")!;
  if (!settings.hidden && settings.dataset.chapter && !settings.querySelector(":focus")) {
    const host = settings.querySelector<HTMLElement>(".settings-body")!;
    if (state.chapters.some((c) => c.id === settings.dataset.chapter)) openChapterSettings(host, store, settings.dataset.chapter);
    else settings.hidden = true;
  }
  for (const b of shell.bar.querySelectorAll<HTMLElement>("[data-mode]")) b.setAttribute("aria-pressed", String(b.dataset.mode === state.mode));
  shell.bar.querySelector("[data-act=preview-as]")!.textContent = `${previewLabel(state)} ▾`;
  shell.hint.textContent = state.mode === "edit"
    ? "Click any text or picture to change it. Drag quests to move them; drag a blue diamond to link."
    : "This is what a player sees. Switch to Edit to change it.";
  board.render();
  minimap?.render();
  renderCard(shell.cardLayer, store, {
    board,
    duplicate: () => editing?.duplicateSelected(),
    editTask: async (index, anchor) => {
      if (index >= 0) return;
      const s = store.get();
      const chapterId = s.openId;
      const tileId = s.selected;
      const task = await pickTask(anchor, s);
      if (task && tileId) store.edit(chapterId, "add task", (c) => { c.tiles.find((t) => t.id === tileId)?.tasks?.push(task); });
    },
    editReward: async (index, anchor) => {
      if (index >= 0) return;
      const s = store.get();
      const chapterId = s.openId;
      const tileId = s.selected;
      const reward = await pickReward(anchor, s);
      if (reward && tileId) store.edit(chapterId, "add reward", (c) => {
        const t = c.tiles.find((x) => x.id === tileId);
        if (t) t.rewards = [...(t.rewards ?? []), reward];
      });
    },
    remove: async () => {
      const s = store.get();
      const tile = s.chapters.find((c) => c.id === s.openId)?.tiles.find((t) => t.id === s.selected);
      if (tile && (await confirmDelete(tile.title || tile.id))) editing?.deleteSelected();
    },
  });
  const zoomLabel = shell.root.querySelector(".zoom-label");
  if (zoomLabel) zoomLabel.textContent = `${Math.round(state.zoom * 100)}%`;
}

function boot() {
  const shell = mountShell(document.getElementById("book-app")!);
  const store = createStore({}, [(chapter, state) => unknownIdWarnings(chapter, state.catalog)]);
  const board = createBoard(shell.board, store);
  shell.board.parentElement!.append(h("div", { class: "board-tools", role: "group", "aria-label": "Zoom" },
    h("button", { type: "button", "aria-label": "Zoom out", onclick: () => zoomCenter(-1) }, "−"),
    h("span", { class: "zoom-label" }, ""),
    h("button", { type: "button", "aria-label": "Zoom in", onclick: () => zoomCenter(1) }, "+"),
    h("button", { type: "button", onclick: () => board.fit() }, "Fit"),
    h("button", { type: "button", "aria-label": "Show or hide the map", onclick: () => minimap?.toggle() }, "Map")));
  minimap = createMinimap(shell.board.parentElement!, board, store, shell.board);
  shell.board.parentElement!.append(
    h("div", { class: "intro-host", hidden: true }),
    h("div", { class: "settings-page", hidden: true, role: "dialog", "aria-label": "Chapter settings" },
      h("button", { type: "button", class: "card-close settings-close", "aria-label": "Close settings",
        onclick: (e: Event) => { (e.currentTarget as HTMLElement).parentElement!.hidden = true; } }, "×"),
      h("div", { class: "settings-body" })));
  const zoomCenter = (direction: 1 | -1) => {
    const rect = shell.board.getBoundingClientRect();
    board.zoomAt(direction, rect.left + rect.width / 2, rect.top + rect.height / 2);
  };
  setDialogHost(shell.popLayer);
  for (const b of shell.bar.querySelectorAll<HTMLElement>("[data-mode]")) {
    b.addEventListener("click", () => store.setMode(b.dataset.mode === "preview" ? "preview" : "edit"));
  }
  shell.bar.querySelector("[data-act=preview-as]")!.addEventListener("click", () => void openPreviewAs(store));
  const noticeEl = h("div", { class: "board-notice", role: "status", hidden: true });
  shell.board.parentElement!.append(noticeEl);
  let noticeTimer = 0;
  const notice = (text: string) => {
    noticeEl.textContent = text;
    noticeEl.hidden = false;
    shell.live.textContent = text;
    window.clearTimeout(noticeTimer);
    noticeTimer = window.setTimeout(() => { noticeEl.hidden = true; }, 5000);
  };
  editing = wireBoardEdit(shell.board, board, store, notice);
  store.subscribe(() => render(shell, store, board));
  void loadBookFont().then((source) => { shell.root.dataset.font = source; });
  let loaded = false;
  const poll = async () => {
    const ready = await bridgeReady();
    // Once a pack is loaded the book stays usable without Minecraft; saving says it is not connected.
    shell.lock.hidden = ready || loaded;
    shell.root.classList.toggle("offline", !ready);
    if (!ready || loaded) return;
    try {
      const pack = await loadPack((path) => bridgeFetch(path));
      loaded = true;
      store.load({ ...pack, openId: pack.chapters[0]?.id ?? "" }, "pack loaded");
      for (const chapter of pack.chapters) store.markSaved(chapter.id);
      render(shell, store, board);
    } catch (error) {
      shell.lock.hidden = false;
      shell.lock.querySelector("small")!.textContent = String((error as Error).message ?? error);
    }
  };
  void poll();
  window.setInterval(() => void poll(), POLL_MS);
  render(shell, store, board);
}

boot();
