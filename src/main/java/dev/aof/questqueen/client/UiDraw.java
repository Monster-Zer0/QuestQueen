package dev.aof.questqueen.client;

import dev.aof.questqueen.QuestQueen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * 1:1 GUI texture blits only. Stretching 1×1 / 16×16 fills is dropped by
 * ImmediatelyFast + Iris in this pack; item icons and unstretched blits still draw.
 */
public final class UiDraw {
    public static final ResourceLocation VOID = QuestQueen.id("textures/gui/panel_void.png");
    public static final ResourceLocation CELL = QuestQueen.id("textures/gui/tile_cell.png");
    public static final ResourceLocation CARD = QuestQueen.id("textures/gui/tile_card.png");
    public static final ResourceLocation LOCKED = QuestQueen.id("textures/gui/tile_locked.png");
    public static final ResourceLocation CURRENT = QuestQueen.id("textures/gui/tile_current.png");
    public static final ResourceLocation NEW = QuestQueen.id("textures/gui/tile_new.png");
    public static final ResourceLocation EDIT = QuestQueen.id("textures/gui/tile_edit.png");
    public static final ResourceLocation COMPLETED = QuestQueen.id("textures/gui/tile_completed.png");
    public static final ResourceLocation SIDEBAR = QuestQueen.id("textures/gui/panel_sidebar.png");

    private static final ResourceLocation PX_GREEN = QuestQueen.id("textures/gui/px_green.png");
    private static final ResourceLocation PX_BLUE = QuestQueen.id("textures/gui/px_blue.png");
    private static final ResourceLocation PX_ORANGE = QuestQueen.id("textures/gui/px_orange.png");
    private static final ResourceLocation PX_RED = QuestQueen.id("textures/gui/px_red.png");
    private static final ResourceLocation PX_YELLOW = QuestQueen.id("textures/gui/px_yellow.png");
    private static final ResourceLocation PX_PURPLE = QuestQueen.id("textures/gui/px_purple.png");
    private static final ResourceLocation PX_DARK = QuestQueen.id("textures/gui/px_dark.png");

    private UiDraw() {
    }

    public static void begin(GuiGraphics graphics) {
        graphics.flush();
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    public static void end() {
    }

    public static void fill(GuiGraphics graphics, int x0, int y0, int x1, int y1, int argb) {
        if (x0 > x1) {
            int t = x0;
            x0 = x1;
            x1 = t;
        }
        if (y0 > y1) {
            int t = y0;
            y0 = y1;
            y1 = t;
        }
        tile(graphics, pixelFor(argb), x0, y0, x1 - x0, y1 - y0);
    }

    public static void frame(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        frame(graphics, x, y, w, h, color, 1);
    }

    public static void frame(GuiGraphics graphics, int x, int y, int w, int h, int color, int thickness) {
        int t = Math.max(1, thickness);
        ResourceLocation px = pixelFor(color);
        tile(graphics, px, x, y, w, t);
        tile(graphics, px, x, y + h - t, w, t);
        tile(graphics, px, x, y, t, h);
        tile(graphics, px, x + w - t, y, t, h);
    }

    /** Repeat a 16×16 texture without stretching (Minecraft inventory-style). */
    public static void tile(GuiGraphics graphics, ResourceLocation texture, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        graphics.setColor(1f, 1f, 1f, 1f);
        int x1 = x + w;
        int y1 = y + h;
        for (int py = y; py < y1; py += 16) {
            int ph = Math.min(16, y1 - py);
            for (int px = x; px < x1; px += 16) {
                int pw = Math.min(16, x1 - px);
                graphics.blit(texture, px, py, 0f, 0f, pw, ph, 16, 16);
            }
        }
    }


    private static ResourceLocation pixelFor(int argb) {
        int r = (argb >> 16) & 0xFF;
        int g = (argb >> 8) & 0xFF;
        int b = argb & 0xFF;
        if (g > r + 40 && g > b + 40) {
            return PX_GREEN;
        }
        if (b > r + 30 && b >= g) {
            return PX_BLUE;
        }
        if (r > 180 && g > 100 && b < 80) {
            return PX_ORANGE;
        }
        if (r > 160 && g > 160 && b < 120) {
            return PX_YELLOW;
        }
        if (r > 160 && g < 110 && b < 130) {
            return PX_RED;
        }
        if (r + g + b < 70) {
            return PX_DARK;
        }
        return PX_PURPLE;
    }

}
