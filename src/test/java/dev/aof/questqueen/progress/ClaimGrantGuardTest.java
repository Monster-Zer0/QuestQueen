package dev.aof.questqueen.progress;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Claims write "claimed" first and then grant each reward through {@code grantGuarded}. A reward that throws
 * used to abort the claim after earlier rewards were already handed out, leaving the tile claimable, so the
 * next click granted those rewards a second time.
 */
class ClaimGrantGuardTest {
    private static final ResourceLocation CHAPTER = ResourceLocation.parse("questqueen:guard");

    @Test
    void aThrowingRewardDoesNotStopTheOthers() {
        List<String> granted = new ArrayList<>();
        assertTrue(ProgressService.grantGuarded("tester", CHAPTER, "tile", "item", () -> granted.add("item")));
        assertFalse(ProgressService.grantGuarded("tester", CHAPTER, "tile", "loot_table", () -> {
            throw new IllegalStateException("broken loot table");
        }));
        assertTrue(ProgressService.grantGuarded("tester", CHAPTER, "tile", "xp", () -> granted.add("xp")));
        assertEquals(List.of("item", "xp"), granted);
    }
}
