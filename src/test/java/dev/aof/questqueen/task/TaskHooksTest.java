package dev.aof.questqueen.task;

import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.Tile;
import dev.aof.questqueen.progress.ProgressSnapshot;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * First coverage of {@link TaskHooks}.
 *
 * <p>{@code docs/LOGIC-AUDIT.md} records that no test touches {@code TaskHooks} at all. Most of the class is
 * event handlers over a {@code ServerPlayer}, which cannot be driven without a running server — so the two
 * decisions that every one of the nineteen task types actually depends on were lifted out of the handlers
 * into pure static methods and are pinned here:
 *
 * <ul>
 *   <li>{@link TaskHooks#questRelativeGain} — the stat task maths shipped in 1.1.160, where "walk 1000 cm"
 *       means from unlock rather than from the beginning of the world.
 *   <li>{@link TaskHooks#stillOpen} — the gate every scan passes before it may credit progress. Getting it
 *       wrong either re-credits a finished task or strands a live one behind its own tile.
 * </ul>
 */
class TaskHooksTest {
    private static Chapter chapter() {
        return new Chapter(ResourceLocation.parse("questqueen:t"),
                "T", List.of(Tile.blank("a", 0, 0)), List.of());
    }

    private static ProgressSnapshot snapshot(Set<String> completedTasks, Set<String> completedTiles) {
        return new ProgressSnapshot("solo", false, Map.of(), completedTasks, completedTiles,
                Set.of(), Set.of(), Set.of(), List.of(), java.util.Optional.empty(), java.util.Optional.empty());
    }

    @Test
    void statProgressIsMeasuredFromTheUnlockBaseline() {
        // Baseline captured on first sight: no progress yet, however much lifetime travel there is.
        assertEquals(0, TaskHooks.questRelativeGain(40_000, 40_000));
        // Everything walked since the task became active counts.
        assertEquals(2_500, TaskHooks.questRelativeGain(42_500, 40_000));
    }

    @Test
    void statProgressNeverGoesBackwards() {
        // A reset or a stale snapshot can hand us a lower lifetime value; that is not negative progress.
        assertEquals(0, TaskHooks.questRelativeGain(900, 1_000));
        assertEquals(0, TaskHooks.questRelativeGain(-1, 0));
    }

    @Test
    void aTaskIsScannedWhileNeitherItNorItsTileIsComplete() {
        ProgressSnapshot fresh = snapshot(Set.of(), Set.of());
        assertTrue(TaskHooks.stillOpen(fresh, chapter(), Tile.blank("a", 0, 0), 0));
        assertTrue(TaskHooks.stillOpen(fresh, chapter(), Tile.blank("a", 0, 0), 3));
    }

    @Test
    void aCompletedTileStopsEveryOneOfItsTasksBeingScanned() {
        ProgressSnapshot done = snapshot(Set.of(), Set.of("questqueen:t/a"));
        assertFalse(TaskHooks.stillOpen(done, chapter(), Tile.blank("a", 0, 0), 0));
        assertFalse(TaskHooks.stillOpen(done, chapter(), Tile.blank("a", 0, 0), 3),
                "the tile key, not the task key, is what closes a finished quest");
    }

    @Test
    void aCompletedTaskIndexGatesOnlyThatIndexOfTheTile() {
        ProgressSnapshot partial = snapshot(Set.of("questqueen:t/a/0"), Set.of());
        assertFalse(TaskHooks.stillOpen(partial, chapter(), Tile.blank("a", 0, 0), 0));
        assertTrue(TaskHooks.stillOpen(partial, chapter(), Tile.blank("a", 0, 0), 1),
                "a multi-task quest must keep scanning its unfinished rows");
        assertTrue(TaskHooks.stillOpen(partial, chapter(), Tile.blank("a", 0, 0), 10));
    }

    @Test
    void taskKeysAreScopedToTheirOwnTileAndChapter() {
        // A key collision would credit the wrong quest, so pin the scoping explicitly.
        ProgressSnapshot other = snapshot(Set.of("questqueen:other/a/0", "questqueen:t/b/0"), Set.of());
        assertTrue(TaskHooks.stillOpen(other, chapter(), Tile.blank("a", 0, 0), 0),
                "another chapter's identically named tile must not close this one");
    }
}
