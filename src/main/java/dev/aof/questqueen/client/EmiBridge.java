package dev.aof.questqueen.client;

import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * Optional EMI bridge: open an item's recipe or usage page from a quest task row.
 * Reflection only, so the mod jar does not depend on EMI.
 */
public final class EmiBridge {
    public static final String MOD_ID = "emi";

    private static final String API = "dev.emi.emi.api.EmiApi";
    private static final String STACK = "dev.emi.emi.api.stack.EmiStack";
    private static final String INGREDIENT = "dev.emi.emi.api.stack.EmiIngredient";

    private static final boolean PRESENT = ModList.get().isLoaded(MOD_ID);

    private EmiBridge() {
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
            Class<?> ingredient = Class.forName(INGREDIENT);
            Object emiStack = Class.forName(STACK).getMethod("of", ItemStack.class).invoke(null, stack);
            Method display = Class.forName(API).getMethod(recipes ? "displayRecipes" : "displayUses", ingredient);
            display.invoke(null, emiStack);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }
}
