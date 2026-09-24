package dev.aof.questqueen.client;

import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

/**
 * Optional REI bridge: open an item's recipe or usage page from a quest task row.
 * Reflection only, so the mod jar does not pull in Architectury or Cloth Config.
 */
public final class ReiBridge {
    public static final String MOD_ID = "roughlyenoughitems";

    private static final String BUILDER = "me.shedaniel.rei.api.client.view.ViewSearchBuilder";
    private static final String ENTRY_STACKS = "me.shedaniel.rei.api.common.util.EntryStacks";
    private static final String ENTRY_STACK = "me.shedaniel.rei.api.common.entry.EntryStack";

    private static final boolean PRESENT = ModList.get().isLoaded(MOD_ID);

    private ReiBridge() {
    }

    public static boolean present() {
        return PRESENT;
    }

    public static boolean available() {
        return PRESENT;
    }

    /**
     * @param recipes true opens recipes that produce the stack; false opens recipes that use it
     */
    public static boolean show(ItemStack stack, boolean recipes) {
        if (!PRESENT || stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            Object builder = Class.forName(BUILDER).getMethod("builder").invoke(null);
            Object entry = Class.forName(ENTRY_STACKS).getMethod("of", ItemStack.class).invoke(null, stack);
            String add = recipes ? "addRecipesFor" : "addUsagesFor";
            Object filled = builder.getClass().getMethod(add, Class.forName(ENTRY_STACK)).invoke(builder, entry);
            Object opened = filled.getClass().getMethod("open").invoke(filled);
            return Boolean.TRUE.equals(opened);
        } catch (Throwable ignored) {
            return false;
        }
    }
}
