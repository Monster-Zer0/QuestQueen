package dev.aof.questqueen.client;

import com.mojang.brigadier.StringReader;
import dev.aof.questqueen.QuestQueen;
import dev.aof.questqueen.data.reward.CommandReward;
import dev.aof.questqueen.data.reward.GiveCommand;
import dev.aof.questqueen.data.reward.Reward;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.commands.arguments.item.ItemParser;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * How a command reward looks in the book: its icon (an item with components, a glyph or a picture), its name and its
 * hover lines. The command itself is never shown.
 *
 * <p>Glyph and picture icons travel through the book's {@code ItemStack} plumbing as a paper stack carrying a
 * {@code questqueen_face} marker in its custom data; {@link #render} draws the marker instead of the paper.
 */
public final class RewardFaces {
    /** The glyph a command reward shows when it sets no icon and is not a {@code give}. */
    public static final String FALLBACK_GLYPH = "star";
    private static final String MARKER = "questqueen_face";
    private static final int TOOLTIP_W = 200;

    private static final Map<String, ItemStack> PARSED = new HashMap<>();
    private static final Set<String> WARNED = new HashSet<>();

    private RewardFaces() {
    }

    /** Forget parsed items, e.g. when the player joins another world with other registries. */
    public static void clearCaches() {
        PARSED.clear();
        WARNED.clear();
    }

    /** The icon stack for a command reward: its own icon, else the item it gives, else the fallback glyph. */
    public static ItemStack face(CommandReward reward) {
        try {
            int count = Math.max(1, Math.min(64, reward.count()));
            Optional<String> glyph = reward.iconGlyph().filter(QuestGlyphs::isKnown);
            if (glyph.isPresent()) {
                return marker("glyph", glyph.get());
            }
            Optional<ResourceLocation> texture = reward.iconTexture();
            if (texture.isPresent()) {
                return marker("texture", texture.get().toString());
            }
            Optional<String> spec = reward.iconItemSpec();
            if (spec.isPresent()) {
                ItemStack stack = parse(spec.get());
                if (!stack.isEmpty()) {
                    return stack.copyWithCount(count);
                }
            }
            Optional<GiveCommand> give = reward.give();
            if (give.isPresent()) {
                ItemStack stack = parse(give.get().itemSpec());
                if (!stack.isEmpty()) {
                    return stack.copyWithCount(count);
                }
            }
            return marker("glyph", FALLBACK_GLYPH);
        } catch (RuntimeException | LinkageError noGame) {
            // No registries (very early client): no icon rather than a crash.
            return ItemStack.EMPTY;
        }
    }

    /** True when {@code face} is a real item (not a glyph or picture marker). */
    public static boolean realItem(ItemStack face) {
        return !face.isEmpty() && markerTag(face) == null;
    }

    /**
     * Draw a reward face at 16×16 with its top-left at {@code (x, y)}: a marker as its glyph or picture, anything
     * else as the item.
     */
    public static void render(GuiGraphics graphics, ItemStack face, int x, int y) {
        CompoundTag tag = markerTag(face);
        if (tag == null) {
            graphics.renderItem(face, x, y);
            return;
        }
        var pose = graphics.pose();
        pose.pushPose();
        // Same depth as an item, so veils and counts layered over item icons layer over this too.
        pose.translate(0, 0, 150);
        if (tag.contains("glyph")) {
            QuestGlyphs.draw(graphics, tag.getString("glyph"), x, y, 16, QuestColors.REWARD);
        } else {
            ResourceLocation texture = ResourceLocation.tryParse(tag.getString("texture"));
            if (texture != null && RichDraw.imageSize(texture) != null) {
                int[] size = RichDraw.imageSize(texture);
                graphics.blit(texture, x, y, 16, 16, 0f, 0f, size[0], size[1], size[0], size[1]);
            } else {
                MockChrome.frame(graphics, x, y, 16, 16, QuestColors.LOCKED_EDGE);
            }
        }
        pose.popPose();
    }

    /** One line naming the reward, for the claim-all list and the log. */
    public static String label(Reward reward) {
        if (reward instanceof CommandReward command && command.name().isBlank()) {
            ItemStack face = face(command);
            if (realItem(face)) {
                String name = face.getHoverName().getString();
                return command.count() > 1 ? command.count() + "x " + name : name;
            }
        }
        return reward == null ? "" : reward.describe();
    }

    /**
     * Hover lines for a command reward. Empty when the reward sets no name or description and gives a real item:
     * then the caller shows that item's own tooltip.
     */
    public static List<FormattedCharSequence> tooltip(Font font, CommandReward reward, ItemStack face) {
        boolean ownText = !reward.name().isBlank() || !reward.description().isBlank();
        if (!ownText && realItem(face)) {
            return List.of();
        }
        List<FormattedCharSequence> lines = new ArrayList<>();
        String title = !reward.name().isBlank() ? reward.name().trim()
                : realItem(face) ? face.getHoverName().getString() : CommandReward.FALLBACK_NAME;
        if (reward.count() > 1) {
            title = reward.count() + "x " + title;
        }
        lines.add(Component.literal(title).getVisualOrderText());
        if (!reward.description().isBlank()) {
            for (String line : RichText.plain(reward.description()).split("\n")) {
                if (line.isBlank()) {
                    continue;
                }
                lines.addAll(font.split(Component.literal(line.trim()).withStyle(ChatFormatting.GRAY), TOOLTIP_W));
            }
        }
        return lines;
    }

    /** An item written as in {@code /give} ({@code id} or {@code id[components]}), or empty when it is not one. */
    public static ItemStack item(String spec) {
        try {
            return spec == null || spec.isBlank() ? ItemStack.EMPTY : parse(spec.trim());
        } catch (RuntimeException | LinkageError noGame) {
            return ItemStack.EMPTY;
        }
    }

    private static ItemStack parse(String spec) {
        ItemStack known = PARSED.get(spec);
        if (known != null) {
            return known;
        }
        var level = Minecraft.getInstance() == null ? null : Minecraft.getInstance().level;
        if (level == null) {
            return ItemStack.EMPTY;
        }
        ItemStack stack;
        try {
            ItemParser.ItemResult result = new ItemParser(level.registryAccess()).parse(new StringReader(spec));
            stack = new ItemStack(result.item(), 1, result.components());
        } catch (Exception bad) {
            if (WARNED.add(spec)) {
                QuestQueen.LOGGER.warn("Quest reward icon '{}' is not an item: {}", spec, bad.getMessage());
            }
            stack = ItemStack.EMPTY;
        }
        PARSED.put(spec, stack);
        return stack;
    }

    private static ItemStack marker(String kind, String value) {
        ItemStack stack = new ItemStack(Items.PAPER);
        CompoundTag inner = new CompoundTag();
        inner.putString(kind, value);
        CompoundTag root = new CompoundTag();
        root.put(MARKER, inner);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(root));
        return stack;
    }

    private static CompoundTag markerTag(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !stack.is(Items.PAPER)) {
            return null;
        }
        CustomData data = stack.get(DataComponents.CUSTOM_DATA);
        if (data == null) {
            return null;
        }
        CompoundTag root = data.copyTag();
        return root.contains(MARKER) ? root.getCompound(MARKER) : null;
    }
}
