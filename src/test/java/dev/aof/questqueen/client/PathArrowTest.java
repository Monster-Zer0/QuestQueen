package dev.aof.questqueen.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PathArrowTest {
    @Test
    void lockedPathsAreAlwaysRed() {
        assertEquals(QuestColors.NEW, MockChrome.pathPortColor(TileVisual.NEW, false));
        assertEquals(QuestColors.PORT_RED, MockChrome.pathPortColor(TileVisual.NEW, true));
        assertEquals(QuestColors.NEW, MockChrome.pathPortColor(TileVisual.COMPLETED, false));
        assertEquals(QuestColors.PORT_RED, MockChrome.pathPortColor(TileVisual.CURRENT, true));
        assertEquals(QuestColors.PORT_RED, MockChrome.pathPortColor(TileVisual.COMPLETED, true));
        assertEquals(QuestColors.NEW, MockChrome.pathArrowColor(false));
        assertEquals(QuestColors.PORT_RED, MockChrome.pathArrowColor(true));
        assertEquals(0xFF488BD4, QuestColors.NEW);
        assertEquals(0xFF8C1A24, QuestColors.PORT_RED);
        assertEquals(0xFF8C1A24, QuestColors.LOCKED_EDGE);
        assertEquals(0xFFE01018, QuestColors.FAILED);
        assertEquals(0xFF6A6080, QuestColors.CLOSED);
        assertEquals(0xFFE88AB0, QuestColors.MODAL_PINK);
    }

    @Test
    void restShapesShareArrowSilhouette() {
        assertEquals("arrow", MockChrome.pathArrowShape(false));
        assertEquals("arrow", MockChrome.pathArrowShape(true));
        assertFalse("barred".equals(MockChrome.pathArrowShape(true)));
        assertFalse("barred".equals(MockChrome.pathArrowShape(false)));
    }

    @Test
    void hoverLockIsGreenOpenAndTrueRedLocked() {
        assertEquals(QuestColors.CURRENT, MockChrome.pathHoverLockColor(false));
        assertEquals(QuestColors.PORT_RED, MockChrome.pathHoverLockColor(true));
        assertEquals(QuestColors.LOCKED_EDGE, MockChrome.pathHoverLockColor(true));
    }

    @Test
    void hoverHitIsTightAroundThePathArrow() {
        assertTrue(MockChrome.pathHoverHit(40, 40, 40, 40));
        assertTrue(MockChrome.pathHoverHit(50, 40, 40, 40));
        assertFalse(MockChrome.pathHoverHit(60, 40, 40, 40));
    }

    @Test
    void pathArrowHasShaftAndTriangleHead() {
        assertTrue(MockChrome.pathArrowFilled(0, 0));
        assertTrue(MockChrome.pathArrowFilled(3, 0));
        assertTrue(MockChrome.pathArrowFilled(3, 1));
        assertTrue(MockChrome.pathArrowFilled(4, 3));
        assertTrue(MockChrome.pathArrowFilled(7, 0));
        assertFalse(MockChrome.pathArrowFilled(4, 0) && !MockChrome.pathArrowFilled(7, 0));
        assertFalse(MockChrome.pathArrowFilled(0, 3));
        assertFalse(MockChrome.pathArrowFilled(8, 0));
        assertFalse(MockChrome.pathArrowFilled(-1, 0));
        int pixels = 0;
        for (int ly = -3; ly <= 3; ly++) {
            for (int lx = 0; lx <= 7; lx++) {
                if (MockChrome.pathArrowFilled(lx, ly)) {
                    pixels++;
                }
            }
        }
        assertTrue(pixels > 12, "readable vs old 5/3/1 stubs");
    }

    @Test
    void lockCallSiteIsSourceEdgeMidpointNotGutterCenter() {
        int ox = 100;
        int oy = 40;
        int size = 64;
        int gap = 12;
        int[] right = QuestBookScreen.sourceEdgeMid(ox, oy, size, 1, 0);
        int[] down = QuestBookScreen.sourceEdgeMid(ox, oy, size, 0, 1);
        int[] left = QuestBookScreen.sourceEdgeMid(ox, oy, size, -1, 0);
        int[] up = QuestBookScreen.sourceEdgeMid(ox, oy, size, 0, -1);
        assertEquals(ox + size - 1, right[0]);
        assertEquals(oy + size / 2, right[1]);
        assertEquals(ox + size / 2, down[0]);
        assertEquals(oy + size - 1, down[1]);
        assertEquals(ox, left[0]);
        assertEquals(oy, up[1]);
        int gutterCenterX = ox + size + gap / 2;
        int gutterCenterY = oy + size + gap / 2;
        assertTrue(right[0] != gutterCenterX);
        assertTrue(down[1] != gutterCenterY);
        assertTrue(right[0] < ox + size + 1);
        assertTrue(down[1] < oy + size + 1);
    }
}
