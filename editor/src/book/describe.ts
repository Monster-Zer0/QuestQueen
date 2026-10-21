// A quest description edited in place: the rendered text on the card turns into its markup, a toolbar for
// the book's markup (bold, italic, underline, headings, bullet, rule, colour, glyph, item, picture) and a live render
// under it. Done keeps it, Cancel or Esc puts it back. Toolbar inserts go after the selection, never over it.
import { GLYPH_IDS } from "../glyphs";
import { descriptionProblems, richToHtml, validTexture, type PreviewOptions } from "../richtext";
import type { PackCatalog } from "../packCatalog";
import { attachCombo, itemIcon, textureIcon } from "./combo";
import { h } from "./dom";

export interface DescribeOptions {
  value: string;
  onCommit: (value: string) => void;
  preview: PreviewOptions;
  /** The pack's lists, for the item and picture boxes. */
  catalog?: () => PackCatalog;
}

/** Open editors, each able to commit itself: a redraw that is about to replace one flushes it first. */
const openEditors = new Map<HTMLElement, () => boolean>();

/**
 * Commit every open description editor (inside `within`, when given), as Done would. Returns whether any of them
 * changed something, so a caller that redraws can stop: the commit has already redrawn.
 */
export function flushDescribe(within?: Element | null): boolean {
  let changed = false;
  for (const [host, commit] of [...openEditors]) {
    if (within && !within.contains(host)) continue;
    if (commit()) changed = true;
  }
  return changed;
}

export function openDescribe(host: HTMLElement, opts: DescribeOptions): void {
  host.dataset.editing = "1";
  const area = h("textarea", { class: "desc-source", rows: "6", "aria-label": "Quest description (book markup)", spellcheck: "true" }) as HTMLTextAreaElement;
  area.value = opts.value;
  const preview = h("div", { class: "desc-live" });
  const problems = h("div", { class: "desc-problems", role: "status" });
  const note = h("div", { class: "desc-note", role: "status" });
  const refresh = () => {
    preview.innerHTML = richToHtml(area.value, opts.preview);
    problems.replaceChildren(...descriptionProblems(area.value).map((p) => h("div", {}, p)));
  };
  const fire = () => area.dispatchEvent(new Event("input", { bubbles: true }));
  const surround = (open: string, close: string, placeholder: string) => {
    const a = area.selectionStart;
    const b = area.selectionEnd;
    const picked = area.value.slice(a, b) || placeholder;
    area.value = area.value.slice(0, a) + open + picked + close + area.value.slice(b);
    area.setSelectionRange(a + open.length, a + open.length + picked.length);
    area.focus();
    fire();
  };
  const prefixLines = (prefix: string) => {
    const a = area.selectionStart;
    const b = area.selectionEnd;
    const start = area.value.lastIndexOf("\n", a - 1) + 1;
    const stopAt = area.value.indexOf("\n", b);
    const stop = stopAt < 0 ? area.value.length : stopAt;
    const block = area.value.slice(start, stop).split("\n").map((line) => prefix + line.replace(/^(#{1,2} |- )/, "")).join("\n");
    area.value = area.value.slice(0, start) + block + area.value.slice(stop);
    area.setSelectionRange(start, start + block.length);
    area.focus();
    fire();
  };
  const insert = (text: string, ownLine = false) => {
    // After the selection, never over it: H1/H2/bullet leave their block selected.
    const a = area.selectionEnd;
    const before = area.value.slice(0, a);
    const after = area.value.slice(a);
    const put = ownLine ? (before && !before.endsWith("\n") ? "\n" : "") + text + (after && !after.startsWith("\n") ? "\n" : "") : text;
    area.value = before + put + after;
    area.setSelectionRange(a + put.length, a + put.length);
    area.focus();
    fire();
  };
  const color = h("input", { type: "color", value: "#ffaa00", "aria-label": "Text colour" }) as HTMLInputElement;
  const glyph = h("select", { "aria-label": "Insert a glyph" }, h("option", { value: "" }, "Glyph…"),
    ...GLYPH_IDS.map((id) => h("option", { value: id }, id))) as HTMLSelectElement;
  glyph.addEventListener("change", () => {
    if (glyph.value) insert(`{glyph:${glyph.value}}`);
    glyph.value = "";
  });
  const item = h("input", { class: "desc-item-id", placeholder: "minecraft:diamond", "aria-label": "Item id to show in the text" }) as HTMLInputElement;
  const picPath = h("input", { class: "desc-pic-path", placeholder: "mypack:textures/quest/castle.png", "aria-label": "Picture: resource pack texture" }) as HTMLInputElement;
  if (opts.catalog) {
    const catalog = opts.catalog;
    attachCombo(item, () => catalog().items, { icon: itemIcon });
    attachCombo(picPath, () => catalog().textures ?? [], { icon: textureIcon });
  }
  const picCaption = h("input", { class: "desc-pic-caption", placeholder: "caption (optional)", "aria-label": "Picture caption" }) as HTMLInputElement;
  const tool = (label: string, title: string, run: () => void) =>
    h("button", { type: "button", class: "desc-tool", title, "aria-label": title, onclick: (event: Event) => { event.preventDefault(); note.textContent = ""; run(); } }, label);
  const toolbar = h("div", { class: "desc-tools", role: "toolbar", "aria-label": "Description formatting" },
    tool("B", "Bold: **text**", () => surround("**", "**", "bold text")),
    tool("I", "Italic: *text*", () => surround("*", "*", "italic text")),
    tool("U", "Underline: __text__", () => surround("__", "__", "underlined text")),
    tool("H1", "Heading: # text", () => prefixLines("# ")),
    tool("H2", "Smaller heading: ## text", () => prefixLines("## ")),
    tool("•", "Bullet: - text", () => prefixLines("- ")),
    tool("—", "Divider line: ---", () => insert("---", true)),
    color,
    tool("Colour", "Colour the selected text", () => surround(`{#${color.value.slice(1).toUpperCase()}}`, "{/#}", "coloured text")),
    glyph,
    h("span", { class: "desc-tool-group" }, item, tool("Item", "Show an item's icon in the text", () => {
      const id = item.value.trim();
      if (!id) return;
      insert(`{item:${id}}`);
      item.value = "";
    })),
    h("span", { class: "desc-tool-group" }, picPath, picCaption, tool("Picture", "Add a picture on its own line", () => {
      const path = picPath.value.trim();
      if (!validTexture(path)) {
        note.textContent = "Use a resource pack texture path: namespace:textures/name.png";
        return;
      }
      insert(`![${picCaption.value.trim()}](${path})`, true);
      picPath.value = "";
      picCaption.value = "";
    })));
  const close = (keep: boolean): boolean => {
    if (!openEditors.has(host)) return false;
    openEditors.delete(host);
    delete host.dataset.editing;
    host.replaceChildren();
    host.innerHTML = richToHtml(keep ? area.value : opts.value, opts.preview);
    if (!keep || area.value === opts.value) return false;
    opts.onCommit(area.value);
    return true;
  };
  openEditors.set(host, () => close(true));
  // Leaving for somewhere else on the page keeps what was written, like every other field here.
  host.addEventListener("focusout", (event) => {
    const next = (event as FocusEvent).relatedTarget as Node | null;
    if (next && !host.contains(next) && host.dataset.editing) close(true);
  });
  const done = h("button", { type: "button", class: "desc-btn primary", dataset: { act: "desc-done" }, onclick: () => close(true) }, "Done");
  const cancel = h("button", { type: "button", class: "desc-btn", dataset: { act: "desc-cancel" }, onclick: () => close(false) }, "Cancel");
  area.addEventListener("input", refresh);
  area.addEventListener("keydown", (event) => {
    if (event.key === "Escape") {
      event.preventDefault();
      event.stopPropagation();
      close(false);
    }
  });
  host.replaceChildren(toolbar, area, h("div", { class: "desc-caption" }, "Players see:"), preview, problems, note, h("div", { class: "desc-actions" }, cancel, done));
  refresh();
  area.focus();
}
