#!/usr/bin/env node
// Play-testing checks against a real dev client: a ready-to-claim tile stands out on every zoom rung, the obtain
// verb reads OBTAIN, and fit-to-chapter reaches the 32 px rung so a 20-column chapter fits on screen.
//
//   1. ./gradlew runClient -Pqq.e2e      (Marionette in run/client/mods)
//   2. node e2e/claimzoom.mjs
//
// It makes its own scratch world (qq-e2e-claim). Screenshots land in run/client/screenshots/ as claim-*.png.
// Exit code 1 if any check fails.

import { readFileSync, writeFileSync, existsSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const GAME = `http://127.0.0.1:${process.env.MARIONETTE_PORT ?? 25585}`;
const EDITOR = "http://127.0.0.1:47821";
const CLICK = join(tmpdir(), "qq-click.json");
const PROBE = join(tmpdir(), "qq-probe.json");
const WORLD = "qq-e2e-claim";
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
const server = (command) => call("POST", "/server_command", { command });
const chat = (command) => call("POST", "/command", { command });
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

// 20 columns: the top row is done and waiting to be claimed, the middle row is open, the bottom row has no rewards.
const tiles = [];
for (let x = 0; x < 20; x++) {
  tiles.push({ id: `c${x}`, pos: { x, y: 0 }, title: `Ready ${x + 1}`, icon: { item: "minecraft:dirt" },
    tasks: [{ type: "obtain", item: "minecraft:dirt", count: 1 }],
    rewards: [{ type: "item", item: "minecraft:bread", count: 2 }] });
  tiles.push({ id: `o${x}`, pos: { x, y: 1 }, title: `Open ${x + 1}`, icon: { item: "minecraft:diamond" },
    tasks: [{ type: "obtain", item: "minecraft:diamond", count: 4 }],
    rewards: [{ type: "item", item: "minecraft:bread", count: 1 }] });
  tiles.push({ id: `p${x}`, pos: { x, y: 2 }, title: `Plain ${x + 1}`, icon: { item: "minecraft:paper" },
    tasks: [{ type: "obtain", item: "minecraft:dirt", count: 1 }] });
}
const wide = { id: "qqe2e:wide", title: "Wide", grid_width: 20, grid_height: 3, tiles, links: [] };

try {
  await call("POST", "/world/create", { name: WORLD, gamemode: "creative", delete_existing: true });
  await wait(100);
  await chat("questqueen editor");
  await wait(20);
  check((await save(wide)) === 202, "the bridge accepts the 20-column chapter");
  await wait(20);
  await server("give @p minecraft:dirt 4");
  await wait(80);

  await chat("questqueen book chapter qqe2e:wide");
  await wait(30);
  await click({ sidebar: "expand" });
  await click({ sidebar: "default" });
  await click({ fit: true }, 20);
  let p = await probe();
  check(p.tilePxNow === 32, `fit-to-chapter lands on the 32 px rung (${p.tilePxNow})`);
  check(p.tilesInView === p.tilesPlaced, `all 20 columns fit beside the default sidebar at GUI ${p.guiW}x${p.guiH} (${p.tilesInView}/${p.tilesPlaced})`);
  await shot("claim-01-fit-32");

  for (const [zoom, px] of [[0.75, 48], [1, 64], [2, 128]]) {
    await click({ zoom, cameraX: 0, cameraY: -40 }, 14);
    p = await probe();
    check(p.tilePxNow === px, `zoom ${zoom} draws ${px} px tiles (${p.tilePxNow})`);
    await shot(`claim-0${px === 48 ? 2 : px === 64 ? 3 : 4}-${px}`);
  }

  await click({ tile: "o0" }, 30);
  await shot("claim-05-obtain-verb");
  console.log(`${checks - failures.length}/${checks} checks passed`);
} finally {
  if (existsSync(CLICK)) rmSync(CLICK);
}
process.exit(failures.length ? 1 : 0);
