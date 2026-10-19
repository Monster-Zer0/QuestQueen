// The ledger tile's layout, ported 1:1 from QuestTileWidget (the statics and drawDecor's arithmetic). All values are
// book pixels for a tile `size` px square. ledger-tile-cases.json pins this file and the Java to the same numbers
// (LedgerTileParityTest, ledgerTile.test.ts); change the Java first, then the cases, then this.

/** Caption scale: tile text is half-size Minecraft text. */
export const TINY_SCALE = 0.5;
/** Line pitch of tile captions. */
export const LINE_STEP = 6;
/** At most this many reward faces at the bottom right. */
export const REWARD_FACE_CAP = 3;
export const TRUNCATION_MARKER = "...";
/** Caption ink (QuestTileWidget.CAPTION_INK) and the REWARDS label ink. */
export const CAPTION_INK = "#F5E6C8";
export const REWARDS_INK = "#E8D4A8";

export const railPx = (size: number) => (size >= 40 ? 3 : 2);
export const padPx = (size: number) => (size >= 56 ? 4 : 2);
export const contentInset = (size: number) => Math.max(padPx(size), railPx(size) + 2);
export const compact = (size: number) => size < 48;
/** The state word overruns a compact tile; there the rail colour alone carries the state. */
export const showsHeader = (size: number) => !compact(size);
// Java's Math.round on a float: half up.
export const iconPx = (size: number) => Math.max(8, Math.min(16, Math.floor((16 * size) / 64 + 0.5))) & ~1;
export const stacked = (size: number, hasIcon: boolean) => hasIcon && size < 64;
export const titleLines = (size: number) => (compact(size) ? 1 : 2);

export function titleBudget(size: number, hasIcon: boolean): number {
  const icon = hasIcon && !stacked(size, hasIcon) ? iconPx(size) + 2 : 0;
  return Math.max(20, (size - padPx(size) - contentInset(size) - icon) * 2);
}

export const rewardPx = (size: number) => Math.max(6, iconPx(size) > 12 ? 10 : 8);
/** Top of the reward icon row; the REWARDS caption sits LINE_STEP above it. */
export const rewardRowY = (y: number, size: number) => y + size - padPx(size) - rewardPx(size);

/** Left edge of the reward strip at the bottom right (x + size when there are no faces). */
export function rewardStripLeft(x: number, size: number, faces: number): number {
  if (faces <= 0) return x + size;
  const show = Math.min(faces, REWARD_FACE_CAP);
  const gap = show > 1 ? 1 : 0;
  return x + size - padPx(size) - (show * rewardPx(size) + Math.max(0, show - 1) * gap);
}

export interface Point { x: number; y: number }

export interface TileLayout {
  rail: number;
  header: Point;
  icon: Point & { size: number };
  title: Point & { maxW: number; lines: number };
  rewardRowY: number;
}

/** Where drawDecor puts things on a tile, with titleBeside = hasIcon as QuestBookScreen passes it. */
export function layout(size: number, hasIcon: boolean): TileLayout {
  const inset = contentInset(size);
  const icon = iconPx(size);
  const isStacked = stacked(size, hasIcon);
  const iconY = Math.max(8, size >= 56 ? 12 : 10);
  const titleX = hasIcon && !isStacked ? inset + icon + 2 : inset;
  const titleY = hasIcon && isStacked ? iconY + icon + 1
    : compact(size) ? iconY + Math.max(0, Math.floor((icon - 6) / 2))
    : hasIcon && !isStacked ? 16 : 20;
  return {
    rail: railPx(size),
    header: { x: inset, y: 2 },
    icon: { x: inset, y: iconY, size: icon },
    title: { x: titleX, y: titleY, maxW: Math.max(8, size - padPx(size) - titleX), lines: titleLines(size) },
    rewardRowY: rewardRowY(0, size),
  };
}

/**
 * QuestTileWidget.wrapTiny: upper-case words wrapped to `visualMax` book px at half scale, at most maxLines lines,
 * the overflow joined onto the last line and cut with "...". widthOf measures full-scale text.
 */
export function wrapTiny(widthOf: (text: string) => number, text: string, visualMax: number, maxLines: number): string[] {
  const upper = (text ?? "").toUpperCase().trim();
  if (!upper) return [];
  const maxW = Math.max(8, Math.round(Math.max(8, visualMax) / TINY_SCALE));
  const keep = Math.max(1, maxLines);
  let lines: string[] = [];
  let line = "";
  for (const word of upper.split(/ +/)) {
    if (!word) continue;
    const next = line ? `${line} ${word}` : word;
    if (widthOf(next) > maxW && line) {
      lines.push(line);
      line = word;
    } else {
      line = next;
    }
  }
  if (line) lines.push(line);
  if (lines.length > keep) {
    const last = [lines[keep - 1], ...lines.slice(keep)].join(" ");
    lines = [...lines.slice(0, keep - 1), last];
  }
  const last = lines[lines.length - 1];
  if (last !== undefined && widthOf(last) > maxW) {
    const markerW = widthOf(TRUNCATION_MARKER);
    let cut = last;
    while (cut.length > 1 && widthOf(cut) + markerW > maxW) cut = cut.slice(0, -1);
    lines[lines.length - 1] = cut + TRUNCATION_MARKER;
  }
  return lines;
}
