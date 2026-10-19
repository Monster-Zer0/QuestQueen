// What the quest book would show a given player: the tile states (LOCKED / NEW / CURRENT / COMPLETED / CLOSED),
// which tiles are concealed and which chapters are open. A port of the game's rules, which stay the authority:
// GateEvaluator, StartNodes and ChapterTree (data/), ProgressService.computeUnlockedChapters / computeRevealed
// (server) and ClientQuestState.visual / isUnlocked / isCurrent / isXorClosed / isConcealed (client).
// gate-cases.json pins both sides: GateParityTest (Java) and progress.test.ts run the same cases.
//
// A previewed player only has completed quests and stages. Every other condition (advancement, trigger,
// scoreboard, team flag, chapter complete) counts as unmet, as ClientQuestState.extraMet reports it.
import { gateOp, linkConditions, startsOf, type Chapter, type GateOpName, type Link, type Tile } from "../types";
import type { SimPlayer } from "./store";

export type Visual = "LOCKED" | "NEW" | "CURRENT" | "COMPLETED" | "FAILED" | "CLOSED";

type Condition = { type: string; id: string };

const STAGE_TYPES = new Set(["stage", "stagelock", "progression", "progressivestages"]);

/** The game's progress key for a quest (ProgressSnapshot.questKey). */
export function questKey(chapterId: string, tileId: string): string {
  return `${chapterId}/${tileId}`;
}

function conditionMet(condition: Condition, sim: SimPlayer): boolean {
  if (STAGE_TYPES.has(condition.type)) return sim.stages.has(condition.id);
  if (condition.type === "quest_complete") return sim.completed.has(condition.id);
  return false;
}

function evaluate(op: GateOpName, incoming: boolean[]): boolean {
  switch (op) {
    case "or": return incoming.some(Boolean);
    case "not": return !incoming.some(Boolean);
    case "xor": return incoming.filter(Boolean).length === 1;
    default: return incoming.every(Boolean);
  }
}

/** GateEvaluator.joinOp: the first non-XOR inbound op, AND when every inbound edge is a fork edge. */
function joinOp(inbound: Link[]): GateOpName {
  for (const link of inbound) if (gateOp(link) !== "xor") return gateOp(link);
  return "and";
}

/** Ids of this chapter's completed quests. */
function localCompleted(ch: Chapter, sim: SimPlayer): Set<string> {
  const prefix = `${ch.id}/`;
  const out = new Set<string>();
  for (const key of sim.completed) if (key.startsWith(prefix)) out.add(key.slice(prefix.length));
  return out;
}

/** GateEvaluator.xorSiblingCompleted. */
export function xorSiblingCompleted(ch: Chapter, tileId: string, done: Set<string>): boolean {
  const links = ch.links ?? [];
  for (const link of links) {
    if (gateOp(link) !== "xor" || link.to !== tileId || !done.has(link.from)) continue;
    for (const sibling of links) {
      if (gateOp(sibling) !== "xor" || sibling.from !== link.from || sibling.to === tileId) continue;
      if (done.has(sibling.to)) return true;
    }
  }
  return false;
}

/** GateEvaluator.unlocked. */
function gateUnlocked(ch: Chapter, tileId: string, done: Set<string>, sim: SimPlayer): boolean {
  if (xorSiblingCompleted(ch, tileId, done)) return false;
  const inbound = (ch.links ?? []).filter((link) => link.to === tileId);
  if (!inbound.length) return true;
  const conditions = inbound.flatMap((link) => linkConditions(link) as Condition[]);
  return evaluate(joinOp(inbound), inbound.map((link) => done.has(link.from)))
    && conditions.every((c) => conditionMet(c, sim));
}

function unlockGate(ch: Chapter): { op: GateOpName; conditions: Condition[] } {
  if (!ch.unlock) return { op: "and", conditions: [] };
  if (typeof ch.unlock === "string") return { op: (ch.unlock.toLowerCase() as GateOpName) || "and", conditions: [] };
  return { op: ((ch.unlock.op ?? "and").toLowerCase() as GateOpName) || "and", conditions: ch.unlock.conditions ?? [] };
}

/** ProgressService.computeUnlockedChapters with ChapterTree.isUnlocked: parents first, then the unlock gate. */
export function unlockedChapters(all: Chapter[], sim: SimPlayer): Set<string> {
  const unlocked = new Set<string>();
  let changed = true;
  while (changed) {
    changed = false;
    for (const ch of all) {
      if (unlocked.has(ch.id)) continue;
      if (ch.parent && !unlocked.has(ch.parent)) continue;
      const gate = unlockGate(ch);
      const met = !gate.conditions.length || evaluate(gate.op, gate.conditions.map((c) => conditionMet(c, sim)));
      if (met) {
        unlocked.add(ch.id);
        changed = true;
      }
    }
  }
  return unlocked;
}

export function chapterUnlocked(ch: Chapter, sim: SimPlayer, all: Chapter[]): boolean {
  return unlockedChapters(all, sim).has(ch.id);
}

/** Everything the game works out for one tile, from the chapter's unlocked state. */
function tileFacts(ch: Chapter, tile: Tile, sim: SimPlayer, chapterOpen: boolean) {
  const done = localCompleted(ch, sim);
  const start = startsOf(ch).has(tile.id);
  const gate = gateUnlocked(ch, tile.id, done, sim);
  const stageOk = !tile.required_stage || sim.stages.has(tile.required_stage);
  const hiddenMet = tile.hidden_until ? conditionMet(tile.hidden_until as Condition, sim) : true;
  const revealed = chapterOpen && (gate || start) && hiddenMet && stageOk;
  const unlocked = chapterOpen && (revealed || (stageOk && (gate || start)));
  return { done, start, revealed, unlocked, completed: done.has(tile.id) };
}

/** ClientQuestState.visual for the simulated player. */
export function visual(ch: Chapter, tile: Tile, sim: SimPlayer, all: Chapter[], open = chapterUnlocked(ch, sim, all)): Visual {
  const f = tileFacts(ch, tile, sim, open);
  if (f.completed) return "COMPLETED";
  if (xorSiblingCompleted(ch, tile.id, f.done)) return "CLOSED";
  if (!(f.unlocked || f.revealed)) return "LOCKED";
  return f.unlocked && f.start ? "CURRENT" : "NEW";
}

/** ClientQuestState.isConcealed: a hidden_until quest the player has not uncovered (and not finished). */
export function concealed(ch: Chapter, tile: Tile, sim: SimPlayer, _all?: Chapter[]): boolean {
  if (sim.completed.has(questKey(ch.id, tile.id))) return false;
  return tile.hidden_until ? !conditionMet(tile.hidden_until as Condition, sim) : false;
}

/**
 * A player who has finished `tileId`: the quest plus a minimal set of quests before it that makes it reachable.
 * AND needs every parent, OR and FORK the first parent in link order, NOT none (finishing its parent would shut it).
 */
export function finishedUpTo(ch: Chapter, tileId: string, _all?: Chapter[]): Set<string> {
  const out = new Set<string>();
  const visit = (id: string) => {
    const key = questKey(ch.id, id);
    if (out.has(key)) return;
    out.add(key);
    const inbound = (ch.links ?? []).filter((link) => link.to === id);
    if (!inbound.length) return;
    const op = joinOp(inbound);
    if (op === "not") return;
    if (op === "and") inbound.forEach((link) => visit(link.from));
    else visit(inbound[0].from);
  };
  visit(tileId);
  return out;
}
