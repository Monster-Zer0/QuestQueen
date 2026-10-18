import { describe, expect, it } from "vitest";
import type { Chapter } from "./types";
import {
  cleanStage,
  flagOn,
  lockCount,
  mergeStages,
  requiresCycle,
  slugify,
  stageProblems,
  stageUsage,
  summarySentence,
  uniqueStageId,
  type StageInfo,
} from "./stages";

const chapter = (id: string, extra: Partial<Chapter> = {}): Chapter => ({ id, title: id, tiles: [], links: [], ...extra });

describe("stage ids", () => {
  it("slugs a name into a StageLock id", () => {
    expect(slugify("Iron Age!")).toBe("iron_age");
    expect(slugify("  Café — Nether  ")).toBe("cafe_nether");
    expect(slugify("!!!")).toBe("");
  });

  it("never reuses a taken id", () => {
    expect(uniqueStageId("Iron Age", ["iron_age"])).toBe("iron_age_2");
    expect(uniqueStageId("Iron Age", ["iron_age", "iron_age_2"])).toBe("iron_age_3");
    expect(uniqueStageId("", [])).toBe("stage");
  });
});

describe("stage list", () => {
  it("lists made-here stages over the pack's copy of the same id", () => {
    const merged = mergeStages([{ id: "iron_age", name: "Iron Age" }, { id: "nether" }], [{ id: "iron_age", name: "Iron Age", locks: [] }]);
    expect(merged.map((s) => [s.id, s.authored])).toEqual([["iron_age", true], ["nether", false]]);
  });
});

describe("stage usage", () => {
  const chapters: Chapter[] = [
    chapter("p:one", {
      tiles: [
        { id: "smelt", pos: { x: 0, y: 0 }, title: "Smelt Iron", rewards: [{ type: "stage", stage: "iron_age" }] },
        { id: "old", pos: { x: 1, y: 0 }, rewards: [{ type: "progressivestages", stage: "IRON_AGE" }] },
        { id: "deep", pos: { x: 2, y: 0 }, required_stage: "iron_age" },
        { id: "secret", pos: { x: 3, y: 0 }, hidden_until: { type: "stagelock", id: "iron_age" } },
      ],
      links: [{ from: "smelt", to: "deep", gate: { op: "and", conditions: [{ type: "stage", id: "iron_age" }] } }],
    }),
    chapter("p:two", { unlock: { op: "and", conditions: [{ type: "stage", id: "iron_age" }] } }),
  ];

  it("finds every quest that grants it, under any stage alias", () => {
    expect(stageUsage(chapters, "iron_age").grants.map((u) => u.tile)).toEqual(["smelt", "old"]);
  });

  it("finds every place that needs it", () => {
    expect(stageUsage(chapters, "iron_age").needs.map((u) => u.how)).toEqual(["required", "hidden", "link", "chapter"]);
  });
});

describe("summary", () => {
  it("says what the stage locks and how to get it", () => {
    const sentence = summarySentence({
      id: "iron_age",
      name: "Iron Age",
      locks: [
        { kind: "item", targets: ["a", "b", "c", "d"] },
        { kind: "dimension", targets: ["minecraft:the_nether"] },
      ],
    }, ["Smelt Iron"], (_kind, id) => (id === "minecraft:the_nether" ? "The Nether" : id));
    expect(sentence).toBe("Players without Iron Age can't use 4 items or enter The Nether. They get it by completing Smelt Iron.");
  });

  it("says plainly when it locks nothing and nothing grants it", () => {
    expect(summarySentence({ id: "x", name: "X" }, [])).toBe("X doesn't lock anything yet. No quest grants it yet.");
  });

  it("counts locked things", () => {
    expect(lockCount({ id: "x", locks: [{ kind: "item", targets: ["a", "b"] }, { kind: "mod", targets: ["create"] }] })).toBe(3);
  });
});

describe("prerequisites", () => {
  const stages: StageInfo[] = [
    { id: "a", name: "A", authored: true, stage: { id: "a", requires: ["b"] } },
    { id: "b", name: "B", authored: true, stage: { id: "b", requires: ["c"] } },
    { id: "c", name: "C", authored: true, stage: { id: "c" } },
  ];

  it("refuses a loop, direct or not", () => {
    expect(requiresCycle(stages, "c", "a")).toBe(true);
    expect(requiresCycle(stages, "c", "c")).toBe(true);
    expect(requiresCycle(stages, "a", "c")).toBe(false);
  });

  it("reports problems in plain words", () => {
    expect(stageProblems({ id: "a", name: "" }, stages, false)).toContain("Give the stage a name.");
    expect(stageProblems({ id: "b", name: "B2" }, stages, true)).toContain("Another stage already uses the id b.");
    expect(stageProblems({ id: "c", name: "C", requires: ["a"] }, stages, false)[0]).toMatch(/already needs this stage/);
    expect(stageProblems({ id: "n", name: "N", locks: [{ kind: "item", targets: [] }] }, stages, true)[0]).toMatch(/nothing picked/);
  });
});

describe("cleanStage", () => {
  it("drops defaults so the saved file only says what the author chose", () => {
    expect(cleanStage({
      id: " Iron_Age ",
      name: "Iron Age",
      description: " ",
      scope: "player",
      message: true,
      locks: [{ kind: "item", targets: ["x", "x"], flags: { use: true, pickup: false }, message: "" }],
    })).toEqual({ id: "iron_age", name: "Iron Age", locks: [{ kind: "item", targets: ["x"], flags: { pickup: false } }] });
  });

  it("reads untouched toggles as their default", () => {
    expect(flagOn({ kind: "block", targets: [] }, "disguise")).toBe(false);
    expect(flagOn({ kind: "block", targets: [] }, "break")).toBe(true);
    expect(flagOn({ kind: "block", targets: [], flags: { break: false } }, "break")).toBe(false);
  });
});
