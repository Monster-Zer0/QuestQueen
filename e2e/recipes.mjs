#!/usr/bin/env node
// Recipes and item lookups in quest descriptions, and display items on tasks, against a real dev client:
//   - {recipe:<item>} and {recipe:id=<recipe>} draw crafting, smelting, stonecutting and smithing recipes;
//   - an ingredient that accepts several items cycles through them;
//   - a bad id draws a "missing recipe" frame, and a datapack recipe appears after /reload;
//   - description items open recipes (left click) or uses (right click) in the recipe viewer, and stay hover-only
//     with none (the dev run has no viewer, so the book's test hook pretends one and records the lookup);
//   - a task with no item of its own shows its display item, and clicking it looks the item up.
//
//   1. ./gradlew runClient -Pqq.e2e -Pqq.marionettePort=25586   (a port no other game is using)
//   2. MARIONETTE_PORT=25586 node e2e/recipes.mjs
//
// It makes its own scratch world (qq-e2e-recipes). Screenshots land in run/client/screenshots/ as recipe-*.png.
// Before running, make sure the Marionette port belongs to the dev client and not to another game.
// Exit code 1 if any check fails.

import { readFileSync, writeFileSync, existsSync, rmSync, mkdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const GAME = `http://127.0.0.1:${process.env.MARIONETTE_PORT ?? 25585}`;
const EDITOR = "http://127.0.0.1:47821";
const CLICK = join(tmpdir(), "qq-click.json");
const PROBE = join(tmpdir(), "qq-probe.json");
const failures = [];
let checks = 0;

async function call(method, route, body, timeoutMs = 60000) {
  const res = await fetch(GAME + route, {
    method,
    headers: body ? { "content-type": "application/json" } : undefined,
    body: body ? JSON.stringify(body) : undefined,
    signal: AbortSignal.timeout(timeoutMs),
  });
  return res.json();
}
const wait = (ticks) => call("POST", "/wait", { ticks }, Math.max(20000, ticks * 100));
const chat = (command) => call("POST", "/command", { command });
const server = (command) => call("POST", "/server_command", { command });
const shot = async (name) => { await wait(10); return call("POST", "/screenshot", { name }); };
async function click(payload, ticks = 14) {
  writeFileSync(CLICK, JSON.stringify(payload));
  await wait(ticks);
}
async function probe() {
  if (existsSync(PROBE)) rmSync(PROBE);
  await click({ probe: true });
  return existsSync(PROBE) ? JSON.parse(readFileSync(PROBE, "utf8")) : {};
}
function check(ok, what) {
  checks++;
  console.log(`${ok ? "PASS" : "FAIL"}  ${what}`);
  if (!ok) failures.push(what);
}
async function save(chapter) {
  const session = await (await fetch(`${EDITOR}/api/session`)).json();
  const res = await fetch(`${EDITOR}/api/chapter`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Editor-Nonce": session.nonce },
    body: JSON.stringify(chapter),
  });
  return res.status;
}

const desc = (id, x, description, tasks = [{ type: "checkmark" }]) => ({ id, pos: { x, y: 0 }, title: id, description,
  icon: { item: "minecraft:crafting_table" }, tasks });
const chapterJson = { id: "qqe2e:recipes", title: "Recipes", links: [], tiles: [
  desc("furnace", 0, "Build a furnace:\n{recipe:minecraft:furnace}"),
  desc("planks", 2, "Any oak log works:\n{recipe:minecraft:oak_planks}"),
  desc("smelt", 4, "{recipe:id=minecraft:iron_ingot_from_smelting_iron_ore}"),
  desc("cutter", 6, "{recipe:id=minecraft:stone_slab_from_stone_stonecutting}"),
  desc("smithing", 8, "{recipe:minecraft:netherite_sword}"),
  desc("missing", 10, "{recipe:minecraft:not_an_item}"),
  desc("later", 12, "{recipe:id=qqe2e:dirt_to_diamond}"),
  desc("display", 14, "Mine {item:minecraft:diamond x3} with a good pick.", [
    { type: "advancement", advancement: "minecraft:story/mine_stone", icon: "minecraft:stone_pickaxe" },
    { type: "kill", entity: "minecraft:zombie", count: 2, icon: "minecraft:rotten_flesh" }]),
] };
const items = (p) => (p.descItems ?? []).map((i) => i.item);

async function open(tile, name) {
  await click({ tile }, 30);
  const p = await probe();
  await shot(`recipe-${name}`);
  return p;
}

try {
  await call("POST", "/world/create", { name: "qq-e2e-recipes", gamemode: "creative", delete_existing: true });
  await wait(100);
  await chat("questqueen editor");
  await wait(20);
  check((await save(chapterJson)) === 202, "the bridge accepts the recipe chapter");
  await wait(40);
  await chat("questqueen book chapter qqe2e:recipes");
  await wait(30);

  let p = await open("furnace", "crafting");
  // The furnace takes any stone crafting material, so its eight slots cycle cobblestone, blackstone, ...
  check(items(p).filter((i) => i !== "minecraft:furnace").length === 8 && items(p).includes("minecraft:furnace"),
    `crafting: eight filled slots around an empty middle, and the furnace (${items(p).join(", ")})`);

  await click({ tile: "planks" }, 30);
  const seen = new Set();
  for (let i = 0; i < 4; i++) {
    p = await probe();
    items(p).filter((it) => it !== "minecraft:oak_planks").forEach((it) => seen.add(it));
    await wait(24);
  }
  check(seen.size >= 2 && [...seen].every((it) => it.includes("oak")),
    `an "any oak log" ingredient cycles through its items (${[...seen].join(", ")})`);
  await shot("recipe-planks");

  p = await open("smelt", "smelting");
  check(items(p).includes("minecraft:iron_ore") && items(p).includes("minecraft:iron_ingot"), `smelting by recipe id (${items(p).join(", ")})`);
  p = await open("cutter", "stonecutting");
  check(items(p).includes("minecraft:stone") && items(p).includes("minecraft:stone_slab"), `stonecutting by recipe id (${items(p).join(", ")})`);
  p = await open("smithing", "smithing");
  check(["minecraft:netherite_upgrade_smithing_template", "minecraft:diamond_sword", "minecraft:netherite_ingot", "minecraft:netherite_sword"]
    .every((it) => items(p).includes(it)), `smithing: template, base, addition and result (${items(p).join(", ")})`);
  p = await open("missing", "missing");
  check(items(p).length === 0, "a recipe that does not exist draws its frame and no items");
  p = await open("later", "later-before");
  check(items(p).length === 0, "a datapack recipe that is not there yet is missing");

  // Add the recipe in a datapack and /reload: the same description now draws it.
  const pack = "run/client/saves/qq-e2e-recipes/datapacks/qq_test_recipes";
  mkdirSync(`${pack}/data/qqe2e/recipe`, { recursive: true });
  writeFileSync(`${pack}/pack.mcmeta`, JSON.stringify({ pack: { pack_format: 48, description: "QuestQueen e2e recipes" } }));
  writeFileSync(`${pack}/data/qqe2e/recipe/dirt_to_diamond.json`, JSON.stringify({ type: "minecraft:crafting_shapeless",
    ingredients: [{ item: "minecraft:dirt" }, { item: "minecraft:dirt" }], result: { id: "minecraft:diamond", count: 1 } }));
  await server("reload");
  await wait(80);
  p = await open("later", "later-after");
  check(items(p).includes("minecraft:dirt") && items(p).includes("minecraft:diamond"), `after /reload the new recipe is drawn (${items(p).join(", ")})`);

  // ---- clicking items: hover-only with no viewer, recipes on left click and uses on right with one ----
  p = await open("display", "display");
  const diamond = (p.descItems ?? []).find((i) => i.item === "minecraft:diamond");
  check(!!diamond, "the description's item icon is on the card");
  await click({ descClick: { x: diamond.x, y: diamond.y, button: 0 } });
  check((await probe()).lastLookup === "", "with no recipe viewer, clicking an item does nothing");
  await click({ pretendViewer: true });
  await click({ descClick: { x: diamond.x, y: diamond.y, button: 0 } });
  check((await probe()).lastLookup === "minecraft:diamond recipes", "left click opens the item's recipes");
  await click({ descClick: { x: diamond.x, y: diamond.y, button: 1 } });
  check((await probe()).lastLookup === "minecraft:diamond uses", "right click opens its uses");
  await click({ tile: "smelt" }, 20);
  const slot = ((await probe()).descItems ?? []).find((i) => i.item === "minecraft:iron_ore");
  await click({ descClick: { x: slot.x, y: slot.y, button: 0 } });
  check((await probe()).lastLookup === "minecraft:iron_ore recipes", "recipe slots are clickable too");

  await click({ tile: "display" }, 20);
  p = await probe();
  const icons = (p.taskIcons ?? []).map((i) => `${i.task}:${i.item}`);
  check(icons.includes("0:minecraft:stone_pickaxe") && icons.includes("1:minecraft:rotten_flesh"),
    `tasks with no item of their own show their display item (${icons.join(", ")})`);
  await click({ jei: 1 });
  check((await probe()).lastLookup === "minecraft:rotten_flesh recipes", "clicking a display item opens its recipe");
  await click({ pretendViewer: false });
  console.log(`${checks - failures.length}/${checks} checks passed`);
} finally {
  if (existsSync(CLICK)) rmSync(CLICK);
}
process.exit(failures.length ? 1 : 0);
