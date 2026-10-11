#!/usr/bin/env node
// Chapter intro checks against a real dev client: the intro text scrolls with the mouse wheel and with its
// scrollbar, both for text that barely overflows (Skylore's Act I) and for long text.
//
//   1. ./gradlew runClient -Pqq.e2e      (Marionette in run/client/mods)
//   2. node e2e/intro.mjs
//
// It makes its own scratch world (qq-e2e-intro). Screenshots land in run/client/screenshots/ as intro-*.png.
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

// Skylore's Act I intro, which overflows its window by a few pixels at 1920x1080 / GUI scale 3.
const SHORT = "# Act I: Stranded on Atmos\n## Academy Fleet Survival Protocol\n{#C4A35A}Survival Log, Day 1. Hull integrity: no. Crew status: you.{/#}\n\nThe Academy trained you for this, in the sense that it printed a binder. The binder is now in effect: water, fire, shelter, a perimeter, a distress call.\n\nWork the protocols in any order. Anything you already did is already logged.\n\n*Then wait for rescue. That is the last page of the binder.*";
const LONG = SHORT + "\n\n" + Array.from({ length: 12 }, (_, i) => `Page ${i + 2} of the binder: more procedure, more advice, more reasons to keep reading.`).join("\n\n") + "\n\n**The end of the binder.**";

const chapters = [
  { id: "qqe2e:act_short", title: "Act Short", order: 0, intro: { body: SHORT } },
  { id: "qqe2e:act_long", title: "Act Long", order: 1, intro: { body: LONG } },
];
const child = (parent, n) => ({ id: `qqe2e:child_${n}`, title: `Child ${n}`, parent, order: 2 + n,
  tiles: [{ id: "t", pos: { x: 0, y: 0 }, title: "Dirt", tasks: [{ type: "obtain", item: "minecraft:dirt", count: 1 }] }], links: [] });

async function intro(id, label) {
  await chat(`questqueen book chapter ${id}`);
  await wait(80);
  let p = await probe();
  const [x, y, w, h] = p.introPane;
  const max = p.introContentH - p.introViewH;
  check(max > 0, `${label}: the text is taller than its window (${p.introContentH} > ${p.introViewH})`);
  check(p.introScroll === 0, `${label}: it starts at the top`);
  await shot(`intro-${label}-top`);
  await click({ wheel: { x: x + w / 2, y: y + h / 2, dir: -1 } });
  p = await probe();
  check(p.introScroll === Math.min(18, max), `${label}: one wheel notch scrolls (${p.introScroll})`);
  await click({ wheel: { x: x + w / 2, y: y + h / 2, dir: -1 } });
  for (let i = 0; i < 40; i++) await click({ wheel: { x: x + w / 2, y: y + h / 2, dir: -1 } }, 2);
  p = await probe();
  check(p.introScroll === max, `${label}: the wheel stops at the end of the text (${p.introScroll} of ${max})`);
  await shot(`intro-${label}-end`);
  // The scrollbar: press near its top and drag it to the bottom, then back to the top.
  const trackX = x + w - 4;
  await click({ mouseDrag: { x: trackX, y: y + 7, toX: trackX, toY: y + h } });
  p = await probe();
  check(p.introScroll === max, `${label}: dragging the scrollbar down reaches the end (${p.introScroll})`);
  await click({ mouseDrag: { x: trackX, y: y + h - 7, toX: trackX, toY: y } });
  p = await probe();
  check(p.introScroll === 0, `${label}: dragging the scrollbar up returns to the top (${p.introScroll})`);
}

try {
  await call("POST", "/world/create", { name: "qq-e2e-intro", gamemode: "creative", delete_existing: true });
  await wait(100);
  await chat("questqueen editor");
  await wait(20);
  for (const c of chapters) check((await save(c)) === 202, `the bridge accepts ${c.id}`);
  await save(child("qqe2e:act_short", 1));
  await save(child("qqe2e:act_long", 2));
  await wait(20);
  await intro("qqe2e:act_short", "short");
  await intro("qqe2e:act_long", "long");
  console.log(`${checks - failures.length}/${checks} checks passed`);
} finally {
  if (existsSync(CLICK)) rmSync(CLICK);
}
process.exit(failures.length ? 1 : 0);
