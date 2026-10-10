package dev.aof.questqueen.client;

import com.mojang.blaze3d.platform.NativeImage;
import dev.aof.questqueen.QuestQueen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import java.io.InputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Paints a {@link RichLayout.Laid} and measures pictures. The client half of the description markup. */
final class RichDraw {
    /** Where an inline item icon was painted this frame, for its hover tooltip. */
    record ItemHit(int x, int y, int w, int h, Item item) {
    }

    private static final int[] MISSING = new int[0];
    private static final Map<ResourceLocation, int[]> SIZES = new HashMap<>();
    private static final Set<ResourceLocation> WARNED = new HashSet<>();

    private RichDraw() {
    }

    static RichLayout.Measure measure(Font font) {
        return (text, bold) -> bold
                ? font.width(Component.literal(text).withStyle(Style.EMPTY.withBold(true)))
                : font.width(text);
    }

    static RichLayout.ImageSizer sizer() {
        return RichDraw::imageSize;
    }

    /** A picture's native size from the resource packs, or {@code null} when it is missing or not a PNG. */
    static int[] imageSize(ResourceLocation texture) {
        int[] known = SIZES.get(texture);
        if (known == null) {
            known = load(texture);
            SIZES.put(texture, known);
        }
        return known == MISSING ? null : known;
    }

    private static int[] load(ResourceLocation texture) {
        try {
            var resource = Minecraft.getInstance().getResourceManager().getResource(texture);
            if (resource.isEmpty()) {
                warn(texture, "no such texture in any resource pack");
                return MISSING;
            }
            try (InputStream in = resource.get().open(); NativeImage image = NativeImage.read(in)) {
                return new int[]{image.getWidth(), image.getHeight()};
            }
        } catch (Exception exception) {
            warn(texture, exception.toString());
            return MISSING;
        }
    }

    private static void warn(ResourceLocation texture, String why) {
        if (WARNED.add(texture)) {
            QuestQueen.LOGGER.warn("Quest description picture {} cannot be shown: {}", texture, why);
        }
    }

    /** Forget measured pictures, so a resource-pack change shows up the next time the book opens. */
    static void clearCaches() {
        SIZES.clear();
        WARNED.clear();
    }

    /**
     * Paint the part of {@code laid} that shows through a window {@code viewH} tall whose top-left is {@code (x, y)},
     * scrolled down by {@code scroll}. The caller sets the clip; this only skips what is clearly out of view.
     */
    static void draw(GuiGraphics graphics, Font font, RichLayout.Laid laid, int x, int y, int scroll, int viewH,
                     int defaultColor, int mutedColor, List<ItemHit> itemHits) {
        for (RichLayout.Piece piece : laid.pieces()) {
            int top = y + piece.y() - scroll;
            if (top + piece.height() < y || top > y + viewH) {
                continue;
            }
            switch (piece) {
                case RichLayout.TextPiece text -> drawText(graphics, font, text, x + text.x(), top,
                        text.muted() ? mutedColor : text.color() == 0 ? defaultColor : text.color());
                case RichLayout.ItemPiece item -> drawItem(graphics, item, x + item.x(), top, itemHits);
                case RichLayout.GlyphPiece glyph -> QuestGlyphs.draw(graphics, glyph.id(), x + glyph.x(), top,
                        RichLayout.ICON, glyph.color() == 0 ? defaultColor : glyph.color());
                case RichLayout.ImagePiece image -> drawImage(graphics, font, image, x + image.x(), top, mutedColor);
                case RichLayout.RulePiece rule -> MockChrome.box(graphics, x + rule.x(), top, rule.w(), 1,
                        QuestColors.SIDEBAR_EDGE);
            }
        }
    }

    private static void drawText(GuiGraphics graphics, Font font, RichLayout.TextPiece piece, int x, int y,
                                 int color) {
        Component text = Component.literal(piece.text()).withStyle(Style.EMPTY.withBold(piece.bold())
                .withItalic(piece.italic()).withUnderlined(piece.underline()));
        if (piece.size() == RichText.BASE_SIZE) {
            graphics.drawString(font, text, x, y, color, false);
            return;
        }
        float scale = piece.size() / (float) RichText.BASE_SIZE;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1f);
        graphics.drawString(font, text, 0, 0, color, false);
        pose.popPose();
    }

    private static void drawItem(GuiGraphics graphics, RichLayout.ItemPiece piece, int x, int y,
                                 List<ItemHit> itemHits) {
        var item = BuiltInRegistries.ITEM.getOptional(piece.id());
        if (item.isEmpty()) {
            MockChrome.box(graphics, x, y, RichLayout.ICON, 1, QuestColors.LOCKED_EDGE);
            MockChrome.box(graphics, x, y + RichLayout.ICON - 1, RichLayout.ICON, 1, QuestColors.LOCKED_EDGE);
            MockChrome.box(graphics, x, y, 1, RichLayout.ICON, QuestColors.LOCKED_EDGE);
            MockChrome.box(graphics, x + RichLayout.ICON - 1, y, 1, RichLayout.ICON, QuestColors.LOCKED_EDGE);
            return;
        }
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        float scale = RichLayout.ICON / 16f;
        pose.scale(scale, scale, 1f);
        graphics.renderItem(new ItemStack(item.get()), 0, 0);
        pose.popPose();
        itemHits.add(new ItemHit(x, y, RichLayout.ICON, RichLayout.ICON, item.get()));
    }

    private static void drawImage(GuiGraphics graphics, Font font, RichLayout.ImagePiece image, int x, int y,
                                  int mutedColor) {
        if (image.missing()) {
            MockChrome.box(graphics, x, y, image.w(), 1, QuestColors.LOCKED_EDGE);
            MockChrome.box(graphics, x, y + image.h() - 1, image.w(), 1, QuestColors.LOCKED_EDGE);
            MockChrome.box(graphics, x, y, 1, image.h(), QuestColors.LOCKED_EDGE);
            MockChrome.box(graphics, x + image.w() - 1, y, 1, image.h(), QuestColors.LOCKED_EDGE);
            String label = "missing picture";
            graphics.drawString(font, label, x + Math.max(2, (image.w() - font.width(label)) / 2),
                    y + (image.h() - 8) / 2, mutedColor, false);
            return;
        }
        graphics.blit(image.texture(), x, y, image.w(), image.h(), 0f, 0f, image.texW(), image.texH(),
                image.texW(), image.texH());
    }
}
