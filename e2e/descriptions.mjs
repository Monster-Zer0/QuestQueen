#!/usr/bin/env node
// Description checks against a real dev client: scrolling a long description in the card, the expand button and
// reader window, formatting/icons/pictures painting, and the chapter list's right edge clear of the collapse tab.
//
//   1. ./gradlew runClient -Pqq.e2e      (Marionette in run/client/mods)
//   2. node e2e/descriptions.mjs
//
// It makes its own scratch world (qq-e2e-desc). Screenshots land in run/client/screenshots/ as desc-*.png.
// Exit code 1 if any check fails.

import { readFileSync, writeFileSync, existsSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const GAME = `http://127.0.0.1:${process.env.MARIONETTE_PORT ?? 25585}`;
const EDITOR = "http://127.0.0.1:47821";
const CLICK = join(tmpdir(), "qq-click.json");
const PROBE = join(tmpdir(), "qq-probe.json");
const WORLD = "qq-e2e-desc";
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

const LONG = [
  "# The Founding",
  "Welcome, **adventurer**! This quest shows *every* kind of {#FFAA00}formatting{/#} a description can use.",
  "",
  "## What you need",
  "- {item:minecraft:oak_log x8} for the first house",
  "- {item:minecraft:cobblestone x32} for the foundations",
  "- A steady supply of {item:minecraft:bread x4}",
  "---",
  "## The sign",
  "![The Minecraft logo](minecraft:textures/gui/title/minecraft.png)",
  "__Underlined__ text and {size:12}larger text{/size} work too.",
  "",
  "## Tips",
  "- Scroll with the mouse wheel.",
  "- Press the expand button to read it in a big window.",
  "- Hover an icon like {item:minecraft:diamond} to see its name.",
  "- A picture that is missing shows a frame:",
  "![Not there](minecraft:textures/quest/not_there.png)",
  "",
  "That is everything. Good luck out there, and may your **tools** never break.",
].join("\n");

const story = {
  id: "qqe2e:story", title: "Story", grid_width: 6, grid_height: 3,
  tiles: [
    { id: "long", pos: { x: 0, y: 1 }, title: "The Founding", description: LONG,
      icon: { item: "minecraft:oak_sapling" }, tasks: [{ type: "obtain", item: "minecraft:dirt", count: 2 }] },
    { id: "short", pos: { x: 2, y: 1 }, title: "A Short Note", description: "Gather {item:minecraft:dirt x2} and bring them home.",
      icon: { item: "minecraft:dirt" }, tasks: [{ type: "obtain", item: "minecraft:dirt", count: 2 }] },
    { id: "plain", pos: { x: 4, y: 1 }, title: "Plain Text",
      description: "No markup at all.\nJust two plain lines of text, as every older pack wrote them.",
      icon: { item: "minecraft:paper" }, tasks: [{ type: "obtain", item: "minecraft:dirt", count: 2 }] },
  ],
  links: [],
};

const FILLERS = Array.from({ length: 14 }, (_, i) => ({
  id: `qqe2e:fill${String(i + 1).padStart(2, "0")}`, title: `Filler chapter ${i + 1}`, order: 10 + i,
  tiles: [{ id: "t", pos: { x: 0, y: 0 }, title: "Dirt", tasks: [{ type: "obtain", item: "minecraft:dirt", count: 1 }],
    rewards: [{ type: "item", item: "minecraft:bread", count: 1 }] }],
  links: [],
}));

try {
  await call("POST", "/world/create", { name: WORLD, gamemode: "creative", delete_existing: true });
  await wait(100);
  await chat("questqueen editor");
  await wait(20);
  check((await save(story)) === 202, "the bridge accepts the description chapter");
  for (const filler of FILLERS) await save(filler);
  await wait(20);
  await server("give @p minecraft:dirt 4");
  await wait(80);

  // ---- the long description scrolls inside the card ----
  await chat("questqueen book chapter qqe2e:story");
  await wait(20);
  await click({ tile: "long" }, 30);
  let p = await probe();
  check(p.descContentH > p.descViewH && p.descViewH > 0, `a long description is taller than its window (${p.descContentH} > ${p.descViewH})`);
  check(p.descScroll === 0, "it starts at the top");
  await shot("desc-01-card-top");
  await click({ scroll: 40 });
  p = await probe();
  check(p.descScroll === 40, "scrolling moves the description");
  await click({ scroll: 100000 });
  p = await probe();
  check(p.descScroll === p.descContentH - p.descViewH, "scrolling stops at the end of the text");
  await shot("desc-02-card-end");

  // ---- the expand button opens the reader ----
  await click({ expand: true }, 20);
  p = await probe();
  check(p.reader === true && p.readerContentH > 0, "the expand button opens the reader window");
  check(p.readerViewH >= p.descViewH, "the reader shows more of the text than the card did");
  await shot("desc-03-reader-top");
  await click({ scroll: 60 });
  p = await probe();
  check(p.readerScroll === Math.min(60, p.readerContentH - p.readerViewH), "the reader scrolls");
  await shot("desc-04-reader-scrolled");
  await click({ scroll: 100000 });
  await shot("desc-05-reader-end");
  await click({ expand: true }, 20);
  p = await probe();
  check(p.reader === false, "closing the reader returns to the card");

  // ---- a tile that fits needs no scrolling; a plain one reads as before ----
  await click({ tile: "short" }, 30);
  p = await probe();
  check(p.descContentH <= p.descViewH, "a short description does not scroll");
  await shot("desc-06-card-short");
  await click({ tile: "plain" }, 30);
  p = await probe();
  check(p.descContentH >= 20 && p.descContentH <= 40 && p.descContentH <= p.descViewH, `plain text reads as rows of 10px and does not scroll (${p.descContentH})`);
  await shot("desc-07-card-plain");

  // ---- the chapter list keeps clear of the collapse tab ----
  await click({ close: true });
  await server("give @p minecraft:dirt 4");
  await wait(80);
  await shot("desc-08-sidebar");
  console.log(`${checks - failures.length}/${checks} checks passed`);
} finally {
  if (existsSync(CLICK)) rmSync(CLICK);
}
process.exit(failures.length ? 1 : 0);
