// Living Book entry: build the font, mount the book, and wait for Minecraft (the bridge) before loading the pack.
import "./book.css";
import { bridgeFetch } from "../bridge";
import { loadBookFont } from "./font";
import { paletteFor } from "./palettes";
import { applyPalette, mountShell, type ShellParts } from "./shell";

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

function boot(): ShellParts {
  const root = document.getElementById("book-app")!;
  const shell = mountShell(root);
  applyPalette(shell.root, paletteFor(undefined));
  shell.root.classList.add("mode-edit");
  void loadBookFont().then((source) => { shell.root.dataset.font = source; });
  const poll = async () => {
    shell.lock.hidden = await bridgeReady();
  };
  void poll();
  window.setInterval(() => void poll(), POLL_MS);
  return shell;
}

boot();
