// What each id box lists. Most lists are the pack's (the catalog the game sent); quests, triggers, team flags
// and NPC names have no registry, so they are the ones this pack's quests already use.
import type { CatalogEntry } from "../packCatalog";
import { linkConditions, unlockConditions, type LinkCondition } from "../types";
import type { BookState } from "./store";

export const stageEntries = (s: BookState): CatalogEntry[] => s.stages.stages.map((st) => ({ id: st.id, name: st.name }));

/** Every quest, as a quest_complete gate names it ("chapter/quest"), titled with its chapter. */
export function questEntries(s: BookState): CatalogEntry[] {
  return s.chapters.flatMap((ch) => (ch.tiles ?? []).map((t) => ({ id: `${ch.id}/${t.id}`, name: `${t.title || t.id} · ${ch.title || ch.id}` })));
}

/** Every gate condition in the pack: chapter unlocks, arrows, and what hides quests. */
function allConditions(s: BookState): LinkCondition[] {
  return s.chapters.flatMap((ch) => [
    ...unlockConditions(ch),
    ...(ch.links ?? []).flatMap(linkConditions),
    ...(ch.tiles ?? []).flatMap((t) => (t.hidden_until ? [t.hidden_until] : [])),
  ]);
}

const distinct = (ids: (string | undefined)[]): CatalogEntry[] =>
  [...new Set(ids.map((id) => id?.trim()).filter((id): id is string => Boolean(id)))].sort().map((id) => ({ id, name: id }));

const taskValues = (s: BookState, type: string, key: "trigger" | "npc") =>
  s.chapters.flatMap((ch) => (ch.tiles ?? []).flatMap((t) => (t.tasks ?? []).filter((task) => task.type === type).map((task) => task[key])));

/** Trigger ids the pack's trigger tasks and trigger gates use. */
export function triggerEntries(s: BookState): CatalogEntry[] {
  return distinct([...taskValues(s, "trigger", "trigger"), ...allConditions(s).filter((c) => c.type === "trigger").map((c) => c.id)]);
}

/** Team flags gates already use, and the "trigger:…" flag each trigger sets. */
export function teamFlagEntries(s: BookState): CatalogEntry[] {
  return distinct([...allConditions(s).filter((c) => c.type === "team_flag").map((c) => c.id), ...triggerEntries(s).map((e) => `trigger:${e.id}`)]);
}

/** NPC names the pack's talk-to tasks use. */
export const npcEntries = (s: BookState): CatalogEntry[] => distinct(taskValues(s, "npc_dialog", "npc"));

/** Scrolls the pack defines, and any a quest already gives. */
export function scrollEntries(s: BookState): CatalogEntry[] {
  const defined = s.catalog.scrolls ?? [];
  const known = new Set(defined.map((e) => e.id));
  const used = distinct(s.chapters.flatMap((ch) => (ch.tiles ?? []).flatMap((t) => t.scrolls ?? []))).filter((e) => !known.has(e.id));
  return [...defined, ...used];
}

/** The list for a gate condition's id, by the condition's type. */
export function conditionEntries(type: string, s: BookState): CatalogEntry[] {
  switch (type) {
    case "advancement": return s.catalog.advancements;
    case "stage": return stageEntries(s);
    case "quest_complete": return questEntries(s);
    case "chapter_complete": return s.chapters.map((ch) => ({ id: ch.id, name: ch.title || ch.id }));
    case "trigger": return triggerEntries(s);
    case "team_flag": return teamFlagEntries(s);
    case "scoreboard": return s.catalog.objectives ?? [];
    default: return [];
  }
}
