package dev.aof.questqueen.client;

import dev.aof.questqueen.data.Tile;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Book search matches a locked quest by its title only: its card, and so its description, stays closed. */
class SearchMatchTest {
    private static final Tile SMELTER = Tile.blank("smelter", 0, 0)
            .withTitle("Smelter")
            .withDescription("Build a blast furnace next to the ore vein");

    @Test
    void anOpenQuestMatchesItsDescription() {
        assertTrue(QuestBookScreen.matchesSearch(SMELTER, "blast", true));
    }

    @Test
    void aLockedQuestDoesNotMatchItsDescription() {
        assertFalse(QuestBookScreen.matchesSearch(SMELTER, "blast", false));
    }

    @Test
    void aLockedQuestStillMatchesItsTitleAndId() {
        assertTrue(QuestBookScreen.matchesSearch(SMELTER, "smelt", false));
        assertTrue(QuestBookScreen.matchesSearch(SMELTER, "smelter", false));
    }
}
