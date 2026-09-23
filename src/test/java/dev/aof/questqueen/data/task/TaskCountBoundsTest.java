package dev.aof.questqueen.data.task;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.aof.questqueen.data.reward.ChoiceReward;
import dev.aof.questqueen.data.reward.ItemReward;
import dev.aof.questqueen.data.reward.Reward;
import dev.aof.questqueen.data.reward.RewardFactory;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the task-count fixes (1.1.155).
 *
 * <p>A non-positive {@code count} used to parse and then complete a task on its first increment
 * ({@code done = min(required, current + amount) >= required} is trivially true for 0). Counts are now
 * bounded to >= 1 by {@link Task#COUNT}.
 */
class TaskCountBoundsTest {
    private static Optional<Task> parse(String json) {
        return Task.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result();
    }

    @Test
    void zeroAndNegativeCountsAreRejected() {
        assertTrue(parse("""
                {"type":"kill","entity":"minecraft:zombie","count":0}""").isEmpty(), "count 0 must not parse");
        assertTrue(parse("""
                {"type":"kill","entity":"minecraft:zombie","count":-5}""").isEmpty(), "negative count must not parse");
        assertTrue(parse("""
                {"type":"obtain","item":"minecraft:dirt","count":0}""").isEmpty());
        assertTrue(parse("""
                {"type":"stat","stat":"minecraft:walk_one_cm","count":0}""").isEmpty());
        assertTrue(parse("""
                {"type":"xp_levels","levels":0}""").isEmpty(), "levels 0 must not parse");
        assertTrue(parse("""
                {"type":"raid","waves":0}""").isEmpty(), "waves 0 must not parse");
    }

    @Test
    void positiveCountsSurviveAndDefaultsToOne() {
        assertEquals(3, parse("""
                {"type":"kill","entity":"minecraft:zombie","count":3}""").orElseThrow().required());
        assertEquals(1, parse("""
                {"type":"kill","entity":"minecraft:zombie"}""").orElseThrow().required());
        assertEquals(1, parse("""
                {"type":"checkmark"}""").orElseThrow().required());
    }

    @Test
    void cyclingTypesKeepsTheCountOnlyWhereItIsHonoured() {
        // TYPES order is ... obtain, submit ... and ... fluid, xp_levels ... — both pairs are counted, so
        // the authoring count must survive the cycle.
        assertEquals(5, TaskFactory.next(new ObtainTask(ResourceLocation.parse("minecraft:dirt"), 5)).required());
        assertEquals(3, TaskFactory.next(new FluidTask(ResourceLocation.parse("minecraft:lava"), 3)).required());

        // submit -> item_tag is counted too, confirming the count is not dropped on the way out.
        assertEquals(9, TaskFactory.next(new SubmitTask(ResourceLocation.parse("minecraft:apple"), 9)).required());

        // A binary type reports the default rather than a count it would silently discard: the old code
        // passed 7 into a withCount that returns `this`, and the authoring count vanished with no warning.
        Task binary = TaskFactory.next(new AdvancementTask(ResourceLocation.parse("minecraft:story/root")));
        assertEquals(1, binary.required());
    }

    @Test
    void withCountIsIdentityForBinaryTypesAndNewInstanceForCounted() {
        // This is the contract TaskFactory.next relies on to tell the two families apart.
        ObtainTask counted = new ObtainTask(ResourceLocation.parse("minecraft:dirt"), 1);
        assertNotSame(counted, counted.withCount(4));
        assertEquals(4, counted.withCount(4).required());

        CheckmarkTask binary = new CheckmarkTask();
        assertSame(binary, binary.withCount(4), "a binary type has no count to carry");
    }

    @Test
    void choiceRetargetKeepsThePickedOptionCount() {
        ChoiceReward choice = new ChoiceReward(List.of(
                new ItemReward(ResourceLocation.parse("minecraft:diamond"), 8),
                new ItemReward(ResourceLocation.parse("minecraft:emerald"), 16)));
        Reward retargeted = RewardFactory.retarget(choice, ResourceLocation.parse("minecraft:gold_ingot"));
        ItemReward picked = ((ChoiceReward) retargeted).options().getFirst();
        assertEquals(ResourceLocation.parse("minecraft:gold_ingot"), picked.item());
        assertEquals(8, picked.count(), "retargeting must preserve the count the author set");
    }
}
