// The Living Book's frame: the slim editor bar, the book (chapter sidebar + board), the layer the quest card
// floats in, the layer pop-ups and dialogs open in, a polite live region, and the "waiting for Minecraft" lock.
import { h } from "./dom";
import { cssColor, type BookPalette } from "./palettes";

export interface ShellParts {
  root: HTMLElement;
  bar: HTMLElement;
  book: HTMLElement;
  sidebar: HTMLElement;
  board: HTMLElement;
  cardLayer: HTMLElement;
  popLayer: HTMLElement;
  live: HTMLElement;
  lock: HTMLElement;
  hint: HTMLElement;
}

function barButton(act: string, label: string, extra: Record<string, unknown> = {}): HTMLElement {
  return h("button", { type: "button", class: "bar-btn", dataset: { act }, ...extra }, label);
}

export function mountShell(root: HTMLElement): ShellParts {
  root.classList.add("qq-book-app");
  const hint = h("span", { class: "bar-hint" }, "Click any text or picture to change it.");
  const save = h("button", { type: "button", class: "bar-btn bar-save", dataset: { act: "save" } },
    "Save to world", h("span", { class: "save-count", hidden: true }, ""));
  const bar = h("header", { class: "book-bar", "aria-label": "Editor" },
    h("span", { class: "bar-brand qq-text" }, "Quest Queen"),
    h("div", { class: "bar-seg", role: "group", "aria-label": "Mode" },
      h("button", { type: "button", dataset: { mode: "edit" }, "aria-pressed": "true" }, "Edit"),
      h("button", { type: "button", dataset: { mode: "preview" }, "aria-pressed": "false" }, "Preview as player")),
    barButton("preview-as", "Previewing: New player ▾", { "aria-haspopup": "menu" }),
    hint,
    h("span", { class: "bar-space" }),
    barButton("stages", "Stages"),
    barButton("find", "Find · Ctrl K"),
    barButton("problems", "", { hidden: true }),
    barButton("file", "File ▾", { "aria-haspopup": "menu" }),
    barButton("help", "?", { "aria-label": "Help for this screen" }),
    save);
  const sidebar = h("nav", { class: "book-sidebar", "aria-label": "Chapters" });
  const cardLayer = h("div", { class: "card-layer" });
  const board = h("section", { class: "book-board", "aria-label": "Quest board" });
  const book = h("main", { class: "book" }, sidebar, h("div", { class: "board-wrap" }, board, cardLayer));
  const popLayer = h("div", { class: "pop-layer" });
  const live = h("div", { class: "visually-hidden", "aria-live": "polite", role: "status" });
  const lock = h("div", { class: "book-lock", role: "alert" },
    h("div", { class: "lock-card qq-text" }, "Waiting for Minecraft…", h("small", {}, "Run /questqueen editor in game.")));
  root.replaceChildren(bar, book, popLayer, live, lock);
  return { root, bar, book, sidebar, board, cardLayer, popLayer, live, lock, hint };
}

/** CSS custom property for a BookPalette field: voidColor → --void, cellLine → --cell-line. */
function propName(field: string): string {
  if (field === "voidColor") return "--void";
  return "--" + field.replace(/[A-Z]/g, (c) => "-" + c.toLowerCase());
}

/** Write a chapter theme's colours onto an element as CSS custom properties. */
export function applyPalette(el: HTMLElement, palette: BookPalette): void {
  for (const [field, argb] of Object.entries(palette)) {
    if (typeof argb === "string" && argb.startsWith("#") && argb.length === 9) el.style.setProperty(propName(field), cssColor(argb));
  }
}
