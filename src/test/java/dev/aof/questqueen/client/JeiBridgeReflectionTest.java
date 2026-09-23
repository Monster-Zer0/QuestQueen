package dev.aof.questqueen.client;

import org.junit.jupiter.api.Test;

import java.io.File;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Guards the JEI reflection in {@link JeiBridge} against API drift.
 *
 * <p>JEI is a soft dependency reached reflectively, so a renamed method would silently disable the feature
 * with no compile error. This test loads the real JEI jar out of the dev instance if it is there and asserts
 * every member {@code JeiBridge} invokes still exists with a compatible shape. It skips (rather than fails)
 * when no JEI jar is present, so it stays valid on a machine without the pack.
 */
class JeiBridgeReflectionTest {
    private static final Path MODS = Path.of("G:\\Minecraft\\New folder\\Instances\\Skylore Dev 1.21\\mods");

    private static File findJeiJar() {
        if (!Files.isDirectory(MODS)) {
            return null;
        }
        try (Stream<Path> files = Files.list(MODS)) {
            return files.map(Path::toFile)
                    .filter(File::isFile)
                    .filter(f -> {
                        String n = f.getName().toLowerCase();
                        return n.startsWith("jei-") && n.endsWith(".jar");
                    })
                    .findFirst()
                    .orElse(null);
        } catch (Exception ignored) {
            return null;
        }
    }

    @Test
    void everyReflectedJeiMemberStillExists() throws Exception {
        File jar = findJeiJar();
        assumeTrue(jar != null, "no JEI jar in the dev instance mods folder — skipping API drift check");

        try (URLClassLoader loader = new URLClassLoader(new URL[]{jar.toURI().toURL()}, getClass().getClassLoader())) {
            Class<?> internal = loader.loadClass("mezz.jei.common.Internal");
            Class<?> runtime = loader.loadClass("mezz.jei.api.runtime.IJeiRuntime");
            Class<?> recipesGui = loader.loadClass("mezz.jei.api.runtime.IRecipesGui");
            Class<?> helpers = loader.loadClass("mezz.jei.api.helpers.IJeiHelpers");
            Class<?> focusFactory = loader.loadClass("mezz.jei.api.recipe.IFocusFactory");
            Class<?> role = loader.loadClass("mezz.jei.api.recipe.RecipeIngredientRole");
            Class<?> ingredientType = loader.loadClass("mezz.jei.api.ingredients.IIngredientType");
            Class<?> focus = loader.loadClass("mezz.jei.api.recipe.IFocus");
            Class<?> vanillaTypes = loader.loadClass("mezz.jei.api.constants.VanillaTypes");

            // Entry point into JEI's runtime.
            assertTrue(hasMethod(internal, "getOptionalJeiRuntime"), "Internal.getOptionalJeiRuntime() moved");

            // What JeiBridge.show() calls on the runtime.
            assertTrue(hasMethod(runtime, "getRecipeManager"), "IJeiRuntime.getRecipeManager() moved");
            assertTrue(hasMethod(runtime, "getRecipesGui"), "IJeiRuntime.getRecipesGui() moved");
            assertTrue(hasMethod(runtime, "getJeiHelpers"), "IJeiRuntime.getJeiHelpers() moved");
            assertTrue(hasMethod(helpers, "getFocusFactory"), "IJeiHelpers.getFocusFactory() moved");

            // Focus construction and display. show() is void, so existence is all that can be asserted.
            assertTrue(hasMethod(focusFactory, "createFocus", role, ingredientType, Object.class),
                    "IFocusFactory.createFocus(role, type, value) changed shape");
            assertTrue(exists(recipesGui, "show", focus), "IRecipesGui.show(IFocus) changed shape");

            // Roles and the item ingredient type the bridge names.
            assertTrue(hasEnumConstant(role, "OUTPUT") && hasEnumConstant(role, "INPUT"),
                    "RecipeIngredientRole lost OUTPUT/INPUT");
            assertTrue(vanillaTypes.getField("ITEM_STACK") != null, "VanillaTypes.ITEM_STACK is gone");
        }
    }

    private static boolean hasMethod(Class<?> owner, String name, Class<?>... params) {
        try {
            Method m = owner.getMethod(name, params);
            return m.getReturnType() != void.class;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    /** For methods the bridge invokes purely for their side effect (void return callbacks). */
    private static boolean exists(Class<?> owner, String name, Class<?>... params) {
        try {
            owner.getMethod(name, params);
            return true;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static boolean hasEnumConstant(Class<?> enumClass, String name) {
        try {
            Enum.valueOf((Class) enumClass, name);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
