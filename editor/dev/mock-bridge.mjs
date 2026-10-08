// A stand-in for the in-game editor bridge (client/EditorBridge.java), so the editor can be driven in a
// browser without Minecraft. It answers the same routes with the same gates: loopback Origin only, and the
// session nonce on every write. Saves land in editor/dev/out/ and in GET /__saves for tests to read.
//
//   node editor/dev/mock-bridge.mjs            (port 47821, like the game)
//   MOCK_BRIDGE_PORT=47900 node ...           (another port)
//   MOCK_CHAPTERS=dir1:dir2 node ...          (load chapters from other folders)
//
// POST /__reset clears the save log and reloads the chapters.

import { createServer } from "node:http";
import { randomBytes } from "node:crypto";
import { mkdirSync, readdirSync, readFileSync, writeFileSync, existsSync } from "node:fs";
import { dirname, join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const here = dirname(fileURLToPath(import.meta.url));
const repo = resolve(here, "..", "..");
const PORT = Number(process.env.MOCK_BRIDGE_PORT ?? 47821);
const OUT = join(here, "out");
const CHAPTER_DIRS = (process.env.MOCK_CHAPTERS
  ? process.env.MOCK_CHAPTERS.split(":")
  : [
      join(repo, "src/main/resources/data/questqueen/questqueen/chapters"),
      join(repo, "src/dev/resources/data/questqueen/questqueen/chapters"),
      // Probe chapters that exercise fields the showcase does not: arrow conditions, a stage reward, etc.
      join(here, "fixtures"),
    ]).filter((dir) => existsSync(dir));
// The real bridge caps a chapter at AuthorSaveC2S.MAX_JSON_BYTES; keep the same order of magnitude.
const MAX_CHAPTER_BYTES = 1024 * 1024;

const nonce = randomBytes(32).toString("hex");
let chapters = loadChapters();
let saves = [];

function loadChapters() {
  const list = [];
  for (const dir of CHAPTER_DIRS) {
    for (const name of readdirSync(dir).filter((file) => file.endsWith(".json")).sort()) {
      list.push(JSON.parse(readFileSync(join(dir, name), "utf8")));
    }
  }
  return list;
}

const ids = (list) => list.map((id) => ({ id, name: id }));
const CATALOG = {
  items: ids([
    "minecraft:dirt", "minecraft:oak_log", "minecraft:chest", "minecraft:bread", "minecraft:apple",
    "minecraft:diamond", "minecraft:emerald", "minecraft:iron_ingot", "minecraft:paper", "minecraft:book",
    "minecraft:rotten_flesh", "minecraft:obsidian", "minecraft:stone", "minecraft:crafting_table",
  ]),
  entities: ids(["minecraft:zombie", "minecraft:chicken", "minecraft:villager", "minecraft:skeleton"]),
  blocks: ids(["minecraft:stone", "minecraft:crafting_table", "minecraft:furnace"]),
  tags: ids(["minecraft:logs", "minecraft:planks"]),
  advancements: ids(["minecraft:story/root", "minecraft:story/mine_stone"]),
  biomes: ids(["minecraft:plains", "minecraft:desert"]),
  structures: ids(["minecraft:village_plains"]),
  loot: ids(["minecraft:chests/simple_dungeon"]),
  fluids: ids(["minecraft:water", "minecraft:lava"]),
  dimensions: ids(["minecraft:overworld", "minecraft:the_nether"]),
  stats: ids(["minecraft:walk_one_cm"]),
  // As the game sends Progression's defined stages: id plus display name.
  stages: [
    { id: "stone_age", name: "Stone Age" },
    { id: "iron_age", name: "Iron Age" },
    { id: "mypack:nether", name: "The Nether" },
  ],
};

// 1x1 transparent PNG: the editor only needs an image to load.
const PIXEL = Buffer.from(
  "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNkYPhfDwAChwGA60e6kgAAAABJRU5ErkJggg==",
  "base64",
);

// Same literal-host rule as EditorBridge.originAllowed: absent Origin is a non-browser client.
function originAllowed(origin) {
  if (!origin) return true;
  try {
    const url = new URL(origin);
    if (url.protocol !== "http:" && url.protocol !== "https:") return false;
    return ["localhost", "127.0.0.1", "[::1]", "::1"].includes(url.hostname);
  } catch {
    return false;
  }
}

function send(req, res, status, type, body) {
  const origin = req.headers.origin;
  if (origin && originAllowed(origin)) res.setHeader("Access-Control-Allow-Origin", origin);
  res.setHeader("Vary", "Origin");
  res.setHeader("X-Content-Type-Options", "nosniff");
  res.setHeader("Content-Type", type);
  res.writeHead(status);
  res.end(body);
}

const json = (req, res, status, value) => send(req, res, status, "application/json", JSON.stringify(value));

function readBody(req) {
  return new Promise((done, fail) => {
    const parts = [];
    let size = 0;
    req.on("data", (chunk) => {
      size += chunk.length;
      if (size <= MAX_CHAPTER_BYTES + 1) parts.push(chunk);
    });
    req.on("end", () => done({ text: Buffer.concat(parts).toString("utf8"), size }));
    req.on("error", fail);
  });
}

// The parts of Chapter.CODEC a save most often trips: a namespaced id, tiles with ids and positions,
// links that name tiles. The game is the real judge; this only catches what the editor sends badly.
function codecErrors(chapter) {
  const errors = [];
  if (typeof chapter?.id !== "string" || !/^[a-z0-9_.-]+:[a-z0-9_./-]+$/.test(chapter.id)) {
    errors.push(`bad chapter id: ${JSON.stringify(chapter?.id)}`);
  }
  if (!Array.isArray(chapter?.tiles)) errors.push("tiles must be a list");
  for (const tile of chapter?.tiles ?? []) {
    if (typeof tile.id !== "string") errors.push("tile without id");
    if (typeof tile.pos?.x !== "number" || typeof tile.pos?.y !== "number") errors.push(`tile ${tile.id} has no pos`);
  }
  return errors;
}

const server = createServer(async (req, res) => {
  const url = new URL(req.url ?? "/", `http://127.0.0.1:${PORT}`);
  const path = url.pathname;
  if (!originAllowed(req.headers.origin)) return send(req, res, 403, "text/plain", "origin not allowed");
  const mutating = ["POST", "PUT", "PATCH", "DELETE"].includes(req.method ?? "");
  const testRoute = path.startsWith("/__");
  if (mutating && !testRoute && req.headers["x-editor-nonce"] !== nonce) {
    return json(req, res, 403, { ok: false, error: "editor session nonce required" });
  }
  if (req.method === "OPTIONS") {
    if (req.headers.origin) res.setHeader("Access-Control-Allow-Origin", req.headers.origin);
    res.setHeader("Vary", "Origin");
    res.setHeader("Access-Control-Allow-Methods", "GET,POST,OPTIONS");
    res.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Editor-Nonce");
    res.writeHead(204);
    return res.end();
  }
  if (path === "/api/status") return json(req, res, 200, { enabled: true, items: CATALOG.items.length });
  if (path === "/api/session") return json(req, res, 200, { nonce });
  if (path === "/api/catalog") return json(req, res, 200, CATALOG);
  if (path.startsWith("/api/icon")) return send(req, res, 200, "image/png", PIXEL);
  if (path === "/api/chapters" && req.method === "GET") return json(req, res, 200, { chapters, scrolls: [] });
  if (path === "/api/chapter" && req.method === "POST") {
    const { text, size } = await readBody(req);
    if (size > MAX_CHAPTER_BYTES) return json(req, res, 413, { ok: false, error: "chapter too large" });
    let chapter;
    try {
      chapter = JSON.parse(text);
    } catch (error) {
      return json(req, res, 400, { ok: false, error: `not JSON: ${error.message}` });
    }
    const errors = codecErrors(chapter);
    if (errors.length) return json(req, res, 400, { ok: false, error: errors.join("; ") });
    mkdirSync(OUT, { recursive: true });
    const file = join(OUT, `${chapter.id.replace(/[:/]/g, "_")}.json`);
    writeFileSync(file, JSON.stringify(chapter, null, 2));
    saves.push({ at: new Date().toISOString(), id: chapter.id, chapter });
    const index = chapters.findIndex((entry) => entry.id === chapter.id);
    if (index >= 0) chapters[index] = chapter;
    else chapters.push(chapter);
    return json(req, res, 202, { ok: true, queued: true, id: chapter.id });
  }
  if (path === "/__saves") return json(req, res, 200, saves);
  if (path === "/__reset" && req.method === "POST") {
    saves = [];
    chapters = loadChapters();
    return json(req, res, 200, { ok: true, chapters: chapters.length });
  }
  return send(req, res, 404, "text/plain", "not found (the mock bridge serves the API only; run the editor with npm run dev)");
});

server.listen(PORT, "127.0.0.1", () => {
  console.log(`mock bridge on http://127.0.0.1:${PORT} with ${chapters.length} chapters from ${CHAPTER_DIRS.length} folder(s)`);
});
