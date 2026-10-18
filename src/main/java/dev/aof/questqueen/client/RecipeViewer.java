package dev.aof.questqueen.client;

import net.minecraft.world.item.ItemStack;

/**
 * Opens a quest item in whichever recipe viewer the pack installed.
 * EMI wins over REI, and REI over JEI, because EMI is often loaded in front of JEI.
 */
public final class RecipeViewer {
    public enum Kind {
        EMI, REI, JEI, NONE
    }

    private RecipeViewer() {
    }

    public static Kind active() {
        if (EmiBridge.present()) {
            return Kind.EMI;
        }
        if (ReiBridge.present()) {
            return Kind.REI;
        }
        if (JeiBridge.present()) {
            return Kind.JEI;
        }
        return Kind.NONE;
    }

    public static boolean loaded() {
        return active() != Kind.NONE;
    }

    public static boolean available() {
        return switch (active()) {
            case EMI -> EmiBridge.available();
            case REI -> ReiBridge.available();
            case JEI -> JeiBridge.available();
            case NONE -> false;
        };
    }

    /** Short name for tooltips. Empty when no viewer is installed. */
    public static String label() {
        return switch (active()) {
            case EMI -> "EMI";
            case REI -> "REI";
            case JEI -> "JEI";
            case NONE -> "";
        };
    }

    public static boolean show(ItemStack stack, boolean recipes) {
        return switch (active()) {
            case EMI -> EmiBridge.show(stack, recipes);
            case REI -> ReiBridge.show(stack, recipes);
            case JEI -> JeiBridge.show(stack, recipes);
            case NONE -> false;
        };
    }
}
