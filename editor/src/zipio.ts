// Chapters to and from a datapack ZIP: data/<namespace>/questqueen/chapters/<path>.json plus a pack.mcmeta, so an
// export drops straight into a world's datapacks folder and a pack's ZIP loads back in.
import JSZip from "jszip";
import { cleanChapter } from "./model";
import type { Chapter } from "./types";

/** Every chapter (a backup of one chapter is not a backup), as a datapack ZIP. */
export async function exportZip(chapters: Chapter[]): Promise<Blob> {
  const zip = new JSZip();
  zip.file("pack.mcmeta", JSON.stringify({ pack: { pack_format: 48, description: "Quest Queen export" } }, null, 2));
  for (const chapter of chapters) {
    const [ns, path] = chapter.id.split(":");
    zip.file(`data/${ns}/questqueen/chapters/${path}.json`, JSON.stringify(cleanChapter(chapter), null, 2));
  }
  return zip.generateAsync({ type: "blob" });
}

export interface ZipRead {
  loaded: Chapter[];
  /** Entries that look like chapters but could not be read, with why. */
  skipped: string[];
  /** Titles of unsaved chapters that loading would replace: ask before going on. */
  losing: string[];
}

const isNum = (v: unknown) => typeof v === "number" && Number.isFinite(v);

/** What would break the editor in a chapter read from a file (it draws every quest and link), or null. */
function shapeProblem(chapter: Chapter): string | null {
  if (!Array.isArray(chapter.tiles)) return "its quests aren't a list";
  for (const tile of chapter.tiles as unknown[]) {
    const t = tile as { id?: unknown; pos?: { x?: unknown; y?: unknown } } | null;
    if (!t || typeof t.id !== "string" || !t.pos || !isNum(t.pos.x) || !isNum(t.pos.y)) return "a quest without an id and a position";
  }
  if (chapter.links !== undefined) {
    if (!Array.isArray(chapter.links)) return "its links aren't a list";
    for (const link of chapter.links as unknown[]) {
      const l = link as { from?: unknown; to?: unknown } | null;
      if (!l || typeof l.from !== "string" || typeof l.to !== "string") return "a link without a from and a to";
    }
  } else chapter.links = [];
  return null;
}

/** Read the chapters in a ZIP. Throws "<name> is not a ZIP file the editor can read." for anything else. */
export async function readZip(file: File, existing: Chapter[], dirty: (id: string) => boolean): Promise<ZipRead> {
  let zip: JSZip;
  try {
    zip = await JSZip.loadAsync(await file.arrayBuffer());
  } catch {
    throw new Error(`${file.name} is not a ZIP file the editor can read.`);
  }
  const loaded: Chapter[] = [];
  const skipped: string[] = [];
  for (const [name, entry] of Object.entries(zip.files)) {
    if (entry.dir || !name.replaceAll("\\", "/").includes("/questqueen/chapters/") || !name.endsWith(".json")) continue;
    try {
      const parsed = JSON.parse(await entry.async("string")) as Chapter;
      if (typeof parsed?.id !== "string" || !parsed.id.includes(":")) {
        skipped.push(`${name} (no chapter id)`);
        continue;
      }
      if (!parsed.tiles) parsed.tiles = [];
      const shape = shapeProblem(parsed);
      if (shape) {
        skipped.push(`${name} (${shape})`);
        continue;
      }
      loaded.push(parsed);
    } catch {
      skipped.push(`${name} (not valid JSON)`);
    }
  }
  const losing = loaded
    .map((next) => existing.find((c) => c.id === next.id))
    .filter((c): c is Chapter => Boolean(c) && dirty(c!.id))
    .map((c) => c.title || c.id);
  return { loaded, skipped, losing };
}
