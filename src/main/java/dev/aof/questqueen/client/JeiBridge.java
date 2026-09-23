package dev.aof.questqueen.client;

import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;

/**
 * Optional JEI bridge: open an item's recipe / usage page from a quest task row.
 *
 * <p>JEI is a soft dependency, so everything here is reflective and every failure path is a silent
 * no-op — the same shape as {@code ProgressiveStagesCompat}. Only three JEI members are needed:
 * {@code Internal.getOptionalJeiRuntime()}, {@code IJeiRuntime.getRecipeManager()},
 * {@code IJeiRuntime.getRecipesGui()}, plus {@code IJeiHelpers.getFocusFactory()}. JEI exposes no public
 * runtime accessor and this version dropped the "show me" chat command, so reaching JEI's own runtime
 * holder is the narrowest way in.
 */
public final class JeiBridge {
    public static final String MOD_ID = "jei";

    private static final String INTERNAL = "mezz.jei.common.Internal";
    private static final String ITEM_STACK_TYPE = "mezz.jei.api.constants.VanillaTypes";
    private static final String ROLE = "mezz.jei.api.recipe.RecipeIngredientRole";

    private static final boolean PRESENT = ModList.get().isLoaded(MOD_ID);

    private JeiBridge() {
    }

    public static boolean present() {
        return PRESENT;
    }

    /** True when a recipe/usage page can be opened right now (JEI loaded and its runtime is up). */
    public static boolean available() {
        return PRESENT && runtime() != null;
    }

    private static Object runtime() {
        try {
            Class<?> internal = Class.forName(INTERNAL);
            Method get = internal.getMethod("getOptionalJeiRuntime");
            Object optional = get.invoke(null);
            if (optional instanceof java.util.Optional<?> opt && opt.isPresent()) {
                return opt.get();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }

    /**
     * Open JEI at {@code stack}. {@code recipes} shows how to make it (for "obtain/submit this item"),
     * otherwise JEI shows where it comes from / what it is used for.
     */
    public static boolean show(ItemStack stack, boolean recipes) {
        if (!PRESENT || stack == null || stack.isEmpty()) {
            return false;
        }
        try {
            Object rt = runtime();
            if (rt == null) {
                return false;
            }
            Object recipeManager = rt.getClass().getMethod("getRecipeManager").invoke(rt);
            Object recipesGui = rt.getClass().getMethod("getRecipesGui").invoke(rt);
            Object helpers = rt.getClass().getMethod("getJeiHelpers").invoke(rt);
            Object focusFactory = helpers.getClass().getMethod("getFocusFactory").invoke(helpers);

            Object itemStackType = Class.forName(ITEM_STACK_TYPE).getField("ITEM_STACK").get(null);
            @SuppressWarnings({"unchecked", "rawtypes"})
            Object role = Enum.valueOf((Class) Class.forName(ROLE), recipes ? "OUTPUT" : "INPUT");

            Method createFocus = focusFactory.getClass().getMethod("createFocus",
                    Class.forName(ROLE), Class.forName("mezz.jei.api.ingredients.IIngredientType"), Object.class);
            Object focus = createFocus.invoke(focusFactory, role, itemStackType, stack);

            Method show = recipesGui.getClass().getMethod("show", Class.forName("mezz.jei.api.recipe.IFocus"));
            show.invoke(recipesGui, focus);
            return true;
        } catch (Throwable ignored) {
            // JEI absent, runtime not ready, or API drift — never break the book over this.
            return false;
        }
    }
}
