package dev.aof.questqueen.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;

/**
 * Faces: overlapping custom 8px rows.
 * 2px edges: 64x2 / U+E000 (those already close a rectangle).
 * Header: vanilla █ stamped every pixel. Custom-font header glyphs collapse to
 * one line or an 8x8 tab in this pack (ImmediatelyFast).
 */
public final class PixelPaint {
    private static final String PX2 = "\uE000";
    private static final String PX8 = "\uE001";
    private static final String H16 = "\uE002";
    private static final String H64 = "\uE004";
    private static final String BLOCK = "\u2588";

    private PixelPaint() {
    }

    public static boolean usable(Font font) {
        int w = glyphW(font);
        return w >= 1 && w <= 3;
    }

    public static int glyphW(Font font) {
        return font.width(one(PX2));
    }

    public static int barW(Font font) {
        return font.width(one(H64));
    }

    public static int blockW(Font font) {
        return font.width(BLOCK);
    }

    public static boolean barsUsable(Font font) {
        int w = barW(font);
        return w >= 50 && w <= 70;
    }

    public static void fill(GuiGraphics graphics, Font font, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0 || !usable(font)) {
            return;
        }
        int box = font.width(one(PX8));
        if (w >= 16 && h >= 8 && box >= 6 && box <= 10) {
            fillRows(graphics, font, x, y, w, h, color, box);
            return;
        }
        stamp(graphics, font, x, y, w, h, color);
    }

    /**
     * Solid header strip. {@code tabW < w} paints the mock NEW tab; otherwise full width.
     */
    public static void headerBar(GuiGraphics graphics, Font font, int x, int y, int w, int color) {
        if (w <= 0) {
            return;
        }
        int cw = Math.max(1, font.width(BLOCK));
        int last = x + Math.max(0, w - cw);
        for (int xx = x; xx <= last; xx++) {
            graphics.drawString(font, BLOCK, xx, y, color, true);
        }
    }

    public static void cardChrome(GuiGraphics graphics, Font font, int x, int y, int w, int h, int color, int headerW) {
        headerBar(graphics, font, x, y, headerW > 0 ? Math.min(headerW, w) : w, color);
        if (headerW > 0 && headerW < w) {
            hline(graphics, font, x, y, w, color);
        }
        hline(graphics, font, x, y + h - 2, w, color);
        int sideH = Math.max(0, h - 14);
        if (sideH > 0) {
            vcol(graphics, font, x, y + 8, sideH, color);
            vcol(graphics, font, x + w - 2, y + 8, sideH, color);
        }
    }

    public static void cardChrome(GuiGraphics graphics, Font font, int x, int y, int w, int h, int color) {
        cardChrome(graphics, font, x, y, w, h, color, w);
    }

    public static void frame(GuiGraphics graphics, Font font, int x, int y, int w, int h, int color) {
        frame(graphics, font, x, y, w, h, color, 1);
    }

    public static void frame(GuiGraphics graphics, Font font, int x, int y, int w, int h, int color, int thickness) {
        if (w <= 0 || h <= 0) {
            return;
        }
        hline(graphics, font, x, y, w, color);
        hline(graphics, font, x, y + h - 2, w, color);
        vcol(graphics, font, x, y + 2, Math.max(0, h - 4), color);
        vcol(graphics, font, x + w - 2, y + 2, Math.max(0, h - 4), color);
    }

    public static void stamp(GuiGraphics graphics, Font font, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0 || !usable(font)) {
            return;
        }
        Component px = one(PX2);
        for (int yy = 0; yy < h; yy++) {
            for (int xx = 0; xx < w; xx++) {
                graphics.drawString(font, px, x + xx, y + yy, color, true);
            }
        }
    }

    private static void fillRows(GuiGraphics graphics, Font font, int x, int y, int w, int h, int color, int box) {
        int n = Math.max(1, w / box);
        Component row = Component.literal(PX8.repeat(n)).withStyle(Style.EMPTY.withFont(QuestBookScreen.FONT_ID));
        int stride = Math.max(1, box - 1);
        int last = y + Math.max(0, h - box);
        for (int yy = y; yy < last; yy += stride) {
            graphics.drawString(font, row, x, yy, color, true);
            graphics.drawString(font, row, x + 1, yy, color, true);
        }
        graphics.drawString(font, row, x, last, color, true);
        graphics.drawString(font, row, x + 1, last, color, true);
    }

    private static void hline(GuiGraphics graphics, Font font, int x, int y, int w, int color) {
        if (w >= 64 && font.width(one(H64)) >= 50) {
            graphics.drawString(font, one(H64), x, y, color, true);
            if (w > 64) {
                graphics.drawString(font, one(H64), x + w - 64, y, color, true);
                for (int xx = x + 64; xx < x + w - 64; xx += 64) {
                    graphics.drawString(font, one(H64), xx, y, color, true);
                }
            }
            return;
        }
        Component bar = one(H16);
        int last = x + w - 16;
        if (last < x) {
            stamp(graphics, font, x, y, w, 2, color);
            return;
        }
        for (int xx = x; xx < last; xx += 16) {
            graphics.drawString(font, bar, xx, y, color, true);
        }
        graphics.drawString(font, bar, last, y, color, true);
    }

    private static void vcol(GuiGraphics graphics, Font font, int x, int y, int h, int color) {
        Component px = one(PX2);
        for (int yy = 0; yy < h; yy++) {
            graphics.drawString(font, px, x, y + yy, color, true);
            graphics.drawString(font, px, x + 1, y + yy, color, true);
        }
    }

    private static Component one(String ch) {
        return Component.literal(ch).withStyle(Style.EMPTY.withFont(QuestBookScreen.FONT_ID));
    }
}
