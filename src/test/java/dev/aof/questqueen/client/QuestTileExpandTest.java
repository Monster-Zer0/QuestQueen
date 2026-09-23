package dev.aof.questqueen.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestTileExpandTest {
    @Test
    void lockedNeverShowsExpand() {
        assertFalse(QuestTileWidget.showsExpand(true, true));
        assertFalse(QuestTileWidget.showsExpand(true, false));
        assertFalse(QuestBookScreen.showExpandGlyph(TileVisual.LOCKED, QuestColors.LOCKED_EDGE));
    }

    @Test
    void openStatusesKeepExpand() {
        assertTrue(QuestTileWidget.showsExpand(false, true));
        assertTrue(QuestBookScreen.showExpandGlyph(TileVisual.CURRENT, QuestColors.CURRENT));
        assertTrue(QuestBookScreen.showExpandGlyph(TileVisual.NEW, QuestColors.NEW));
        assertTrue(QuestBookScreen.showExpandGlyph(TileVisual.COMPLETED, QuestColors.COMPLETED));
        assertTrue(QuestBookScreen.showExpandGlyph(TileVisual.FAILED, QuestColors.FAILED));
        assertTrue(QuestBookScreen.showExpandGlyph(TileVisual.CLOSED, QuestColors.CLOSED));
    }
}
