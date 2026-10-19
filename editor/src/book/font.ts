// The book's text font. The quest book draws text with Minecraft's own font, which this editor may not ship.
// The player's game has it, though: the bridge serves minecraft:textures/font/ascii.png, and this module turns
// that bitmap sheet into an OpenType font in the browser, with Minecraft's bitmap-font spacing. Without a game (or
// with a pack that removes the sheet) the editor's bundled QuestQueen pixel font is used instead.
import opentype from "opentype.js";
import { bridgeBase } from "../bridge";

/** A bitmap font sheet: glyph cells in a grid, read left to right, top to bottom, from firstCode. */
export interface FontSheet {
  cols: number;
  rows: number;
  firstCode: number;
}

/** Minecraft's ascii.png: 16×16 cells, cell index = character code. */
export const ASCII_SHEET: FontSheet = { cols: 16, rows: 16, firstCode: 0 };
/** The editor's bundled public/font/questqueen.png: 16×6 cells from the space character. */
export const QQ_SHEET: FontSheet = { cols: 16, rows: 6, firstCode: 0x20 };

/** Alpha (0–255) of the sheet pixel at x, y. */
export type Alpha = (x: number, y: number) => number;

export interface GlyphMetrics {
  /** Ink width in book pixels (8 per cell). */
  width: number;
  /** Pen advance in book pixels: width + 1, as Minecraft spaces bitmap glyphs. */
  advance: number;
}

const SPACE = 0x20;
const SPACE_ADVANCE = 4;
const UNITS_PER_EM = 1024;
/** Font units per book pixel: an 8-pixel cell fills the em. */
const UNIT = UNITS_PER_EM / 8;
const ASCENT_PX = 7;

/** Printable ASCII codes held by a sheet. */
function codesOf(sheet: FontSheet): number[] {
  const codes: number[] = [];
  const last = sheet.firstCode + sheet.cols * sheet.rows - 1;
  for (let code = Math.max(SPACE, sheet.firstCode); code <= Math.min(0x7e, last); code++) codes.push(code);
  return codes;
}

/** Top-left of a code's cell, and the cell size, in sheet pixels. */
function cellOf(code: number, width: number, sheet: FontSheet) {
  const cell = width / sheet.cols;
  const index = code - sheet.firstCode;
  return { x: (index % sheet.cols) * cell, y: Math.floor(index / sheet.cols) * cell, cell };
}

/**
 * Minecraft's bitmap rule: a glyph is as wide as its rightmost opaque column, and advances one more. Cells of any
 * size (HD packs use 16 px) are scaled to 8 book pixels. Space always advances 4; empty cells are left out.
 */
export function glyphAdvances(alpha: Alpha, width: number, _height: number, sheet: FontSheet): Map<number, GlyphMetrics> {
  const out = new Map<number, GlyphMetrics>();
  for (const code of codesOf(sheet)) {
    if (code === SPACE) {
      out.set(code, { width: 0, advance: SPACE_ADVANCE });
      continue;
    }
    const { x, y, cell } = cellOf(code, width, sheet);
    let right = -1;
    for (let cx = cell - 1; cx >= 0 && right < 0; cx--) {
      for (let cy = 0; cy < cell; cy++) {
        if (alpha(x + cx, y + cy) > 0) {
          right = cx;
          break;
        }
      }
    }
    if (right < 0) continue;
    const ink = Math.ceil(((right + 1) * 8) / cell);
    out.set(code, { width: ink, advance: ink + 1 });
  }
  return out;
}

/** An OpenType font of the sheet: one square contour per run of opaque pixels, advances from glyphAdvances. */
export function buildBookFont(alpha: Alpha, width: number, height: number, sheet: FontSheet): ArrayBuffer {
  const metrics = glyphAdvances(alpha, width, height, sheet);
  const glyphs = [new opentype.Glyph({ name: ".notdef", unicode: 0, advanceWidth: SPACE_ADVANCE * UNIT, path: new opentype.Path() })];
  for (const [code, m] of metrics) {
    const { x, y, cell } = cellOf(code, width, sheet);
    const px = (8 / cell) * UNIT; // font units per sheet pixel
    const path = new opentype.Path();
    for (let cy = 0; cy < cell; cy++) {
      let run = -1;
      for (let cx = 0; cx <= cell; cx++) {
        const on = cx < cell && alpha(x + cx, y + cy) > 0;
        if (on && run < 0) run = cx;
        if (!on && run >= 0) {
          const left = run * px;
          const right = cx * px;
          const top = ASCENT_PX * UNIT - cy * px;
          const bottom = top - px;
          path.moveTo(left, bottom);
          path.lineTo(right, bottom);
          path.lineTo(right, top);
          path.lineTo(left, top);
          path.close();
          run = -1;
        }
      }
    }
    glyphs.push(new opentype.Glyph({ name: `u${code.toString(16)}`, unicode: code, advanceWidth: m.advance * UNIT, path }));
  }
  const font = new opentype.Font({
    familyName: "QQBook",
    styleName: "Regular",
    unitsPerEm: UNITS_PER_EM,
    ascender: ASCENT_PX * UNIT,
    descender: -UNIT,
    glyphs,
  });
  return font.toArrayBuffer();
}

export interface FontDeps {
  fetch: (url: string) => Promise<Response>;
  /** Decode PNG bytes to pixels. */
  decode: (png: Blob) => Promise<{ width: number; height: number; alpha: Alpha }>;
  /** Register the built font under the family "QQBook". */
  addFont: (buffer: ArrayBuffer) => Promise<void>;
}

async function decodeInBrowser(png: Blob) {
  const bitmap = await createImageBitmap(png);
  const canvas = document.createElement("canvas");
  canvas.width = bitmap.width;
  canvas.height = bitmap.height;
  const ctx = canvas.getContext("2d")!;
  ctx.drawImage(bitmap, 0, 0);
  const data = ctx.getImageData(0, 0, bitmap.width, bitmap.height).data;
  return { width: bitmap.width, height: bitmap.height, alpha: (x: number, y: number) => data[(y * bitmap.width + x) * 4 + 3] };
}

async function addFontInBrowser(buffer: ArrayBuffer) {
  const face = new FontFace("QQBook", buffer);
  await face.load();
  document.fonts.add(face);
}

const BROWSER: FontDeps = { fetch: (url) => fetch(url), decode: decodeInBrowser, addFont: addFontInBrowser };

/**
 * Build and register the book font: the player's Minecraft font when the game serves it, else the bundled one.
 * Resolves with the source used, or "none" when neither could be read (text then falls back to system fonts).
 */
export async function loadBookFont(deps: FontDeps = BROWSER): Promise<"minecraft" | "fallback" | "none"> {
  const sources: { url: string; sheet: FontSheet; name: "minecraft" | "fallback" }[] = [
    { url: `${bridgeBase()}/api/texture?id=${encodeURIComponent("minecraft:textures/font/ascii.png")}`, sheet: ASCII_SHEET, name: "minecraft" },
    { url: "/font/questqueen.png", sheet: QQ_SHEET, name: "fallback" },
  ];
  for (const source of sources) {
    try {
      const response = await deps.fetch(source.url);
      if (!response.ok) continue;
      const image = await deps.decode(await response.blob());
      await deps.addFont(buildBookFont(image.alpha, image.width, image.height, source.sheet));
      return source.name;
    } catch {
      // Try the next source.
    }
  }
  return "none";
}
