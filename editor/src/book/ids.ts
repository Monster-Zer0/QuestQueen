// Ids checked against the pack. A typed id the pack does not have ("minecraft:chiken") is a warning, not a save
// problem: the catalog comes from the game the editor is attached to, and another pack or a mod added later may
// still provide it. A catalog list that is empty (no game, or a registry it could not read) checks nothing.
import type { CatalogEntry, PackCatalog } from "../packCatalog";
import type { Chapter, Reward, Task } from "../types";
import { kindOf, rewardKindOf, type Picker } from "./sentences";

const LISTS: Partial<Record<Picker, { list: keyof PackCatalog; noun: string }>> = {
  item: { list: "items", noun: "item" },
  tag: { list: "tags", noun: "tag" },
  entityTag: { list: "entityTags", noun: "mob tag" },
  stat: { list: "stats", noun: "statistic" },
  entity: { list: "entities", noun: "mob" },
  block: { list: "blocks", noun: "block" },
  biome: { list: "biomes", noun: "biome" },
  structure: { list: "structures", noun: "structure" },
  dimension: { list: "dimensions", noun: "dimension" },
  fluid: { list: "fluids", noun: "fluid" },
  advancement: { list: "advancements", noun: "advancement" },
  loot: { list: "loot", noun: "loot table" },
};

/** "stone" → "minecraft:stone"; "minecraft:diamond[…]" → "minecraft:diamond". */
function normalize(id: string): string {
  const bare = id.trim().split("[")[0];
  return bare.includes(":") ? bare : `minecraft:${bare}`;
}

function distance(a: string, b: string): number {
  const row = Array.from({ length: b.length + 1 }, (_, i) => i);
  for (let i = 1; i <= a.length; i++) {
    let prev = row[0];
    row[0] = i;
    for (let j = 1; j <= b.length; j++) {
      const next = Math.min(row[j] + 1, row[j - 1] + 1, prev + (a[i - 1] === b[j - 1] ? 0 : 1));
      prev = row[j];
      row[j] = next;
    }
  }
  return row[b.length];
}

const path = (id: string) => id.slice(id.indexOf(":") + 1);

/** The known id whose path is within 2 edits of this one's, if any. */
function closest(id: string, known: CatalogEntry[]): string | undefined {
  let best: string | undefined;
  let bestDistance = 3;
  for (const entry of known) {
    const d = distance(path(id), path(entry.id));
    if (d < bestDistance) {
      bestDistance = d;
      best = entry.id;
    }
  }
  return best;
}

function check(where: string, picker: Picker, raw: unknown, catalog: PackCatalog, out: string[]) {
  const spec = LISTS[picker];
  if (!spec || typeof raw !== "string" || !raw.trim()) return;
  const known = (catalog[spec.list] as CatalogEntry[] | undefined) ?? [];
  if (!known.length) return;
  const id = normalize(raw);
  if (known.some((entry) => entry.id === id)) return;
  const near = closest(id, known);
  out.push(`${where}: no ${spec.noun} called "${raw}" in this pack.${near ? ` Did you mean ${near}?` : ""}`);
}

function checkFields(where: string, value: Task | Reward, fields: { key: string; picker: Picker }[], catalog: PackCatalog, out: string[]) {
  for (const field of fields) check(where, field.picker, (value as unknown as Record<string, unknown>)[field.key], catalog, out);
}

function checkReward(where: string, reward: Reward, catalog: PackCatalog, out: string[]) {
  if (reward.type === "choice") {
    (reward.options ?? []).forEach((option, i) => checkReward(`${where} option ${i + 1}`, { ...option, type: "item" }, catalog, out));
    return;
  }
  checkFields(where, reward, rewardKindOf(reward.type)?.fields ?? [], catalog, out);
}

export function unknownIdWarnings(ch: Chapter, catalog: PackCatalog): string[] {
  const out: string[] = [];
  for (const tile of ch.tiles ?? []) {
    const quest = tile.title || tile.id;
    if (tile.icon?.item) check(`${quest} · icon`, "item", tile.icon.item, catalog, out);
    (tile.tasks ?? []).forEach((task, i) => checkFields(`${quest} · task ${i + 1}`, task, kindOf(task.type)?.fields ?? [], catalog, out));
    (tile.rewards ?? []).forEach((reward, i) => checkReward(`${quest} · reward ${i + 1}`, reward, catalog, out));
  }
  return out;
}
