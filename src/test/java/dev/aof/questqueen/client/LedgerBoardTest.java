package dev.aof.questqueen.client;

import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.HiddenUntil;
import dev.aof.questqueen.data.Tile;
import dev.aof.questqueen.data.reward.ChoiceReward;
import dev.aof.questqueen.data.reward.ItemReward;
import dev.aof.questqueen.data.task.CheckmarkTask;
import dev.aof.questqueen.data.task.ObtainTask;
import dev.aof.questqueen.progress.ProgressSnapshot;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ledger board and sidebar: tile counts and bars, the claim cue, chapter stats, and the motion maths behind
 * the bar tween, the claim glint, the reward flight, the badge pop and the unlock descramble.
 */
class LedgerBoardTest {
    private static final ResourceLocation CH = ResourceLocation.parse("questqueen:ledger_board");
    private static final ItemReward BREAD = new ItemReward(ResourceLocation.parse("minecraft:bread"), 2);

    private ProgressSnapshot savedProgress;
    private boolean savedFx;
    private java.util.function.LongSupplier savedClock;

    @BeforeEach
    void save() {
        savedProgress = ClientQuestState.progress;
        savedFx = UiFx.enabled();
        savedClock = UiFx.clock;
        UiFx.setEnabled(true);
    }

    @AfterEach
    void restore() {
        ClientQuestState.progress = savedProgress;
        UiFx.setEnabled(savedFx);
        UiFx.clock = savedClock;
    }

    private static Tile tile(String id, List<dev.aof.questqueen.data.task.Task> tasks, List<dev.aof.questqueen.data.reward.Reward> rewards) {
        return Tile.blank(id, 0, 0).withTasks(tasks).withRewards(rewards);
    }

    private static void progress(Map<String, Integer> values, Set<String> tasks, Set<String> tiles) {
        ClientQuestState.progress = new ProgressSnapshot("solo", false, values, tasks, tiles, Set.of(), Set.of(),
                Set.of(), List.of(), Optional.empty(), Optional.empty());
    }

    @Test
    void fractionWeighsEachTaskByItsOwnCount() {
        Tile t = tile("gather", List.of(new ObtainTask(ResourceLocation.parse("minecraft:dirt"), 16), new CheckmarkTask()), List.of());
        Chapter chapter = new Chapter(CH, "Ledger", List.of(t), List.of());
        String q = CH + "/gather";
        progress(Map.of(q + "/0", 8), Set.of(), Set.of());
        assertEquals(8f / 17f, ClientQuestState.taskFraction(chapter, t), 1e-6);
        assertEquals("0/2", ClientQuestState.tileCountText(chapter, t));
        progress(Map.of(q + "/0", 8), Set.of(q + "/1"), Set.of());
        assertEquals(9f / 17f, ClientQuestState.taskFraction(chapter, t), 1e-6);
        assertEquals("1/2", ClientQuestState.tileCountText(chapter, t));
        progress(Map.of(), Set.of(), Set.of(CH + "/gather"));
        assertEquals(1f, ClientQuestState.taskFraction(chapter, t), 1e-6, "a finished tile is full");
    }

    @Test
    void cardLeaderDotsLightUpWithEachTasksProgress() {
        Tile t = tile("find", List.of(new ObtainTask(ResourceLocation.parse("minecraft:dirt"), 16), new CheckmarkTask()), List.of());
        Chapter chapter = new Chapter(CH, "Ledger", List.of(t), List.of());
        String q = CH + "/find";
        progress(Map.of(q + "/0", 8), Set.of(), Set.of());
        assertEquals(0.5f, ClientQuestState.taskFraction(chapter, t, 0), 1e-6, "8/16 lights half the dots");
        assertEquals(0f, ClientQuestState.taskFraction(chapter, t, 1), 1e-6);
        progress(Map.of(q + "/0", 40), Set.of(q + "/1"), Set.of());
        assertEquals(1f, ClientQuestState.taskFraction(chapter, t, 0), 1e-6, "overshoot is capped");
        assertEquals(1f, ClientQuestState.taskFraction(chapter, t, 1), 1e-6);
        assertEquals(150, QuestBookScreen.leaderSplit(100, 200, 0.5f));
        assertEquals(100, QuestBookScreen.leaderSplit(100, 200, 0f));
        assertEquals(200, QuestBookScreen.leaderSplit(100, 200, 1f));
        assertEquals(100, QuestBookScreen.leaderSplit(100, 90, 0.5f), "no room, nothing lit");
    }

    @Test
    void aSingleCountedTaskShowsItsOwnCountAndAOneShotShowsNone() {
        Tile counted = tile("logs", List.of(new ObtainTask(ResourceLocation.parse("minecraft:oak_log"), 16)), List.of());
        Tile oneShot = tile("tick", List.of(new CheckmarkTask()), List.of());
        Chapter chapter = new Chapter(CH, "Ledger", List.of(counted, oneShot), List.of());
        progress(Map.of(CH + "/logs/0", 7), Set.of(), Set.of());
        assertEquals("7/16", ClientQuestState.tileCountText(chapter, counted));
        assertEquals("", ClientQuestState.tileCountText(chapter, oneShot));
    }

    @Test
    void claimableMatchesTheCardButton() {
        Tile plain = tile("plain", List.of(new CheckmarkTask()), List.of(BREAD));
        Tile choice = tile("choice", List.of(new CheckmarkTask()), List.of(new ChoiceReward(List.of(BREAD, BREAD))));
        Tile bare = tile("bare", List.of(new CheckmarkTask()), List.of());
        Chapter chapter = new Chapter(CH, "Ledger", List.of(plain, choice, bare), List.of());
        progress(Map.of(), Set.of(), Set.of());
        assertFalse(ClientQuestState.claimable(chapter, plain), "not finished yet");
        progress(Map.of(), Set.of(), Set.of(CH + "/plain", CH + "/choice", CH + "/bare"));
        assertTrue(ClientQuestState.claimable(chapter, plain));
        assertTrue(ClientQuestState.claimable(chapter, choice), "TAKE A / TAKE B still waiting");
        assertFalse(ClientQuestState.claimable(chapter, bare), "nothing to claim");
        progress(Map.of(), Set.of(CH + "/plain/claimed", CH + "/choice/choice"),
                Set.of(CH + "/plain", CH + "/choice", CH + "/bare"));
        assertFalse(ClientQuestState.claimable(chapter, plain));
        assertFalse(ClientQuestState.claimable(chapter, choice));
    }

    @Test
    void chapterStatsCountVisibleQuestsOnly() {
        Tile a = tile("a", List.of(new CheckmarkTask()), List.of(BREAD));
        Tile b = tile("b", List.of(new CheckmarkTask()), List.of());
        Tile hidden = new Tile("secret", new dev.aof.questqueen.data.GridPos(2, 0), "Secret", "", Optional.empty(),
                List.of(new CheckmarkTask()), List.of(), List.of(), Optional.empty(),
                Optional.of(new HiddenUntil("quest_complete", CH + "/never")), Optional.empty());
        Chapter chapter = new Chapter(CH, "Ledger", List.of(a, b, hidden), List.of());
        progress(Map.of(), Set.of(), Set.of(CH + "/a"));
        ClientQuestState.ChapterStats stats = ClientQuestState.chapterStats(chapter);
        assertEquals(new ClientQuestState.ChapterStats(1, 2, 1), stats, "the hidden quest is not counted");
        assertEquals(0.5f, stats.fraction(), 1e-6);
        assertFalse(stats.complete());

        progress(Map.of(), Set.of(CH + "/a/claimed"), Set.of(CH + "/a", CH + "/b"));
        ClientQuestState.ChapterStats after = ClientQuestState.chapterStats(chapter);
        assertEquals(new ClientQuestState.ChapterStats(2, 2, 0), after, "a new snapshot refreshes the cache");
        assertTrue(after.complete());
    }

    @Test
    void barTweenLandsFirstThenEasesToEachNewTarget() {
        BarTween bar = new BarTween();
        bar.retarget(0.25f, 1000L);
        assertEquals(0.25f, bar.value(1000L), 1e-6, "the first value lands at once");
        assertFalse(bar.moving(1000L));
        bar.retarget(0.75f, 2000L);
        assertEquals(0.25f, bar.value(2000L), 1e-6);
        float mid = bar.value(2000L + BarTween.DURATION_MS / 2);
        assertTrue(mid > 0.5f && mid < 0.75f, "ease-out: past halfway at half time, not there yet: " + mid);
        assertTrue(bar.edgeAlpha(2100L) > 0f);
        assertEquals(0.75f, bar.value(2000L + BarTween.DURATION_MS), 1e-6);
        assertEquals(0f, bar.edgeAlpha(2000L + BarTween.DURATION_MS), 1e-6);
        UiFx.setEnabled(false);
        bar.retarget(0.1f, 3000L);
        assertEquals(0.1f, bar.value(3000L), 1e-6, "with motion off the bar jumps");
    }

    @Test
    void claimGlintPassesThenRests() {
        assertEquals(-1, QuestTileWidget.claimShineX(0f, 20), "starts just outside the chip");
        int a = QuestTileWidget.claimShineX(0.1f, 20);
        int b = QuestTileWidget.claimShineX(0.2f, 20);
        assertTrue(a >= 0 && b > a && b < 20, "moves left to right inside the chip: " + a + " " + b);
        assertEquals(-1, QuestTileWidget.claimShineX(0.5f, 20), "rests for the rest of the loop");
    }

    @Test
    void flightStartsAndLandsOnItsEndsAndArcsUpward() {
        // From a card reward slot at (300, 200) to a sidebar badge at (40, 60).
        assertArrayEquals(new int[]{300, 200}, QuestBookScreen.flightPoint(300, 200, 40, 60, 0f));
        assertArrayEquals(new int[]{40, 60}, QuestBookScreen.flightPoint(300, 200, 40, 60, 1f));
        int[] mid = QuestBookScreen.flightPoint(300, 200, 40, 60, 0.5f);
        assertTrue(mid[1] < 130, "the midpoint is lifted above the straight line: " + mid[1]);
    }

    @Test
    void badgePopsUpAndSettles() {
        assertEquals(1f, QuestBookScreen.badgePopScale(Long.MIN_VALUE, 5000L), 1e-6);
        assertEquals(1f, QuestBookScreen.badgePopScale(5000L, 4000L), 1e-6, "not before it starts");
        assertTrue(QuestBookScreen.badgePopScale(5000L, 5175L) > 1.25f, "peaks mid-pop");
        assertEquals(1f, QuestBookScreen.badgePopScale(5000L, 6000L), 1e-6);
    }

    @Test
    void descrambleSettlesLeftToRightAndKeepsSpaces() {
        String title = "SMELT IRON";
        String start = QuestTileWidget.descramble(title, 0f, 7, 0L);
        assertEquals(title.length(), start.length());
        assertEquals(' ', start.charAt(5), "spaces keep the word shapes");
        assertNotEquals(title, start);
        String half = QuestTileWidget.descramble(title, 0.5f, 7, 0L);
        assertTrue(half.startsWith("SMELT"), half);
        assertEquals(title, QuestTileWidget.descramble(title, 1f, 7, 0L));
    }

    @Test
    void contentClearsTheRail() {
        for (int size : new int[]{32, 40, 48, 56, 64, 96}) {
            assertTrue(QuestTileWidget.contentInset(size) >= QuestTileWidget.railPx(size) + 2,
                    "content at size " + size + " touches the rail or its hover width");
        }
    }

    @Test
    void sidebarRowsStopShortOfTheCollapseTab() {
        // The tab sits on the sidebar's right edge, so a badge or count flush right ran underneath it.
        assertTrue(QuestBookScreen.SIDEBAR_RIGHT_PAD >= QuestBookScreen.TAB_W,
                "rows end at least a tab width from the edge: " + QuestBookScreen.SIDEBAR_RIGHT_PAD);
    }

    @Test
    void claimableTilesGetTheWashAndLockedOnesNever() {
        assertTrue(QuestTileWidget.claimWash(false, true));
        assertFalse(QuestTileWidget.claimWash(false, false));
        assertFalse(QuestTileWidget.claimWash(true, true));
    }

    @Test
    void everyThemeCarriesClaimColoursAndTheConfigCanOverrideThem() {
        for (BookTheme theme : BookTheme.all()) {
            BookPalette palette = theme.palette();
            assertEquals(BookPalette.DEFAULT_CLAIM_TINT, palette.claimTint(), theme.id());
            assertEquals(BookPalette.DEFAULT_CLAIM_EDGE, palette.claimEdge(), theme.id());
            assertTrue((palette.claimTint() >>> 24) < 0x80, "the wash is translucent so the tile text still reads");
        }
        assertEquals(0x4400FF00, BookPalette.parseArgb("4400FF00", 1));
        assertEquals(0xFF123456, BookPalette.parseArgb("#123456", 1));
        assertEquals(1, BookPalette.parseArgb("", 1));
        assertEquals(1, BookPalette.parseArgb("nope", 1));
        assertEquals(1, BookPalette.parseArgb("12345", 1));
        BookPalette custom = BookTheme.MIDNIGHT_ROYALTY.palette().withClaim(0x22000000, 0xFF000000);
        assertEquals(0x22000000, custom.claimTint());
        assertEquals(BookTheme.MIDNIGHT_ROYALTY.palette().completed(), custom.completed());
    }

    @Test
    void theZoomLadderReachesHalfAndKeepsGapsOnPixel() {
        assertEquals(32, QuestBookScreen.TILE_STEPS[0]);
        assertEquals(0.5f, QuestBookScreen.fitFloor(), 1e-6);
        for (int step : QuestBookScreen.TILE_STEPS) {
            assertEquals(0, QuestBookScreen.GAP * step % QuestBookScreen.TILE, "gap stays on-pixel at " + step);
        }
        // A 20-column chapter at the smallest rung, on a 1920-wide window at GUI scale 2 with the sidebar open.
        int step = QuestBookScreen.TILE_STEPS[0];
        int wide = 19 * (step + QuestBookScreen.GAP * step / QuestBookScreen.TILE) + step;
        assertTrue(wide + 24 <= 960 - QuestBookScreen.SIDEBAR, "20 columns fit: " + wide);
    }

    @Test
    void wheelZoomKeepsThePointUnderTheCursorInPlace() {
        int[] steps = QuestBookScreen.TILE_STEPS;
        double camera = -137.25;
        for (int i = 0; i + 1 < steps.length; i++) {
            float small = steps[i] / (float) QuestBookScreen.TILE;
            float big = steps[i + 1] / (float) QuestBookScreen.TILE;
            for (double offset : new double[]{0, 41, 333.5, 900}) {
                double world = camera + offset / small;
                double in = QuestBookScreen.anchoredCamera(camera, offset, small, big);
                assertEquals(offset, (world - in) * big, 1e-6, "zoom in keeps the point at " + offset);
                double out = QuestBookScreen.anchoredCamera(in, offset, big, small);
                assertEquals(camera, out, 1e-6, "zooming back out returns to the same camera");
            }
        }
    }
}
