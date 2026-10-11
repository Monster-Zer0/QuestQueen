package dev.aof.questqueen.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Where the pinned quest sits for each config position. */
class PinnedHudTest {
    @Test
    void positionsAndDefault() {
        assertEquals(QuestHudOverlay.Anchor.TOP_LEFT, QuestHudOverlay.Anchor.parse(""));
        assertEquals(QuestHudOverlay.Anchor.TOP_LEFT, QuestHudOverlay.Anchor.parse("nonsense"));
        assertEquals(QuestHudOverlay.Anchor.BOTTOM_RIGHT, QuestHudOverlay.Anchor.parse("Bottom-Right"));
        int w = 120, h = QuestHudOverlay.HEIGHT;
        assertArrayEquals(new int[]{8, 8}, QuestHudOverlay.place(QuestHudOverlay.Anchor.TOP_LEFT, 640, 360, w, h, 0, 0));
        assertArrayEquals(new int[]{260, 8}, QuestHudOverlay.place(QuestHudOverlay.Anchor.TOP_CENTER, 640, 360, w, h, 0, 0));
        assertArrayEquals(new int[]{512, 8}, QuestHudOverlay.place(QuestHudOverlay.Anchor.TOP_RIGHT, 640, 360, w, h, 0, 0));
        assertArrayEquals(new int[]{8, 169}, QuestHudOverlay.place(QuestHudOverlay.Anchor.MIDDLE_LEFT, 640, 360, w, h, 0, 0));
        assertArrayEquals(new int[]{512, 330}, QuestHudOverlay.place(QuestHudOverlay.Anchor.BOTTOM_RIGHT, 640, 360, w, h, 0, 0));
        assertArrayEquals(new int[]{18, 28}, QuestHudOverlay.place(QuestHudOverlay.Anchor.TOP_LEFT, 640, 360, w, h, 10, 20));
        assertArrayEquals(new int[]{502, 310}, QuestHudOverlay.place(QuestHudOverlay.Anchor.BOTTOM_RIGHT, 640, 360, w, h, 10, 20));
        assertArrayEquals(new int[]{0, 0}, QuestHudOverlay.place(QuestHudOverlay.Anchor.TOP_LEFT, 640, 360, w, h, -500, -500));
    }
}
