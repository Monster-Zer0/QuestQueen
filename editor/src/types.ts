export interface GridPos { x: number; y: number; }
export interface Icon { item?: string; glyph?: string; }
export interface Task {
  type: string;
  item?: string;
  tag?: string;
  entity?: string;
  count?: number;
  waves?: number;
  levels?: number;
  advancement?: string;
  trigger?: string;
  npc?: string;
  structure?: string;
  block?: string;
  stat?: string;
  dimension?: string;
  biome?: string;
  fluid?: string;
  x?: number;
  y?: number;
  z?: number;
  radius?: number;
}
export interface Reward {
  type: string;
  item?: string;
  count?: number;
  amount?: number;
  levels?: number;
  command?: string;
  table?: string;
  message?: string;
  advancement?: string;
  stage?: string;
  options?: Reward[];
}
export interface JumpTarget { chapter: string; tile: string; }
export interface HiddenUntil { type: string; id: string; }
export interface ChapterIntro { image?: string; body?: string; }
export interface Tile {
  id: string;
  pos: GridPos;
  title?: string;
  description?: string;
  icon?: Icon;
  tasks?: Task[];
  rewards?: Reward[];
  scrolls?: string[];
  target?: JumpTarget;
  hidden_until?: HiddenUntil;
  required_stage?: string;
}
export type LinkCondition = { type: string; id: string };
export interface Link { from: string; to: string; gate?: string | { op: string; conditions?: LinkCondition[] }; }
export interface Chapter {
  id: string;
  title?: string;
  tiles: Tile[];
  links?: Link[];
  grid_width?: number;
  grid_height?: number;
  parent?: string;
  order?: number;
  icon?: Icon;
  unlock?: string | { op?: string; conditions?: { type: string; id: string }[] };
  hide_until_unlocked?: boolean;
  theme?: string;
  background?: ChapterBackground;
  intro?: ChapterIntro;
}

export interface ChapterBackground {
  mode?: "color" | "image";
  color?: string;
  image?: string;
  opacity?: number;
}

export type UnlockCondition = { type: string; id: string };

export function unlockConditions(chapter: Chapter): UnlockCondition[] {
  if (!chapter.unlock || typeof chapter.unlock === "string") return [];
  return chapter.unlock.conditions ?? [];
}

export function setUnlockConditions(chapter: Chapter, conditions: UnlockCondition[]) {
  chapter.unlock = { op: typeof chapter.unlock === "object" && chapter.unlock?.op ? chapter.unlock.op : "and", conditions };
}

export interface ChapterNode { chapter: Chapter; children: ChapterNode[] }

/** True when following parents from {@code id} comes back to {@code id}. */
export function parentLoops(byId: Map<string, Chapter>, id: string): boolean {
  const seen = new Set<string>();
  let cursor = byId.get(id)?.parent;
  while (cursor && byId.has(cursor)) {
    if (cursor === id) return true;
    if (seen.has(cursor)) return false;
    seen.add(cursor);
    cursor = byId.get(cursor)?.parent;
  }
  return false;
}

export function chapterTree(chapters: Chapter[]): ChapterNode[] {
  const byId = new Map(chapters.map((chapter) => [chapter.id, chapter]));
  const children = new Map<string, Chapter[]>();
  const roots: Chapter[] = [];
  for (const chapter of chapters) {
    // A parent chain that loops back would hide every chapter on the loop; treat such a chapter as a root,
    // the way the mod's ChapterTree does.
    if (!chapter.parent || !byId.has(chapter.parent) || parentLoops(byId, chapter.id)) {
      roots.push(chapter);
      continue;
    }
    const list = children.get(chapter.parent) ?? [];
    list.push(chapter);
    children.set(chapter.parent, list);
  }
  const sort = (a: Chapter, b: Chapter) => (a.order ?? 0) - (b.order ?? 0) || a.id.localeCompare(b.id);
  roots.sort(sort);
  const build = (chapter: Chapter): ChapterNode => {
    const kids = [...(children.get(chapter.id) ?? [])].sort(sort);
    return { chapter, children: kids.map(build) };
  };
  return roots.map(build);
}

export const GRID_MIN = 1;
export const GRID_MAX = 128;
export const GRID_DEFAULT_WIDTH = 12;
export const GRID_DEFAULT_HEIGHT = 10;

export const GRID_PRESETS: { label: string; width: number; height: number }[] = [
  { label: "12×10 (default)", width: 12, height: 10 },
  { label: "16×16", width: 16, height: 16 },
  { label: "32×32", width: 32, height: 32 },
  { label: "64×64", width: 64, height: 64 },
  { label: "128×128", width: 128, height: 128 },
];

export function clampGrid(value: number, fallback: number): number {
  if (!Number.isFinite(value) || value <= 0) return fallback;
  return Math.max(GRID_MIN, Math.min(GRID_MAX, Math.floor(value)));
}

/**
 * Board width: the authored width grown to cover every quest, the same rule as the mod's Chapter.boardWidth. Packs
 * (Skylore among them) place quests past their authored grid, and those quests still need a cell on the board.
 */
export function chapterGridWidth(chapter: Chapter): number {
  const extent = Math.max(0, ...(chapter.tiles ?? []).map((tile) => tile.pos.x + 1));
  return clampGrid(Math.max(chapter.grid_width ?? GRID_DEFAULT_WIDTH, extent), GRID_DEFAULT_WIDTH);
}

/** Board height: the authored height grown to cover every quest. */
export function chapterGridHeight(chapter: Chapter): number {
  const extent = Math.max(0, ...(chapter.tiles ?? []).map((tile) => tile.pos.y + 1));
  return clampGrid(Math.max(chapter.grid_height ?? GRID_DEFAULT_HEIGHT, extent), GRID_DEFAULT_HEIGHT);
}

export function inGrid(chapter: Chapter, x: number, y: number): boolean {
  return x >= 0 && y >= 0 && x < chapterGridWidth(chapter) && y < chapterGridHeight(chapter);
}
export interface Scroll { id: string; title: string; text: string; }

export type GateOpName = "and" | "or" | "xor" | "not";

export function gateOp(link: Link): GateOpName {
  if (!link.gate) return "and";
  if (typeof link.gate === "string") return (link.gate.toLowerCase() as GateOpName) || "and";
  return ((link.gate.op ?? "and").toLowerCase() as GateOpName) || "and";
}

/** The link's conditions (advancement / stage / … gates authored in JSON); kept through every op change. */
export function linkConditions(link: Link): LinkCondition[] {
  return typeof link.gate === "object" && link.gate?.conditions ? link.gate.conditions : [];
}

/** The link with a new op. A link that carries conditions keeps its object form so they are not lost. */
export function withOp(link: Link, op: GateOpName): Link {
  const conditions = linkConditions(link);
  return { ...link, gate: conditions.length ? { op, conditions } : op };
}

/**
 * Add or re-gate {@code from → to}. AND / OR / NOT is how the CHILD opens, so it is applied to the child's other
 * non-fork arrows. XOR is the PARENT's fork ("pick one path"), so it is applied to every arrow out of the parent
 * and never touches the child's other parents. Mirrors {@code Chapter.upsertLink} in the mod.
 */
export function upsertLink(chapter: Chapter, from: string, to: string, op: GateOpName): void {
  const links = [...(chapter.links ?? [])];
  let replaced = false;
  for (let i = 0; i < links.length; i++) {
    const link = links[i];
    if (link.from === from && link.to === to) {
      links[i] = withOp(link, op);
      replaced = true;
    } else if (op === "xor" && link.from === from) {
      links[i] = withOp(link, "xor");
    } else if (op !== "xor" && link.to === to && gateOp(link) !== "xor") {
      links[i] = withOp(link, op);
    }
  }
  if (!replaced) links.push({ from, to, gate: op });
  chapter.links = links;
}

/** True when adding {@code from → to} would close a loop (a path from {@code to} back to {@code from}). */
export function wouldCreateCycle(chapter: Chapter, from: string, to: string): boolean {
  if (from === to) return true;
  const next = new Map<string, string[]>();
  for (const link of chapter.links ?? []) {
    next.set(link.from, [...(next.get(link.from) ?? []), link.to]);
  }
  const queue = [to];
  const seen = new Set<string>([to]);
  while (queue.length) {
    const node = queue.shift()!;
    if (node === from) return true;
    for (const out of next.get(node) ?? []) {
      if (!seen.has(out)) {
        seen.add(out);
        queue.push(out);
      }
    }
  }
  return false;
}

export function removeLink(chapter: Chapter, from: string, to: string): void {
  chapter.links = (chapter.links ?? []).filter((link) => !(link.from === from && link.to === to));
}

export function hasLink(chapter: Chapter, from: string, to: string): boolean {
  return (chapter.links ?? []).some((link) => link.from === from && link.to === to);
}

export function cardinalAdjacent(a: { x: number; y: number }, b: { x: number; y: number }): boolean {
  return Math.abs(a.x - b.x) + Math.abs(a.y - b.y) === 1;
}

export const TASK_TYPES = [
  "obtain", "submit", "item_tag", "kill", "raid", "advancement",
  "location", "visit_structure", "visit_dimension", "visit_biome",
  "observation", "checkmark", "stat", "interact_block", "interact_entity",
  "fluid", "xp_levels", "npc_dialog", "trigger",
] as const;

export const REWARD_TYPES = [
  "item", "xp", "xp_levels", "command", "loot", "toast", "advancement", "choice", "stage",
] as const;

export function defaultTask(type: string): Task {
  switch (type) {
    case "obtain": return { type, item: "minecraft:chest", count: 1 };
    case "submit": return { type, item: "minecraft:rotten_flesh", count: 1 };
    case "item_tag": return { type, tag: "minecraft:planks", count: 1 };
    case "kill": return { type, entity: "minecraft:zombie", count: 1 };
    case "raid": return { type, waves: 1 };
    case "advancement": return { type, advancement: "minecraft:story/root" };
    case "location": return { type, x: 0, y: 64, z: 0, radius: 8, dimension: "" };
    case "visit_structure": return { type, structure: "minecraft:village_plains" };
    case "visit_dimension": return { type, dimension: "minecraft:the_nether" };
    case "visit_biome": return { type, biome: "minecraft:plains" };
    case "observation": return { type, block: "minecraft:stone" };
    case "stat": return { type, stat: "minecraft:walk_one_cm", count: 1000 };
    case "interact_block": return { type, block: "minecraft:crafting_table", count: 1 };
    case "interact_entity": return { type, entity: "minecraft:villager", count: 1 };
    case "fluid": return { type, fluid: "minecraft:lava", count: 1 };
    case "xp_levels": return { type, levels: 5 };
    case "npc_dialog": return { type, npc: "guide" };
    case "trigger": return { type, trigger: "questqueen:custom" };
    default: return { type: "checkmark" };
  }
}

export function defaultReward(type: string): Reward {
  switch (type) {
    case "item": return { type, item: "minecraft:diamond", count: 1 };
    case "xp": return { type, amount: 25 };
    case "xp_levels": return { type, levels: 1 };
    case "command": return { type, command: "say Quest complete" };
    case "loot": return { type, table: "minecraft:chests/simple_dungeon" };
    case "toast": return { type, message: "Quest complete" };
    case "advancement": return { type, advancement: "minecraft:story/root" };
    case "choice": return {
      type,
      options: [
        { type: "item", item: "minecraft:diamond", count: 1 },
        { type: "item", item: "minecraft:emerald", count: 8 },
      ],
    };
    case "stage": return { type, stage: "example" };
    default: return { type: "xp", amount: 5 };
  }
}

export const BLANK: Chapter = {
  id: "questqueen:blank",
  title: "New Chapter",
  grid_width: GRID_DEFAULT_WIDTH,
  grid_height: GRID_DEFAULT_HEIGHT,
  tiles: [{
    id: "start",
    pos: { x: 1, y: 1 },
    title: "First step",
    description: "Replace this template tile.",
    icon: { item: "minecraft:paper" },
    tasks: [{ type: "obtain", item: "minecraft:dirt", count: 1 }],
    rewards: [{ type: "xp", amount: 5 }],
  }],
  links: [],
};

export const STARTER: Chapter = {
  id: "questqueen:starter",
  title: "Getting Started",
  grid_width: GRID_DEFAULT_WIDTH,
  grid_height: GRID_DEFAULT_HEIGHT,
  tiles: [
    {
      id: "make_chest",
      pos: { x: 1, y: 1 },
      title: "Make a chest",
      description: "Craft or find a chest.",
      icon: { item: "minecraft:chest" },
      tasks: [{ type: "obtain", item: "minecraft:chest", count: 1 }],
      rewards: [{ type: "item", item: "minecraft:oak_log", count: 8 }],
      scrolls: ["questqueen:chest_note"],
    },
    {
      id: "fight_zombie",
      pos: { x: 2, y: 1 },
      title: "Fight a zombie",
      description: "Slay a zombie.",
      icon: { item: "minecraft:rotten_flesh" },
      tasks: [{ type: "kill", entity: "minecraft:zombie", count: 1 }],
      rewards: [{ type: "item", item: "minecraft:iron_ingot", count: 2 }],
    },
    {
      id: "story_time",
      pos: { x: 3, y: 1 },
      title: "Story time",
      description: "Earn the Minecraft story root advancement.",
      icon: { item: "minecraft:book" },
      tasks: [{ type: "advancement", advancement: "minecraft:story/root" }],
      rewards: [{ type: "xp", amount: 10 }],
    },
    {
      id: "tribute",
      pos: { x: 2, y: 2 },
      title: "Tribute",
      description: "Submit rotten flesh to prove the hunt.",
      icon: { item: "minecraft:rotten_flesh" },
      tasks: [{ type: "submit", item: "minecraft:rotten_flesh", count: 1 }],
      rewards: [{ type: "item", item: "minecraft:bread", count: 4 }],
    },
    {
      id: "go_nether",
      pos: { x: 3, y: 2 },
      title: "See the nether",
      description: "Stand in the Nether.",
      icon: { item: "minecraft:obsidian" },
      tasks: [{ type: "visit_dimension", dimension: "minecraft:the_nether" }],
      rewards: [{ type: "xp_levels", levels: 1 }],
    },
    {
      id: "take_your_pick",
      pos: { x: 2, y: 3 },
      title: "Take your pick",
      description: "Mark this complete, then claim one reward.",
      icon: { item: "minecraft:emerald" },
      tasks: [{ type: "checkmark" }],
      rewards: [{
        type: "choice",
        options: [
          { type: "item", item: "minecraft:diamond", count: 1 },
          { type: "item", item: "minecraft:emerald", count: 8 },
        ],
      }],
    },
  ],
  links: [
    { from: "make_chest", to: "fight_zombie", gate: "and" },
    { from: "fight_zombie", to: "story_time", gate: "and" },
    { from: "fight_zombie", to: "tribute", gate: "and" },
    { from: "story_time", to: "go_nether", gate: "and" },
    { from: "tribute", to: "take_your_pick", gate: "and" },
  ],
};

export function startsOf(chapter: Chapter): Set<string> {
  const inbound = new Set((chapter.links ?? []).map((link) => link.to));
  return new Set(chapter.tiles.filter((tile) => !inbound.has(tile.id)).map((tile) => tile.id));
}
