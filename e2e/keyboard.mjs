#!/usr/bin/env node
// Keyboard control of the quest book against a real dev client: arrows move between quests, Enter opens a card and
// then focuses inside it, R/U look up the focused item, C claims, Shift+C claims all, P (rebound to O for this run)
// pins, E reads, Page keys change chapter, = / - / F zoom and fit, / searches, L opens the log, F1 the key help, Esc
// backs out one step at a time; and none of Quest Queen's keys reach other mods (a stand-in listener in dev runs).
//
//   0. Rebind Pin to O first: add  key_key.questqueen.pin:key.keyboard.o:NONE  to run/client/options.txt
//   1. ./gradlew runClient -Pqq.e2e -Pqq.marionettePort=25586   (a port no other game is using)
//   2. MARIONETTE_PORT=25586 node e2e/keyboard.mjs
//
// It makes its own scratch world (qq-e2e-keys). Screenshots land in run/client/screenshots/ as keys-*.png.
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

const KEY = { ENTER: 257, ESCAPE: 256, UP: 265, DOWN: 264, LEFT: 263, RIGHT: 262, C: 67, P: 80, O: 79, E: 69, R: 82,
  U: 85, L: 76, F: 70, K: 75, PAGE_UP: 266, PAGE_DOWN: 267, EQUAL: 61, MINUS: 45, SLASH: 47, F1: 290 };
const SHIFT = 1;
const press = async (key, mods = 0, ticks = 8, char) => { await click(char ? { key, mods, char } : { key, mods }, ticks); return probe(); };
const tile = (id, x, y, extra = {}) => ({ id, pos: { x, y }, title: id.toUpperCase(), icon: { item: "minecraft:oak_sapling" },
  description: `Quest ${id}. Mine {item:minecraft:diamond} if you can.`,
  tasks: [{ type: "obtain", item: "minecraft:dirt", count: 1 }], rewards: [{ type: "item", item: "minecraft:bread", count: 1 }], ...extra });
const keys = { id: "qqe2e:keys", title: "Keys", order: 0, links: [],
  tiles: [tile("c", 2, 2), tile("n", 2, 0), tile("s", 2, 4), tile("w", 0, 2),
    tile("e", 4, 2, { tasks: [{ type: "obtain", item: "minecraft:diamond", count: 1 }] })] };
const keys2 = { id: "qqe2e:keys2", title: "Keys Two", order: 1, links: [], tiles: [tile("t", 0, 0)] };

try {
  await call("POST", "/world/create", { name: "qq-e2e-keys", gamemode: "creative", delete_existing: true });
  await wait(100);
  await chat("questqueen editor");
  await wait(20);
  check((await save(keys)) === 202 && (await save(keys2)) === 202, "the bridge accepts the two chapters");
  await wait(40);
  await chat("questqueen book chapter qqe2e:keys");
  await wait(30);
  await click({ pretendViewer: true });
  await click({ resetOtherMod: true });

  // ---- arrows move between quests ----
  let p = await press("RIGHT");
  const start = p.selected;
  check(["c", "n", "s", "w", "e"].includes(start) && p.keyboardMode && !p.expanded,
    `the first arrow selects the chapter's starting quest without opening it (${start})`);
  await shot("keys-01-selected");
  // Walk from the starting quest to the centre one with the arrows.
  const toCentre = { n: ["DOWN"], s: ["UP"], w: ["RIGHT"], e: ["LEFT"], c: [] }[start];
  for (const key of toCentre) p = await press(key);
  check(p.selected === "c", `arrows reach the centre quest (${p.selected})`);
  for (const [key, want] of [["UP", "n"], ["DOWN", "c"], ["DOWN", "s"], ["UP", "c"], ["LEFT", "w"], ["RIGHT", "c"], ["RIGHT", "e"]]) {
    p = await press(key);
    check(p.selected === want, `${key} moves to ${want} (${p.selected})`);
  }
  p = await press("RIGHT");
  check(p.selected === "e", "nothing further right: the selection stays");
  await press("LEFT");

  // ---- Enter opens the card, Enter again focuses inside it ----
  p = await press("ENTER");
  check(p.expanded && p.selected === "c" && !p.cardFocus, "Enter opens the selected quest's card");
  p = await press("ENTER");
  check(p.cardFocus && p.cardTargets >= 3, `Enter again focuses inside the card (${p.cardTargets} parts)`);
  let seenDirt = false;
  for (let i = 0; i < 8 && !seenDirt; i++) {
    seenDirt = p.focusItem === "minecraft:dirt";
    if (!seenDirt) p = await press("DOWN");
  }
  check(seenDirt, `Down reaches the task's item (${p.focusItem})`);
  await shot("keys-02-card-focus");
  p = await press("R");
  check(p.lastLookup === "minecraft:dirt recipes", `R looks up its recipes (${p.lastLookup})`);
  p = await press("U");
  check(p.lastLookup === "minecraft:dirt uses", `U looks up its uses (${p.lastLookup})`);
  p = await press("ESCAPE");
  check(!p.cardFocus && p.expanded, "Esc leaves the card focus first");
  p = await press("ESCAPE");
  check(!p.expanded, "Esc again closes the card");

  // ---- claim, pin (rebound to O), read ----
  await server("give @p minecraft:dirt 4");
  await wait(60);
  const bread = async () => Number(/Found (\d+)/.exec((await server("clear @p minecraft:bread 0")).output?.join(" ") ?? "")?.[1] ?? 0);
  const breadBefore = await bread();
  await press("C", 0, 30);
  const breadAfter = await bread();
  check(breadAfter === breadBefore + 1, `C claims the selected quest (bread ${breadBefore} → ${breadAfter})`);
  await press("RIGHT");
  p = await press("P", 0, 20);
  check(p.pin !== "e", "P no longer pins once Pin is rebound to O");
  p = await press("O", 0, 20);
  check(p.pin === "e", `the rebound key (O) pins the quest (${p.pin})`);
  p = await press("E", 0, 10);
  check(p.modal === "READER", `E opens the reader (${p.modal})`);
  await shot("keys-03-reader");
  p = await press("ESCAPE");
  check(p.modal === "NONE", "Esc closes the reader");

  // ---- claim all, chapters, zoom, fit ----
  p = await press("C", SHIFT, 10);
  check(p.modal === "CLAIM_ALL", `Shift+C opens claim all (${p.modal})`);
  p = await press("ENTER", 0, 30);
  check(p.modal === "NONE", "Enter confirms claim all");
  p = await press("PAGE_DOWN", 0, 20);
  check(p.chapter === "qqe2e:keys2", `Page Down opens the next chapter (${p.chapter})`);
  p = await press("PAGE_UP", 0, 20);
  check(p.chapter === "qqe2e:keys", "Page Up goes back");
  const px0 = (await probe()).tilePxNow;
  p = await press("EQUAL");
  check(p.tilePxNow > px0, `= zooms in (${px0} → ${p.tilePxNow})`);
  p = await press("MINUS");
  p = await press("MINUS");
  check(p.tilePxNow < px0, `- zooms out (${p.tilePxNow})`);
  p = await press("F");
  check(p.tilePxNow > 0, "F fits the chapter");

  // ---- search, log, help ----
  const OURS = [KEY.ENTER, KEY.UP, KEY.DOWN, KEY.LEFT, KEY.RIGHT, KEY.C, KEY.O, KEY.E, KEY.R, KEY.U, KEY.L, KEY.F,
    KEY.PAGE_UP, KEY.PAGE_DOWN, KEY.EQUAL, KEY.MINUS, KEY.SLASH, KEY.F1, KEY.ESCAPE];
  let leakedEarly = ((await probe()).otherModKeys ?? []).filter((k) => OURS.includes(k));
  check(leakedEarly.length === 0, `board keys never reached the other mod (leaked: ${leakedEarly.join(", ") || "none"})`);
  p = await press("SLASH", 0, 8, "/");
  check(p.searchFocused, "/ focuses the search box");
  await click({ resetOtherMod: true });
  await click({ type: "dirt" });
  p = await probe();
  check(p.searchText === "dirt", `typing goes to search, without the slash (${JSON.stringify(p.searchText)})`);
  p = await press("C");
  check(p.searchText === "dirt" && p.modal === "NONE", "book keys do nothing while typing in search");
  check(!(p.otherModKeys ?? []).includes(KEY.C), "keys typed into the search box do not reach other mods");
  p = await press("ESCAPE");
  check(!p.searchFocused, "Esc leaves the search box");
  p = await press("L", 0, 20);
  check(p.logOpen, "L opens the log");
  p = await press("L", 0, 30);
  check(!p.logOpen, "L closes it");
  p = await press("F1");
  check(p.helpOpen, "F1 opens the key help");
  await shot("keys-04-help");
  p = await press("ENTER");
  check(!p.helpOpen, "any key closes it");
  await click({ hoverX: p.keysIconX, hoverY: 13 }, 6);
  await shot("keys-05-icon-tooltip");
  await click({ clearHover: true }, 4);
  check((p.keyHelp ?? []).some((line) => line.startsWith("Pin or unpin = ") && /O/.test(line)),
    `the key list shows the current binding (${(p.keyHelp ?? []).find((l) => l.startsWith("Pin"))})`);

  // ---- other mods never see Quest Queen's keys ----
  p = await probe();
  const leaked = (p.otherModKeys ?? []).filter((k) => OURS.includes(k));
  check(leaked.length === 0, `no Quest Queen key reached the other mod (leaked: ${leaked.join(", ") || "none"})`);
  await click({ resetOtherMod: true });
  p = await press("K");
  check((p.otherModKeys ?? []).includes(KEY.K), "a key Quest Queen does not use still reaches other mods");
  p = await press("P");
  check((p.otherModKeys ?? []).includes(KEY.P), "P, no longer bound, reaches other mods again");
  await click({ pretendViewer: false });
  console.log(`${checks - failures.length}/${checks} checks passed`);
} finally {
  if (existsSync(CLICK)) rmSync(CLICK);
}
process.exit(failures.length ? 1 : 0);
