// The quest card, in the original mock's chrome: a 1px frame in the state colour, a filled state tab with the
// angled notch top-left, close × top-right, the title, the description, the tasks as sentences, and a REWARD
// section. In Edit it is the mock's EDIT MODE card: orange frame and tab, title and text in EDIT gold, everything
// editable in place, rewards in the EDIT ring, ⊕ TASK / ⊕ REWARD chips and a bottom strip (More, Duplicate,
// Delete). In Preview it is the card a player would get, with the play buttons drawn but inert.
import { bridgeBase } from "../bridge";
import { GLYPH_IDS, glyphSvg } from "../glyphs";
import { iconUrl } from "../itemSelect";
import { richToHtml } from "../richtext";
import type { Chapter, Reward, Task, Tile } from "../types";
import type { Board } from "./board";
import { openDescribe } from "./describe";
import { h, keptImage, releaseImages } from "./dom";
import { inlineText } from "./inline";
import { namer } from "./names";
import { concealed, unlockedChapters, visual } from "./progress";
import { rewardSentence, taskSentence } from "./sentences";
import type { BookState, Store } from "./store";

export interface CardHooks {
  board?: Board;
  /** Open the sentence builder for task i (Task 13), or for a new task when i is -1. */
  editTask?: (index: number, anchor: HTMLElement) => void;
  editReward?: (index: number, anchor: HTMLElement) => void;
  more?: (anchor: HTMLElement) => void;
  duplicate?: () => void;
  remove?: () => void;
}

const STATE_VAR: Record<string, string> = {
  CURRENT: "--current", NEW: "--neu", COMPLETED: "--completed", FAILED: "--failed", CLOSED: "--closed", LOCKED: "--port-red", EDIT: "--edit",
};

const pictureUrl = (texture: string) => `${bridgeBase()}/api/texture?id=${encodeURIComponent(texture)}`;
const preview = { iconUrl: (id: string) => iconUrl(bridgeBase(), id), pictureUrl };

/** The item a task row shows: its own item, else its display item. */
function taskItem(task: Task): string | undefined {
  return task.item || task.icon || undefined;
}

function rewardItem(reward: Reward): string | undefined {
  if (reward.type === "item") return reward.item || undefined;
  if (reward.type === "choice") return reward.options?.find((o) => o.item)?.item;
  if (reward.type === "command" && reward.icon && !reward.icon.startsWith("glyph:") && !reward.icon.includes("textures/")) return reward.icon;
  if (reward.type === "xp" || reward.type === "xp_levels") return "minecraft:experience_bottle";
  if (reward.type === "loot") return "minecraft:chest";
  return undefined;
}

function iconBox(scope: HTMLElement, item: string | undefined, glyph: string | undefined, cls: string): HTMLElement {
  const box = h("span", { class: `card-icon-box ${cls}`, "aria-hidden": "true" });
  if (glyph) box.innerHTML = glyphSvg(glyph);
  else if (item) box.append(keptImage(scope, iconUrl(bridgeBase(), item.split("[")[0]), { class: "icon", alt: "" }));
  return box;
}

/** Pick a quest icon: an item from the pack, a QuestQueen glyph, or none. */
function openIconPicker(anchor: HTMLElement, state: BookState, current: Tile["icon"], onPick: (icon: Tile["icon"]) => void) {
  document.querySelector(".icon-picker")?.remove();
  const listId = "icon-picker-items";
  const input = h("input", { class: "icon-picker-input", list: listId, placeholder: "Item id, e.g. minecraft:diamond", "aria-label": "Item", value: current?.item ?? "" }) as HTMLInputElement;
  const close = () => pop.remove();
  const glyphs = h("div", { class: "icon-picker-glyphs", role: "group", "aria-label": "QuestQueen glyphs" },
    ...GLYPH_IDS.map((id) => {
      const b = h("button", { type: "button", class: "glyph-btn", "aria-label": id, title: id, onclick: () => { onPick({ glyph: id }); close(); } });
      b.innerHTML = glyphSvg(id);
      return b;
    }));
  const pop = h("div", { class: "icon-picker", role: "dialog", "aria-label": "Quest icon" },
    h("label", { class: "pick-label" }, "Item", input),
    h("datalist", { id: listId }, ...state.catalog.items.slice(0, 5000).map((e) => h("option", { value: e.id }, e.name ?? e.id))),
    h("div", { class: "pick-label" }, "or a glyph"), glyphs,
    h("div", { class: "pick-actions" },
      h("button", { type: "button", class: "desc-btn", onclick: () => { onPick(undefined); close(); } }, "No icon"),
      h("button", { type: "button", class: "desc-btn primary", onclick: () => { const id = input.value.trim(); onPick(id ? { item: id } : undefined); close(); } }, "Use item")));
  pop.addEventListener("keydown", (event) => {
    if (event.key === "Escape") { event.stopPropagation(); close(); anchor.focus(); }
    if (event.key === "Enter" && event.target === input) { const id = input.value.trim(); if (id) { onPick({ item: id }); close(); } }
  });
  const rect = anchor.getBoundingClientRect();
  pop.style.left = `${Math.max(8, rect.left)}px`;
  pop.style.top = `${rect.bottom + 6}px`;
  document.body.append(pop);
  input.focus();
}

/** Place the card beside the selected quest's tile, on whichever side has room, inside the layer. */
function place(card: HTMLElement, layer: HTMLElement, tile: Tile, board: Board | undefined) {
  if (!board) return;
  const g = board.geometry();
  const plane = board.plane.getBoundingClientRect();
  const box = layer.getBoundingClientRect();
  const left = plane.left - box.left + tile.pos.x * g.stride;
  const top = plane.top - box.top + tile.pos.y * g.stride;
  const size = g.size * g.px;
  const w = card.offsetWidth || 416;
  const hgt = card.offsetHeight || 300;
  let x = left + size + 16;
  if (x + w > box.width - 8) x = left - w - 16;
  x = Math.max(8, Math.min(x, box.width - w - 8));
  const y = Math.max(8, Math.min(top, box.height - hgt - 8));
  card.style.left = `${Math.round(x)}px`;
  card.style.top = `${Math.round(y)}px`;
}

export function renderCard(layer: HTMLElement, store: Store, hooks: CardHooks = {}): void {
  const s = store.get();
  const ch: Chapter | undefined = s.chapters.find((c) => c.id === s.openId);
  const tile = s.selected ? ch?.tiles.find((t) => t.id === s.selected) : undefined;
  const existing = layer.querySelector<HTMLElement>(".quest-card");
  if (!ch || !tile) {
    existing?.remove();
    return;
  }
  // Never redraw over a field someone is typing in.
  if (existing?.querySelector("[data-editing]") && existing.dataset.tile === tile.id) return;
  const chapterId = ch.id;
  const tileId = tile.id;
  const editing = s.mode === "edit";
  const v = visual(ch, tile, s.sim, s.chapters, unlockedChapters(s.chapters, s.sim).has(ch.id));
  const hidden = concealed(ch, tile, s.sim);
  const word = editing ? "EDIT MODE" : v;
  const name = namer(s);
  const editTile = (why: string, fn: (t: Tile) => void) =>
    store.edit(chapterId, why, (c) => { const t = c.tiles.find((x) => x.id === tileId); if (t) fn(t); });

  const card = existing ?? h("article", { class: "quest-card", role: "region" });
  card.dataset.tile = tile.id;
  card.className = `quest-card${editing ? " editing" : ""}`;
  card.style.setProperty("--state", `var(${STATE_VAR[editing ? "EDIT" : v] ?? "--neu"})`);
  card.setAttribute("aria-label", `Quest: ${tile.title || tile.id}`);

  const head = h("header", { class: "card-head" },
    h("span", { class: "card-tab qq-text" }, word),
    h("button", { type: "button", class: "card-close", "aria-label": "Close the card", onclick: () => store.select(null) }, "×"));

  const iconBtn = h(editing ? "button" : "span", { type: editing ? "button" : undefined, class: `card-icon${editing ? " editable" : ""}`, "aria-label": editing ? "Change the quest icon" : undefined },
    iconBox(card, tile.icon?.item, tile.icon?.glyph, "big"));
  if (editing) iconBtn.addEventListener("click", () => openIconPicker(iconBtn, s, tile.icon, (icon) => editTile("quest icon", (t) => {
    if (icon) t.icon = icon;
    else delete t.icon;
  })));
  const title = h("h2", { class: "card-title qq-text" });
  if (editing) inlineText(title, { value: tile.title ?? "", label: "Quest title", onCommit: (text) => editTile("rename quest", (t) => { t.title = text; }) });
  else title.textContent = tile.title || tile.id;

  const desc = h("div", { class: `card-desc qq-text${editing ? " editable" : ""}`, tabindex: editing ? "0" : undefined, role: editing ? "button" : undefined, "aria-label": editing ? "Edit the description" : undefined });
  desc.innerHTML = tile.description ? richToHtml(tile.description, preview) : editing ? '<span class="card-empty">Add a description…</span>' : "";
  if (editing) {
    const openEditor = () => {
      if (desc.dataset.editing) return;
      desc.classList.add("describing");
      openDescribe(desc, { value: tile.description ?? "", preview, onCommit: (text) => editTile("description", (t) => { t.description = text; }) });
    };
    desc.addEventListener("click", (event) => { if (!desc.dataset.editing && !(event.target as HTMLElement).closest("a")) openEditor(); });
    desc.addEventListener("keydown", (event) => { if (!desc.dataset.editing && (event.key === "Enter" || event.key === " ")) { event.preventDefault(); openEditor(); } });
  }

  const tasks = h("div", { class: "card-tasks" },
    ...(tile.tasks ?? []).map((task, i) => {
      const row = h(editing ? "button" : "div", { type: editing ? "button" : undefined, class: `task-row${editing ? " editable" : ""}`, dataset: { index: String(i) } },
        iconBox(card, taskItem(task), undefined, "small"),
        h("span", { class: "task-text qq-text" }, taskSentence(task, name)),
        !editing && v !== "COMPLETED" ? h("span", { class: "task-count qq-text" }, (task.count ?? 1) > 1 ? `0/${task.count}` : "") : null);
      if (editing) row.addEventListener("click", () => hooks.editTask?.(i, row));
      if (!editing) return row;
      const remove = h("button", { type: "button", class: "row-remove", "aria-label": `Remove task ${i + 1}`, onclick: () => editTile("remove task", (t) => { t.tasks?.splice(i, 1); }) }, "✕");
      return h("div", { class: "row-wrap" }, row, remove);
    }));
  if (editing) {
    const add = h("button", { type: "button", class: "edit-chip qq-text", onclick: () => hooks.editTask?.(-1, add) }, "⊕ TASK");
    tasks.append(add);
  }

  const rewards = h("div", { class: "card-rewards" }, h("div", { class: "card-caption qq-text" }, "REWARD"),
    ...(tile.rewards ?? []).map((reward, i) => {
      const row = h(editing ? "button" : "div", { type: editing ? "button" : undefined, class: `reward-row${editing ? " editable" : ""}` },
        h("span", { class: editing ? "reward-ring" : "reward-slot" }, iconBox(card, rewardItem(reward), reward.type === "command" && reward.icon?.startsWith("glyph:") ? reward.icon.slice(6) : undefined, "small")),
        h("span", { class: "reward-text qq-text" }, rewardSentence(reward, name)));
      if (editing) row.addEventListener("click", () => hooks.editReward?.(i, row));
      if (!editing) return row;
      const remove = h("button", { type: "button", class: "row-remove", "aria-label": `Remove reward ${i + 1}`, onclick: () => editTile("remove reward", (t) => { t.rewards?.splice(i, 1); }) }, "✕");
      return h("div", { class: "row-wrap" }, row, remove);
    }));
  if (editing) {
    const add = h("button", { type: "button", class: "edit-chip qq-text", onclick: () => hooks.editReward?.(-1, add) }, "⊕ REWARD");
    rewards.append(add);
  }

  const foot = editing
    ? h("div", { class: "card-strip" },
        h("button", { type: "button", class: "strip-btn", "aria-haspopup": "dialog", onclick: (e: Event) => hooks.more?.(e.currentTarget as HTMLElement) }, "More ▾"),
        h("span", { class: "strip-space" }),
        h("button", { type: "button", class: "strip-btn", onclick: () => hooks.duplicate?.() }, "Duplicate"),
        h("button", { type: "button", class: "strip-btn danger", onclick: () => hooks.remove?.() }, "Delete"))
    : h("div", { class: "card-play" },
        v === "COMPLETED" && (tile.rewards ?? []).length ? h("button", { type: "button", class: "play-btn qq-text", dataset: { play: "claim" }, disabled: true }, "CLAIM") : null,
        v !== "COMPLETED" && v !== "FAILED" && v !== "LOCKED" && v !== "CLOSED" ? h("button", { type: "button", class: "play-btn outline qq-text", dataset: { play: "pin" }, disabled: true }, "PIN") : null);

  const top = h("div", { class: "card-top" }, iconBtn, title);
  const body = hidden && !editing ? [head, h("p", { class: "card-desc qq-text" }, "???")] : [head, top, desc, tasks, rewards, foot];
  card.replaceChildren(...body);
  releaseImages(card);
  if (!existing) layer.append(card);
  place(card, layer, tile, hooks.board);
}
