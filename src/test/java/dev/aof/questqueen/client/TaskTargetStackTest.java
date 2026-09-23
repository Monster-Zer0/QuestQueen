package dev.aof.questqueen.client;

import dev.aof.questqueen.data.task.BiomeTask;
import dev.aof.questqueen.data.task.CheckmarkTask;
import dev.aof.questqueen.data.task.FluidTask;
import dev.aof.questqueen.data.task.InteractBlockTask;
import dev.aof.questqueen.data.task.ObtainTask;
import dev.aof.questqueen.data.task.SubmitTask;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for the task-row target icon (1.1.157).
 *
 * <p>Only {@link QuestBookScreen#opensRecipe} is unit-testable here. {@code taskStack} and
 * {@code JeiBridge} cannot be: merely touching {@code ItemStack.EMPTY} runs {@code ItemStack}'s static
 * initialiser, which registers into {@code BuiltInRegistries} and throws "Not bootstrapped" without the
 * FML loader — the same wall the reward codecs hit. Id resolution is therefore verified in-game, and what
 * is pinned here is the decision logic: which tasks offer a JEI target, and recipes vs usages.
 */
class TaskTargetStackTest {
    @Test
    void recipeLookupOnlyForMakeOrFindTasks() {
        assertTrue(QuestBookScreen.opensRecipe(new ObtainTask(ResourceLocation.parse("minecraft:dirt"), 1)),
                "FIND/obtain should open recipes");
        assertTrue(QuestBookScreen.opensRecipe(new SubmitTask(ResourceLocation.parse("minecraft:apple"), 1)),
                "GIVE/submit should open recipes");
        assertFalse(QuestBookScreen.opensRecipe(new InteractBlockTask(ResourceLocation.parse("minecraft:furnace"), 1)),
                "an interact task opens usages, not recipes");
        assertFalse(QuestBookScreen.opensRecipe(new FluidTask(ResourceLocation.parse("minecraft:lava"), 1)),
                "a fluid task opens usages");
        assertFalse(QuestBookScreen.opensRecipe(new BiomeTask(ResourceLocation.parse("minecraft:plains"))));
        assertFalse(QuestBookScreen.opensRecipe(new CheckmarkTask()));
        assertFalse(QuestBookScreen.opensRecipe(null));
    }
}
