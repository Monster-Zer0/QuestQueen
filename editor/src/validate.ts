// Every check the editor makes before a save or export, in one place. Mirrors what the mod's codecs require and
// what makes a quest impossible to finish, so problems show in the editor instead of in game.
import { chapterGridHeight, chapterGridWidth, gateOp, parentLoops, type Chapter, type Reward, type Task } from "./types";

/** A resource location, with or without its namespace (the game reads "stone" as minecraft:stone). */
const RESOURCE = /^([a-z0-9_.-]+:)?[a-z0-9_./-]+$/;
const CHAPTER_ID = /^[a-z0-9_.-]+:[a-z0-9_./-]+$/;

/** Fields each task type must name. Two entries joined by "|" mean "one of these". */
const TASK_REQUIRES: Record<string, string[]> = {
  obtain: ["item"], submit: ["item"], item_tag: ["tag"], kill: ["entity|tag"],
  advancement: ["advancement"], visit_structure: ["structure"], visit_dimension: ["dimension"],
  visit_biome: ["biome"], observation: ["block|entity"], stat: ["stat"], interact_block: ["block"],
  interact_entity: ["entity"], fluid: ["fluid"], npc_dialog: ["npc"], trigger: ["trigger"],
};
/** Free-text fields: they must be filled in but are not resource ids. */
const FREE_TEXT = new Set(["npc", "trigger", "command", "message"]);
/** Progression's stage id rule (it lowercases ids itself): 1-64 of a-z 0-9 _ . : / - */
const STAGE_ID = /^[a-z0-9_.:/-]{1,64}$/;
const STAGE_TYPES = new Set(["stage", "progression", "progressivestages"]);

export function validStageId(id: string): boolean {
  return STAGE_ID.test(id.trim().toLowerCase());
}

function checkStage(where: string, id: string | undefined, errors: string[]) {
  if (id === undefined) return;
  if (!id.trim()) errors.push(`${where}: stage is empty`);
  else if (!validStageId(id)) errors.push(`${where}: "${id}" is not a valid stage id (a-z 0-9 _ . : / -, up to 64 characters)`);
}
const COUNTED = new Set(["obtain", "submit", "item_tag", "kill", "stat", "interact_block", "interact_entity", "fluid"]);

const REWARD_REQUIRES: Record<string, string[]> = {
  item: ["item"], command: ["command"], loot: ["table"], toast: ["message"], advancement: ["advancement"], stage: ["stage"],
};

function fieldProblem(value: unknown, field: string): string | null {
  const text = typeof value === "string" ? value.trim() : "";
  if (!text) return "is empty";
  if (field === "stage") return validStageId(text) ? null : `"${text}" is not a valid stage id`;
  if (!FREE_TEXT.has(field) && !RESOURCE.test(text)) return `"${text}" is not a valid id`;
  return null;
}

function checkRequired(where: string, record: Record<string, unknown>, required: string[], errors: string[]) {
  for (const spec of required) {
    const options = spec.split("|");
    const filled = options.filter((field) => typeof record[field] === "string" && (record[field] as string).trim());
    if (!filled.length) {
      errors.push(`${where}: ${options.join(" or ")} is empty`);
      continue;
    }
    for (const field of filled) {
      const problem = fieldProblem(record[field], field);
      if (problem) errors.push(`${where}: ${field} ${problem}`);
    }
  }
}

function checkTask(where: string, task: Task, errors: string[]) {
  checkRequired(where, task as unknown as Record<string, unknown>, TASK_REQUIRES[task.type] ?? [], errors);
  if (COUNTED.has(task.type) && task.count !== undefined && !(task.count >= 1)) errors.push(`${where}: count must be at least 1`);
  if (task.type === "raid" && !((task.waves ?? 1) >= 1)) errors.push(`${where}: waves must be at least 1`);
  if (task.type === "xp_levels" && !((task.levels ?? 1) >= 1)) errors.push(`${where}: levels must be at least 1`);
}

function checkReward(where: string, reward: Reward, errors: string[]) {
  checkRequired(where, reward as unknown as Record<string, unknown>, REWARD_REQUIRES[reward.type] ?? [], errors);
  if (reward.type === "item" && reward.count !== undefined && !(reward.count >= 1)) errors.push(`${where}: count must be at least 1`);
  if (reward.type === "xp" && !((reward.amount ?? 1) >= 1)) errors.push(`${where}: XP must be at least 1`);
  if (reward.type === "xp_levels" && !((reward.levels ?? 1) >= 1)) errors.push(`${where}: levels must be at least 1`);
  if (reward.type === "choice") {
    const options = reward.options ?? [];
    if (options.length < 2) errors.push(`${where}: a choice needs at least 2 options`);
    options.forEach((option, i) => checkReward(`${where} option ${i + 1}`, { ...option, type: "item" }, errors));
  }
}

/** Problems with {@code next}, judged against the whole pack {@code all} (for duplicate ids and parent loops). */
export function validate(next: Chapter, all: Chapter[] = [next]): string[] {
  const errors: string[] = [];
  if (!next.id || !CHAPTER_ID.test(next.id)) errors.push(`Chapter id "${next.id}" must be namespace:path in lowercase`);
  if (all.filter((chapter) => chapter.id === next.id).length > 1) errors.push(`Another chapter already uses the id ${next.id}`);
  const byId = new Map(all.map((chapter) => [chapter.id, chapter]));
  const unlock = typeof next.unlock === "object" ? next.unlock?.conditions ?? [] : [];
  for (const condition of unlock) {
    if (STAGE_TYPES.has(condition.type)) checkStage("Chapter unlock", condition.id, errors);
  }
  if (next.parent && byId.has(next.parent) && parentLoops(byId, next.id)) errors.push(`Parent ${next.parent} makes a loop of chapters`);
  // A chapter may leave "tiles" out entirely (act intros do); the mod reads that as no quests.
  const tiles = Array.isArray(next.tiles) ? next.tiles : [];
  const links = next.links ?? [];
  const ids = new Set<string>();
  const width = chapterGridWidth(next);
  const height = chapterGridHeight(next);
  for (const tile of tiles) {
    const name = tile.title ? `${tile.title} (${tile.id})` : tile.id;
    if (ids.has(tile.id)) errors.push(`Two quests use the id ${tile.id}`);
    ids.add(tile.id);
    // The board grows to cover every quest (chapterGridWidth/Height), so only a negative position is off it.
    if (tile.pos.x < 0 || tile.pos.y < 0 || tile.pos.x >= width || tile.pos.y >= height) {
      errors.push(`${name} is off the board at ${tile.pos.x},${tile.pos.y}`);
    }
    if (!(tile.tasks ?? []).length) errors.push(`${name} has no tasks, so players can never complete it`);
    checkStage(`${name} required stage`, tile.required_stage, errors);
    if (tile.hidden_until && STAGE_TYPES.has(tile.hidden_until.type)) checkStage(`${name} hidden until`, tile.hidden_until.id, errors);
    (tile.tasks ?? []).forEach((task, i) => checkTask(`${name} task ${i + 1} (${task.type})`, task, errors));
    (tile.rewards ?? []).forEach((reward, i) => checkReward(`${name} reward ${i + 1} (${reward.type})`, reward, errors));
  }
  for (const link of links) {
    if (!ids.has(link.from) || !ids.has(link.to)) errors.push(`Link ${link.from}->${link.to} missing tile`);
  }
  const adj = new Map<string, string[]>();
  for (const id of ids) adj.set(id, []);
  for (const link of links) {
    if (ids.has(link.from) && ids.has(link.to)) adj.get(link.from)!.push(link.to);
  }
  const cycle = findCycle(ids, adj);
  if (cycle) {
    // A loop also makes every quest on it look "unreachable"; report the loop alone, it is the real problem.
    errors.push(`Loop: ${cycle.join(" → ")}`);
    return errors;
  }
  const forkOut = new Map<string, number>();
  for (const link of links) {
    if (gateOp(link) === "xor") forkOut.set(link.from, (forkOut.get(link.from) ?? 0) + 1);
  }
  for (const [from, count] of forkOut) {
    if (count < 2) errors.push(`The fork from ${from} has only ${count} path; a fork needs at least 2`);
  }
  if (links.length && tiles.length) {
    const inbound = new Set(links.map((l) => l.to));
    const reach = new Set<string>();
    const queue = tiles.filter((t) => !inbound.has(t.id)).map((t) => t.id);
    for (const id of queue) reach.add(id);
    while (queue.length) {
      const n = queue.shift()!;
      for (const d of adj.get(n) ?? []) {
        if (!reach.has(d)) {
          reach.add(d);
          queue.push(d);
        }
      }
    }
    for (const t of tiles) {
      if (!reach.has(t.id)) errors.push(`Orphan tile (unreachable): ${t.id}`);
    }
  }
  return errors;
}

function findCycle(ids: Set<string>, adj: Map<string, string[]>): string[] | null {
  const visiting = new Set<string>();
  const visited = new Set<string>();
  const stack: string[] = [];
  let found: string[] | null = null;
  const dfs = (node: string): boolean => {
    if (visiting.has(node)) {
      found = [...stack.slice(stack.indexOf(node)), node];
      return true;
    }
    if (visited.has(node)) return false;
    visiting.add(node);
    stack.push(node);
    for (const next of adj.get(node) ?? []) {
      if (dfs(next)) return true;
    }
    stack.pop();
    visiting.delete(node);
    visited.add(node);
    return false;
  };
  for (const id of ids) {
    if (!visited.has(id) && dfs(id)) break;
  }
  return found;
}
