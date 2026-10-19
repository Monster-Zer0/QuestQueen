// "Previewing as": the player the book is drawn for. New player, a player who has finished up to a quest (it and
// a minimal path to it), and/or one who has some stages.
import { choose } from "./dialog";
import { finishedUpTo } from "./progress";
import { newPlayer, type BookState, type Store } from "./store";

/** Remembered with the sim so the bar can say what it is. */
const described = new WeakMap<object, { upTo?: string }>();

export function simNewPlayer(store: Store): void {
  store.setSim(newPlayer());
}

export function simFinishedUpTo(store: Store, chapterId: string, tileId: string): void {
  const s = store.get();
  const ch = s.chapters.find((c) => c.id === chapterId);
  if (!ch) return;
  const sim = { completed: finishedUpTo(ch, tileId, s.chapters), stages: new Set(s.sim.stages) };
  described.set(sim, { upTo: ch.tiles.find((t) => t.id === tileId)?.title || tileId });
  store.setSim(sim);
}

export function simWithStages(store: Store, stages: string[]): void {
  const s = store.get();
  const sim = { completed: new Set(s.sim.completed), stages: new Set(stages) };
  described.set(sim, described.get(s.sim) ?? {});
  store.setSim(sim);
}

export function previewLabel(state: BookState): string {
  const parts: string[] = [];
  const upTo = described.get(state.sim)?.upTo;
  if (upTo && state.sim.completed.size) parts.push(`finished up to ${upTo}`);
  else if (state.sim.completed.size) parts.push(`${state.sim.completed.size} quests done`);
  if (state.sim.stages.size) {
    const names = [...state.sim.stages].map((id) => state.stages.stages.find((s) => s.id === id)?.name || id);
    parts.push(`has ${names.join(", ")}`);
  }
  return `Previewing: ${parts.length ? parts.join(", ") : "new player"}`;
}

/** The bar's "Previewing as" menu. */
export async function openPreviewAs(store: Store): Promise<void> {
  const s = store.get();
  const pick = await choose<"new" | "upto" | "stage">("Preview the book as…", [
    { label: "A new player", hint: "Nothing done, no stages", value: "new" },
    { label: "Finished up to a quest…", hint: "That quest and a path to it are done", value: "upto" },
    { label: "Has a stage…", hint: "Add or remove one stage", value: "stage" },
  ]);
  if (pick === "new") simNewPlayer(store);
  if (pick === "upto") {
    const ch = s.chapters.find((c) => c.id === s.openId);
    if (!ch) return;
    const tile = await choose("Finished up to which quest?", ch.tiles.map((t) => ({ label: t.title || t.id, value: t.id })));
    if (tile) simFinishedUpTo(store, ch.id, tile);
  }
  if (pick === "stage") {
    const stage = await choose("Has which stage?", s.stages.stages.map((st) => ({
      label: `${s.sim.stages.has(st.id) ? "✓ " : ""}${st.name || st.id}`, hint: s.sim.stages.has(st.id) ? "Click to take it away" : undefined, value: st.id })));
    if (!stage) return;
    const next = new Set(store.get().sim.stages);
    if (next.has(stage)) next.delete(stage);
    else next.add(stage);
    simWithStages(store, [...next]);
  }
}
