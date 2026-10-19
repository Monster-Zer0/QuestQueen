// Read the pack from the game: its item/mob/… catalog, its chapters and its stages.
import { fromApi, type PackCatalog } from "../packCatalog";
import { mergeStages, type AuthoredStage, type StagesState } from "../stages";
import type { Chapter } from "../types";

export interface Pack {
  catalog: PackCatalog;
  chapters: Chapter[];
  stages: StagesState;
}

type Fetcher = (path: string) => Promise<Response>;

/** Stages from GET /api/stages; an unreachable route leaves none (StageLock off). */
export async function loadStages(fetcher: Fetcher): Promise<StagesState> {
  try {
    const res = await fetcher("/api/stages");
    if (!res.ok) return { stageLock: false, stages: [] };
    const raw = (await res.json()) as { stageLock?: boolean; defined?: { id: string; name?: string }[]; authored?: AuthoredStage[] };
    return { stageLock: raw.stageLock === true, stages: mergeStages(raw.defined ?? [], raw.authored ?? []) };
  } catch {
    return { stageLock: false, stages: [] };
  }
}

export async function loadPack(fetcher: Fetcher): Promise<Pack> {
  const catalogRes = await fetcher("/api/catalog");
  if (!catalogRes.ok) throw new Error("The pack catalog is unavailable.");
  const catalog = fromApi((await catalogRes.json()) as Record<string, unknown>);
  const chaptersRes = await fetcher("/api/chapters");
  const raw = chaptersRes.ok ? ((await chaptersRes.json()) as { chapters?: Chapter[] }).chapters ?? [] : [];
  const chapters = raw.map((entry) => {
    const copy = structuredClone(entry);
    if (!Array.isArray(copy.tiles)) copy.tiles = [];
    return copy;
  });
  return { catalog, chapters, stages: await loadStages(fetcher) };
}
