package dev.aof.questqueen.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Layout contract for the tag mosaic (1.1.168).
 *
 * <p>An {@code item_tag} task used to be drawn as ONE representative item — {@code minecraft:logs} rendered as
 * a single oak log, which reads as "bring me oak logs" to a player whose only tree is a modded fir. The row
 * now draws a mosaic of up to {@link QuestBookScreen#MOSAIC_MAX} members plus a {@code +N} chip.
 *
 * <p>Only the pure planner is testable here: the members themselves come from {@code BuiltInRegistries}, and
 * merely touching {@code ItemStack.EMPTY} runs {@code ItemStack}'s static initialiser, which throws "Not
 * bootstrapped" without the FML loader. What is pinned is the arithmetic that decides how many cells fit and
 * that the task label always keeps its own width — the strip yields to the text, never the reverse.
 */
class TagMosaicLayoutTest {
    private static final int CARD_W = 208;

    @Test
    void noMembersMeansNoMosaic() {
        assertEquals(0, QuestBookScreen.mosaicIcons(0, 200), "an unbound tag must fall back to one icon");
        assertEquals(0, QuestBookScreen.mosaicIcons(-3, 200));
    }

    @Test
    void aStripNarrowerThanOneCellIsNotWorthDrawing() {
        assertEquals(0, QuestBookScreen.mosaicIcons(199, QuestBookScreen.MOSAIC_CELL - 1));
        assertEquals(1, QuestBookScreen.mosaicIcons(199, QuestBookScreen.MOSAIC_CELL),
                "exactly one cell must fit in exactly one cell of room");
    }

    @Test
    void theMosaicIsCappedAtSixCellsHoweverBigTheTagIs() {
        assertEquals(QuestBookScreen.MOSAIC_MAX, QuestBookScreen.mosaicIcons(199, 1_000));
        assertEquals(QuestBookScreen.MOSAIC_MAX, QuestBookScreen.mosaicIcons(6, 1_000));
        assertEquals(5, QuestBookScreen.mosaicIcons(5, 1_000));
    }

    @Test
    void cellWidthIsPitchTimesCellsMinusTheTrailingGap() {
        assertEquals(QuestBookScreen.MOSAIC_CELL, QuestBookScreen.mosaicStripWidth(1));
        assertEquals(QuestBookScreen.MOSAIC_MAX * QuestBookScreen.MOSAIC_PITCH - QuestBookScreen.MOSAIC_GAP,
                QuestBookScreen.mosaicStripWidth(QuestBookScreen.MOSAIC_MAX));
        assertEquals(0, QuestBookScreen.mosaicStripWidth(0));
    }

    @Test
    void theChosenStripNeverExceedsItsBudget() {
        int[] memberCounts = {1, 2, 3, 6, 7, 50, 199};
        int[] labelWidths = {0, 14, 40, 70, 106};
        for (int members : memberCounts) {
            for (int label : labelWidths) {
                int budget = QuestBookScreen.labelStripBudget(0, CARD_W, label);
                int icons = QuestBookScreen.mosaicIcons(members, budget);
                int strip = QuestBookScreen.mosaicStripWidth(icons);
                assertTrue(strip <= budget,
                        "members=" + members + " label=" + label + ": strip " + strip + " > budget " + budget);
            }
        }
    }

    @Test
    void theStripAlwaysYieldsToTheLabel() {
        // The invariant the draw relies on: wherever the mosaic ends, the label still starts early enough to
        // render at its natural width, so a long verb is never ellipsized to make room for icons.
        int[] labelWidths = {10, 14, 40, 70, 100, 106};
        for (int members : new int[]{1, 6, 199}) {
            for (int label : labelWidths) {
                int budget = QuestBookScreen.labelStripBudget(0, CARD_W, label);
                int icons = QuestBookScreen.mosaicIcons(members, budget);
                int strip = QuestBookScreen.mosaicStripWidth(icons);
                if (icons == 0) {
                    continue;
                }
                int labelStart = QuestBookScreen.STRIP_X + strip;
                int labelRight = CARD_W - QuestBookScreen.LABEL_RIGHT_MARGIN;
                assertTrue(labelStart + label <= labelRight,
                        "members=" + members + " label=" + label + ": label ends at "
                                + (labelStart + label) + ", past its bound " + labelRight);
            }
        }
    }

    @Test
    void aLongerLabelIsGivenLessRoomForIcons() {
        int roomy = QuestBookScreen.labelStripBudget(0, CARD_W, 14);
        int tight = QuestBookScreen.labelStripBudget(0, CARD_W, 106);
        assertTrue(roomy > tight, "the budget must shrink as the label grows");
        assertTrue(tight >= 0, "the budget must never go negative");
        assertEquals(0, QuestBookScreen.labelStripBudget(0, 96, 200),
                "on a card too narrow for the label the mosaic yields entirely");
    }

    @Test
    void overflowCountsTheMembersTheMosaicCouldNotShow() {
        assertEquals(193, QuestBookScreen.mosaicOverflow(199, 6));
        assertEquals(0, QuestBookScreen.mosaicOverflow(6, 6));
        assertEquals(0, QuestBookScreen.mosaicOverflow(3, 6));
        assertEquals(3, QuestBookScreen.mosaicOverflow(3, 0));
    }

    @Test
    void tagSampleLineNamesTheShownMembersAndMarksTheRest() {
        assertEquals("Oak Log, Birch Log", QuestBookScreen.tagSampleLine(List.of("Oak Log", "Birch Log"), 0));
        assertEquals("Oak Log, Birch Log, …", QuestBookScreen.tagSampleLine(List.of("Oak Log", "Birch Log"), 193));
        assertEquals("", QuestBookScreen.tagSampleLine(List.of(), 199));
        assertEquals("", QuestBookScreen.tagSampleLine(null, 199));
    }
}
