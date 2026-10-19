// The Living Book's state: the pack's chapters, which one is open, the selected quest, the mode, the simulated
// player the board previews as, and what the game last saved. Every change to a chapter goes through edit(),
// which names its chapter (an inline editor captures it when it opens, so a chapter switch mid-edit can never
// redirect the change), snapshots it for undo, and tells subscribers why.
import { cleanChapter } from "../model";
import { emptyCatalog, type PackCatalog } from "../packCatalog";
import type { StagesState } from "../stages";
import type { Chapter } from "../types";
import { stageWarnings, validate } from "../validate";

export type Mode = "edit" | "preview";

/** A player the board is previewed as. `completed` keys are "<chapterId>#<tileId>". */
export interface SimPlayer {
  completed: Set<string>;
  stages: Set<string>;
}

export interface BookState {
  chapters: Chapter[];
  openId: string;
  selected: string | null;
  mode: Mode;
  zoom: number;
  sim: SimPlayer;
  /** JSON of each chapter as the game last saw it, by chapter id. */
  savedJson: Map<string, string>;
  catalog: PackCatalog;
  stages: StagesState;
}

export interface SaveResult {
  ok: boolean;
  error?: string;
  problems?: string[];
}

export interface Store {
  get(): Readonly<BookState>;
  subscribe(fn: (state: BookState, why: string) => void): () => void;
  /** Change one chapter: snapshot it for undo, run fn on it, notify with why. A missing chapter is a no-op. */
  edit(chapterId: string, why: string, fn: (chapter: Chapter) => void): void;
  undo(): void;
  redo(): void;
  canUndo(): boolean;
  canRedo(): boolean;
  open(chapterId: string): void;
  select(tileId: string | null): void;
  setMode(mode: Mode): void;
  setZoom(zoom: number): void;
  setSim(sim: SimPlayer): void;
  /** Replace state wholesale (pack loaded, catalog or stages refreshed); no undo. */
  load(patch: Partial<BookState>, why: string): void;
  dirty(chapterId: string): boolean;
  markSaved(chapterId: string): void;
  problems(chapterId: string): string[];
  warnings(chapterId: string): string[];
  /** Validate, then post the clean chapter JSON. Problems or a refusal leave the chapter dirty. */
  save(chapterId: string, post: (json: string) => Promise<Response>): Promise<SaveResult>;
}

/** Extra warnings (id checks, Task 8) are plugged in here so the store stays free of catalog logic. */
export type WarningSource = (chapter: Chapter, state: BookState) => string[];

const UNDO_LIMIT = 100;
const NOT_CONNECTED = "Minecraft isn't connected. Your changes are kept here.";

export function newPlayer(): SimPlayer {
  return { completed: new Set(), stages: new Set() };
}

export function createStore(initial: Partial<BookState> = {}, extraWarnings: WarningSource[] = []): Store {
  const state: BookState = {
    chapters: [],
    openId: "",
    selected: null,
    mode: "edit",
    zoom: 2,
    sim: newPlayer(),
    savedJson: new Map(),
    catalog: emptyCatalog(),
    stages: { stageLock: false, stages: [] },
    ...initial,
  };
  const listeners = new Set<(state: BookState, why: string) => void>();
  const undoStacks = new Map<string, string[]>();
  const redoStacks = new Map<string, string[]>();

  const notify = (why: string) => listeners.forEach((fn) => fn(state, why));
  const indexOf = (id: string) => state.chapters.findIndex((c) => c.id === id);
  const find = (id: string) => state.chapters.find((c) => c.id === id);
  const json = (c: Chapter) => JSON.stringify(cleanChapter(c));
  const stack = (map: Map<string, string[]>, id: string) => {
    let s = map.get(id);
    if (!s) map.set(id, (s = []));
    return s;
  };
  /** Swap the open chapter for a snapshot, pushing the current one onto `onto`. */
  const restore = (from: Map<string, string[]>, onto: Map<string, string[]>, why: string) => {
    const id = state.openId;
    const i = indexOf(id);
    const snap = from.get(id)?.pop();
    if (i < 0 || snap === undefined) return;
    stack(onto, id).push(JSON.stringify(state.chapters[i]));
    state.chapters[i] = JSON.parse(snap) as Chapter;
    if (state.selected && !state.chapters[i].tiles?.some((t) => t.id === state.selected)) state.selected = null;
    notify(why);
  };

  const store: Store = {
    get: () => state,
    subscribe(fn) {
      listeners.add(fn);
      return () => listeners.delete(fn);
    },
    edit(chapterId, why, fn) {
      const chapter = find(chapterId);
      if (!chapter) return;
      const undo = stack(undoStacks, chapterId);
      undo.push(JSON.stringify(chapter));
      if (undo.length > UNDO_LIMIT) undo.shift();
      redoStacks.delete(chapterId);
      fn(chapter);
      notify(why);
    },
    undo: () => restore(undoStacks, redoStacks, "undo"),
    redo: () => restore(redoStacks, undoStacks, "redo"),
    canUndo: () => (undoStacks.get(state.openId)?.length ?? 0) > 0,
    canRedo: () => (redoStacks.get(state.openId)?.length ?? 0) > 0,
    open(chapterId) {
      if (!find(chapterId) || state.openId === chapterId) return;
      state.openId = chapterId;
      state.selected = null;
      notify("open chapter");
    },
    select(tileId) {
      if (state.selected === tileId) return;
      state.selected = tileId;
      notify("select");
    },
    setMode(mode) {
      if (state.mode === mode) return;
      state.mode = mode;
      notify("mode");
    },
    setZoom(zoom) {
      if (state.zoom === zoom) return;
      state.zoom = zoom;
      notify("zoom");
    },
    setSim(sim) {
      state.sim = sim;
      notify("preview player");
    },
    load(patch, why) {
      Object.assign(state, patch);
      notify(why);
    },
    dirty(chapterId) {
      const chapter = find(chapterId);
      return chapter ? state.savedJson.get(chapterId) !== json(chapter) : false;
    },
    markSaved(chapterId) {
      const chapter = find(chapterId);
      if (chapter) state.savedJson.set(chapterId, json(chapter));
    },
    problems(chapterId) {
      const chapter = find(chapterId);
      return chapter ? validate(chapter, state.chapters) : [];
    },
    warnings(chapterId) {
      const chapter = find(chapterId);
      if (!chapter) return [];
      const stageIds = new Set(state.stages.stages.map((s) => s.id));
      const stages = state.stages.stageLock ? stageWarnings(chapter, stageIds, state.chapters) : [];
      return [...stages, ...extraWarnings.flatMap((source) => source(chapter, state))];
    },
    async save(chapterId, post) {
      const chapter = find(chapterId);
      if (!chapter) return { ok: false, error: "No such chapter." };
      const problems = store.problems(chapterId);
      if (problems.length) return { ok: false, error: `${problems.length} problem${problems.length === 1 ? "" : "s"} to fix first`, problems };
      let response: Response;
      try {
        response = await post(json(chapter));
      } catch {
        return { ok: false, error: NOT_CONNECTED };
      }
      if (!response.ok) {
        let error = "Minecraft refused the save.";
        try {
          const body = (await response.json()) as { error?: string };
          if (body?.error) error = body.error;
        } catch {
          /* not JSON: keep the generic reason */
        }
        return { ok: false, error };
      }
      store.markSaved(chapterId);
      notify("saved");
      return { ok: true };
    },
  };
  return store;
}
