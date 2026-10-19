// Tasks and rewards in plain words. The book shows a task as a sentence ("Defeat 3 Zombie"), the pickers group
// task types the way a player thinks about them (Gather · Fight · Explore · Do), and a new task or reward starts
// with empty fields, never an example id, so an unfinished one is a save problem instead of a stray zombie.
import { rewardName } from "../commandReward";
import type { Reward, Task } from "../types";

export type Group = "Gather" | "Fight" | "Explore" | "Do";

/** What a field holds, and so which picker edits it. */
export type Picker =
  | "item" | "tag" | "entity" | "entityTag" | "block" | "biome" | "structure" | "dimension" | "fluid"
  | "advancement" | "loot" | "stage" | "number" | "text";

export interface FieldSpec {
  key: string;
  label: string;
  picker: Picker;
  /** Either this field or that one is needed ("entity or tag", "block or entity"). */
  optionalWith?: string;
}

export interface Kind {
  type: string;
  group: Group;
  name: string;
  hint: string;
  /** `{a}` is a field; `{a|b}` the first of the two that is filled in. */
  sentence: string;
  fields: FieldSpec[];
}

const f = (key: string, label: string, picker: Picker, optionalWith?: string): FieldSpec =>
  optionalWith ? { key, label, picker, optionalWith } : { key, label, picker };
const COUNT = f("count", "How many", "number");

export const TASK_KINDS: Kind[] = [
  { type: "obtain", group: "Gather", name: "Collect items", hint: "Have them in the inventory", sentence: "Collect {count} {item}", fields: [COUNT, f("item", "Item", "item")] },
  { type: "submit", group: "Gather", name: "Hand items in", hint: "Taken when the quest ends", sentence: "Hand in {count} {item}", fields: [COUNT, f("item", "Item", "item")] },
  { type: "item_tag", group: "Gather", name: "Collect by tag", hint: "Any planks, any logs…", sentence: "Collect {count} of any {tag}", fields: [COUNT, f("tag", "Tag", "tag")] },
  { type: "fluid", group: "Gather", name: "Collect a fluid", hint: "Water, lava…", sentence: "Collect {count} {fluid}", fields: [COUNT, f("fluid", "Fluid", "fluid")] },
  { type: "kill", group: "Fight", name: "Defeat mobs", hint: "A mob, or any in a tag", sentence: "Defeat {count} {entity|tag}", fields: [COUNT, f("entity", "Mob", "entity", "tag"), f("tag", "Or any mob in tag", "entityTag", "entity")] },
  { type: "raid", group: "Fight", name: "Win a raid", hint: "Survive the waves", sentence: "Win a raid of {waves} waves", fields: [f("waves", "Waves", "number")] },
  { type: "location", group: "Explore", name: "Go to a place", hint: "Coordinates and radius", sentence: "Go within {radius} blocks of {x} {y} {z}", fields: [f("x", "X", "number"), f("y", "Y", "number"), f("z", "Z", "number"), f("radius", "Radius", "number"), f("dimension", "Dimension", "dimension")] },
  { type: "visit_structure", group: "Explore", name: "Find a structure", hint: "Village, fortress…", sentence: "Find a {structure}", fields: [f("structure", "Structure", "structure")] },
  { type: "visit_dimension", group: "Explore", name: "Enter a dimension", hint: "The Nether, the End…", sentence: "Enter {dimension}", fields: [f("dimension", "Dimension", "dimension")] },
  { type: "visit_biome", group: "Explore", name: "Visit a biome", hint: "Desert, plains…", sentence: "Visit {biome}", fields: [f("biome", "Biome", "biome")] },
  { type: "observation", group: "Explore", name: "Look at something", hint: "A block or a mob", sentence: "Look at {block|entity}", fields: [f("block", "Block", "block", "entity"), f("entity", "Or a mob", "entity", "block")] },
  { type: "checkmark", group: "Do", name: "Tick a box", hint: "The player clicks it done", sentence: "Tick it off", fields: [] },
  { type: "advancement", group: "Do", name: "Earn an advancement", hint: "Any advancement", sentence: "Earn {advancement}", fields: [f("advancement", "Advancement", "advancement")] },
  { type: "interact_block", group: "Do", name: "Use a block", hint: "Right-click it", sentence: "Use {block} {count} times", fields: [f("block", "Block", "block"), COUNT] },
  { type: "interact_entity", group: "Do", name: "Use on a mob", hint: "Right-click it", sentence: "Use on {entity} {count} times", fields: [f("entity", "Mob", "entity"), COUNT] },
  { type: "xp_levels", group: "Do", name: "Spend XP levels", hint: "Taken when handed in", sentence: "Spend {levels} XP levels", fields: [f("levels", "Levels", "number")] },
  { type: "stat", group: "Do", name: "Reach a statistic", hint: "Blocks walked, jumps…", sentence: "Reach {count} {stat}", fields: [COUNT, f("stat", "Statistic", "text")] },
  { type: "npc_dialog", group: "Do", name: "Talk to someone", hint: "An NPC by name", sentence: "Talk to {npc}", fields: [f("npc", "NPC", "text")] },
  { type: "trigger", group: "Do", name: "Script trigger", hint: "Fired by a command or script", sentence: "Wait for {trigger}", fields: [f("trigger", "Trigger id", "text")] },
];

export const REWARD_KINDS: Kind[] = [
  { type: "item", group: "Do", name: "Item", hint: "Into the inventory", sentence: "{count} {item}", fields: [COUNT, f("item", "Item", "item")] },
  { type: "xp", group: "Do", name: "XP points", hint: "Experience points", sentence: "{amount} XP", fields: [f("amount", "XP", "number")] },
  { type: "xp_levels", group: "Do", name: "XP levels", hint: "Whole levels", sentence: "{levels} levels", fields: [f("levels", "Levels", "number")] },
  { type: "command", group: "Do", name: "Run a command", hint: "With its own name and icon", sentence: "", fields: [f("command", "Command", "text")] },
  { type: "loot", group: "Do", name: "Loot table", hint: "Rolled when claimed", sentence: "Loot from {table}", fields: [f("table", "Loot table", "loot")] },
  { type: "toast", group: "Do", name: "Message", hint: "A pop-up message", sentence: "Shows “{message}”", fields: [f("message", "Message", "text")] },
  { type: "advancement", group: "Do", name: "Grant an advancement", hint: "Any advancement", sentence: "{advancement}", fields: [f("advancement", "Advancement", "advancement")] },
  { type: "choice", group: "Do", name: "Player chooses", hint: "One item of several", sentence: "", fields: [] },
  { type: "stage", group: "Do", name: "Give a stage", hint: "Unlocks what the stage locks", sentence: "Stage {stage}", fields: [f("stage", "Stage", "stage")] },
];

export function kindOf(type: string): Kind | undefined {
  return TASK_KINDS.find((k) => k.type === type);
}

export function rewardKindOf(type: string): Kind | undefined {
  return REWARD_KINDS.find((k) => k.type === type);
}

/** Starting values for a field nobody has filled in yet. */
function blank(field: FieldSpec, type: string): string | number | undefined {
  if (field.picker !== "number") return field.optionalWith && field.key !== firstOf(type) ? undefined : "";
  if (type === "location") return { x: 0, y: 64, z: 0, radius: 8 }[field.key];
  return 1;
}

/** Of an "a or b" pair, the field a new task fills in. */
function firstOf(type: string): string | undefined {
  return kindOf(type)?.fields.find((field) => field.optionalWith)?.key;
}

function fill<T extends { type: string }>(kind: Kind | undefined, type: string): T {
  const out: Record<string, unknown> = { type };
  for (const field of kind?.fields ?? []) {
    const value = blank(field, type);
    if (value !== undefined) out[field.key] = value;
  }
  return out as T;
}

/** A new task of this type: counts at 1, every id and text empty. */
export function emptyTask(type: string): Task {
  return fill<Task>(kindOf(type) ?? kindOf("checkmark"), kindOf(type) ? type : "checkmark");
}

/** A new reward of this type: counts at 1, every id and text empty; a choice starts with two empty items. */
export function emptyReward(type: string): Reward {
  if (type === "choice") return { type, options: [emptyReward("item"), emptyReward("item")] };
  if (type === "command") return { type, command: "" };
  return fill<Reward>(rewardKindOf(type), type);
}

/** Display name for an id: (picker kind, id) → name. The book passes the pack catalog's names. */
export type Namer = (kind: string, id: string) => string;

function render(kind: Kind, value: Record<string, unknown>, name: Namer): string {
  return kind.sentence.replace(/\{([a-z_]+)(?:\|([a-z_]+))?\}/g, (_m, a: string, b?: string) => {
    const key = [a, b].find((k) => k && value[k] !== undefined && value[k] !== "") ?? a;
    const raw = value[key];
    if (raw === undefined || raw === "") return "…";
    if (typeof raw === "number") return String(raw);
    const spec = kind.fields.find((field) => field.key === key);
    return spec && spec.picker !== "text" && spec.picker !== "number" ? name(spec.picker, String(raw)) : String(raw);
  }).replace(/\s+/g, " ").trim();
}

export function taskSentence(task: Task, name: Namer): string {
  const kind = kindOf(task.type);
  return kind ? render(kind, task as unknown as Record<string, unknown>, name) : task.type;
}

export function rewardSentence(reward: Reward, name: Namer): string {
  if (reward.type === "command") return rewardName(reward);
  if (reward.type === "choice") return "One of: " + (reward.options ?? []).map((o) => rewardSentence(o, name)).join(" · ");
  const kind = rewardKindOf(reward.type);
  if (!kind) return reward.type;
  if (reward.type === "item" && (reward.count ?? 1) === 1) return reward.item ? name("item", reward.item) : "…";
  return render(kind, reward as unknown as Record<string, unknown>, name);
}
