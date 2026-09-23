package dev.aof.questqueen.progress;

import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.Gate;
import dev.aof.questqueen.data.Tile;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChapterCompletionTest {
    @Test
    void unknownChapterIsFalse() {
        Chapter leaf = board("questqueen:crash", "q1");
        assertFalse(ChapterCompletion.fullyComplete("questqueen:missing", List.of(leaf), Set.of("questqueen:crash/q1")));
    }

    @Test
    void emptyLeafIsFalse() {
        Chapter stub = empty("questqueen:stub");
        assertFalse(ChapterCompletion.fullyComplete("questqueen:stub", List.of(stub), Set.of()));
        assertFalse(ChapterCompletion.fullyComplete("questqueen:stub", List.of(stub), Set.of("questqueen:stub/start")));
    }

    @Test
    void emptyParentFalseWhileChildUnfinished() {
        Chapter act = empty("questqueen:act");
        Chapter child = childBoard(act, "questqueen:crash", "q1");
        assertFalse(ChapterCompletion.fullyComplete("questqueen:act", List.of(act, child), Set.of()));
    }

    @Test
    void emptyParentTrueWhenEveryChildTileIsDone() {
        Chapter act = empty("questqueen:act");
        Chapter a = childBoard(act, "questqueen:crash", "q1");
        Chapter b = childBoard(act, "questqueen:camp", "q2");
        List<Chapter> pack = List.of(act, a, b);
        assertFalse(ChapterCompletion.fullyComplete("questqueen:act", pack, Set.of("questqueen:crash/q1")));
        assertTrue(ChapterCompletion.fullyComplete("questqueen:act", pack,
                Set.of("questqueen:crash/q1", "questqueen:camp/q2")));
    }

    @Test
    void nestedIntroIsOnlyAContainer() {
        Chapter act = empty("questqueen:act");
        Chapter nested = empty("questqueen:act_i_b").withMeta(
                Optional.of(act.id()), 1, Optional.empty(), Gate.AND);
        Chapter leaf = childBoard(nested, "questqueen:crash", "q1");
        List<Chapter> pack = List.of(act, nested, leaf);
        assertFalse(ChapterCompletion.fullyComplete("questqueen:act", pack, Set.of()));
        assertFalse(ChapterCompletion.fullyComplete("questqueen:act_i_b", pack, Set.of()));
        Set<String> done = Set.of("questqueen:crash/q1");
        assertTrue(ChapterCompletion.fullyComplete("questqueen:act", pack, done));
        assertTrue(ChapterCompletion.fullyComplete("questqueen:act_i_b", pack, done));
    }

    @Test
    void chapterWithTilesUsesOwnTilesOnly() {
        Chapter act = Chapter.blank(id("questqueen:act")).withTiles(List.of(Tile.blank("briefing", 0, 0)));
        Chapter child = childBoard(act, "questqueen:crash", "q1");
        List<Chapter> pack = List.of(act, child);
        assertFalse(ChapterCompletion.fullyComplete("questqueen:act", pack, Set.of()));
        assertTrue(ChapterCompletion.fullyComplete("questqueen:act", pack, Set.of("questqueen:act/briefing")));
        assertFalse(ChapterCompletion.fullyComplete("questqueen:crash", pack, Set.of("questqueen:act/briefing")));
    }

    @Test
    void leafWithTilesRequiresEveryTile() {
        Chapter leaf = Chapter.blank(id("questqueen:crash"))
                .withTiles(List.of(Tile.blank("q1", 0, 0), Tile.blank("q2", 1, 0)));
        assertFalse(ChapterCompletion.fullyComplete("questqueen:crash", List.of(leaf), Set.of("questqueen:crash/q1")));
        assertTrue(ChapterCompletion.fullyComplete("questqueen:crash", List.of(leaf),
                Set.of("questqueen:crash/q1", "questqueen:crash/q2")));
    }

    private static Chapter empty(String id) {
        return Chapter.blank(id(id)).withTiles(List.of());
    }

    private static Chapter board(String id, String tileId) {
        return Chapter.blank(id(id)).withTiles(List.of(Tile.blank(tileId, 0, 0)));
    }

    private static Chapter childBoard(Chapter parent, String id, String tileId) {
        return board(id, tileId).withMeta(Optional.of(parent.id()), 0, Optional.empty(), Gate.AND);
    }

    private static ResourceLocation id(String value) {
        return ResourceLocation.parse(value);
    }
}
