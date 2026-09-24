package dev.aof.questqueen.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;

public final class MockChrome {
    public static final int HEADER_H = 8;
    public static final int FRAME = 1;
    public static final int INK = 0xFF0F0A10;

    /** Label ink on dark headers — follows the active chapter theme. */
    public static int tagWhite() {
        return QuestColors.TEXT;
    }

    private MockChrome() {
    }

    public static void box(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        if (w > 0 && h > 0) {
            graphics.fill(x, y, x + w, y + h, color);
        }
    }

    public static void frame(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        box(graphics, x, y, w, 1, color);
        box(graphics, x, y + h - 1, w, 1, color);
        box(graphics, x, y, 1, h, color);
        box(graphics, x + w - 1, y, 1, h, color);
    }

    public static void cell(GuiGraphics graphics, int x, int y, int size) {
        box(graphics, x, y, size, size, QuestColors.CELL);
        box(graphics, x, y, size, 2, QuestColors.CELL_LINE);
        box(graphics, x, y, 2, size, QuestColors.CELL_LINE);
        box(graphics, x, y + size - 2, size, 2, QuestColors.CELL_SHADOW);
        box(graphics, x + size - 2, y, 2, size, QuestColors.CELL_SHADOW);
        box(graphics, x, y, 2, 2, QuestColors.CELL_LINE);
        box(graphics, x + size - 2, y + size - 2, 2, 2, QuestColors.CELL_SHADOW);
    }

    /**
     * Empty slot as the player sees it: a faint flat square with a hairline edge. The bevelled {@link #cell}
     * reads as a tile in its own right, so a sparse chapter looked like a wall of blank quests.
     */
    public static void quietCell(GuiGraphics graphics, int x, int y, int size) {
        box(graphics, x, y, size, size, withAlpha(QuestColors.CELL, 0x70));
        frame(graphics, x, y, size, size, withAlpha(QuestColors.CELL_LINE, 0x48));
    }

    private static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    public static int tabWidth(int labelHalfPx, int max) {
        int w = Math.max(18, labelHalfPx + 8);
        return Math.min(Math.max(18, max), w) & -2;
    }

    public static void statusTab(GuiGraphics graphics, int x, int y, int headerW, int edge, int face) {
        int w = Math.max(0, headerW);
        if (w <= 0) {
            return;
        }
        box(graphics, x, y, w, 8, edge);
        int steps = w >= 36 ? 3 : 2;
        for (int i = 0; i < steps; i++) {
            int cut = steps - i;
            box(graphics, x + w - cut, y + 8 - 1 - i, cut, 1, face);
        }
    }

    public static void tile(GuiGraphics graphics, int x, int y, int size, int face, int edge, int headerW) {
        box(graphics, x, y, size, size, face);
        if (edge != 0) {
            frame(graphics, x, y, size, size, edge);
            if (headerW > 0) {
                statusTab(graphics, x, y, Math.min(headerW, size - 14), edge, face);
            }
        }
    }

    public static void panel(GuiGraphics graphics, int x, int y, int w, int h, int face, int edge, int headerW) {
        box(graphics, x, y, w, h, face);
        frame(graphics, x, y, w, h, edge);
        if (edge != 0 && headerW > 0) {
            statusTab(graphics, x, y, Math.min(headerW, w - 16), edge, face);
        }
    }

    public static void panel(GuiGraphics graphics, int x, int y, int w, int h, int face, int edge) {
        panel(graphics, x, y, w, h, face, edge, Math.min(72, w / 3));
    }

    public static void plus(GuiGraphics graphics, int cx, int cy, int color) {
        box(graphics, cx - 5, cy - 1, 11, 3, color);
        box(graphics, cx - 1, cy - 5, 3, 11, color);
    }

    /** Small pushpin glyph for the PIN / PINNED control. */
    public static void pinIcon(GuiGraphics graphics, int x, int y, int color) {
        // head
        box(graphics, x + 1, y, 5, 4, color);
        box(graphics, x + 2, y + 4, 3, 2, color);
        // needle
        box(graphics, x + 3, y + 6, 1, 4, color);
    }

    public static void cornerPlus(GuiGraphics graphics, int cx, int cy, int color) {
        box(graphics, cx - 3, cy - 1, 7, 3, color);
        box(graphics, cx - 1, cy - 3, 3, 7, color);
    }

    public static void bottomCornerPluses(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        cornerPlus(graphics, x, y + h, color);
        cornerPlus(graphics, x + w, y + h, color);
    }

    public static int tagInk(int edge) {
        return edge != QuestColors.CURRENT && edge != QuestColors.COMPLETED && edge != QuestColors.EDIT
                ? tagWhite() : INK;
    }

    public static void cornerOrnaments(GuiGraphics graphics, int x, int y, int size, int color) {
        int arm = 3;
        int inset = 3;
        int left = x + inset;
        int right = x + size - inset - 1;
        int top = y + inset;
        int bottom = y + size - inset - 1;
        box(graphics, left, top, arm, 1, color);
        box(graphics, left, top, 1, arm, color);
        box(graphics, right - arm + 1, top, arm, 1, color);
        box(graphics, right, top, 1, arm, color);
        box(graphics, left, bottom, arm, 1, color);
        box(graphics, left, bottom - arm + 1, 1, arm, color);
        box(graphics, right - arm + 1, bottom, arm, 1, color);
        box(graphics, right, bottom - arm + 1, 1, arm, color);
    }

    public static void expandIcon(GuiGraphics graphics, int x, int y, int color) {
        box(graphics, x + 1, y + 3, 4, 1, color);
        box(graphics, x + 1, y + 6, 4, 1, color);
        box(graphics, x + 1, y + 3, 1, 4, color);
        box(graphics, x + 4, y + 3, 1, 4, color);
        box(graphics, x + 4, y + 1, 1, 1, color);
        box(graphics, x + 5, y + 1, 1, 1, color);
        box(graphics, x + 6, y + 1, 1, 1, color);
        box(graphics, x + 6, y + 2, 1, 1, color);
        box(graphics, x + 6, y + 3, 1, 1, color);
        box(graphics, x + 5, y + 2, 1, 1, color);
    }

    public static void linkElbow(GuiGraphics graphics, int x1, int y1, int x2, int y2, int color) {
        if (x1 == x2) {
            int top = Math.min(y1, y2);
            int bot = Math.max(y1, y2);
            box(graphics, x1, top, 1, bot - top + 1, color);
        } else if (y1 == y2) {
            int left = Math.min(x1, x2);
            int right = Math.max(x1, x2);
            box(graphics, left, y1, right - left + 1, 1, color);
        } else {
            int left = Math.min(x1, x2);
            int right = Math.max(x1, x2);
            box(graphics, left, y1, right - left + 1, 1, color);
            int top = Math.min(y1, y2);
            int bot = Math.max(y1, y2);
            box(graphics, x2, top, 1, bot - top + 1, color);
        }
    }

    public static void arrowWedge(GuiGraphics graphics, int cx, int cy, int dx, int dy, int color) {
        paintPortWedge(graphics, cx, cy, dx, dy, color, false);
    }

    public static void arrowBarred(GuiGraphics graphics, int cx, int cy, int dx, int dy, int color) {
        paintPortWedge(graphics, cx, cy, dx, dy, color, true);
    }

    /**
     * Jerry mock border-port (Data pixel trace, native): diamond fused to edge midpoint.
     * Depths by |ly| from center: 8,7,6,2,1 — bbox ~8×9, tip 1px, base ~8, shaft 0.
     * Outward column lx=0 at tile edge.
     */
    static int portDepth(int ly) {
        return switch (Math.abs(ly)) {
            case 0 -> 8;
            case 1 -> 7;
            case 2 -> 6;
            case 3 -> 2;
            case 4 -> 1;
            default -> 0;
        };
    }

    /** Half-height of filled dart at outward column lx (inverse of portDepth). */
    static int portHalf(int lx) {
        // lx 0..7 → half such that depth(ly) covers this column
        return switch (lx) {
            case 0, 1, 2, 3, 4, 5 -> 2; // covered by |ly|<=2 (depth>=6)
            case 6 -> 1;                 // |ly|<=1 (depth>=7)
            case 7 -> 0;                 // |ly|==0 (depth 8)
            default -> -1;
        };
    }

    static boolean inPort(int lx, int ly) {
        int depth = portDepth(ly);
        return depth > 0 && lx >= 0 && lx < depth;
    }

    /** Outward span [lo,hi] inclusive at row ly — from edge (0) through tip. */
    static int[] dartSpan(int ly) {
        int depth = portDepth(ly);
        if (depth <= 0) {
            return new int[]{1, 0};
        }
        return new int[]{0, depth - 1};
    }

    static boolean dartFilled(int lx, int ly, boolean barred) {
        return inPort(lx, ly);
    }

    static boolean barredInk(int lx, int ly) {
        if (inPort(lx, ly)) {
            return lx == 1;
        }
        return inPort(lx - 1, ly) || inPort(lx + 1, ly) || inPort(lx, ly - 1) || inPort(lx, ly + 1);
    }

    static int dartPixelCount(boolean barred) {
        int n = 0;
        for (int ly = -3; ly <= 3; ly++) {
            for (int lx = -1; lx <= 3; lx++) {
                if (dartFilled(lx, ly, barred) || (barred && barredInk(lx, ly))) {
                    n++;
                }
            }
        }
        return n;
    }

    private static void plotPort(GuiGraphics graphics, int cx, int cy, int adx, int ady, int lx, int ly, int color) {
        int wx;
        int wy;
        if (adx != 0) {
            wx = cx + adx * lx;
            wy = cy + ly;
        } else {
            wx = cx + ly;
            wy = cy + ady * lx;
        }
        box(graphics, wx, wy, 1, 1, color);
    }

    private static void paintPortWedge(GuiGraphics graphics, int cx, int cy, int dx, int dy, int color, boolean barred) {
        int adx = Integer.signum(dx);
        int ady = Integer.signum(dy);
        if (adx == 0 && ady == 0) {
            adx = 1;
        }
        // Solid mock wedge only — hue carries locked; barred ink dropped (Jerry/Worf).
        for (int ly = -4; ly <= 4; ly++) {
            int[] span = dartSpan(ly);
            for (int lx = span[0]; lx <= span[1]; lx++) {
                plotPort(graphics, cx, cy, adx, ady, lx, ly, color);
            }
        }
    }

    public static void arrowHead(GuiGraphics graphics, int cx, int cy, int dx, int dy, int color) {
        arrowWedge(graphics, cx, cy, dx, dy, color);
    }

    public static void arrow(GuiGraphics graphics, int cx, int cy, int dx, int dy, int color) {
        arrowHead(graphics, cx + dx * 2, cy + dy * 2, dx, dy, color);
        if (dx != 0) {
            box(graphics, cx - (dx > 0 ? 2 : 0), cy, 3, 1, color);
        } else if (dy != 0) {
            box(graphics, cx, cy - (dy > 0 ? 2 : 0), 1, 3, color);
        }
    }

    public static void diamond(GuiGraphics graphics, int cx, int cy, int color) {
        box(graphics, cx, cy - 2, 1, 5, color);
        box(graphics, cx - 1, cy - 1, 3, 3, color);
    }

    public static void padlock(GuiGraphics graphics, int cx, int cy, int color) {
        int left = cx - 4;
        int top = cy - 5;
        box(graphics, left + 1, top - 1, 6, 1, INK);
        box(graphics, left, top, 1, 3, INK);
        box(graphics, left + 7, top, 1, 3, INK);
        box(graphics, left - 1, top + 3, 10, 7, INK);
        box(graphics, left + 2, top, 4, 1, color);
        box(graphics, left + 2, top + 1, 1, 2, color);
        box(graphics, left + 5, top + 1, 1, 2, color);
        box(graphics, left, top + 4, 8, 5, color);
        box(graphics, left + 3, top + 6, 2, 1, QuestColors.VOID);
    }

    public static void openPadlock(GuiGraphics graphics, int cx, int cy, int color) {
        int left = cx - 4;
        int top = cy - 5;
        box(graphics, left - 1, top + 3, 10, 7, INK);
        box(graphics, left + 1, top - 1, 1, 4, INK);
        box(graphics, left + 1, top - 2, 5, 1, INK);
        box(graphics, left + 6, top - 3, 1, 3, INK);
        box(graphics, left, top + 4, 8, 5, color);
        box(graphics, left + 3, top + 6, 2, 1, QuestColors.VOID);
        box(graphics, left + 2, top + 1, 1, 3, color);
        box(graphics, left + 2, top, 4, 1, color);
        box(graphics, left + 6, top - 2, 1, 2, color);
    }

    public static void flowArrow(GuiGraphics graphics, int cx, int cy, int dx, int dy, int color) {
        if (dx != 0) {
            box(graphics, cx - 3, cy, 5, 1, color);
        } else if (dy != 0) {
            box(graphics, cx, cy - 3, 1, 5, color);
        }
        arrowHead(graphics, cx + dx * 3, cy + dy * 3, dx, dy, color);
    }

    public static void pathPadlock(GuiGraphics graphics, int cx, int cy, int color) {
        int left = cx - 3;
        int bodyTop = cy - 2;
        box(graphics, left + 1, bodyTop - 3, 3, 1, color);
        box(graphics, left + 1, bodyTop - 2, 1, 2, color);
        box(graphics, left + 3, bodyTop - 2, 1, 2, color);
        box(graphics, left, bodyTop, 6, 5, color);
        box(graphics, left + 2, bodyTop + 2, 2, 1, QuestColors.VOID);
    }

    public static void pathOpenPadlock(GuiGraphics graphics, int cx, int cy, int color) {
        int left = cx - 3;
        int bodyTop = cy - 2;
        box(graphics, left + 1, bodyTop - 3, 3, 1, color);
        box(graphics, left + 1, bodyTop - 2, 1, 2, color);
        box(graphics, left, bodyTop, 6, 5, color);
        box(graphics, left + 2, bodyTop + 2, 2, 1, QuestColors.VOID);
    }

    public static int pathPortColor(TileVisual source, boolean destLocked) {
        return destLocked ? QuestColors.PORT_RED : QuestColors.NEW;
    }

    public static int pathArrowColor(boolean locked) {
        return locked ? QuestColors.PORT_RED : QuestColors.NEW;
    }

    public static String pathArrowShape(boolean locked) {
        return "arrow";
    }

    public static int pathHoverLockColor(boolean locked) {
        return locked ? QuestColors.PORT_RED : QuestColors.CURRENT;
    }

    public static boolean pathHoverHit(int mouseX, int mouseY, int cx, int cy) {
        return Math.abs(mouseX - cx) <= 10 && Math.abs(mouseY - cy) <= 10;
    }

    /**
     * Directional path glyph: short shaft + solid triangle head along (dx,dy).
     * Open and locked share this silhouette; hue alone carries lock state.
     */
    static boolean pathArrowFilled(int lx, int ly) {
        int ay = Math.abs(ly);
        if (lx >= 0 && lx <= 3 && ay <= 1) {
            return true;
        }
        if (lx >= 4 && lx <= 7) {
            return ay <= 7 - lx;
        }
        return false;
    }

    /** Soft opacity pulse (~2.5s loop). Keeps hue; stays readable for open blue + locked red. */
    private static int pathArrowPulse(int color) {
        return pathArrowPulse(color, 1f);
    }

    /**
     * As {@link #pathArrowPulse(int)} but scaled by a travelling {@code flow} factor, so brightness sweeps
     * along the link. Only alpha changes — the locked/open hue is never touched.
     */
    private static int pathArrowPulse(int color, float flow) {
        long periodMs = 2500L;
        double phase = (Util.getMillis() % periodMs) / (double) periodMs * Math.PI * 2.0;
        double wave = 0.5 + 0.5 * Math.sin(phase);
        int a = (color >>> 24) & 0xFF;
        if (a == 0) {
            a = 255;
        }
        float scale = 0.74f + 0.26f * (float) wave;
        if (flow < 1f) {
            scale *= Math.max(0.35f, flow);
        }
        if (!UiFx.enabled()) {
            scale = 1f;
        }
        int na = Math.max(0, Math.min(255, Math.round(a * scale)));
        return (na << 24) | (color & 0x00FFFFFF);
    }

    public static void pathArrow(GuiGraphics graphics, int cx, int cy, int dx, int dy, int color) {
        pathArrow(graphics, cx, cy, dx, dy, color, 1f);
    }

    /** {@code flow} in [0,1] brightens the arrow head as the travelling highlight arrives. */
    public static void pathArrow(GuiGraphics graphics, int cx, int cy, int dx, int dy, int color, float flow) {
        int adx = Integer.signum(dx);
        int ady = Integer.signum(dy);
        if (adx == 0 && ady == 0) {
            adx = 1;
        }
        int ink = pathArrowPulse(color, flow);
        for (int ly = -3; ly <= 3; ly++) {
            for (int lx = 0; lx <= 7; lx++) {
                if (pathArrowFilled(lx, ly)) {
                    plotPort(graphics, cx, cy, adx, ady, lx, ly, ink);
                }
            }
        }
    }

    public static void pathGate(GuiGraphics graphics, int cx, int cy, int dx, int dy, boolean locked) {
        pathGate(graphics, cx, cy, dx, dy, locked, false);
    }

    public static void pathGate(GuiGraphics graphics, int cx, int cy, int dx, int dy, boolean locked, boolean hover) {
        pathGate(graphics, cx, cy, dx, dy, locked, hover, pathArrowColor(locked));
    }

    public static void pathGate(GuiGraphics graphics, int cx, int cy, int dx, int dy, boolean locked, boolean hover, int color) {
        pathGate(graphics, cx, cy, dx, dy, locked, hover, color, 1f);
    }

    /** Full form: {@code flow} drives the travelling highlight along the path arrow. */
    public static void pathGate(GuiGraphics graphics, int cx, int cy, int dx, int dy, boolean locked, boolean hover,
            int color, float flow) {
        if (dx == 0 && dy == 0) {
            dx = 1;
        }
        pathArrow(graphics, cx, cy, dx, dy, color, flow);
        if (hover) {
            if (locked) {
                pathPadlock(graphics, cx, cy, pathHoverLockColor(true));
            } else {
                pathOpenPadlock(graphics, cx, cy, pathHoverLockColor(false));
            }
        }
    }

    public static void fitIcon(GuiGraphics graphics, int x, int y, int color) {
        frame(graphics, x + 4, y + 4, 6, 6, color);
        box(graphics, x + 1, y + 1, 3, 1, color);
        box(graphics, x + 1, y + 1, 1, 3, color);
        box(graphics, x + 10, y + 1, 3, 1, color);
        box(graphics, x + 12, y + 1, 1, 3, color);
        box(graphics, x + 1, y + 12, 3, 1, color);
        box(graphics, x + 1, y + 10, 1, 3, color);
        box(graphics, x + 10, y + 12, 3, 1, color);
        box(graphics, x + 12, y + 10, 1, 3, color);
        box(graphics, x + 3, y + 3, 1, 1, color);
        box(graphics, x + 10, y + 3, 1, 1, color);
        box(graphics, x + 3, y + 10, 1, 1, color);
        box(graphics, x + 10, y + 10, 1, 1, color);
    }

    public static void caret(GuiGraphics graphics, int x, int y, boolean open, int color) {
        if (open) {
            box(graphics, x, y + 1, 1, 1, color);
            box(graphics, x + 1, y + 2, 1, 1, color);
            box(graphics, x + 2, y + 3, 1, 1, color);
            box(graphics, x + 3, y + 2, 1, 1, color);
            box(graphics, x + 4, y + 1, 1, 1, color);
        } else {
            box(graphics, x + 1, y, 1, 1, color);
            box(graphics, x + 2, y + 1, 1, 1, color);
            box(graphics, x + 3, y + 2, 1, 1, color);
            box(graphics, x + 2, y + 3, 1, 1, color);
            box(graphics, x + 1, y + 4, 1, 1, color);
        }
    }

    public static void chevron(GuiGraphics graphics, int x, int y, boolean left, int color) {
        if (left) {
            box(graphics, x + 3, y, 1, 1, color);
            box(graphics, x + 2, y + 1, 1, 1, color);
            box(graphics, x + 1, y + 2, 1, 1, color);
            box(graphics, x + 2, y + 3, 1, 1, color);
            box(graphics, x + 3, y + 4, 1, 1, color);
        } else {
            box(graphics, x + 1, y, 1, 1, color);
            box(graphics, x + 2, y + 1, 1, 1, color);
            box(graphics, x + 3, y + 2, 1, 1, color);
            box(graphics, x + 2, y + 3, 1, 1, color);
            box(graphics, x + 1, y + 4, 1, 1, color);
        }
    }

    public static void closeX(GuiGraphics graphics, int x, int y, int color) {
        box(graphics, x, y, 1, 1, color);
        box(graphics, x + 1, y + 1, 1, 1, color);
        box(graphics, x + 2, y + 2, 1, 1, color);
        box(graphics, x + 3, y + 3, 1, 1, color);
        box(graphics, x + 4, y + 4, 1, 1, color);
        box(graphics, x + 4, y, 1, 1, color);
        box(graphics, x + 3, y + 1, 1, 1, color);
        box(graphics, x + 1, y + 3, 1, 1, color);
        box(graphics, x, y + 4, 1, 1, color);
    }
}
