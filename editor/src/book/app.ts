// Living Book entry: build the font, mount the book, wait for Minecraft (the bridge), load the pack into the
// store, and redraw the parts on every change.
import "./book.css";
import { bridgeFetch } from "../bridge";
import { loadBookFont } from "./font";
import { unknownIdWarnings } from "./ids";
import { loadPack } from "./pack";
import { paletteFor } from "./palettes";
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

function render(shell: ShellParts, store: Store) {
  const state = store.get();
  const open = state.chapters.find((c) => c.id === state.openId);
  applyPalette(shell.root, paletteFor(open?.theme));
  shell.root.classList.toggle("mode-edit", state.mode === "edit");
  shell.root.classList.toggle("mode-preview", state.mode === "preview");
  shell.root.style.setProperty("--px", String(state.zoom));
  renderSidebar(shell.sidebar, store);
}

function boot() {
  const shell = mountShell(document.getElementById("book-app")!);
  const store = createStore({}, [(chapter, state) => unknownIdWarnings(chapter, state.catalog)]);
  store.subscribe(() => render(shell, store));
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
      render(shell, store);
    } catch (error) {
      shell.lock.hidden = false;
      shell.lock.querySelector("small")!.textContent = String((error as Error).message ?? error);
    }
  };
  void poll();
  window.setInterval(() => void poll(), POLL_MS);
  render(shell, store);
}

boot();
