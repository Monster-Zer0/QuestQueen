// How a command reward looks to players: its own icon, name and description, else the item a `give` hands out,
// else "Bonus reward". Mirrors CommandReward / GiveCommand / RewardFaces in the mod; the command is never shown.

import { GLYPH_IDS, glyphSvg } from "./glyphs";
import { validItemId, validTexture } from "./richtext";
import type { Reward } from "./types";

export const FALLBACK_NAME = "Bonus reward";
export const FALLBACK_GLYPH = "star";

export interface Give { spec: string; item: string; count: number; }

export type IconKind =
  | { kind: "none" }
  | { kind: "glyph"; id: string }
  | { kind: "texture"; path: string }
  | { kind: "item"; spec: string; item: string };

/** Space-separated token where spaces inside [...], {...} or quotes do not split it. */
function tokens(text: string): string[] {
  const out: string[] = [];
  let i = 0;
  while (i < text.length) {
    while (i < text.length && /\s/.test(text[i])) i++;
    if (i >= text.length) break;
    const start = i;
    let depth = 0;
    let quote = "";
    while (i < text.length) {
      const c = text[i];
      if (quote) {
        if (c === "\\" && i + 1 < text.length) { i += 2; continue; }
        if (c === quote) quote = "";
      } else if (c === '"' || c === "'") quote = c;
      else if (c === "[" || c === "{") depth++;
      else if (c === "]" || c === "}") depth = Math.max(0, depth - 1);
      else if (/\s/.test(c) && depth === 0) break;
      i++;
    }
    out.push(text.slice(start, i));
  }
  return out;
}

/** `give <target> <item>[components] [count]`, or null for anything else. */
export function parseGive(command: string | undefined): Give | null {
  let text = (command ?? "").trim();
  if (text.startsWith("/")) text = text.slice(1);
  const parts = tokens(text);
  if (parts.length < 3 || parts.length > 4 || parts[0].toLowerCase() !== "give") return null;
  const spec = parts[2];
  const bracket = spec.indexOf("[");
  const item = bracket < 0 ? spec : spec.slice(0, bracket);
  if (!item || !validItemId(item)) return null;
  let count = 1;
  if (parts.length === 4) {
    if (!/^\d+$/.test(parts[3])) return null;
    count = Number(parts[3]);
    if (count < 1) return null;
  }
  return { spec, item: item.includes(":") ? item : `minecraft:${item}`, count };
}

export function iconKind(icon: string | undefined): IconKind {
  const raw = (icon ?? "").trim();
  if (!raw) return { kind: "none" };
  if (raw.toLowerCase().startsWith("glyph:")) return { kind: "glyph", id: raw.slice(6).trim().toLowerCase() };
  if (raw.endsWith(".png")) return { kind: "texture", path: raw };
  const bracket = raw.indexOf("[");
  const item = bracket < 0 ? raw : raw.slice(0, bracket);
  return { kind: "item", spec: raw, item: item.includes(":") ? item : `minecraft:${item}` };
}

/** Problems that would stop the book showing the chosen icon. */
export function commandRewardProblems(reward: Reward): string[] {
  const icon = iconKind(reward.icon);
  switch (icon.kind) {
    case "glyph":
      return (GLYPH_IDS as readonly string[]).includes(icon.id) ? [] : [`icon "glyph:${icon.id}" is not a known glyph (${GLYPH_IDS.join(", ")})`];
    case "texture":
      return validTexture(icon.path) ? [] : [`icon "${icon.path}" is not a picture path (namespace:textures/….png)`];
    case "item": {
      const problems = validItemId(icon.item) ? [] : [`icon "${icon.item}" is not an item id`];
      const spec = icon.spec;
      if (spec.includes("[") && !spec.endsWith("]")) problems.push(`icon "${spec}" has unclosed [components]`);
      return problems;
    }
    default:
      return [];
  }
}

/** The name players see. */
export function rewardName(reward: Reward): string {
  if (reward.name?.trim()) return reward.name.trim();
  const give = parseGive(reward.command);
  if (give) return give.item.split(":")[1].replaceAll("_", " ");
  return FALLBACK_NAME;
}

/** The count drawn on the icon (from the give command). */
export function rewardCount(reward: Reward): number {
  return parseGive(reward.command)?.count ?? 1;
}

const escape = (text: string) => text.replace(/[&<>"']/g, (c) => `&#${c.charCodeAt(0)};`);

/** A small mock of the book's hover: icon, name, description lines. */
export function commandPreviewHtml(reward: Reward, iconUrl: (id: string) => string): string {
  const icon = iconKind(reward.icon);
  const give = parseGive(reward.command);
  let face: string;
  if (icon.kind === "glyph") face = glyphSvg(icon.id, "#fff540");
  else if (icon.kind === "texture") face = `<span class="reward-face-frame" title="${escape(icon.path)}">PNG</span>`;
  else if (icon.kind === "item") face = `<img alt="" width="16" height="16" src="${escape(iconUrl(icon.item))}" />`;
  else if (give) face = `<img alt="" width="16" height="16" src="${escape(iconUrl(give.item))}" />`;
  else face = glyphSvg(FALLBACK_GLYPH, "#fff540");
  const count = rewardCount(reward);
  const title = `${count > 1 ? `${count}x ` : ""}${rewardName(reward)}`;
  const own = !!(reward.name?.trim() || reward.description?.trim());
  const lines = (reward.description ?? "").split("\n").map((line) => line.trim()).filter(Boolean)
    .map((line) => `<div class="reward-preview-line">${escape(line)}</div>`).join("");
  const note = !own && give ? `<div class="reward-preview-note">In game the hover is the item's own tooltip.</div>` : "";
  return `<div class="reward-preview-face">${face}</div>
    <div class="reward-preview-text"><div class="reward-preview-title">${escape(title)}</div>${lines}${note}</div>`;
}
