// A chapter's settings, in a book-style page: title, id (only until the game has the chapter), parent, theme,
// background, icon, unlock conditions, hide until unlocked and the board size. Each field saves as you leave it.
import { descendantIds, minGrid } from "../model";
import { chapterGridHeight, chapterGridWidth, GRID_PRESETS, type Chapter, type UnlockCondition } from "../types";
import { h } from "./dom";
import { BOOK_PALETTES } from "./palettes";
import type { Store } from "./store";

const CONDITION_TYPES: [string, string][] = [["stage", "Stage"], ["advancement", "Advancement"], ["quest_complete", "Quest done"], ["chapter_complete", "Chapter done"], ["team_flag", "Team flag"], ["trigger", "Trigger"]];

function select(key: string, options: [string, string][], value: string, label: string): HTMLSelectElement {
  const el = h("select", { dataset: { setting: key }, "aria-label": label }, ...options.map(([v, text]) => h("option", { value: v }, text))) as HTMLSelectElement;
  el.value = value;
  return el;
}

const row = (label: string, ...controls: (HTMLElement | null)[]) => h("label", { class: "settings-row" }, h("span", { class: "settings-label" }, label), ...controls);

export function openChapterSettings(host: HTMLElement, store: Store, chapterId: string): void {
  const s = store.get();
  const ch = s.chapters.find((c) => c.id === chapterId);
  if (!ch) return;
  const edit = (why: string, fn: (c: Chapter) => void) => store.edit(chapterId, why, fn);
  const note = h("p", { class: "settings-note", role: "status" });

  const title = h("input", { value: ch.title ?? "", dataset: { setting: "title" }, "aria-label": "Chapter title" }) as HTMLInputElement;
  title.addEventListener("change", () => edit("chapter title", (c) => { c.title = title.value.trim(); }));

  const saved = s.savedJson.has(chapterId);
  const id = h("input", { value: ch.id, readonly: saved, dataset: { setting: "id" }, "aria-label": "Chapter id",
    title: saved ? "The game already has this chapter under this id" : "namespace:path, lowercase" }) as HTMLInputElement;
  id.readOnly = saved;
  id.addEventListener("change", () => {
    const next = id.value.trim();
    if (saved || next === chapterId) return;
    if (!/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(next)) { note.textContent = "A chapter id is namespace:path in lowercase, like mypack:getting_started."; return; }
    if (store.get().chapters.some((c) => c.id === next)) { note.textContent = `Another chapter already uses ${next}.`; return; }
    const chapters = store.get().chapters.map((c) => (c.id === chapterId ? { ...c, id: next } : c.parent === chapterId ? { ...c, parent: next } : c));
    store.load({ chapters, openId: store.get().openId === chapterId ? next : store.get().openId }, "chapter id");
  });

  const blocked = descendantIds(s.chapters, chapterId);
  const parent = select("parent", [["", "None (top level)"], ...s.chapters.filter((c) => c.id !== chapterId && !blocked.has(c.id)).map((c) => [c.id, c.title || c.id] as [string, string])], ch.parent ?? "", "Parent chapter");
  parent.addEventListener("change", () => edit("chapter parent", (c) => { if (parent.value) c.parent = parent.value; else delete c.parent; }));

  const theme = select("theme", BOOK_PALETTES.map((p) => [p.id, p.name] as [string, string]), ch.theme ?? "midnight_royalty", "Theme");
  theme.addEventListener("change", () => edit("chapter theme", (c) => { c.theme = theme.value; }));

  const bgMode = select("bg_mode", [["", "Theme colour"], ["color", "Colour"], ["image", "Picture"]], ch.background?.mode ?? "", "Background");
  const bgColor = h("input", { type: "color", value: ch.background?.color ?? "#24142c", dataset: { setting: "bg_color" }, "aria-label": "Background colour" }) as HTMLInputElement;
  const bgImage = h("input", { value: ch.background?.image ?? "", placeholder: "mypack:textures/gui/bg.png", dataset: { setting: "bg_image" }, "aria-label": "Background picture" }) as HTMLInputElement;
  const bgOpacity = h("input", { type: "range", min: "0", max: "1", step: "0.05", value: String(ch.background?.opacity ?? 1), dataset: { setting: "bg_opacity" }, "aria-label": "Background opacity" }) as HTMLInputElement;
  const writeBg = () => edit("chapter background", (c) => {
    if (!bgMode.value) delete c.background;
    else c.background = { mode: bgMode.value as "color" | "image", ...(bgMode.value === "color" ? { color: bgColor.value } : { image: bgImage.value.trim() }), opacity: Number(bgOpacity.value) };
  });
  [bgMode, bgColor, bgImage, bgOpacity].forEach((el) => el.addEventListener("change", writeBg));

  const icon = h("input", { value: ch.icon?.item ?? (ch.icon?.glyph ? `glyph:${ch.icon.glyph}` : ""), list: "qq-list-item", placeholder: "minecraft:book or glyph:star", dataset: { setting: "icon" }, "aria-label": "Chapter icon" }) as HTMLInputElement;
  icon.addEventListener("change", () => edit("chapter icon", (c) => {
    const v = icon.value.trim();
    if (!v) delete c.icon;
    else c.icon = v.startsWith("glyph:") ? { glyph: v.slice(6) } : { item: v };
  }));

  const conditions: UnlockCondition[] = typeof ch.unlock === "object" ? [...(ch.unlock.conditions ?? [])] : [];
  const unlockOp = select("unlock_op", [["and", "All of these"], ["or", "Any one of these"]], typeof ch.unlock === "object" ? ch.unlock.op ?? "and" : ch.unlock ?? "and", "Unlock rule");
  const writeUnlock = () => edit("chapter unlock", (c) => {
    c.unlock = { op: unlockOp.value, conditions: [...conditions] };
  });
  unlockOp.addEventListener("change", writeUnlock);
  const unlockRows = h("div", { class: "settings-conditions" }, ...conditions.map((cond, i) => {
    const type = select("unlock_type", CONDITION_TYPES, cond.type, "Condition type");
    const value = h("input", { value: cond.id, "aria-label": "Condition id", dataset: { setting: "unlock_id" } }) as HTMLInputElement;
    const update = () => { conditions[i] = { type: type.value, id: value.value.trim() }; writeUnlock(); };
    type.addEventListener("change", update);
    value.addEventListener("change", update);
    return h("div", { class: "more-row" }, type, value,
      h("button", { type: "button", class: "row-remove", "aria-label": "Remove condition", onclick: () => { conditions.splice(i, 1); writeUnlock(); openChapterSettings(host, store, chapterId); } }, "✕"));
  }), h("button", { type: "button", class: "picker-btn small", onclick: () => {
    // Kept in the chapter at once (an empty id is a save problem until it is filled in), so it survives the redraw.
    conditions.push({ type: "stage", id: "" });
    writeUnlock();
    openChapterSettings(host, store, chapterId);
  } }, "+ Condition"));

  const hide = h("input", { type: "checkbox", checked: Boolean(ch.hide_until_unlocked), dataset: { setting: "hide" } }) as HTMLInputElement;
  hide.checked = Boolean(ch.hide_until_unlocked);
  hide.addEventListener("change", () => edit("chapter hidden", (c) => { if (hide.checked) c.hide_until_unlocked = true; else delete c.hide_until_unlocked; }));

  const w = chapterGridWidth(ch);
  const hh = chapterGridHeight(ch);
  const sizeKey = `${w}x${hh}`;
  const presets: [string, string][] = GRID_PRESETS.map((p) => [`${p.width}x${p.height}`, `${p.width} × ${p.height}`]);
  if (!presets.some(([k]) => k === sizeKey)) presets.unshift([sizeKey, `${w} × ${hh}`]);
  const grid = select("grid", [...presets, ["custom", "Custom size…"]], sizeKey, "Board size");
  const customW = h("input", { type: "number", min: "1", max: "128", value: String(w), "aria-label": "Board width", hidden: true }) as HTMLInputElement;
  const customH = h("input", { type: "number", min: "1", max: "128", value: String(hh), "aria-label": "Board height", hidden: true }) as HTMLInputElement;
  const applyGrid = (width: number, height: number) => {
    const least = minGrid(store.get().chapters.find((c) => c.id === chapterId)!);
    const fw = Math.max(width, least.width);
    const fh = Math.max(height, least.height);
    note.textContent = fw !== width || fh !== height ? `The board must be at least ${least.width} × ${least.height} to keep every quest on it, so it is ${fw} × ${fh}. Move quests first to make it smaller.` : "";
    edit("board size", (c) => { c.grid_width = Math.min(128, fw); c.grid_height = Math.min(128, fh); });
  };
  grid.addEventListener("change", () => {
    const custom = grid.value === "custom";
    customW.hidden = customH.hidden = !custom;
    if (custom) { customW.focus(); return; }
    const [gw, gh] = grid.value.split("x").map(Number);
    applyGrid(gw, gh);
  });
  [customW, customH].forEach((el) => el.addEventListener("change", () => applyGrid(Number(customW.value) || w, Number(customH.value) || hh)));

  host.replaceChildren(h("div", { class: "chapter-settings", role: "region", "aria-label": `Settings for ${ch.title || ch.id}` },
    h("h2", { class: "settings-title" }, "Chapter settings"),
    row("Title", title), row("Id", id), row("Parent", parent), row("Theme", theme),
    row("Background", bgMode, bgColor, bgImage, bgOpacity), row("Icon", icon),
    h("div", { class: "settings-row" }, h("span", { class: "settings-label" }, "Unlocks"), unlockOp, unlockRows),
    row("Hide until unlocked", hide), row("Board size", grid, customW, customH), note));
}
