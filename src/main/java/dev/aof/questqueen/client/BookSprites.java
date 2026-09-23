package dev.aof.questqueen.client;

import dev.aof.questqueen.QuestQueen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/** GUI-atlas sprites — same blitSprite path vanilla buttons use. */
public final class BookSprites {
    public static final ResourceLocation VOID = QuestQueen.id("backdrop/void");
    public static final ResourceLocation SIDEBAR = QuestQueen.id("panel/sidebar");
    public static final ResourceLocation HIGHLIGHT = QuestQueen.id("panel/highlight");
    public static final ResourceLocation INSPECT = QuestQueen.id("panel/inspect");
    public static final ResourceLocation CELL = QuestQueen.id("tile/cell");
    public static final ResourceLocation LOCKED = QuestQueen.id("tile/locked");
    public static final ResourceLocation CURRENT = QuestQueen.id("tile/current");
    public static final ResourceLocation NEW = QuestQueen.id("tile/new");
    public static final ResourceLocation EDIT = QuestQueen.id("tile/edit");
    public static final ResourceLocation COMPLETED = QuestQueen.id("tile/completed");
    public static final ResourceLocation FAILED = QuestQueen.id("tile/failed");
    public static final ResourceLocation CLOSED = QuestQueen.id("tile/closed");
    public static final ResourceLocation PORT = QuestQueen.id("icon/port");
    public static final ResourceLocation PLUS = QuestQueen.id("icon/plus");

    private BookSprites() {
    }

    public static ResourceLocation tileOf(TileVisual visual) {
        return switch (visual) {
            case CURRENT -> CURRENT;
            case NEW -> NEW;
            case EDIT -> EDIT;
            case COMPLETED -> COMPLETED;
            case FAILED -> FAILED;
            case CLOSED -> CLOSED;
            case LOCKED -> LOCKED;
        };
    }

    public static ResourceLocation headerOf(TileVisual visual) {
        return QuestQueen.id("header/" + visual.name().toLowerCase());
    }

    public static ResourceLocation frameOf(TileVisual visual) {
        return QuestQueen.id("frame/" + visual.name().toLowerCase());
    }

    public static ResourceLocation headerOfColor(int argb) {
        if (argb == QuestColors.CURRENT) {
            return headerOf(TileVisual.CURRENT);
        }
        if (argb == QuestColors.NEW) {
            return headerOf(TileVisual.NEW);
        }
        if (argb == QuestColors.EDIT) {
            return headerOf(TileVisual.EDIT);
        }
        if (argb == QuestColors.COMPLETED) {
            return headerOf(TileVisual.COMPLETED);
        }
        if (argb == QuestColors.FAILED) {
            return headerOf(TileVisual.FAILED);
        }
        if (argb == QuestColors.CLOSED) {
            return headerOf(TileVisual.CLOSED);
        }
        if (argb == QuestColors.MODAL_PINK || argb == QuestColors.LOCKED_EDGE) {
            return headerOf(TileVisual.LOCKED);
        }
        return headerOf(TileVisual.CURRENT);
    }

    public static ResourceLocation frameOfColor(int argb) {
        if (argb == QuestColors.CURRENT) {
            return frameOf(TileVisual.CURRENT);
        }
        if (argb == QuestColors.NEW) {
            return frameOf(TileVisual.NEW);
        }
        if (argb == QuestColors.EDIT) {
            return frameOf(TileVisual.EDIT);
        }
        if (argb == QuestColors.COMPLETED) {
            return frameOf(TileVisual.COMPLETED);
        }
        if (argb == QuestColors.FAILED) {
            return frameOf(TileVisual.FAILED);
        }
        if (argb == QuestColors.CLOSED) {
            return frameOf(TileVisual.CLOSED);
        }
        if (argb == QuestColors.PORT_RED) {
            return frameOf(TileVisual.FAILED);
        }
        if (argb == QuestColors.MODAL_PINK || argb == QuestColors.LOCKED_EDGE) {
            return frameOf(TileVisual.LOCKED);
        }
        return frameOf(TileVisual.NEW);
    }

    public static void blit(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        graphics.setColor(1f, 1f, 1f, 1f);
        graphics.blitSprite(sprite, x, y, w, h);
    }

    public static void tint(GuiGraphics graphics, ResourceLocation sprite, int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) {
            return;
        }
        float a = ((argb >>> 24) & 0xFF) / 255f;
        float r = ((argb >> 16) & 0xFF) / 255f;
        float g = ((argb >> 8) & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        graphics.setColor(r, g, b, a <= 0f ? 1f : a);
        graphics.blitSprite(sprite, x, y, w, h);
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    public static void panel(GuiGraphics graphics, int x, int y, int w, int h, int accent) {
        blit(graphics, INSPECT, x, y, w, h);
        blit(graphics, headerOfColor(accent), x, y, w, 10);
        blit(graphics, frameOfColor(accent), x, y, w, h);
    }

    public static void button(GuiGraphics graphics, int x, int y, int w, int h, int accent) {
        blit(graphics, INSPECT, x, y, w, h);
        blit(graphics, frameOfColor(accent), x, y, w, h);
    }
}
