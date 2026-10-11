#!/usr/bin/env node
// Command reward checks against a real dev client: a give command shows the item it gives (components included),
// icon/name/description fields replace it, other commands read "Bonus reward", and no command text reaches the book.
//
//   1. ./gradlew runClient -Pqq.e2e -Pqq.marionettePort=25586   (a port no other game is using)
//   2. MARIONETTE_PORT=25586 node e2e/commandrewards.mjs
//
// It makes its own scratch world (qq-e2e-cmd). Screenshots land in run/client/screenshots/ as cmd-*.png.
// Before running, make sure the Marionette port belongs to the dev client and not to another game.
// Exit code 1 if any check fails.

import { readFileSync, writeFileSync, existsSync, rmSync } from "node:fs";
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

const SWORD = `give @s minecraft:diamond_sword[minecraft:custom_name='"Excalibur"'] 1`;
const ARROWS = "give @s minecraft:arrow 32";
const tiles = [
  { id: "gives", pos: { x: 0, y: 0 }, title: "Gives", icon: { item: "minecraft:chest" },
    tasks: [{ type: "obtain", item: "minecraft:dirt", count: 1 }],
    rewards: [{ type: "command", command: SWORD }, { type: "command", command: ARROWS }] },
  { id: "custom", pos: { x: 2, y: 0 }, title: "Custom", icon: { item: "minecraft:chest" },
    tasks: [{ type: "obtain", item: "minecraft:dirt", count: 1 }],
    rewards: [
      { type: "command", command: "effect give @s minecraft:speed 60", icon: "glyph:flame", name: "Quick Feet",
        description: "Sixty seconds of speed.\nRun." },
      { type: "command", command: ARROWS, icon: "minecraft:apple", name: "Academy Rounds" },
      { type: "command", command: "effect give @s minecraft:regeneration 30", icon: "minecraft:textures/item/emerald.png" },
    ] },
  { id: "plain", pos: { x: 4, y: 0 }, title: "Plain", icon: { item: "minecraft:chest" },
    tasks: [{ type: "obtain", item: "minecraft:dirt", count: 1 }],
    rewards: [{ type: "command", command: "effect give @s minecraft:regeneration 30" }] },
];
const chapterJson = { id: "qqe2e:cmd", title: "Commands", tiles, links: [] };

async function card(tile, name) {
  await click({ tile }, 30);
  const p = await probe();
  for (const [i, r] of (p.cardRewards ?? []).entries()) {
    if (!r.shown) continue;
    await click({ hoverX: r.x, hoverY: r.y }, 6);
    await shot(`cmd-${name}-hover-${i + 1}`);
  }
  await click({ clearHover: true }, 4);
  return p.cardRewards ?? [];
}

try {
  await call("POST", "/world/create", { name: "qq-e2e-cmd", gamemode: "creative", delete_existing: true });
  await wait(100);
  await chat("questqueen editor");
  await wait(20);
  check((await save(chapterJson)) === 202, "the bridge accepts command rewards with icon, name and description");
  await wait(20);
  await chat("questqueen book chapter qqe2e:cmd");
  await wait(30);

  let r = await card("gives", "gives");
  check(r[0]?.realItem && r[0].item === "minecraft:diamond_sword" && r[0].label === "Excalibur",
    `a give with components shows that item and its own name (${JSON.stringify(r[0])})`);
  check(r[1]?.realItem && r[1].item === "minecraft:arrow" && r[1].label === "32x Arrow", `a counted give reads "32x Arrow" (${r[1]?.label})`);

  r = await card("custom", "custom");
  check(!r[0]?.realItem && r[0]?.label === "Quick Feet", `a glyph icon with a name (${JSON.stringify(r[0])})`);
  check(r[1]?.realItem && r[1].item === "minecraft:apple" && r[1].label === "Academy Rounds", `an item icon and name replace the give's (${JSON.stringify(r[1])})`);
  check(!r[2]?.realItem && r[2]?.label === "Bonus reward", `a picture icon with no name reads "Bonus reward" (${JSON.stringify(r[2])})`);

  r = await card("plain", "plain");
  check(!r[0]?.realItem && r[0]?.label === "Bonus reward", `a bare non-give command reads "Bonus reward" (${JSON.stringify(r[0])})`);
  const all = JSON.stringify(await probe());
  check(!all.includes("effect give") && !all.includes("give @s"), "no command text in the book's state");

  // Finish the quests and open claim-all and the log, where rewards are listed by name.
  await server("give @p minecraft:dirt 4");
  await wait(80);
  await click({ close: true });
  await shot("cmd-board-claimable");
  console.log(`${checks - failures.length}/${checks} checks passed`);
} finally {
  if (existsSync(CLICK)) rmSync(CLICK);
}
process.exit(failures.length ? 1 : 0);
