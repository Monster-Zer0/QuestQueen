#!/usr/bin/env node
// Advancement tasks, the pinned quest and the inventory button, against a real dev client:
//   - an advancement task row's hover says what the advancement is: its title and description, or, for a tracking
//     advancement with no display (Skylore's "logged" ones), what it asks for ("Have any Logs");
//   - the pinned quest sits top left by default, says what its advancement is, and shows details on hover;
//   - the survival inventory has a quest book button that opens the book.
//
//   1. ./gradlew runClient -Pqq.e2e -Pqq.marionettePort=25586   (a port no other game is using)
//   2. MARIONETTE_PORT=25586 node e2e/advancements.mjs
//
// It makes its own scratch world (qq-e2e-adv) with a small datapack of test advancements. Screenshots land in
// run/client/screenshots/ as adv-*.png.
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

const HOVER = join(tmpdir(), "qq-hud-hover.json");
const screen = (route, body = {}) => call("POST", route, body);
const tile = (id, x, advancement, title) => ({ id, pos: { x, y: 0 }, title, icon: { item: "minecraft:oak_log" },
  description: `${title}: a quest with an advancement task. A second sentence so the pinned hover has a description.`,
  tasks: [{ type: "advancement", advancement }], rewards: [{ type: "xp", amount: 50 }] });
const chapterJson = { id: "qqe2e:adv", title: "Advancements", links: [], tiles: [
  tile("timber", 0, "qqe2e:logged/timber", "Timber"), tile("stone", 2, "minecraft:story/mine_stone", "Stone Age"),
  tile("missing", 4, "qqe2e:nope", "Missing"), tile("multi", 6, "qqe2e:logged/multi", "Multi")] };

try {
  if (existsSync(HOVER)) rmSync(HOVER);
  await call("POST", "/world/create", { name: "qq-e2e-adv", gamemode: "creative", delete_existing: true });
  await wait(100);
  await chat("questqueen editor");
  await wait(20);
  check((await save(chapterJson)) === 202, "the bridge accepts the advancement chapter");
  await wait(40);
  const pack = "run/client/saves/qq-e2e-adv/datapacks/qq_test_advancements";
  mkdirSync(`${pack}/data/qqe2e/advancement/logged`, { recursive: true });
  writeFileSync(`${pack}/pack.mcmeta`, JSON.stringify({ pack: { pack_format: 48, description: "QuestQueen e2e advancements" } }));
  writeFileSync(`${pack}/data/qqe2e/advancement/logged/timber.json`, JSON.stringify({ criteria: { got: {
    trigger: "minecraft:inventory_changed", conditions: { items: [{ items: "#minecraft:logs" }] } } } }));
  writeFileSync(`${pack}/data/qqe2e/advancement/logged/multi.json`, JSON.stringify({ criteria: {
    a: { trigger: "minecraft:inventory_changed", conditions: { items: [{ items: "minecraft:glow_berries", count: { min: 64 } }] } },
    b: { trigger: "minecraft:consume_item", conditions: { item: { items: "minecraft:bread" } } } }, requirements: [["a", "b"]] }));
  await server("reload");
  await wait(80);
  await chat("questqueen book chapter qqe2e:adv");
  await wait(30);

  const want = {
    timber: ["Have any Logs", []],
    stone: ["Stone Age", ["Mine Stone with your new Pickaxe"]],
    missing: ["Nope", ["No advancement with this id is loaded"]],
    multi: ["Any of these:", ["• Have 64 Glow Berries", "• Eat or drink Bread"]],
  };
  for (const [id, [title, lines]] of Object.entries(want)) {
    await click({ tile: id }, 30);
    const row = (await probe()).advancementRows?.[0];
    check(row?.title === title && JSON.stringify(row.lines) === JSON.stringify(lines),
      `${id}: the row's hover reads "${title}" ${JSON.stringify(lines)} (${JSON.stringify(row)})`);
    if (row) {
      await click({ hoverX: row.x + 30, hoverY: row.y }, 8);
      await shot(`adv-row-${id}`);
      await click({ clearHover: true }, 4);
    }
  }

  // ---- the pinned quest: top left, names its advancement, details on hover ----
  await click({ tile: "timber" }, 20);
  await click({ pin: true }, 20);
  await screen("/screen/close");
  await wait(20);
  await shot("adv-pin-topleft");
  await server("gamemode survival @a");
  await wait(20);
  await screen("/screen/open_inventory");
  await wait(10);
  writeFileSync(HOVER, JSON.stringify({ x: 20, y: 16 }));
  await wait(20);
  await shot("adv-pin-hover");
  rmSync(HOVER);

  // ---- the inventory button opens the book ----
  const inv = await call("GET", "/screen");
  check(inv.class?.endsWith("InventoryScreen"), `the survival inventory is open (${inv.class})`);
  await shot("adv-inventory-button");
  const left = Math.floor((inv.gui_width - 176) / 2), top = Math.floor((inv.gui_height - 166) / 2);
  await screen("/screen/mouse", { x: left + 152 + 10, y: top + 5 + 9 });
  await wait(10);
  const after = await call("GET", "/screen");
  check(after.class?.endsWith("QuestBookScreen"), `the inventory's quest book button opens the book (${after.class})`);
  console.log(`${checks - failures.length}/${checks} checks passed`);
} finally {
  if (existsSync(CLICK)) rmSync(CLICK);
  if (existsSync(HOVER)) rmSync(HOVER);
}
process.exit(failures.length ? 1 : 0);
