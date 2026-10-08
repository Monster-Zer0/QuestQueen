package dev.aof.questqueen.client;

import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.Tile;
import dev.aof.questqueen.data.reward.ItemReward;
import dev.aof.questqueen.data.reward.XpReward;
import dev.aof.questqueen.data.task.CheckmarkTask;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ledger quest card: rewards run left to right, buttons sit flush right, and the two never meet. One layout
 * feeds the draw, the clicks, the reward tooltips and the claim surface, so these numbers are what all of them see.
 */
class CardLedgerLayoutTest {
    private static final int X = 100;
    private static final int W = 208;
    private static final int RIGHT = X + W - QuestBookScreen.CARD_PAD;

    @Test
    void thePrimaryButtonIsFlushRightAndPinSitsLeftOfIt() {
        var layout = QuestBookScreen.footerLayout(X, W, new int[]{16}, 40, 0, 36, 10);
        assertEquals(RIGHT - 40, layout.actionX());
        assertEquals(layout.actionX() - QuestBookScreen.BUTTON_GAP - 36, layout.pinX());
    }

    @Test
    void theOneTakeButtonSitsRightOfPin() {
        var layout = QuestBookScreen.footerLayout(X, W, new int[0], 0, 38, 36, 10);
        assertEquals(RIGHT - 38, layout.choiceX());
        assertEquals(layout.choiceX() - QuestBookScreen.BUTTON_GAP - 36, layout.pinX());
    }

    @Test
    void threeChoiceIconsFitBesideTakeAndPin() {
        // A three-option choice (1 diamond, 8 emerald, 16 iron) beside PICK ONE. A pending choice is on a
        // COMPLETED quest, which never shows PIN, so PICK ONE is the only button. Every option stays clickable.
        var layout = QuestBookScreen.footerLayout(X, W, new int[]{16, 24, 30}, 0, 54, 0, 12);
        assertEquals(3, layout.shown());
        assertEquals(-1, layout.overflowX());
    }

    @Test
    void moreTasksTextCountsWhatTheCardLeftOut() {
        assertEquals("+1 more task", QuestBookScreen.moreTasksText(1));
        assertEquals("+13 more tasks", QuestBookScreen.moreTasksText(13));
    }

    @Test
    void rewardsStartAtTheContentInsetAndStepByTheirOwnWidth() {
        var layout = QuestBookScreen.footerLayout(X, W, new int[]{16, 30, 22}, 40, 0, 0, 10);
        assertEquals(3, layout.shown());
        assertEquals(X + QuestBookScreen.CARD_PAD, layout.slotX()[0]);
        assertEquals(layout.slotX()[0] + 16 + QuestBookScreen.REWARD_GAP, layout.slotX()[1]);
        assertEquals(layout.slotX()[1] + 30 + QuestBookScreen.REWARD_GAP, layout.slotX()[2]);
        assertEquals(-1, layout.overflowX(), "everything fit, so no +N chip");
    }

    @Test
    void rewardsNeverReachTheLeftmostButton() {
        int[] many = {30, 30, 30, 30, 30, 30};
        var layout = QuestBookScreen.footerLayout(X, W, many, 38, 38, 46, 12);
        int leftmostButton = Math.min(layout.pinX(), layout.choiceX());
        for (int i = 0; i < layout.shown(); i++) {
            assertTrue(layout.slotX()[i] + many[i] <= leftmostButton - QuestBookScreen.REWARD_GAP,
                    "slot " + i + " runs into the buttons");
        }
        assertTrue(layout.shown() < many.length, "six wide slots cannot fit beside the buttons");
        assertTrue(layout.overflowX() >= 0, "the slots that did not fit are counted in a +N chip");
        assertTrue(layout.overflowX() + 12 <= leftmostButton, "the +N chip itself stays clear of the buttons");
    }

    @Test
    void withNoButtonsRewardsMayUseTheWholeRow() {
        var layout = QuestBookScreen.footerLayout(X, W, new int[]{16, 16}, 0, 0, 0, 10);
        assertEquals(RIGHT, layout.limit());
        assertEquals(2, layout.shown());
    }

    @Test
    void rewardCountsShowTheRewardsOwnAmount() {
        assertEquals("8", QuestBookScreen.rewardCountText(new ItemReward(ResourceLocation.parse("minecraft:bread"), 8)));
        assertEquals("50", QuestBookScreen.rewardCountText(new XpReward(50)));
        assertEquals("", QuestBookScreen.rewardCountText(new ItemReward(ResourceLocation.parse("minecraft:bread"), 1)));
    }

    @Test
    void taskCountReadsValueOverNeedThenDone() {
        ResourceLocation chapterId = ResourceLocation.parse("questqueen:ledger");
        Tile tile = Tile.blank("gather", 0, 0).withTasks(List.of(
                new ItemTagTask(ResourceLocation.parse("minecraft:logs"), 16),
                new ObtainTask(ResourceLocation.parse("minecraft:dirt"), 16),
                new CheckmarkTask()));
        Chapter chapter = new Chapter(chapterId, "Ledger", List.of(tile), List.of());
        String quest = chapterId + "/" + tile.id();
        ProgressSnapshot previous = ClientQuestState.progress;
        ClientQuestState.progress = new ProgressSnapshot("solo", false, Map.of(quest + "/0", 7, quest + "/1", 16),
                Set.of(quest + "/1"), Set.of(), Set.of(), Set.of(), Set.of(), List.of(),
                Optional.empty(), Optional.empty());
        try {
            assertEquals("7/16", ClientQuestState.taskCount(chapter, tile, 0));
            assertEquals("DONE", ClientQuestState.taskCount(chapter, tile, 1));
            assertEquals("", ClientQuestState.taskCount(chapter, tile, 2), "an open one-shot task has nothing to count");
            assertEquals("", ClientQuestState.taskCount(chapter, tile, 9));
        } finally {
            ClientQuestState.progress = previous;
        }
    }

    @Test
    void theTaskBlockSitsAboveTheFooterRule() {
        int y = 40;
        int h = 160;
        int footerRule = y + h - QuestBookScreen.FOOTER_FROM_BOTTOM - 5;
        int top = QuestBookScreen.taskBlockTop(y, h, 3);
        assertEquals(footerRule - 4 - 3 * 12, top);
    }
}
