package dev.aof.questqueen.client;

import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;

public final class MockChrome {
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

    public static void panel(GuiGraphics graphics, int x, int y, int w, int h, int face, int edge, int headerW) {
        box(graphics, x, y, w, h, face);
        frame(graphics, x, y, w, h, edge);
        if (edge != 0 && headerW > 0) {
            // Inset by FRAME so COMPLETED/CURRENT tab sits inside the gold border
            // (drawing at x,y painted over the top-left frame corner).
            int tabX = x + FRAME;
            int tabY = y + FRAME;
            int tabMax = Math.max(0, w - 16 - FRAME);
            statusTab(graphics, tabX, tabY, Math.min(headerW, tabMax), edge, face);
        }
    }

    /** Ledger card chrome: face, a hairline frame, and a 3px rail down the left edge in the state colour. */
    public static void railPanel(GuiGraphics graphics, int x, int y, int w, int h, int face, int frame, int rail) {
        box(graphics, x, y, w, h, face);
        frame(graphics, x, y, w, h, frame);
        if (rail != 0) {
            box(graphics, x, y, 3, h, rail);
        }
    }

    /** One-pixel dots every other pixel from {@code x1} to {@code x2}: the leader between a task and its count. */
    public static void dottedLine(GuiGraphics graphics, int x1, int x2, int y, int color) {
        for (int x = x1 + (x1 & 1); x < x2; x += 2) {
            box(graphics, x, y, 1, 1, color);
        }
    }

    /** A 1px frame drawn every other pixel, for something that is not open yet. */
    public static void dottedFrame(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        dottedLine(graphics, x, x + w, y, color);
        dottedLine(graphics, x, x + w, y + h - 1, color);
        for (int py = y + (y & 1); py < y + h; py += 2) {
            box(graphics, x, py, 1, 1, color);
            box(graphics, x + w - 1, py, 1, 1, color);
        }
    }

    public static void panel(GuiGraphics graphics, int x, int y, int w, int h, int face, int edge) {
        panel(graphics, x, y, w, h, face, edge, Math.min(72, w / 3));
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

    /** Header label ink. Always theme text — never force near-black on CURRENT/COMPLETED/EDIT tabs. */
    public static int tagInk(int edge) {
        return tagWhite();
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
