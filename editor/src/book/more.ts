// The card's More drawer: what has no place in the book itself. How this quest opens (each arrow into it: its
// rule and its conditions), the stage it needs, what hides it until when, where it jumps, and the scrolls it gives.
import { gateOp, linkConditions, upsertLink, type GateOpName, type LinkCondition, type Tile } from "../types";
import { attachCombo } from "./combo";
import { conditionEntries, scrollEntries, stageEntries } from "./lists";
import { h } from "./dom";
import type { Store } from "./store";

const GATES: [GateOpName, string][] = [["and", "AND"], ["or", "OR"], ["not", "NOT"], ["xor", "FORK"]];
const HIDDEN: [string, string][] = [
  ["", "Always shown"], ["quest_complete", "Until a quest is done"], ["stage", "Until a stage"],
  ["advancement", "Until an advancement"], ["team_flag", "Until a team flag"], ["trigger", "Until a trigger"],
];
const CONDITION_TYPES: [string, string][] = [["advancement", "Advancement"], ["stage", "Stage"], ["quest_complete", "Quest done"], ["team_flag", "Team flag"], ["trigger", "Trigger"], ["scoreboard", "Scoreboard"]];

function select(data: string, options: [string, string][], value: string, label: string): HTMLSelectElement {
  const el = h("select", { dataset: { more: data }, "aria-label": label }, ...options.map(([v, text]) => h("option", { value: v }, text))) as HTMLSelectElement;
  el.value = value;
  return el;
}

export interface MoreHooks {
  /** "New stage…": make a stage (named from what was typed) and resolve its id, or null. */
  newStage?: (prefill: string) => Promise<string | null>;
}

export function renderMore(host: HTMLElement, store: Store, hooks: MoreHooks = {}): void {
  const s = store.get();
  const ch = s.chapters.find((c) => c.id === s.openId);
  const tile = ch?.tiles.find((t) => t.id === s.selected);
  if (!ch || !tile) {
    host.replaceChildren();
    return;
  }
  const chapterId = ch.id;
  const tileId = tile.id;
  const editTile = (why: string, fn: (t: Tile) => void) =>
    store.edit(chapterId, why, (c) => { const t = c.tiles.find((x) => x.id === tileId); if (t) fn(t); });
  const titleOf = (id: string) => ch.tiles.find((t) => t.id === id)?.title || id;

  // How it opens: one row per arrow into this quest.
  const inbound = (ch.links ?? []).filter((l) => l.to === tileId);
  const opens = h("section", { class: "more-section" }, h("h3", {}, "How it opens"),
    inbound.length ? null : h("p", { class: "more-note" }, "Nothing leads here, so players can start it right away."),
    ...inbound.map((link) => {
      const conditions = linkConditions(link) as LinkCondition[];
      const gate = select("gate", GATES, gateOp(link), `How ${titleOf(tileId)} opens after ${titleOf(link.from)}`);
      gate.classList.add("gate-select");
      gate.addEventListener("change", () => store.edit(chapterId, "gate", (c) => upsertLink(c, link.from, tileId, gate.value as GateOpName)));
      const setConditions = (next: LinkCondition[]) => store.edit(chapterId, "link conditions", (c) => {
        const l = (c.links ?? []).find((x) => x.from === link.from && x.to === tileId);
        if (l) l.gate = next.length ? { op: gateOp(l), conditions: next } : gateOp(l);
      });
      const rows = conditions.map((cond, i) => {
        const type = select("condition_type", CONDITION_TYPES, cond.type, "Condition type");
        const id = h("input", { value: cond.id, "aria-label": "Condition id", dataset: { more: "condition_id" } }) as HTMLInputElement;
        attachCombo(id, () => conditionEntries(type.value, s), {});
        const update = () => setConditions(conditions.map((c, j) => (j === i ? { type: type.value, id: id.value.trim() } : c)));
        type.addEventListener("change", update);
        id.addEventListener("change", update);
        return h("div", { class: "more-row" }, type, id,
          h("button", { type: "button", class: "row-remove", "aria-label": "Remove condition", onclick: () => setConditions(conditions.filter((_c, j) => j !== i)) }, "✕"));
      });
      return h("div", { class: "more-inbound", dataset: { inbound: link.from } },
        h("div", { class: "more-row" }, h("span", {}, `After ${titleOf(link.from)}`), gate,
          h("span", { class: "more-note" }, `${conditions.length} condition${conditions.length === 1 ? "" : "s"}`)),
        ...rows,
        h("button", { type: "button", class: "picker-btn small", onclick: () => setConditions([...conditions, { type: "advancement", id: "" }]) }, "+ Condition"));
    }));

  // Requires stage.
  const required = h("input", { value: tile.required_stage ?? "", placeholder: "none (pick a stage)", dataset: { more: "required_stage" }, "aria-label": "Requires stage" }) as HTMLInputElement;
  attachCombo(required, () => stageEntries(s), {});
  required.addEventListener("change", () => editTile("requires stage", (t) => {
    const v = required.value.trim();
    if (v) t.required_stage = v;
    else delete t.required_stage;
  }));

  // Hidden until.
  const hiddenType = select("hidden_type", HIDDEN, tile.hidden_until?.type ?? "", "Hidden until");
  const hiddenId = h("input", { value: tile.hidden_until?.id ?? "", placeholder: "id", dataset: { more: "hidden_id" }, "aria-label": "Hidden until: id", hidden: !tile.hidden_until }) as HTMLInputElement;
  const writeHidden = () => editTile("hidden until", (t) => {
    if (!hiddenType.value) delete t.hidden_until;
    else t.hidden_until = { type: hiddenType.value, id: hiddenId.value.trim() };
  });
  hiddenType.addEventListener("change", () => { hiddenId.hidden = !hiddenType.value; writeHidden(); });
  attachCombo(hiddenId, () => conditionEntries(hiddenType.value, s), {});
  hiddenId.addEventListener("change", writeHidden);

  // Jump to: a chapter, then one of its quests.
  const jumpChapter = select("jump_chapter", [["", "No jump"], ...s.chapters.map((c) => [c.id, c.title || c.id] as [string, string])], tile.target?.chapter ?? "", "Jump to chapter");
  const target = s.chapters.find((c) => c.id === (tile.target?.chapter ?? ""));
  const jumpTile = select("jump_tile", [["", "Chapter only"], ...(target?.tiles ?? []).map((t) => [t.id, t.title || t.id] as [string, string])], tile.target?.tile ?? "", "Jump to quest");
  jumpTile.hidden = !tile.target?.chapter;
  jumpChapter.addEventListener("change", () => editTile("jump target", (t) => {
    if (!jumpChapter.value) delete t.target;
    else t.target = { chapter: jumpChapter.value, tile: "" };
  }));
  jumpTile.addEventListener("change", () => editTile("jump target", (t) => {
    if (t.target) t.target = { chapter: t.target.chapter, tile: jumpTile.value };
  }));

  // Scrolls.
  const scrolls = h("input", { value: (tile.scrolls ?? []).join(", "), placeholder: "pack:note, pack:other", dataset: { more: "scrolls" }, "aria-label": "Scrolls this quest gives" }) as HTMLInputElement;
  attachCombo(scrolls, () => scrollEntries(s), { multi: true });
  scrolls.addEventListener("change", () => editTile("scrolls", (t) => {
    const list = scrolls.value.split(",").map((x) => x.trim()).filter(Boolean);
    if (list.length) t.scrolls = list;
    else delete t.scrolls;
  }));

  host.replaceChildren(
    opens,
    h("section", { class: "more-section" }, h("h3", {}, "Requires stage"), h("div", { class: "more-row" }, required,
      hooks.newStage ? h("button", { type: "button", class: "picker-btn small", dataset: { act: "new-stage" }, onclick: async () => {
        const id = await hooks.newStage!(required.value.trim());
        if (!id) return;
        required.value = id;
        required.dispatchEvent(new Event("change"));
      } }, "New stage…") : null)),
    h("section", { class: "more-section" }, h("h3", {}, "Hidden"), h("div", { class: "more-row" }, hiddenType, hiddenId)),
    h("section", { class: "more-section" }, h("h3", {}, "Jump to"), h("div", { class: "more-row" }, jumpChapter, jumpTile)),
    h("section", { class: "more-section" }, h("h3", {}, "Scrolls it gives"), scrolls));
}
