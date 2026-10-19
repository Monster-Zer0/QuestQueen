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
import { h } from "./dom";
import { applyPalette, mountShell, type ShellParts } from "./shell";
import { renderSidebar } from "./sidebar";
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

function render(shell: ShellParts, store: Store, board: Board) {
  const state = store.get();
  const open = state.chapters.find((c) => c.id === state.openId);
  applyPalette(shell.root, paletteFor(open?.theme));
  shell.root.classList.toggle("mode-edit", state.mode === "edit");
  shell.root.classList.toggle("mode-preview", state.mode === "preview");
  renderSidebar(shell.sidebar, store);
  board.render();
  minimap?.render();
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
  const zoomCenter = (direction: 1 | -1) => {
    const rect = shell.board.getBoundingClientRect();
    board.zoomAt(direction, rect.left + rect.width / 2, rect.top + rect.height / 2);
  };
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
