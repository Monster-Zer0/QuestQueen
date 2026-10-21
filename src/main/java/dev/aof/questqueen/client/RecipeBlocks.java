package dev.aof.questqueen.client;

import dev.aof.questqueen.QuestQueen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmithingTransformRecipe;
import net.minecraft.world.item.crafting.SmithingTrimRecipe;
import net.neoforged.fml.util.ObfuscationReflectionHelper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Recipe blocks in descriptions ({@code {recipe:<item>}} / {@code {recipe:id=<recipe>}}): finds the recipe in the
 * client's recipe list (synced by the server, so datapack and KubeJS changes show after {@code /reload}) and lays
 * it out the way the game's own screens do: a crafting grid, a furnace row with its flame, the stonecutter, the
 * smithing table, and for any other (modded) recipe a plain row of inputs, an arrow and the output.
 */
public final class RecipeBlocks {
    static final int SLOT = 18;
    static final int ARROW_W = 24;
    static final int ARROW_H = 16;
    static final int GAP = 4;
    /** A modded recipe's inputs wrap after this many per row. */
    static final int MAX_ROW_INPUTS = 6;
    private static final ResourceLocation ARROW = ResourceLocation.withDefaultNamespace("container/furnace/burn_progress");
    private static final ResourceLocation FLAME = ResourceLocation.withDefaultNamespace("container/furnace/lit_progress");

    /** A slot: where it is in the block and what may go in it (an ingredient's items cycle). */
    record Slot(int x, int y, List<ItemStack> stacks, boolean output) {
    }

    /** A laid-out recipe: its size, its slots, the arrow, and a flame for cooking recipes. */
    record Shown(int w, int h, List<Slot> slots, int arrowX, int arrowY, int flameX, int flameY) {
    }

    private static final Map<String, Optional<Shown>> CACHE = new HashMap<>();
    private static final Set<RichText.Recipe> WARNED = new HashSet<>();

    private RecipeBlocks() {
    }

    private static int generation;

    /** The recipe list changed (join, /reload): drop what was laid out from the old one. */
    public static void clear() {
        CACHE.clear();
        WARNED.clear();
        generation++;
    }

    /** Bumped whenever the recipe list changes, so cached description layouts are rebuilt. */
    public static int generation() {
        return generation;
    }

    static RichLayout.RecipeSizer sizer() {
        return (recipe, column) -> resolve(recipe, column).map(shown -> new int[]{shown.w(), shown.h()}).orElse(null);
    }

    static Optional<Shown> resolve(RichText.Recipe ref, int column) {
        String key = ref.byId() + "|" + ref.target() + "|" + column;
        Optional<Shown> known = CACHE.get(key);
        if (known != null) {
            return known;
        }
        Optional<Shown> shown;
        try {
            shown = find(ref).map(holder -> lay(holder.value(), column));
        } catch (RuntimeException | LinkageError broken) {
            shown = Optional.empty();
        }
        if (shown.isEmpty() && WARNED.add(ref)) {
            QuestQueen.LOGGER.warn("Quest description recipe {}{} cannot be shown: no such recipe",
                    ref.byId() ? "id=" : "", ref.target());
        }
        CACHE.put(key, shown);
        return shown;
    }

    private static Optional<RecipeHolder<?>> find(RichText.Recipe ref) {
        var level = Minecraft.getInstance().level;
        if (level == null) {
            return Optional.empty();
        }
        var recipes = level.getRecipeManager();
        if (ref.byId()) {
            return recipes.byKey(ref.target());
        }
        var item = BuiltInRegistries.ITEM.getOptional(ref.target());
        if (item.isEmpty()) {
            return Optional.empty();
        }
        // Several recipes can make the item: prefer the crafting table, then the furnace family, then anything,
        // and within each the lowest id, so the same recipe shows every time.
        return recipes.getRecipes().stream()
                .filter(holder -> resultOf(holder.value()).is(item.get()))
                .min(Comparator.comparingInt((RecipeHolder<?> holder) -> rank(holder.value().getType()))
                        .thenComparing(holder -> holder.id().toString()));
    }

    private static int rank(RecipeType<?> type) {
        if (type == RecipeType.CRAFTING) {
            return 0;
        }
        if (type == RecipeType.SMELTING || type == RecipeType.BLASTING || type == RecipeType.SMOKING
                || type == RecipeType.CAMPFIRE_COOKING) {
            return 1;
        }
        if (type == RecipeType.STONECUTTING || type == RecipeType.SMITHING) {
            return 2;
        }
        return 3;
    }

    static ItemStack resultOf(Recipe<?> recipe) {
        var level = Minecraft.getInstance().level;
        try {
            return level == null ? ItemStack.EMPTY : recipe.getResultItem(level.registryAccess());
        } catch (RuntimeException unusual) {
            return ItemStack.EMPTY;
        }
    }

    private static Shown lay(Recipe<?> recipe, int column) {
        ItemStack result = resultOf(recipe);
        RecipeType<?> type = recipe.getType();
        if (type == RecipeType.CRAFTING && !recipe.getIngredients().isEmpty()) {
            return crafting(recipe, result);
        }
        if (recipe instanceof AbstractCookingRecipe && !recipe.getIngredients().isEmpty()) {
            // Input over a flame, then the arrow and the output, as in a furnace.
            List<Slot> slots = new ArrayList<>();
            slots.add(new Slot(0, 0, stacks(recipe.getIngredients().getFirst()), false));
            int arrowX = SLOT + GAP;
            slots.add(new Slot(arrowX + ARROW_W + GAP, (SLOT + 14) / 2 - SLOT / 2 + 2, List.of(result), true));
            return new Shown(arrowX + ARROW_W + GAP + SLOT, SLOT + 14, slots, arrowX, (SLOT + 14) / 2 - ARROW_H / 2 + 2,
                    2, SLOT);
        }
        List<Ingredient> inputs = smithing(recipe);
        if (inputs == null) {
            inputs = recipe.getIngredients().stream().filter(ingredient -> !ingredient.isEmpty()).toList();
        }
        if (inputs.isEmpty() || result.isEmpty()) {
            throw new IllegalStateException("nothing to draw");
        }
        return row(inputs, result, column);
    }

    /** A 3×3 grid (or the shaped recipe's own size) with the arrow and the output to its right. */
    private static Shown crafting(Recipe<?> recipe, ItemStack result) {
        int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : 3;
        List<Ingredient> ingredients = recipe.getIngredients();
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < 9; i++) {
            int col = i % 3;
            int row = i / 3;
            int index = recipe instanceof ShapedRecipe ? (col < width ? row * width + col : -1) : i;
            List<ItemStack> stacks = index >= 0 && index < ingredients.size() ? stacks(ingredients.get(index)) : List.of();
            slots.add(new Slot(col * SLOT, row * SLOT, stacks, false));
        }
        int arrowX = 3 * SLOT + GAP;
        int midY = (3 * SLOT) / 2;
        slots.add(new Slot(arrowX + ARROW_W + GAP, midY - SLOT / 2, List.of(result), true));
        return new Shown(arrowX + ARROW_W + GAP + SLOT, 3 * SLOT, slots, arrowX, midY - ARROW_H / 2, -1, -1);
    }

    /** Template, base and addition for the smithing table; {@code null} for any other recipe. */
    private static List<Ingredient> smithing(Recipe<?> recipe) {
        Class<?> type = recipe instanceof SmithingTransformRecipe ? SmithingTransformRecipe.class
                : recipe instanceof SmithingTrimRecipe ? SmithingTrimRecipe.class : null;
        if (type == null) {
            return null;
        }
        List<Ingredient> out = new ArrayList<>();
        for (String name : new String[]{"template", "base", "addition"}) {
            try {
                var field = ObfuscationReflectionHelper.findField(type, name);
                out.add((Ingredient) field.get(recipe));
            } catch (RuntimeException | IllegalAccessException unreadable) {
                return null;
            }
        }
        return out;
    }

    /** Inputs in rows (wrapping), then the arrow and the output, vertically centered. */
    private static Shown row(List<Ingredient> inputs, ItemStack result, int column) {
        int room = Math.max(1, (column - ARROW_W - SLOT - 2 * GAP) / SLOT);
        int perRow = Math.max(1, Math.min(Math.min(MAX_ROW_INPUTS, room), inputs.size()));
        int rows = (inputs.size() + perRow - 1) / perRow;
        List<Slot> slots = new ArrayList<>();
        for (int i = 0; i < inputs.size(); i++) {
            slots.add(new Slot((i % perRow) * SLOT, (i / perRow) * SLOT, stacks(inputs.get(i)), false));
        }
        int h = rows * SLOT;
        int arrowX = perRow * SLOT + GAP;
        slots.add(new Slot(arrowX + ARROW_W + GAP, h / 2 - SLOT / 2, List.of(result), true));
        return new Shown(arrowX + ARROW_W + GAP + SLOT, h, slots, arrowX, h / 2 - ARROW_H / 2, -1, -1);
    }

    private static List<ItemStack> stacks(Ingredient ingredient) {
        return ingredient == null || ingredient.isEmpty() ? List.of() : List.of(ingredient.getItems());
    }

    /** What a slot shows now: an ingredient with several items steps through them, one a second. */
    static ItemStack current(Slot slot, int index) {
        if (slot.stacks().isEmpty()) {
            return ItemStack.EMPTY;
        }
        long step = UiFx.nowMs() / 1000L;
        return slot.stacks().get((int) ((step + index) % slot.stacks().size()));
    }

    /** Paint {@code piece} with its top-left at {@code (x, y)}, adding each filled slot to {@code hits}. */
    static void draw(GuiGraphics graphics, RichLayout.RecipePiece piece, int x, int y, List<RichDraw.ItemHit> hits) {
        Optional<Shown> shown = resolve(piece.recipe(), piece.column());
        if (shown.isEmpty()) {
            return;
        }
        Shown s = shown.get();
        for (int i = 0; i < s.slots().size(); i++) {
            Slot slot = s.slots().get(i);
            int sx = x + slot.x();
            int sy = y + slot.y();
            slotFrame(graphics, sx, sy, slot.output());
            ItemStack stack = current(slot, i);
            if (!stack.isEmpty()) {
                graphics.renderItem(stack, sx + 1, sy + 1);
                graphics.renderItemDecorations(Minecraft.getInstance().font, stack, sx + 1, sy + 1);
                hits.add(new RichDraw.ItemHit(sx + 1, sy + 1, 16, 16, stack.getItem(), stack));
            }
        }
        graphics.blitSprite(ARROW, x + s.arrowX(), y + s.arrowY(), ARROW_W, ARROW_H);
        if (s.flameX() >= 0) {
            graphics.blitSprite(FLAME, x + s.flameX(), y + s.flameY(), 14, 14);
        }
    }

    /** A slot like the inventory's: dark top-left edge, light bottom-right edge, gray inside. */
    private static void slotFrame(GuiGraphics graphics, int x, int y, boolean output) {
        MockChrome.box(graphics, x, y, SLOT, SLOT, 0xFF8B8B8B);
        MockChrome.box(graphics, x, y, SLOT - 1, 1, 0xFF373737);
        MockChrome.box(graphics, x, y, 1, SLOT - 1, 0xFF373737);
        MockChrome.box(graphics, x + 1, y + SLOT - 1, SLOT - 1, 1, 0xFFFFFFFF);
        MockChrome.box(graphics, x + SLOT - 1, y + 1, 1, SLOT - 1, 0xFFFFFFFF);
        if (output) {
            MockChrome.frame(graphics, x - 1, y - 1, SLOT + 2, SLOT + 2, QuestColors.REWARD);
        }
    }
}
