// A dropdown for any box that holds a pack id (item, mob, block, structure, biome, stage…): focusing the box opens
// a list of everything the pack has, typing narrows it, and choosing a row fills the box. An id the pack doesn't
// list can still be typed and kept. The box stays a plain <input>, so the page hears the same `input` events as
// before whether the id was typed or chosen. A box holding several ids ("a, b") searches and fills the last one.
import { bridgeBase } from "../bridge";
import { iconUrl } from "../itemSelect";
import type { CatalogEntry } from "../packCatalog";
import { h } from "./dom";

const PAGE = 120;
/** The book colors the panel's CSS uses. */
const THEME = ["--edit", "--ui", "--muted", "--sidebar-edge"];

/** 3: id or name starts with the text · 2: a word in it does · 1: it appears inside. Order within a rank is kept. */
function rank(entry: CatalogEntry, q: string): number {
  let best = 0;
  for (const text of [entry.id, entry.name ?? ""]) {
    const lower = text.toLowerCase();
    if (lower.startsWith(q) || lower.split(":").pop()!.startsWith(q)) return 3;
    const at = lower.indexOf(q);
    if (at < 0) continue;
    best = Math.max(best, at === 0 || /[^a-z0-9]/.test(lower[at - 1]) ? 2 : 1);
  }
  return best;
}

export function filterEntries(entries: CatalogEntry[], text: string): CatalogEntry[] {
  const q = text.trim().toLowerCase();
  if (!q) return entries;
  return entries
    .map((entry, i) => ({ entry, i, r: rank(entry, q) }))
    .filter((x) => x.r > 0)
    .sort((a, b) => b.r - a.r || a.i - b.i)
    .map((x) => x.entry);
}

export interface ComboOptions {
  /** Each row's picture, by id (none when it gives nothing). */
  icon?: (id: string) => string | undefined;
  /** The box holds a comma-separated list: the list searches and fills the last entry. */
  multi?: boolean;
}

/** Row pictures: the game's render of an item, or a texture itself. */
export const itemIcon = (id: string) => iconUrl(bridgeBase(), id);
export const textureIcon = (id: string) => `${bridgeBase()}/api/texture?id=${encodeURIComponent(id)}`;

export interface Combo {
  close(): void;
}

export function attachCombo(input: HTMLInputElement, entries: () => CatalogEntry[], options: ComboOptions): Combo {
  let panel: HTMLElement | null = null;
  let shown: CatalogEntry[] = [];
  let count = PAGE;
  let at = -1;
  let list: HTMLElement;
  let note: HTMLElement;

  // What the list is about: the whole box, or the entry after the last comma.
  const term = () => (options.multi ? input.value.slice(input.value.lastIndexOf(",") + 1) : input.value).trim();
  const exact = () => entries().some((e) => e.id === term());
  // A box that already holds a real id shows everything (to pick another); otherwise the text is the search.
  const compute = () => {
    const all = entries();
    shown = exact() ? all : filterEntries(all, term());
    const chosen = shown.findIndex((e) => e.id === term());
    at = chosen >= 0 ? chosen : shown.length && term() ? 0 : -1;
    if (chosen >= count) count = chosen + PAGE;
  };

  const place = () => {
    if (!panel) return;
    if (!input.isConnected) return close();
    const box = input.getBoundingClientRect();
    const below = window.innerHeight - box.bottom;
    const height = Math.min(320, Math.max(160, below > 220 ? below - 12 : box.top - 12));
    panel.style.left = `${Math.max(4, Math.min(box.left, window.innerWidth - 324))}px`;
    panel.style.width = `${Math.max(box.width, 320)}px`;
    panel.style.maxHeight = `${height}px`;
    if (below > 220 || below >= box.top) { panel.style.top = `${box.bottom + 2}px`; panel.style.bottom = ""; }
    else { panel.style.bottom = `${window.innerHeight - box.top + 2}px`; panel.style.top = ""; }
  };

  const row = (entry: CatalogEntry, i: number) => {
    const named = entry.name && entry.name !== entry.id;
    const picture = options.icon?.(entry.id);
    return h("div", {
      class: `combo-row${i === at ? " active" : ""}${entry.id === term() ? " chosen" : ""}`, role: "option", dataset: { id: entry.id },
      onmousedown: (event: Event) => { event.preventDefault(); choose(entry.id); },
    }, picture ? h("img", { class: "combo-icon icon", alt: "", loading: "lazy", src: picture }) : null,
    h("span", { class: "combo-name" }, named ? entry.name! : entry.id), named ? h("span", { class: "combo-id" }, entry.id) : null);
  };

  const paint = () => {
    if (!panel) return;
    list.replaceChildren(...shown.slice(0, count).map(row));
    const total = entries().length;
    const text = term();
    note.textContent = shown.length
      ? `${shown.length === total ? total : `${shown.length} of ${total}`} in this pack`
      : text ? `Not in this pack. Enter keeps “${text}” as typed.` : "Nothing in the pack to list.";
    note.className = shown.length ? "combo-count" : "combo-none";
    list.querySelector(".active")?.scrollIntoView?.({ block: "nearest" });
  };

  const choose = (id: string) => {
    const comma = options.multi ? input.value.lastIndexOf(",") : -1;
    input.value = comma >= 0 ? `${input.value.slice(0, comma + 1)} ${id}` : id;
    input.dispatchEvent(new Event("input", { bubbles: true }));
    input.dispatchEvent(new Event("change", { bubbles: true }));
    close();
  };

  function open() {
    if (panel) return;
    list = h("div", { class: "combo-list", role: "listbox" });
    note = h("div", { class: "combo-count" });
    panel = h("div", { class: "combo-panel" }, list, note);
    list.addEventListener("scroll", () => {
      if (count < shown.length && list.scrollTop + list.clientHeight >= list.scrollHeight - 60) {
        count += PAGE;
        paint();
      }
    });
    count = PAGE;
    compute();
    // On the page body, above every popover and dialog (the icon and task pickers live there too, over the book),
    // wearing the chapter's colors copied from the book root.
    const root = document.querySelector(".qq-book-app");
    if (root) {
      const theme = getComputedStyle(root);
      for (const name of THEME) panel.style.setProperty(name, theme.getPropertyValue(name));
    }
    document.body.append(panel);
    place();
    paint();
  }

  function close() {
    panel?.remove();
    panel = null;
  }

  const refresh = () => {
    if (!panel) return;
    count = PAGE;
    compute();
    paint();
  };

  input.addEventListener("focus", open);
  input.addEventListener("click", open);
  input.addEventListener("input", refresh);
  input.addEventListener("blur", () => setTimeout(close, 0));
  input.addEventListener("keydown", (event) => {
    if (event.key === "ArrowDown" || event.key === "ArrowUp") {
      if (!panel) open();
      if (!shown.length) return;
      event.preventDefault();
      at = (at + (event.key === "ArrowDown" ? 1 : shown.length - 1)) % shown.length;
      if (at >= count) count = at + PAGE;
      paint();
    } else if (event.key === "Enter" && panel) {
      if (at >= 0 && shown[at] && !(exact() && shown[at].id !== term())) {
        event.preventDefault();
        choose(shown[at].id);
      } else close();
    } else if (event.key === "Escape" && panel) {
      event.preventDefault();
      event.stopPropagation();
      close();
    }
  });
  // The panel is fixed to the screen: follow the box or go.
  const follow = () => panel && place();
  window.addEventListener("resize", follow);
  document.addEventListener("scroll", follow, true);
  return { close };
}
