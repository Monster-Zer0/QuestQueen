// Quest description markup, mirrored from the game's RichText.java so the editor's preview shows what the book
// will draw. The two parsers are kept in step by editor/src/richtext.test.ts and src/test/resources/richtext-cases.json,
// which both sides read.
//
//   # Heading   ## Smaller heading   - bullet   ---   **bold**  *italic*  __underline__
//   {#RRGGBB}coloured{/#}   {size:12}bigger{/size}
//   {item:minecraft:diamond}   {item:minecraft:iron_ingot x3}   {glyph:sword}
//   ![caption](mypack:textures/quest/castle.png)       a picture alone on its line
//   \*  a backslash keeps the next markup character literal

import { GLYPH_IDS, glyphSvg } from "./glyphs";

export const BASE_SIZE = 9;
export const MIN_SIZE = 8;
export const MAX_SIZE = 24;
export const MAX_ITEM_COUNT = 9999;

export type Heading = "none" | "title" | "subtitle";

export type Inline =
  | { kind: "text"; text: string; color: number; size: number; bold: boolean; italic: boolean; underline: boolean }
  | { kind: "item"; id: string; count: number }
  | { kind: "glyph"; id: string; color: number };

export type Block =
  | { kind: "para"; heading: Heading; bullet: boolean; inlines: Inline[] }
  | { kind: "image"; texture: string; caption: string }
  | { kind: "rule" }
  | { kind: "gap" };

const ESCAPABLE = "\\*_{}#-![]()";

interface State {
  color: number;
  size: number;
  bold: boolean;
  italic: boolean;
  underline: boolean;
}

/** A resource location as Minecraft reads it: lowercase namespace and path, default namespace "minecraft". */
function parseLocation(raw: string): { namespace: string; path: string } | null {
  const match = /^(?:([a-z0-9_.-]+):)?([a-z0-9/._-]*)$/.exec(raw);
  return match ? { namespace: match[1] ?? "minecraft", path: match[2] } : null;
}

/** A picture path the book can load: a resource location under textures/, ending in .png. */
export function validTexture(raw: string | null | undefined): boolean {
  const loc = parseLocation((raw ?? "").trim());
  return !!loc && loc.path.startsWith("textures/") && loc.path.endsWith(".png");
}

/** An item id as the description tag takes it: a valid resource location with a path. */
export function validItemId(raw: string): boolean {
  const loc = parseLocation(raw);
  return !!loc && loc.path.length > 0;
}

function location(raw: string): string {
  const loc = parseLocation(raw)!;
  return `${loc.namespace}:${loc.path}`;
}

export function parseRich(source: string | null | undefined): Block[] {
  const blocks: Block[] = [];
  if (!source || !source.trim()) return blocks;
  let pendingGap = false;
  for (const raw of source.replace(/\r\n?/g, "\n").split("\n")) {
    if (!raw.trim()) {
      pendingGap = true;
      continue;
    }
    if (pendingGap && blocks.length) blocks.push({ kind: "gap" });
    pendingGap = false;
    blocks.push(parseLine(raw));
  }
  return blocks;
}

function parseLine(raw: string): Block {
  const line = raw.trim();
  if (line.length >= 3 && /^-+$/.test(line)) return { kind: "rule" };
  const image = parseImage(line);
  if (image) return image;
  let heading: Heading = "none";
  let bullet = false;
  let rest = raw.replace(/^\s+/, "");
  if (rest.startsWith("## ")) {
    heading = "subtitle";
    rest = rest.slice(3);
  } else if (rest.startsWith("# ")) {
    heading = "title";
    rest = rest.slice(2);
  } else if (rest.startsWith("- ")) {
    bullet = true;
    rest = rest.slice(2);
  } else if (rest.startsWith("• ")) {
    bullet = true;
    rest = rest.slice(2);
  } else {
    rest = raw;
  }
  const inlines: Inline[] = [];
  parseInline(rest, 0, rest.length, { color: 0, size: BASE_SIZE, bold: false, italic: false, underline: false }, inlines);
  return { kind: "para", heading, bullet, inlines };
}

function parseImage(line: string): Block | null {
  if (!line.startsWith("![")) return null;
  const captionEnd = line.indexOf("](");
  if (captionEnd < 0 || !line.endsWith(")")) return null;
  const path = line.slice(captionEnd + 2, line.length - 1).trim();
  if (!validTexture(path)) return null;
  return { kind: "image", texture: location(path), caption: line.slice(2, captionEnd).trim() };
}

function parseInline(s: string, start: number, end: number, st: State, out: Inline[]): void {
  let buf = "";
  const flush = () => {
    if (buf) {
      out.push({ kind: "text", text: buf, color: st.color, size: st.size, bold: st.bold, italic: st.italic, underline: st.underline });
      buf = "";
    }
  };
  let i = start;
  while (i < end) {
    const c = s[i];
    if (c === "\\" && i + 1 < end && ESCAPABLE.includes(s[i + 1])) {
      buf += s[i + 1];
      i += 2;
      continue;
    }
    if (c === "{") {
      const next = braceTag(s, i, end, st, flush, out);
      if (next > i) {
        i = next;
        continue;
      }
    }
    if (c === "*" && s.startsWith("**", i)) {
      const close = indexOf(s, "**", i + 2, end);
      if (close > i + 2) {
        flush();
        parseInline(s, i + 2, close, { ...st, bold: true }, out);
        i = close + 2;
        continue;
      }
    }
    if (c === "_" && s.startsWith("__", i)) {
      const close = indexOf(s, "__", i + 2, end);
      if (close > i + 2) {
        flush();
        parseInline(s, i + 2, close, { ...st, underline: true }, out);
        i = close + 2;
        continue;
      }
    }
    if (c === "*" && !s.startsWith("**", i) && i + 1 < end && !/\s/.test(s[i + 1])) {
      const close = singleStar(s, i + 1, end);
      if (close > i + 1) {
        flush();
        parseInline(s, i + 1, close, { ...st, italic: true }, out);
        i = close + 1;
        continue;
      }
    }
    buf += c;
    i++;
  }
  flush();
}

/** A {...} tag at i; returns the index after it, or i when it is not valid markup. */
function braceTag(s: string, i: number, end: number, st: State, flush: () => void, out: Inline[]): number {
  if (s.startsWith("{#", i) && i + 9 <= end && s[i + 8] === "}" && /^[0-9a-fA-F]{6}$/.test(s.slice(i + 2, i + 8))) {
    const close = indexOf(s, "{/#}", i + 9, end);
    if (close >= 0) {
      flush();
      const color = (0xff000000 | parseInt(s.slice(i + 2, i + 8), 16)) >>> 0;
      parseInline(s, i + 9, close, { ...st, color }, out);
      return close + 4;
    }
    return i;
  }
  if (s.startsWith("{size:", i)) {
    const spec = s.indexOf("}", i + 6);
    const close = spec < 0 ? -1 : indexOf(s, "{/size}", spec + 1, end);
    if (spec > 0 && spec < end && close >= 0) {
      const size = parseSmallInt(s.slice(i + 6, spec));
      if (size !== null) {
        flush();
        parseInline(s, spec + 1, close, { ...st, size: Math.max(MIN_SIZE, Math.min(MAX_SIZE, size)) }, out);
        return close + 7;
      }
    }
    return i;
  }
  if (s.startsWith("{item:", i)) {
    const close = s.indexOf("}", i + 6);
    if (close > 0 && close < end) {
      const item = parseItem(s.slice(i + 6, close));
      if (item) {
        flush();
        out.push(item);
        return close + 1;
      }
    }
    return i;
  }
  if (s.startsWith("{glyph:", i)) {
    const close = s.indexOf("}", i + 7);
    if (close > 0 && close < end) {
      const id = s.slice(i + 7, close).trim().toLowerCase();
      if ((GLYPH_IDS as readonly string[]).includes(id)) {
        flush();
        out.push({ kind: "glyph", id, color: st.color });
        return close + 1;
      }
    }
  }
  return i;
}

function parseItem(spec: string): Inline | null {
  const parts = spec.trim().split(/\s+/);
  if (parts.length === 0 || parts.length > 2) return null;
  if (!validItemId(parts[0])) return null;
  let count = 1;
  if (parts.length === 2) {
    const n = /^[xX](\d{1,9})$/.exec(parts[1]);
    if (!n) return null;
    count = Math.max(1, Math.min(MAX_ITEM_COUNT, parseInt(n[1], 10)));
  }
  return { kind: "item", id: location(parts[0]), count };
}

function parseSmallInt(raw: string): number | null {
  const n = raw.trim();
  return /^[+-]?\d{1,9}$/.test(n) ? parseInt(n, 10) : null;
}

function indexOf(s: string, token: string, from: number, end: number): number {
  const at = s.indexOf(token, from);
  return at >= 0 && at + token.length <= end ? at : -1;
}

/** Next lone * that follows a non-space, so "5 * 3 * 2" is not italic. */
function singleStar(s: string, from: number, end: number): number {
  for (let i = from; i < end; i++) {
    if (s[i] === "*" && !s.startsWith("**", i) && s[i - 1] !== "*" && !/\s/.test(s[i - 1])) return i;
  }
  return -1;
}

/** The words a player can read, markup removed. Pictures contribute their caption. */
export function plainText(source: string | null | undefined): string {
  const lines: string[] = [];
  for (const block of parseRich(source)) {
    let line = "";
    if (block.kind === "para") {
      line = block.inlines.map((inline) => (inline.kind === "text" ? inline.text : "")).join("");
    } else if (block.kind === "image") {
      line = block.caption;
    }
    if (line.trim()) lines.push(line);
  }
  return lines.join("\n");
}

/** Problems an author should fix: markup that looks meant but would show up as literal text. */
export function descriptionProblems(source: string | null | undefined): string[] {
  const problems: string[] = [];
  for (const raw of (source ?? "").replace(/\r\n?/g, "\n").split("\n")) {
    const line = raw.trim();
    if (line.startsWith("![") && line.includes("](") && line.endsWith(")")) {
      const path = line.slice(line.indexOf("](") + 2, -1).trim();
      if (!validTexture(path)) {
        problems.push(`picture "${path}" is not a texture path: use namespace:textures/name.png`);
      }
    }
  }
  for (const m of (source ?? "").matchAll(/(?<!\\)\{item:([^}]*)\}/g)) {
    if (!parseItem(m[1])) problems.push(`{item:${m[1]}} is not an item: use {item:minecraft:diamond} or {item:minecraft:diamond x3}`);
  }
  for (const m of (source ?? "").matchAll(/(?<!\\)\{glyph:([^}]*)\}/g)) {
    if (!(GLYPH_IDS as readonly string[]).includes(m[1].trim().toLowerCase())) {
      problems.push(`{glyph:${m[1]}} is not a known glyph`);
    }
  }
  return problems;
}

// ---- HTML preview ------------------------------------------------------------------------------------------

export interface PreviewOptions {
  /** URL of an item's icon, as the bridge serves it. */
  iconUrl?: (id: string) => string;
}

function esc(text: string): string {
  return text.replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;").replace(/"/g, "&quot;");
}

function cssColor(color: number): string {
  return `#${(color & 0xffffff).toString(16).padStart(6, "0")}`;
}

function inlineHtml(inline: Inline, heading: Heading, options: PreviewOptions): string {
  if (inline.kind === "text") {
    const scale = heading === "title" ? 1.5 : heading === "subtitle" ? 1.2 : 1;
    const size = Math.max(MIN_SIZE, Math.min(MAX_SIZE, Math.round(inline.size * scale)));
    const style: string[] = [];
    if (inline.color) style.push(`color:${cssColor(inline.color)}`);
    if (size !== BASE_SIZE) style.push(`font-size:${size / BASE_SIZE}em`);
    if (inline.bold || heading !== "none") style.push("font-weight:700");
    if (inline.italic) style.push("font-style:italic");
    if (inline.underline) style.push("text-decoration:underline");
    return style.length ? `<span style="${style.join(";")}">${esc(inline.text)}</span>` : esc(inline.text);
  }
  if (inline.kind === "item") {
    const src = options.iconUrl ? options.iconUrl(inline.id) : "";
    const img = src ? `<img class="desc-item" alt="${esc(inline.id)}" title="${esc(inline.id)}" src="${esc(src)}" width="14" height="14" />`
      : `<span class="desc-item desc-item-missing" title="${esc(inline.id)}"></span>`;
    return img + (inline.count > 1 ? `<span class="desc-count">x${inline.count}</span>` : "");
  }
  return `<span class="desc-glyph" title="${esc(inline.id)}">${glyphSvg(inline.id, inline.color ? cssColor(inline.color) : "#c8bad8")}</span>`;
}

/** The description the way the card draws it, as HTML for the inspector's preview. */
export function richToHtml(source: string | null | undefined, options: PreviewOptions = {}): string {
  const html: string[] = [];
  for (const block of parseRich(source)) {
    if (block.kind === "gap") {
      html.push('<div class="desc-gap"></div>');
    } else if (block.kind === "rule") {
      html.push("<hr />");
    } else if (block.kind === "image") {
      html.push(`<figure class="desc-pic"><div class="desc-pic-frame" title="${esc(block.texture)}">${esc(block.texture)}</div>`
        + (block.caption ? `<figcaption>${esc(block.caption)}</figcaption>` : "") + "</figure>");
    } else {
      const inner = block.inlines.map((inline) => inlineHtml(inline, block.heading, options)).join("");
      const cls = ["desc-line", block.heading !== "none" ? `desc-${block.heading}` : "", block.bullet ? "desc-bullet" : ""]
        .filter(Boolean).join(" ");
      html.push(`<div class="${cls}">${inner || "&nbsp;"}</div>`);
    }
  }
  return html.join("");
}

/** The shape both parsers must agree on (see richtext-cases.json). */
export function toShape(blocks: Block[]): unknown[] {
  return blocks.map((block) => {
    if (block.kind === "gap") return "gap";
    if (block.kind === "rule") return "rule";
    if (block.kind === "image") return { img: block.texture, cap: block.caption };
    return {
      h: block.heading,
      bullet: block.bullet,
      in: block.inlines.map((inline) => {
        if (inline.kind === "text") {
          return { t: inline.text, c: inline.color, s: inline.size, b: inline.bold, i: inline.italic, u: inline.underline };
        }
        if (inline.kind === "item") return { item: inline.id, n: inline.count };
        return { glyph: inline.id, c: inline.color };
      }),
    };
  });
}
