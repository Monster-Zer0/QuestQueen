package dev.aof.questqueen.task;

import dev.aof.questqueen.client.ClientQuestState;
import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.Tile;
import dev.aof.questqueen.data.task.ItemTagTask;
import dev.aof.questqueen.data.task.ObtainTask;
import dev.aof.questqueen.progress.ProgressSnapshot;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Obtain and item_tag store the live inventory count, not only the finishing write.
 *
 * <p>A player with 7 logs and 23 cobblestone used to read 0/16 and 0/32 until the stack hit the
 * requirement, because the scan called {@code setCompleted} and never the value write. The count
 * itself is still {@code countItem}; these tests pin what that count is stored as, when a scan runs,
 * and that submit is still a hand-in. The same behaviour on a real server — mock player, event handlers,
 * SQLite — is {@code dev.aof.questqueen.gametest.TaskProgressGameTests} under {@code runGameTestServer}.
 */
class InventoryProgressTest {
    @Test
    void sevenLogsStoreSevenOfSixteenAndSixteenCompletes() {
        Row logs = new Row();
        logs.scan(7, 16);
        assertEquals(7, logs.value);
        assertFalse(logs.completed);
        logs.scan(16, 16);
        assertEquals(16, logs.value);
        assertTrue(logs.completed);
    }

    @Test
    void cobblestoneStoresTwentyThreeThenCompletesAtThirtyTwo() {
        Row cobble = new Row();
        cobble.scan(23, 32);
        assertEquals(23, cobble.value);
        assertFalse(cobble.completed);
        cobble.scan(32, 32);
        assertEquals(32, cobble.value);
        assertTrue(cobble.completed);
    }

    @Test
    void anOpenCountTracksDownAndAFinishedCountDoesNot() {
        Row logs = new Row();
        logs.scan(7, 16);
        logs.scan(3, 16);
        assertEquals(3, logs.value);
        assertFalse(logs.completed);
        logs.scan(16, 16);
        logs.scan(0, 16);
        assertEquals(16, logs.value);
        assertTrue(logs.completed, "dropping logs after the task is done must not reopen it");
    }

    @Test
    void teamMembersStoreTheSameSummedCount() {
        // One shared row, two members scanning in turn: 10 logs and 0 logs used to flip it 10, 0, 10.
        Row logs = new Row();
        int total = TaskHooks.teamHeld(List.of(10, 0));
        logs.scan(total, 16);
        assertEquals(10, logs.value);
        logs.scan(TaskHooks.teamHeld(List.of(10, 0)), 16);
        assertEquals(10, logs.value, "the second member's scan must not overwrite the first");
        logs.scan(TaskHooks.teamHeld(List.of(10, 6)), 16);
        assertTrue(logs.completed, "stacks held across the team complete the task together");
    }

    @Test
    void anUncountableTaskStaysUncountableForATeam() {
        assertEquals(-1, TaskHooks.teamHeld(List.of(-1, -1)));
        assertEquals(7, TaskHooks.teamHeld(List.of(7)));
    }

    @Test
    void extraStacksClampAtTheRequirement() {
        assertEquals(16, TaskHooks.storedInventoryValue(27, 16));
        assertEquals(0, TaskHooks.storedInventoryValue(0, 16));
        assertEquals(0, TaskHooks.storedInventoryValue(-1, 16));
    }

    @Test
    void dirtCanFinishWhileLogsAndCobbleStayPartial() {
        Row logs = new Row();
        Row cobble = new Row();
        Row dirt = new Row();
        logs.scan(7, 16);
        cobble.scan(23, 32);
        dirt.scan(27, 16);
        assertEquals(7, logs.value);
        assertFalse(logs.completed);
        assertEquals(23, cobble.value);
        assertFalse(cobble.completed);
        assertEquals(16, dirt.value);
        assertTrue(dirt.completed);
    }

    @Test
    void anUnchangedCountIsNotWrittenAgain() {
        assertFalse(TaskHooks.inventoryWriteNeeded(7, 7), "a player standing still must not reach the database");
        assertFalse(TaskHooks.inventoryWriteNeeded(0, 0), "an empty inventory with no stored row writes nothing");
        assertTrue(TaskHooks.inventoryWriteNeeded(7, 9), "a pickup is written");
        assertTrue(TaskHooks.inventoryWriteNeeded(7, 3), "a drop below the requirement is written");
        assertTrue(TaskHooks.inventoryWriteNeeded(15, 16), "reaching the requirement is written, so it completes");
    }

    @Test
    void pickupUpdatesBetweenGeneralScans() {
        assertFalse(TaskHooks.inventoryScanRuns(1, false));
        assertFalse(TaskHooks.inventoryScanRuns(19, false));
        assertTrue(TaskHooks.inventoryScanRuns(20, false));
        assertTrue(TaskHooks.inventoryScanRuns(7, true), "a pickup must not wait for the 20-tick poll");
    }

    @Test
    void submitIsNotAnInventoryCount() {
        assertEquals(List.of("obtain", "item_tag"), TaskHooks.COUNTED_INVENTORY_TYPES);
        assertFalse(TaskHooks.COUNTED_INVENTORY_TYPES.contains("submit"));
    }

    @Test
    void theBookReadsTheStoredPartialCounts() {
        ResourceLocation chapterId = ResourceLocation.parse("skylore:crash_landing_protocol");
        Tile tile = Tile.blank("gather_wood_stone_and_dirt", 0, 0).withTasks(List.of(
                new ItemTagTask(ResourceLocation.parse("minecraft:logs"), 16),
                new ObtainTask(ResourceLocation.parse("minecraft:cobblestone"), 32),
                new ObtainTask(ResourceLocation.parse("minecraft:dirt"), 16)));
        Chapter chapter = new Chapter(chapterId, "Crash Landing", List.of(tile), List.of());
        String quest = chapterId + "/" + tile.id();
        ProgressSnapshot previous = ClientQuestState.progress;
        ClientQuestState.progress = new ProgressSnapshot("solo", false, Map.of(
                quest + "/0", 7,
                quest + "/1", 23,
                quest + "/2", 16),
                Set.of(quest + "/2"), Set.of(), Set.of(), Set.of(), Set.of(), List.of(),
                Optional.empty(), Optional.empty());
        try {
            assertEquals("COLLECT 7/16", ClientQuestState.taskProgressLabel(chapter, tile, 0));
            assertEquals("OBTAIN 23/32", ClientQuestState.taskProgressLabel(chapter, tile, 1));
            assertEquals("OBTAIN 16/16", ClientQuestState.taskProgressLabel(chapter, tile, 2));
            assertEquals(7, ClientQuestState.progress.value(quest, "0"));
            assertEquals(23, ClientQuestState.progress.value(quest, "1"));
        } finally {
            ClientQuestState.progress = previous;
        }
    }

    /**
     * One open task row. A completed row ignores later scans, matching {@link TaskHooks#stillOpen}.
     */
    private static final class Row {
        private int value;
        private boolean completed;

        private void scan(int have, int required) {
            if (completed) {
                return;
            }
            value = TaskHooks.storedInventoryValue(have, required);
            completed = value >= required;
        }
    }
}
