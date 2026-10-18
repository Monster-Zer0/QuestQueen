#!/usr/bin/env node
// Stages made in the web editor, end to end: the bridge saves a stage, the server writes StageLock's files into
// the world's questqueen_authored pack and reloads on its own, StageLock enforces the lock until the stage is
// granted, and deleting the stage removes its files.
//
//   1. StageLock (0.1.1+) in run/client/mods, then
//      ./gradlew runClient -Pqq.e2e -Pqq.marionettePort=25586 -Pneo_version=21.1.256   (a port no other game uses)
//   2. MARIONETTE_PORT=25586 node e2e/stages.mjs
//
// It makes its own scratch world (qq-e2e-stages). The locked item is a spyglass because StageLock's example pack,
// when enabled, already rules on diamond gear (with hold: allow), and the first matching rule wins.
// Exit code 1 if any check fails.

import { existsSync, readdirSync, readFileSync, statSync } from "node:fs";
import { join } from "node:path";

const GAME = `http://127.0.0.1:${process.env.MARIONETTE_PORT ?? 25585}`;
const EDITOR = "http://127.0.0.1:47821";
const WORLD = "qq-e2e-stages";
const PACK = join("run/client/saves", WORLD, "datapacks/questqueen_authored");
const ID = "qq_e2e_iron";
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
function check(ok, what) {
  checks++;
  console.log(`${ok ? "PASS" : "FAIL"}  ${what}`);
  if (!ok) failures.push(what);
}
async function inventoryCount(id) {
  const res = await call("GET", "/inventory");
  return [...(res.main ?? []), ...(res.hotbar ?? [])].filter((slot) => slot.id === id).reduce((n, slot) => n + slot.count, 0);
}
async function editorPost(path, body) {
  const session = await (await fetch(`${EDITOR}/api/session`)).json();
  const res = await fetch(`${EDITOR}${path}`, {
    method: "POST",
    headers: { "Content-Type": "application/json", "X-Editor-Nonce": session.nonce },
    body: JSON.stringify(body),
  });
  return res.status;
}
const stages = async () => (await fetch(`${EDITOR}/api/stages`)).json();
async function until(test, ticks = 200) {
  for (let waited = 0; waited < ticks; waited += 10) {
    if (await test()) return true;
    await wait(10);
  }
  return false;
}
/** Every file under a folder, relative to it. */
function files(dir, prefix = "") {
  if (!existsSync(dir)) return [];
  return readdirSync(dir).flatMap((name) => {
    const path = join(dir, name);
    return statSync(path).isDirectory() ? files(path, `${prefix}${name}/`) : [`${prefix}${name}`];
  });
}

const stage = {
  id: ID,
  name: "E2E Iron",
  icon: "minecraft:iron_ingot",
  locks: [
    { kind: "item", targets: ["minecraft:spyglass"] },
    { kind: "dimension", targets: ["minecraft:the_nether"], message: "Not yet." },
  ],
};

await call("POST", "/world/create", { name: WORLD, gamemode: "creative", delete_existing: true });
await wait(100);
await chat("questqueen editor");
await wait(20);

const before = await stages();
check(before.stageLock === true, `the bridge reports StageLock (${JSON.stringify(before.stageLock)})`);

// ---- save: files written, reloaded without a /reload, StageLock knows the stage ----
check((await editorPost("/api/stage", stage)) === 202, "the bridge accepts the stage");
check(await until(async () => (await stages()).authored?.some((s) => s.id === ID)), "the editor sees it as made here");
check(await until(async () => (await stages()).defined?.some((s) => s.id === ID && s.name === "E2E Iron")),
  "StageLock defines it after the automatic reload");
const written = files(PACK);
const def = written.find((f) => f.endsWith(`stagelock/stages/${ID}.json`));
const rules = written.find((f) => f.endsWith(`stagelock/restrictions/questqueen/${ID}.json`));
check(!!def && !!rules && written.some((f) => f.endsWith(`questqueen/stages/${ID}.json`)),
  `three files in the authored pack (${written.join(", ")})`);
if (rules) {
  const types = JSON.parse(readFileSync(join(PACK, rules), "utf8")).rules.map((r) => r.type);
  check(JSON.stringify(types) === JSON.stringify(["stagelock:item", "stagelock:dimension"]), `rule types ${types}`);
}

// ---- enforcement: with the stage the spyglass stays; taking the stage away takes the spyglass ----
// (StageLock rescans an inventory when its change counter, the stages or the rules change. /give does not bump the
// counter, so the order here is grant, give, revoke: the revoke is what makes StageLock look.)
await server("gamemode survival @a");
await server("clear @a");
await server(`stagelock grant @a ${ID}`);
await wait(10);
await server("give @a minecraft:spyglass");
await wait(20);
check((await inventoryCount("minecraft:spyglass")) === 1, "with the stage the spyglass stays");
await server(`stagelock revoke @a ${ID}`);
await wait(20);
check((await inventoryCount("minecraft:spyglass")) === 0, "without the stage the spyglass is taken away");
await shot("stages-locked");
await server("kill @e[type=item]");

// ---- edit: dropping the item lock leaves only the dimension rule ----
check((await editorPost("/api/stage", { ...stage, locks: [stage.locks[1]] })) === 202, "the bridge accepts the edit");
check(await until(async () => {
  const path = rules && join(PACK, rules);
  return !!path && existsSync(path) && JSON.parse(readFileSync(path, "utf8")).rules.length === 1;
}), "the rule file now holds one rule");

// ---- a bad id never reaches the server ----
const refused = await editorPost("/api/stage", { id: "not a stage!" });
check(refused === 400, `an invalid id is refused by the bridge (${refused})`);

// ---- delete: files gone, StageLock forgets it after the reload ----
check((await editorPost("/api/stage/delete", { id: ID })) === 202, "the bridge accepts the delete");
check(await until(async () => !(await stages()).authored?.some((s) => s.id === ID)), "the editor no longer lists it as made here");
check(await until(async () => !(await stages()).defined?.some((s) => s.id === ID)), "StageLock forgets it after the reload");
check(!files(PACK).some((f) => f.includes(ID)), "its files are gone");

console.log(`${checks - failures.length}/${checks} checks passed`);
process.exit(failures.length ? 1 : 0);
