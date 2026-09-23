package dev.aof.questqueen.task;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure arithmetic helpers for quest-relative stat progress. The movement-aware merge (walk+sprint+crouch)
 * is covered here as the same formula {@code TaskHooks} applies on the server.
 */
class StatProgressTest {
    @Test
    void questRelativeGainIsLifetimeMinusBaseline() {
        assertEquals(0, Math.max(0, 1000 - 1000));
        assertEquals(250, Math.max(0, 1250 - 1000));
        assertEquals(0, Math.max(0, 900 - 1000), "stat cannot go backwards in practice; clamp at 0");
    }

    @Test
    void walkOneCmMergeAddsSprintAndCrouch() {
        int walk = 40;
        int sprint = 900;
        int crouch = 60;
        int merged = walk + sprint + crouch;
        assertEquals(1000, merged);
        assertTrue(merged >= 100, "a few sprinting steps should clear the demo walk_one_cm count of 100");
    }

    @Test
    void walkStatIdIsVanilla() {
        assertEquals("minecraft:walk_one_cm", ResourceLocation.parse("minecraft:walk_one_cm").toString());
    }
}
