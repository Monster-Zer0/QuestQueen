#!/usr/bin/env node
// End-to-end quest flow against a real dev client: Marionette drives the game, the real editor bridge saves the
// chapters, and the book's dev test hook (qq-click.json) presses its buttons. Every step asserts on server state
// (/questqueen status), the inventory or chat, and screenshots land in run/client/screenshots/.
//
//   1. ./gradlew runClient -Pqq.e2e   (Marionette in run/client/mods; the test hook is on in dev runs and
//      -Pqq.e2e stops the editor command opening a browser tab)
//   2. node e2e/flow.mjs
//
// It makes its own scratch world (qq-e2e-flow) and deletes it at the end. Exit code 1 if any check fails.

import { readFileSync, writeFileSync, existsSync, rmSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";

const GAME = `http://127.0.0.1:${process.env.MARIONETTE_PORT ?? 25585}`;
const EDITOR = "http://127.0.0.1:47821";
const CLICK = join(tmpdir(), "qq-click.json");
const PROBE = join(tmpdir(), "qq-probe.json");
const WORLD = "qq-e2e-flow";
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
const chat = async (command) => (await call("POST", "/command", { command })).messages?.map((m) => m.text) ?? [];
const shot = (name) => call("POST", "/screenshot", { name });
let seq = 0;
async function newChat() {
  const res = await call("GET", `/messages?since=${seq}`);
  const messages = res.messages ?? [];
  for (const m of messages) seq = Math.max(seq, m.seq);
  const out = [...pending, ...messages.map((m) => m.text)];
  pending = [];
  return out;
}
// The status reply can land after /command returns, so read it from the chat log once it has arrived. Lines
// seen here are also kept for newChat(), so a caller checking chat does not lose them.
let pending = [];
async function status() {
  const before = seq;
  await chat("questqueen status");
  let lines = [];
  for (let i = 0; i < 10; i++) {
    await wait(5);
    const res = await call("GET", `/messages?since=${before}`);
    lines = (res.messages ?? []).map((m) => m.text);
    if (lines.some((l) => l.startsWith("QQ status chapters="))) break;
  }
  pending.push(...lines.filter((l) => !l.startsWith("QQ status")));
  const last = (key) => [...lines].reverse().find((l) => l.startsWith(`QQ status ${key}=`)) ?? "";
  const pick = (key) => last(key).slice(`QQ status ${key}=`.length);
  return { completed: pick("completed"), pin: pick("pin"), stages: pick("stages"), chapters: pick("chapters") };
}
async function inventory() {
  const res = await call("GET", "/inventory");
  const counts = {};
  for (const slot of res.main ?? []) counts[slot.id] = (counts[slot.id] ?? 0) + slot.count;
  return counts;
}
async function click(payload) {
  writeFileSync(CLICK, JSON.stringify(payload));
  await wait(12);
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

const FLOW = {
  id: "qqe2e:flow", title: "E2E Flow", grid_width: 8, grid_height: 6,
  tiles: [
    { id: "a", pos: { x: 0, y: 1 }, title: "Gather dirt", tasks: [{ type: "obtain", item: "minecraft:dirt", count: 4 }],
      rewards: [{ type: "item", item: "minecraft:bread", count: 2 }] },
    { id: "b", pos: { x: 1, y: 1 }, title: "Level up", tasks: [{ type: "xp_levels", levels: 2 }],
      rewards: [{ type: "choice", options: [
        { type: "item", item: "minecraft:diamond", count: 1 },
        { type: "item", item: "minecraft:emerald", count: 8 },
        { type: "item", item: "minecraft:iron_ingot", count: 16 }] }] },
    { id: "c1", pos: { x: 2, y: 0 }, title: "Branch one", tasks: [{ type: "obtain", item: "minecraft:apple", count: 1 }],
      rewards: [{ type: "xp", amount: 10 }] },
    { id: "c2", pos: { x: 2, y: 2 }, title: "Branch two", tasks: [{ type: "obtain", item: "minecraft:stick", count: 1 }],
      rewards: [{ type: "xp", amount: 10 }] },
    { id: "d", pos: { x: 3, y: 1 }, title: "Either way", tasks: [{ type: "obtain", item: "minecraft:stone", count: 1 }] },
    { id: "h", pos: { x: 3, y: 0 }, title: "Hidden one", hidden_until: { type: "quest_complete", id: "qqe2e:flow/c1" },
      tasks: [{ type: "obtain", item: "minecraft:oak_log", count: 1 }] },
    { id: "n", pos: { x: 0, y: 3 }, title: "Before dirt", tasks: [{ type: "obtain", item: "minecraft:flint", count: 1 }] },
  ],
  links: [
    { from: "a", to: "b", gate: "and" },
    { from: "b", to: "c1", gate: "xor" }, { from: "b", to: "c2", gate: "xor" },
    { from: "c1", to: "d", gate: "or" }, { from: "c2", to: "d", gate: "or" },
    { from: "a", to: "n", gate: "not" },
  ],
};
const AFTER_QUEST = {
  id: "qqe2e:after_quest", title: "After one quest",
  unlock: { op: "and", conditions: [{ type: "quest_complete", id: "qqe2e:flow/d" }] },
  tiles: [{ id: "z", pos: { x: 0, y: 0 }, title: "Pin me", tasks: [{ type: "checkmark" }] }], links: [],
};
const AFTER_CHAPTER = {
  id: "qqe2e:after_chapter", title: "After the chapter",
  unlock: { op: "and", conditions: [{ type: "chapter_complete", id: "qqe2e:flow" }] },
  tiles: [{ id: "y", pos: { x: 0, y: 0 }, title: "Reward chapter", tasks: [{ type: "checkmark" }] }], links: [],
};

/**
 * A quest whose reward grants a stage, and a quest that needs it. Progression accepts any valid id; ProgressiveStages
 * only grants stages its own files define, so that run uses one (QQ_PS_STAGE, default: its bundled showcase:mage).
 */
const staged = (stage) => ({
  id: "qqe2e:staged", title: "Staged",
  tiles: [
    { id: "key", pos: { x: 0, y: 0 }, title: "Earn the stage", tasks: [{ type: "obtain", item: "minecraft:clay_ball", count: 1 }],
      rewards: [{ type: "stage", stage }] },
    { id: "gated", pos: { x: 1, y: 1 }, title: "Needs a stage", required_stage: stage,
      tasks: [{ type: "obtain", item: "minecraft:flint", count: 1 }] },
  ],
  links: [],
});

async function main() {
  const ping = await call("GET", "/ping").catch(() => null);
  if (!ping?.ok) throw new Error(`Marionette is not answering on ${GAME}; start the dev client first`);
  await call("POST", "/world/create", {
    type: "flat", name: WORLD, cheats: true, delete_existing: true, gamemode: "survival", difficulty: "peaceful",
    gamerules: { doMobSpawning: false, doDaylightCycle: false, keepInventory: true },
  }, 300000);
  await newChat();
  const bridge = await fetch(`${EDITOR}/api/status`).catch(() => null);
  if (!bridge?.ok) await chat("questqueen editor");
  await wait(40);

  check((await save(FLOW)) === 202, "the editor bridge accepts the flow chapter");
  check((await save(AFTER_QUEST)) === 202, "the bridge accepts the quest_complete chapter");
  check((await save(AFTER_CHAPTER)) === 202, "the bridge accepts the chapter_complete chapter");
  await wait(20);
  await chat("questqueen book chapter qqe2e:flow");
  await wait(20);
  await shot("e2e-01-board-titles.png");

  await server("give @p minecraft:dirt 4");
  await wait(60);
  let s = await status();
  check(s.completed.includes("qqe2e:flow/a"), "obtaining 4 dirt completes Gather dirt");
  check((await newChat()).some((l) => l.includes("Gather dirt") && l.includes("claim")), "a quest with rewards says to claim them");
  await click({ tile: "a" });
  await click({ action: true });
  await wait(20);
  check((await inventory())["minecraft:bread"] === 2, "CLAIM grants 2 bread");

  await server("xp add @p 2 levels");
  await wait(20);
  await click({ tile: "b" });
  await click({ action: true });
  await wait(30);
  s = await status();
  check(s.completed.includes("qqe2e:flow/b"), "SUBMIT on the level task completes Level up");
  let p = await probe();
  check(p.modal === "XOR", "finishing the fork quest shows the pick-a-path prompt once");
  await click({ confirm: true });
  await click({ tile: "b" });
  p = await probe();
  check(p.modal === "NONE" && p.expanded === true, "after the prompt, the fork quest's card opens instead of the prompt");
  await shot("e2e-02-choice-card.png");
  await click({ claim: 2 });
  await wait(20);
  check((await inventory())["minecraft:iron_ingot"] === 16, "the third choice option (16 iron) can be taken");
  await shot("e2e-03-choice-taken.png");

  await server("give @p minecraft:apple 1");
  await wait(60);
  s = await status();
  check(s.completed.includes("qqe2e:flow/c1"), "Branch one completes");
  await click({ confirm: true });
  await server("give @p minecraft:stick 1");
  await wait(60);
  s = await status();
  check(!s.completed.includes("qqe2e:flow/c2"), "Branch two stays closed after the fork is taken");
  await newChat();
  await server("give @p minecraft:stone 1");
  await server("give @p minecraft:oak_log 1");
  await wait(80);
  s = await status();
  check(s.completed.includes("qqe2e:flow/d") && s.completed.includes("qqe2e:flow/h"), "Either way and the hidden quest complete");
  const lines = await newChat();
  check(lines.some((l) => l === "Quest complete: Hidden one"), "a quest with no rewards does not mention claiming");
  check(s.chapters.includes("qqe2e:after_quest"), "the quest_complete chapter unlocks");
  check(s.chapters.includes("qqe2e:after_chapter"), "chapter_complete fires with a closed branch and a NOT-shut quest");
  await click({ chapter: "qqe2e:flow" });
  await wait(10);
  await shot("e2e-04-sidebar-done.png");

  await click({ chapter: "qqe2e:after_quest" });
  await click({ tile: "z" });
  await click({ pin: true });
  await wait(10);
  await click({ close: true });
  await wait(20);
  s = await status();
  check(s.pin === "qqe2e:after_quest/z", "closing a pinned card keeps the pin");
  p = await probe();
  check(p.showInspect === false, "the pinned card stays closed after X");
  await call("POST", "/screen/close");
  await wait(20);
  await shot("e2e-05-hud.png");

  // Optional: stage gating through whichever stage mod is installed (Progression or ProgressiveStages). Progression
  // needs NeoForge 21.1.256+: ./gradlew runClient -Pqq.e2e -Pneo_version=21.1.256 with its jar in run/client/mods.
  s = await status();
  const backend = s.stages.split(" ")[0];
  if (backend && backend !== "none") {
    const stage = backend === "progression" ? "qqe2e_gate" : (process.env.QQ_PS_STAGE ?? "showcase:mage");
    console.log(`INFO  stage mod: ${backend}, stage ${stage}`);
    check((await save(staged(stage))) === 202, "the bridge accepts a stage-gated chapter");
    await wait(20);
    await server("give @p minecraft:flint 1");
    await wait(60);
    s = await status();
    check(!s.completed.includes("qqe2e:staged/gated"), "a required_stage quest ignores progress before the stage");
    // The stage reward is Quest Queen's own grant path, the same for both stage mods.
    await server("give @p minecraft:clay_ball 1");
    await wait(60);
    await chat("questqueen book chapter qqe2e:staged");
    await wait(20);
    await click({ tile: "key" });
    await click({ action: true });
    await wait(60);
    s = await status();
    check(s.stages.includes(stage), `the stage reward grants the stage through ${backend}`);
    check(s.completed.includes("qqe2e:staged/gated"), "holding the stage opens the quest without a relog");
    if (backend === "progression") {
      // A stage granted outside Quest Queen: Progression's command, as op through the server.
      await server("progression grant @p qqe2e_outside");
      await wait(60);
      s = await status();
      check(s.stages.includes("qqe2e_outside"), "a /progression grant reaches the book without a relog");
    }
    await shot("e2e-06-stage-quest.png");
  } else {
    console.log("SKIP  no stage mod installed; stage checks not run");
  }

  await call("POST", "/world/leave", undefined, 120000);
  await call("POST", "/world/delete", { name: WORLD });
  if (existsSync(CLICK)) rmSync(CLICK);
  console.log(`\n${checks - failures.length}/${checks} checks passed`);
  if (failures.length) process.exit(1);
}

main().catch((error) => {
  console.error(error);
  process.exit(1);
});
