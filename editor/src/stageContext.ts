// The stage editor's context: saving and deleting a stage through the bridge and waiting for the game to confirm
// it. Shared by the old editor (main.ts) and the book (stagesPage.ts); each supplies its own state through a host.
import { bridgeBase, bridgeFetch, iconUrl } from "./bridge";
import type { StageEditorContext } from "./stageEditor";
import { cleanStage, type AuthoredStage, type StageInfo } from "./stages";
import type { PackCatalog } from "./packCatalog";
import type { Chapter } from "./types";

export interface StageHost {
  catalog: () => PackCatalog;
  stages: () => StageInfo[];
  chapters: () => Chapter[];
  /** Re-read /api/stages into stages(). */
  refreshStages: () => Promise<unknown>;
  connected: () => boolean;
  grant: (stageId: string, chapterId: string, tileId: string) => void;
  jump: (chapterId: string, tileId?: string) => void;
  open?: (id: string) => void;
}

function stableJson(value: unknown): string {
  return JSON.stringify(value, (_key, v) => v && typeof v === "object" && !Array.isArray(v)
    ? Object.fromEntries(Object.entries(v).sort(([a], [b]) => a.localeCompare(b)))
    : v);
}

/** Poll the bridge until {@code done} holds or {@code ms} passes. */
async function waitForStages(host: StageHost, done: () => boolean, ms: number): Promise<boolean> {
  const until = Date.now() + ms;
  while (Date.now() < until) {
    await host.refreshStages();
    if (done()) return true;
    await new Promise((resolve) => window.setTimeout(resolve, 500));
  }
  return false;
}

async function bridgeError(response: Response, fallback: string): Promise<string> {
  try {
    const detail = (await response.json()) as { error?: string };
    if (detail?.error) return `Minecraft refused it: ${detail.error}`;
  } catch {
    /* not JSON */
  }
  return fallback;
}

async function saveStage(host: StageHost, stage: AuthoredStage, progress: (text: string) => void): Promise<string | null> {
  if (!host.connected()) return "Minecraft isn't connected.";
  let response: Response;
  try {
    response = await bridgeFetch("/api/stage", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(stage),
    });
  } catch {
    return "Couldn't reach Minecraft.";
  }
  if (!response.ok) return bridgeError(response, "Minecraft refused the stage. Is the editor still on?");
  progress("Saved. Reloading the game's data packs…");
  const want = stableJson(cleanStage(stage));
  const stored = await waitForStages(host, () => {
    const info = host.stages().find((entry) => entry.id === stage.id);
    return !!info?.authored && !!info.stage && stableJson(cleanStage(info.stage)) === want;
  }, 8000);
  if (!stored) return "Sent to Minecraft, but it didn't confirm. Check the game chat for the reason.";
  return null;
}

async function deleteStage(host: StageHost, id: string): Promise<string | null> {
  let response: Response;
  try {
    response = await bridgeFetch("/api/stage/delete", {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ id }),
    });
  } catch {
    return "Couldn't reach Minecraft.";
  }
  if (!response.ok) return bridgeError(response, "Minecraft refused the delete.");
  const gone = await waitForStages(host, () => !host.stages().find((entry) => entry.id === id)?.authored, 8000);
  // StageLock's own list catches up after the reload; give it a moment so the stage doesn't reappear as "pack".
  if (gone) await waitForStages(host, () => !host.stages().find((entry) => entry.id === id), 4000);
  return gone ? null : "Sent to Minecraft, but it didn't confirm. Check the game chat for the reason.";
}

export function stageEditorContext(host: StageHost, onDone: (id: string) => void, onCancel?: () => void): StageEditorContext {
  return {
    catalog: () => host.catalog(),
    stages: () => host.stages(),
    chapters: () => host.chapters(),
    bridgeBase: () => bridgeBase(),
    iconUrl: (item) => iconUrl(item),
    onSave: (stage, progress) => saveStage(host, stage, progress),
    onDelete: (id) => deleteStage(host, id),
    onGrantFromQuest: host.grant,
    onJump: host.jump,
    onDone,
    onCancel,
    onOpen: host.open,
  };
}
