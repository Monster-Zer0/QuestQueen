package dev.aof.questqueen.client;

import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.Tile;
import dev.aof.questqueen.data.reward.ItemReward;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for the scrollable log (1.1.158).
 *
 * <p>The log used to render a single pass and truncate with "..."; it now flattens into rows that the wheel
 * scrolls. These tests exercise the row builder directly — it deliberately avoids {@code ItemStack} and any
 * registry so it runs without bootstrapping Minecraft (see {@code LogRow.stack()}).
 */
class LogRowLayoutTest {
    /**
     * The row builder is package-visible. The icon resolver is stubbed to null so nothing touches
     * {@code BuiltInRegistries} and the test runs without bootstrapping Minecraft.
     */
    private static List<?> completedRows(Chapter chapter, int startY, java.util.function.Predicate<Tile> done) {
        return QuestBookScreen.completedTileRows(chapter, startY, done, reward -> null);
    }

    private static int yOf(Object row) throws Exception {
        var m = row.getClass().getDeclaredMethod("y");
        m.setAccessible(true);
        return (int) m.invoke(row);
    }

    private static String textOf(Object row) throws Exception {
        var m = row.getClass().getDeclaredMethod("text");
        m.setAccessible(true);
        return (String) m.invoke(row);
    }

    private static Chapter chapterWith(List<Tile> tiles) {
        return new Chapter(ResourceLocation.parse("questqueen:log"), "Log", tiles, List.of());
    }

    @Test
    void incompleteTilesContributeNoRows() throws Exception {
        Chapter chapter = chapterWith(List.of(Tile.blank("a", 0, 0), Tile.blank("b", 1, 0)));
        assertTrue(completedRows(chapter, 40, tile -> false).isEmpty(),
                "nothing completed means nothing to report");
    }

    @Test
    void eachCompletedTileContributesAHeaderAndRewardLine() throws Exception {
        Chapter chapter = chapterWith(List.of(
                Tile.blank("a", 0, 0).withRewards(List.of(
                        new ItemReward(ResourceLocation.parse("minecraft:diamond"), 1)))));
        List<?> rows = completedRows(chapter, 40, tile -> true);
        assertEquals(2, rows.size(), "one header row plus one reward row");
        assertEquals(40, yOf(rows.get(0)));
        assertEquals(51, yOf(rows.get(1)), "reward line sits 11px under its header");
        assertTrue(textOf(rows.get(0)).startsWith("LOG"), "header is CHAPTER / TILE: " + textOf(rows.get(0)));
        assertTrue(textOf(rows.get(1)).startsWith("reward"), "second row describes the reward");
    }

    @Test
    void rewardFreeTilesSaySoRatherThanOmittingTheLine() throws Exception {
        Chapter chapter = chapterWith(List.of(Tile.blank("a", 0, 0)));
        List<?> rows = completedRows(chapter, 0, tile -> true);
        assertEquals(2, rows.size());
        assertEquals("no reward", textOf(rows.get(1)));
    }

    @Test
    void rowsAdvanceMonotonicallySoAbsoluteScrollMathsHolds() throws Exception {
        // The scroll offset is subtracted from each row's content-space y, so the layout must be strictly
        // increasing or a row could be skipped as "above the viewport" when it is actually visible.
        Chapter chapter = chapterWith(List.of(
                Tile.blank("a", 0, 0).withRewards(List.of(
                        new ItemReward(ResourceLocation.parse("minecraft:diamond"), 1),
                        new ItemReward(ResourceLocation.parse("minecraft:emerald"), 2))),
                Tile.blank("b", 1, 0)));
        List<?> rows = completedRows(chapter, 20, tile -> true);
        assertTrue(rows.size() >= 5, "2 tiles: header+2 rewards, header+no reward");
        int prev = Integer.MIN_VALUE;
        for (Object row : rows) {
            int y = yOf(row);
            assertTrue(y > prev, "row y must increase: " + y + " after " + prev);
            prev = y;
        }
    }

    @Test
    void aTilesRewardsProduceOneRowEachAndDescribeIsUsed() throws Exception {
        Chapter chapter = chapterWith(List.of(Tile.blank("a", 0, 0).withRewards(List.of(
                new ItemReward(ResourceLocation.parse("minecraft:diamond"), 3),
                new ItemReward(ResourceLocation.parse("minecraft:emerald"), 8)))));
        List<?> rows = completedRows(chapter, 0, tile -> true);
        assertEquals(3, rows.size(), "header plus one row per reward");
        assertTrue(textOf(rows.get(1)).contains("3x diamond"), textOf(rows.get(1)));
        assertTrue(textOf(rows.get(2)).contains("8x emerald"), textOf(rows.get(2)));
    }

    @Test
    void iconIsOptionalSoNoRowForcesAGameBootstrap() throws Exception {
        Chapter chapter = chapterWith(List.of(Tile.blank("a", 0, 0)));
        List<?> rows = completedRows(chapter, 0, tile -> true);
        var m = rows.get(0).getClass().getDeclaredMethod("stack");
        m.setAccessible(true);
        assertEquals(Optional.empty(), m.invoke(rows.get(0)),
                "rows carry an Optional icon, never an ItemStack sentinel");
    }
}
