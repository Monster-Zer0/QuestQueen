package dev.aof.questqueen.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Arrow-key movement between quests (and card parts), and key matching with modifiers. */
class KeyNavTest {
    private static final KeyNav.Point CENTRE = new KeyNav.Point("c", 5, 5);
    private static final List<KeyNav.Point> CROSS = List.of(CENTRE,
            new KeyNav.Point("n", 5, 3), new KeyNav.Point("s", 5, 8), new KeyNav.Point("w", 2, 5),
            new KeyNav.Point("e", 7, 5), new KeyNav.Point("ne", 6, 4));

    private static String go(List<KeyNav.Point> points, KeyNav.Point from, KeyNav.Dir dir) {
        return KeyNav.next(points, from, dir).map(KeyNav.Point::id).orElse("-");
    }

    @Test
    void eachArrowPicksTheNearestQuestThatWay() {
        assertEquals("n", go(CROSS, CENTRE, KeyNav.Dir.UP), "straight up beats the closer diagonal");
        assertEquals("s", go(CROSS, CENTRE, KeyNav.Dir.DOWN));
        assertEquals("w", go(CROSS, CENTRE, KeyNav.Dir.LEFT));
        assertEquals("e", go(CROSS, CENTRE, KeyNav.Dir.RIGHT), "2 straight on beats the diagonal (1 along + 2x1 aside)");
    }

    @Test
    void nothingThatWayStaysPut() {
        assertEquals("-", go(CROSS, new KeyNav.Point("n", 5, 3), KeyNav.Dir.UP));
        assertEquals("-", go(List.of(CENTRE), CENTRE, KeyNav.Dir.LEFT));
    }

    @Test
    void gapsAreJumpedAndTiesGoToTheFirstListed() {
        List<KeyNav.Point> row = List.of(new KeyNav.Point("a", 0, 0), new KeyNav.Point("far", 9, 0),
                new KeyNav.Point("up", 3, -1), new KeyNav.Point("down", 3, 1));
        assertEquals("up", go(row, row.getFirst(), KeyNav.Dir.RIGHT), "3 + 2 = 5 beats 9; up and down tie, first wins");
        assertEquals("far", go(List.of(row.get(0), row.get(1)), row.getFirst(), KeyNav.Dir.RIGHT));
    }

    @Test
    void modifiersSeparateCFromShiftC() {
        int c = 67;
        assertTrue(KeyNav.matches(c, 0, c, 0));
        assertFalse(KeyNav.matches(c, 0, c, KeyNav.SHIFT), "plain C does not fire with Shift held");
        assertTrue(KeyNav.matches(c, KeyNav.SHIFT, c, KeyNav.SHIFT));
        assertFalse(KeyNav.matches(c, KeyNav.SHIFT, c, 0));
        assertFalse(KeyNav.matches(c, 0, 68, 0));
        assertFalse(KeyNav.matches(-1, 0, -1, 0), "an unbound key never matches");
        assertTrue(KeyNav.matches(c, 0, c, 16), "Caps/Num lock bits are ignored");
    }

    @Test
    void keyNamesAreShortInTheHelp() {
        assertEquals("Up / Down / Left / Right",
                QuestBookScreen.compactKeys(List.of("Up Arrow", "Down Arrow", "Left Arrow", "Right Arrow")));
        assertEquals("SHIFT + Up / Down", QuestBookScreen.compactKeys(List.of("SHIFT + Up Arrow", "SHIFT + Down Arrow")));
        assertEquals("SHIFT + C", QuestBookScreen.compactKeys(List.of("SHIFT + C")));
        assertEquals("R / U", QuestBookScreen.compactKeys(List.of("R", "U")));
        assertEquals("CTRL + R / U", QuestBookScreen.compactKeys(List.of("CTRL + R", "U")), "a modifier only one key has stays on that key");
    }
}
