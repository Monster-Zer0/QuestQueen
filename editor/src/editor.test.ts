// One test (at least) per editor bug from the 1.1.211 test pass, against the pure modules main.ts uses.
import { describe, expect, it } from "vitest";
import { chapterGridHeight, chapterGridWidth, chapterTree, gateOp, linkConditions, upsertLink, wouldCreateCycle, type Chapter, type Tile } from "./types";
import { cleanChapter, descendantIds, isIntroChapter, minGrid, newTile, packNamespace, uniqueChapterId } from "./model";
import { validStageId, validate } from "./validate";
import { canUndo, snapshot, undo } from "./undo";

const tile = (id: string, x: number, y: number, extra: Partial<Tile> = {}): Tile =>
  ({ id, pos: { x, y }, title: id, tasks: [{ type: "checkmark" }], ...extra });

const chapter = (id: string, tiles: Tile[], extra: Partial<Chapter> = {}): Chapter =>
  ({ id, title: id, tiles, links: [], grid_width: 12, grid_height: 10, ...extra });

const op = (c: Chapter, from: string, to: string) => gateOp(c.links!.find((l) => l.from === from && l.to === to)!);

describe("links", () => {
  it("XOR forks the parent and leaves the child's other parent alone (#14)", () => {
    const c = chapter("p:c", [tile("a", 0, 0), tile("b", 0, 1), tile("child", 1, 0), tile("other", 1, 1)]);
    upsertLink(c, "a", "child", "and");
    upsertLink(c, "b", "child", "and");
    upsertLink(c, "a", "other", "and");
    upsertLink(c, "a", "child", "xor");
    expect(op(c, "a", "child")).toBe("xor");
    expect(op(c, "a", "other")).toBe("xor");
    expect(op(c, "b", "child")).toBe("and");
    expect(validate(c)).toEqual([]);
  });

  it("changing a rule keeps the arrow's conditions (#7)", () => {
    const c = chapter("p:c", [tile("a", 0, 0), tile("b", 1, 0)], {
      links: [{ from: "a", to: "b", gate: { op: "and", conditions: [{ type: "advancement", id: "minecraft:story/mine_stone" }] } }],
    });
    upsertLink(c, "a", "b", "or");
    expect(op(c, "a", "b")).toBe("or");
    expect(linkConditions(c.links![0])).toEqual([{ type: "advancement", id: "minecraft:story/mine_stone" }]);
  });

  it("refuses an arrow that would make a loop", () => {
    const c = chapter("p:c", [tile("a", 0, 0), tile("b", 1, 0), tile("c", 2, 0)]);
    upsertLink(c, "a", "b", "and");
    upsertLink(c, "b", "c", "and");
    expect(wouldCreateCycle(c, "c", "a")).toBe(true);
    expect(wouldCreateCycle(c, "a", "c")).toBe(false);
  });
});

describe("undo", () => {
  it("stays in its own chapter (#2)", () => {
    const one = chapter("p:one", [tile("gated", 0, 0)]);
    const two = chapter("p:two", [tile("start", 0, 0)]);
    snapshot(one);
    one.tiles = [];
    expect(undo(two)).toBe(false);
    expect(two.tiles.map((t) => t.id)).toEqual(["start"]);
    expect(undo(one)).toBe(true);
    expect(one.tiles.map((t) => t.id)).toEqual(["gated"]);
  });

  it("undoes a layout with every quest's position (#8)", () => {
    const c = chapter("p:c", [tile("a", 3, 2), tile("b", 5, 4)]);
    snapshot(c);
    c.tiles.forEach((t, i) => { t.pos = { x: i, y: 0 }; });
    undo(c);
    expect(c.tiles.map((t) => `${t.pos.x},${t.pos.y}`)).toEqual(["3,2", "5,4"]);
    expect(canUndo(c)).toBe(false);
  });
});

describe("chapters", () => {
  it("a parent loop keeps both chapters listed and the loop is reported (#3)", () => {
    const parent = chapter("p:test", [tile("a", 0, 0)], { parent: "p:bonus" });
    const child = chapter("p:bonus", [tile("b", 0, 0)], { parent: "p:test" });
    const ids = (nodes: ReturnType<typeof chapterTree>): string[] =>
      nodes.flatMap((node) => [node.chapter.id, ...ids(node.children)]);
    expect(ids(chapterTree([parent, child])).sort()).toEqual(["p:bonus", "p:test"]);
    expect(descendantIds([chapter("p:test", []), child], "p:test")).toEqual(new Set(["p:bonus"]));
    expect(validate(parent, [parent, child]).some((e) => e.includes("loop of chapters"))).toBe(true);
  });

  it("only a parent with no quests is an act intro (#13)", () => {
    const showcase = chapter("p:showcase", [tile("a", 0, 0)]);
    const wing = chapter("p:wing", [tile("b", 0, 0)], { parent: "p:showcase" });
    const act = chapter("p:act", []);
    const scene = chapter("p:scene", [tile("c", 0, 0)], { parent: "p:act" });
    const all = [showcase, wing, act, scene];
    expect(isIntroChapter(all, showcase)).toBe(false);
    expect(isIntroChapter(all, act)).toBe(true);
  });

  it("a duplicate chapter id is caught (#4)", () => {
    const a = chapter("p:nether", [tile("a", 0, 0)]);
    const b = chapter("p:nether", [tile("b", 0, 0)]);
    expect(validate(a, [a, b]).some((e) => e.includes("already uses the id"))).toBe(true);
  });

  it("new chapters and templates get a free id in the pack's namespace (#6)", () => {
    const pack = [chapter("skylore:act_i", []), chapter("skylore:new_chapter", []), chapter("questqueen:showcase", [])];
    expect(packNamespace(pack)).toBe("skylore");
    expect(uniqueChapterId(pack, "skylore", "new_chapter")).toBe("skylore:new_chapter_2");
  });
});

describe("validation", () => {
  it("a new quest has a task, and a task-less quest is a problem (#9)", () => {
    expect(newTile("t", 0, 0).tasks).toEqual([{ type: "checkmark" }]);
    const c = chapter("p:c", [tile("empty", 0, 0, { tasks: [] })]);
    expect(validate(c).some((e) => e.includes("has no tasks"))).toBe(true);
  });

  it("blank ids and zero counts are problems (#11)", () => {
    const c = chapter("p:c", [tile("q", 0, 0, {
      tasks: [{ type: "item_tag", tag: "", count: 1 }, { type: "obtain", item: "minecraft:chest", count: 0 }],
    })]);
    const errors = validate(c);
    expect(errors.some((e) => e.includes("tag is empty"))).toBe(true);
    expect(errors.some((e) => e.includes("count must be at least 1"))).toBe(true);
  });

  it("a kill task needs an entity or a tag, not both", () => {
    const c = chapter("p:c", [tile("k", 0, 0, { tasks: [{ type: "kill", tag: "minecraft:skeletons", count: 1, entity: "" }] })]);
    expect(validate(c)).toEqual([]);
    expect(cleanChapter(c).tiles[0].tasks![0]).toEqual({ type: "kill", tag: "minecraft:skeletons", count: 1 });
  });

  it("the board grows to cover a quest past the grid, and the grid cannot shrink past it (#10)", () => {
    const c = chapter("p:c", [tile("in", 0, 0), tile("out", 5, 5)], { grid_width: 2, grid_height: 2 });
    expect([chapterGridWidth(c), chapterGridHeight(c)]).toEqual([6, 6]);
    expect(validate(c)).toEqual([]);
    expect(minGrid(c)).toEqual({ width: 6, height: 6 });
    const off = chapter("p:c", [tile("neg", -1, 0)]);
    expect(validate(off).some((e) => e.includes("off the board"))).toBe(true);
  });

  it("an act chapter may leave tiles out", () => {
    const act = { id: "p:act", title: "Act" } as Chapter;
    expect(validate(act, [act])).toEqual([]);
  });

  it("a loop is reported once, without calling every quest unreachable", () => {
    const c = chapter("p:c", [tile("a", 0, 0), tile("b", 1, 0)], { links: [{ from: "a", to: "b" }, { from: "b", to: "a" }] });
    const errors = validate(c);
    expect(errors).toHaveLength(1);
    expect(errors[0]).toMatch(/^Loop:/);
  });

  it("a choice keeps every option and needs at least two (#12)", () => {
    const three = chapter("p:c", [tile("q", 0, 0, { rewards: [{ type: "choice", options: [
      { type: "item", item: "minecraft:diamond", count: 1 },
      { type: "item", item: "minecraft:emerald", count: 8 },
      { type: "item", item: "minecraft:iron_ingot", count: 16 },
    ] }] })]);
    expect(validate(three)).toEqual([]);
    expect(cleanChapter(three).tiles[0].rewards![0].options).toHaveLength(3);
    const one = chapter("p:c", [tile("q", 0, 0, { rewards: [{ type: "choice", options: [{ type: "item", item: "minecraft:diamond" }] }] })]);
    expect(validate(one).some((e) => e.includes("at least 2 options"))).toBe(true);
  });

  it("a one-path fork names the parent the author set", () => {
    const c = chapter("p:c", [tile("hub", 0, 0), tile("only", 1, 0)], { links: [{ from: "hub", to: "only", gate: "xor" }] });
    expect(validate(c)).toContain("The fork from hub has only 1 path; a fork needs at least 2");
  });

  it("stage ids follow StageLock's rule wherever a stage is named", () => {
    expect(validStageId("mypack:iron_age")).toBe(true);
    expect(validStageId("Iron_Age")).toBe(true);
    expect(validStageId("iron age")).toBe(false);
    const c = chapter("p:c", [tile("q", 0, 0, {
      required_stage: "iron age",
      rewards: [{ type: "stage", stage: "Bad Stage!" }],
    })], { unlock: { op: "and", conditions: [{ type: "stage", id: "x".repeat(65) }] } });
    const errors = validate(c);
    expect(errors.some((e) => e.includes("required stage") && e.includes("not a valid stage id"))).toBe(true);
    expect(errors.some((e) => e.includes("reward 1 (stage)") && e.includes("not a valid stage id"))).toBe(true);
    expect(errors.some((e) => e.startsWith("Chapter unlock"))).toBe(true);
    const ok = chapter("p:c", [tile("q", 0, 0, { required_stage: "mypack:iron_age", rewards: [{ type: "stage", stage: "iron_age" }] })]);
    expect(validate(ok)).toEqual([]);
    // StageLock's type name, and its old name from 1.1.213 packs, are stage conditions too.
    for (const type of ["stagelock", "progression"]) {
      const named = chapter("p:c", [tile("q", 0, 0)], { unlock: { op: "and", conditions: [{ type, id: "Bad Stage!" }] } });
      expect(validate(named).some((e) => e.startsWith("Chapter unlock") && e.includes("not a valid stage id"))).toBe(true);
    }
  });
});
