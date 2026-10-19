// Stages made in the editor: the shape the bridge saves (data/stage/AuthoredStage.java), the lock kinds and their
// toggles, and the pure questions the UI asks — which quests grant or need a stage, how many things it locks, and
// the one-sentence summary. No DOM here, so all of it is unit-tested.
import { linkConditions, unlockConditions, type Chapter } from "./types";
import { isStageType, validStageId } from "./validate";

export type LockKind = "item" | "block" | "mod" | "dimension" | "entity";

export interface StageLockGroup {
  kind: LockKind;
  targets: string[];
  flags?: Record<string, boolean>;
  message?: string;
}

export interface AuthoredStage {
  id: string;
  name?: string;
  description?: string;
  icon?: string;
  scope?: "player" | "team" | "global";
  message?: boolean;
  requires?: string[];
  advancements?: string[];
  locks?: StageLockGroup[];
}

/** One stage as the editor lists it: made here (editable) or by a datapack (read-only). */
export interface StageInfo {
  id: string;
  name: string;
  authored: boolean;
  stage?: AuthoredStage;
}

export interface StagesState {
  stageLock: boolean;
  stages: StageInfo[];
}

export interface FlagSpec {
  key: string;
  label: string;
  /** The value when the author has not touched it; mirrors StageLockFiles.java. */
  on: boolean;
}

export interface KindSpec {
  kind: LockKind;
  label: string;
  emoji: string;
  /** Short noun for the summary: "4 items". */
  noun: [string, string];
  /** What the picker searches. */
  pick: "items" | "blocks" | "mods" | "dimensions" | "entities";
  placeholder: string;
  flags: FlagSpec[];
  /** A message players see when they bump into the lock. */
  message?: string;
}

export const LOCK_KINDS: KindSpec[] = [
  {
    kind: "item", label: "Items", emoji: "🧱", noun: ["item", "items"], pick: "items",
    placeholder: "Search items, or type #tag",
    flags: [
      { key: "use", label: "Use, place, or attack with it", on: true },
      { key: "craft", label: "Craft it", on: true },
      { key: "pickup", label: "Pick it up or hold it", on: true },
      { key: "hide", label: "Hide it in JEI / EMI / REI", on: true },
      { key: "hide_name", label: "Hide its name (shows as locked)", on: true },
    ],
  },
  {
    kind: "block", label: "Blocks", emoji: "⛏", noun: ["block", "blocks"], pick: "blocks",
    placeholder: "Search blocks, or type #tag",
    flags: [
      { key: "break", label: "Break it", on: true },
      { key: "place", label: "Place it", on: true },
      { key: "use", label: "Use it (right-click)", on: true },
      { key: "disguise", label: "Disguise it as stone", on: false },
    ],
  },
  {
    kind: "mod", label: "Whole mod", emoji: "📦", noun: ["mod", "mods"], pick: "mods",
    placeholder: "Search installed mods",
    flags: [{ key: "hide", label: "Hide its items and recipes in JEI / EMI / REI", on: true }],
  },
  {
    kind: "dimension", label: "Dimension", emoji: "🌀", noun: ["dimension", "dimensions"], pick: "dimensions",
    placeholder: "Search dimensions",
    flags: [],
    message: "The portal refuses you…",
  },
  {
    kind: "entity", label: "Mob", emoji: "🐺", noun: ["mob", "mobs"], pick: "entities",
    placeholder: "Search mobs, or type #tag",
    flags: [
      { key: "attack", label: "Attack it", on: true },
      { key: "interact", label: "Interact with it (trade, ride, tame)", on: true },
      { key: "spawn", label: "Let it spawn near players without this stage", on: false },
    ],
  },
];

export function kindSpec(kind: LockKind): KindSpec {
  return LOCK_KINDS.find((spec) => spec.kind === kind) ?? LOCK_KINDS[0];
}

export function flagOn(lock: StageLockGroup, key: string): boolean {
  const value = lock.flags?.[key];
  if (value !== undefined) return value;
  return kindSpec(lock.kind).flags.find((flag) => flag.key === key)?.on ?? true;
}

/** "Iron Age!" → "iron_age". Always a valid StageLock id, or "" when nothing usable is left. */
export function slugify(name: string): string {
  return name
    .normalize("NFKD")
    .replace(/[̀-ͯ]/g, "")
    .toLowerCase()
    .replace(/[^a-z0-9]+/g, "_")
    .replace(/^_+|_+$/g, "")
    .slice(0, 64);
}

/** A slug no other stage has: iron_age, iron_age_2, … */
export function uniqueStageId(name: string, taken: Iterable<string>): string {
  const used = new Set(taken);
  const base = slugify(name) || "stage";
  if (!used.has(base)) return base;
  for (let n = 2; ; n++) {
    const next = `${base.slice(0, 60)}_${n}`;
    if (!used.has(next)) return next;
  }
}

/** Merge the pack's StageLock stages with the ones made here. Made-here wins: it carries the editable copy. */
export function mergeStages(defined: { id: string; name?: string }[], authored: AuthoredStage[]): StageInfo[] {
  const byId = new Map<string, StageInfo>();
  for (const entry of defined) {
    if (entry.id) byId.set(entry.id, { id: entry.id, name: entry.name || entry.id, authored: false });
  }
  for (const stage of authored) {
    if (stage.id) byId.set(stage.id, { id: stage.id, name: stage.name || stage.id, authored: true, stage });
  }
  return [...byId.values()].sort((a, b) => a.name.localeCompare(b.name));
}

/** Where a quest (or chapter) touches a stage. */
export interface StageUse {
  chapter: string;
  chapterTitle: string;
  tile?: string;
  tileTitle?: string;
  how: "reward" | "required" | "hidden" | "link" | "chapter";
}

function same(a: string | undefined, b: string): boolean {
  return a !== undefined && a.trim().toLowerCase() === b.toLowerCase();
}

/** Every quest that grants {@code id} (a stage reward) and every place that needs it, across all chapters. */
export function stageUsage(chapters: Chapter[], id: string): { grants: StageUse[]; needs: StageUse[] } {
  const grants: StageUse[] = [];
  const needs: StageUse[] = [];
  for (const chapter of chapters) {
    const at = (tile: { id: string; title?: string } | undefined, how: StageUse["how"]): StageUse => ({
      chapter: chapter.id,
      chapterTitle: chapter.title || chapter.id,
      ...(tile ? { tile: tile.id, tileTitle: tile.title || tile.id } : {}),
      how,
    });
    for (const tile of chapter.tiles ?? []) {
      if ((tile.rewards ?? []).some((reward) => isStageType(reward.type) && same(reward.stage, id))) grants.push(at(tile, "reward"));
      if (same(tile.required_stage, id)) needs.push(at(tile, "required"));
      if (isStageType(tile.hidden_until?.type) && same(tile.hidden_until?.id, id)) needs.push(at(tile, "hidden"));
    }
    const tiles = new Map((chapter.tiles ?? []).map((tile) => [tile.id, tile]));
    for (const link of chapter.links ?? []) {
      if (linkConditions(link).some((c) => isStageType(c.type) && same(c.id, id))) needs.push(at(tiles.get(link.to), "link"));
    }
    if (unlockConditions(chapter).some((c) => isStageType(c.type) && same(c.id, id))) needs.push(at(undefined, "chapter"));
  }
  return { grants, needs };
}

/** How many things a stage locks (a "#tag" or a whole mod counts as one). */
export function lockCount(stage: AuthoredStage | undefined): number {
  return (stage?.locks ?? []).reduce((sum, lock) => sum + lock.targets.length, 0);
}

function plural(n: number, [one, many]: [string, string]): string {
  return `${n} ${n === 1 ? one : many}`;
}

function list(parts: string[]): string {
  if (parts.length <= 1) return parts.join("");
  return `${parts.slice(0, -1).join(", ")} and ${parts[parts.length - 1]}`;
}

/** What players without the stage can't do, as one clause per lock kind: "use 4 items", "enter The Nether". */
export function lockClauses(stage: AuthoredStage, names: (kind: LockKind, id: string) => string = (_k, id) => id): string[] {
  const clauses: string[] = [];
  for (const spec of LOCK_KINDS) {
    const targets = (stage.locks ?? []).filter((lock) => lock.kind === spec.kind).flatMap((lock) => lock.targets);
    if (!targets.length) continue;
    const what = targets.length <= 2 ? list(targets.map((id) => names(spec.kind, id))) : plural(targets.length, spec.noun);
    switch (spec.kind) {
      case "item": clauses.push(`use ${what}`); break;
      case "block": clauses.push(`touch ${what}`); break;
      case "mod": clauses.push(`use anything from ${what}`); break;
      case "dimension": clauses.push(`enter ${what}`); break;
      case "entity": clauses.push(`fight ${what}`); break;
    }
  }
  return clauses;
}

/** "Players without Iron Age can't use 4 items or enter the_nether. They get it by completing Smelt Iron." */
export function summarySentence(
  stage: AuthoredStage,
  grantedBy: string[],
  names?: (kind: LockKind, id: string) => string,
): string {
  const title = stage.name?.trim() || stage.id || "this stage";
  const clauses = lockClauses(stage, names);
  const first = clauses.length
    ? `Players without ${title} can't ${clauses.length === 1 ? clauses[0] : `${clauses.slice(0, -1).join(", ")} or ${clauses[clauses.length - 1]}`}.`
    : `${title} doesn't lock anything yet.`;
  const ways = [...grantedBy.map((quest) => `completing ${quest}`), ...(stage.advancements ?? []).map((adv) => `earning ${adv}`)];
  const second = ways.length
    ? ` They get it by ${ways.length === 1 ? ways[0] : `${ways.slice(0, -1).join(", ")} or ${ways[ways.length - 1]}`}.`
    : " No quest grants it yet.";
  return first + second;
}

/** Would making {@code id} need {@code next} first create a loop (next already needs id, directly or not)? */
export function requiresCycle(stages: StageInfo[], id: string, next: string): boolean {
  if (id === next) return true;
  const byId = new Map(stages.map((info) => [info.id, info.stage?.requires ?? []]));
  const seen = new Set<string>();
  const stack = [next];
  while (stack.length) {
    const cursor = stack.pop()!;
    if (cursor === id) return true;
    if (seen.has(cursor)) continue;
    seen.add(cursor);
    stack.push(...(byId.get(cursor) ?? []));
  }
  return false;
}

/** Problems that block a save, in plain words. */
export function stageProblems(stage: AuthoredStage, stages: StageInfo[], isNew: boolean): string[] {
  const problems: string[] = [];
  if (!stage.name?.trim()) problems.push("Give the stage a name.");
  if (!validStageId(stage.id)) problems.push(`"${stage.id}" can't be a stage id: use a-z, 0-9 and _ . : / - (up to 64).`);
  else if (isNew && stages.some((info) => info.id === stage.id)) problems.push(`Another stage already uses the id ${stage.id}.`);
  for (const need of stage.requires ?? []) {
    if (requiresCycle(stages.filter((info) => info.id !== stage.id).concat([{ id: stage.id, name: "", authored: true, stage }]), stage.id, need)) {
      problems.push(`${need} already needs this stage, so this stage can't need it.`);
    }
  }
  (stage.locks ?? []).forEach((lock, index) => {
    if (!lock.targets.length) problems.push(`Lock ${index + 1} (${kindSpec(lock.kind).label}) has nothing picked.`);
  });
  return problems;
}

/** The stage as the server wants it: no empty fields, ids lowercased, untouched toggles left out. */
export function cleanStage(stage: AuthoredStage): AuthoredStage {
  const out: AuthoredStage = { id: stage.id.trim().toLowerCase() };
  if (stage.name?.trim()) out.name = stage.name.trim();
  if (stage.description?.trim()) out.description = stage.description.trim();
  if (stage.icon?.trim()) out.icon = stage.icon.trim();
  if (stage.scope && stage.scope !== "player") out.scope = stage.scope;
  if (stage.message === false) out.message = false;
  if (stage.requires?.length) out.requires = [...new Set(stage.requires)];
  if (stage.advancements?.length) out.advancements = [...new Set(stage.advancements)];
  const locks = (stage.locks ?? []).map((lock) => {
    const clean: StageLockGroup = { kind: lock.kind, targets: [...new Set(lock.targets)] };
    const flags: Record<string, boolean> = {};
    for (const flag of kindSpec(lock.kind).flags) {
      const value = lock.flags?.[flag.key];
      if (value !== undefined && value !== flag.on) flags[flag.key] = value;
    }
    if (Object.keys(flags).length) clean.flags = flags;
    if (lock.message?.trim()) clean.message = lock.message.trim();
    return clean;
  });
  if (locks.length) out.locks = locks;
  return out;
}
