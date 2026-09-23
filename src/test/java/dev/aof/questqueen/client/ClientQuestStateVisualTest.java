package dev.aof.questqueen.client;

import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.Gate;
import dev.aof.questqueen.data.Link;
import dev.aof.questqueen.data.QuestPack;
import dev.aof.questqueen.data.Tile;
import dev.aof.questqueen.progress.ProgressSnapshot;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClientQuestStateVisualTest {
    @AfterEach
    void reset() {
        ClientQuestState.pack = QuestPack.empty();
        ClientQuestState.progress = ProgressSnapshot.empty();
        ClientQuestState.gotchaShowCount = 0;
        ClientQuestState.lastLockedCueKind = "";
    }

    @Test
    void failedBeatsXorClosedOnSibling() {
        Chapter chapter = xorChapter();
        Tile blue = chapter.tile("blue").orElseThrow();
        ClientQuestState.progress = snapshot(
                Set.of("questqueen:xor/blue/failed"),
                Set.of("questqueen:xor/hub", "questqueen:xor/red"));

        assertTrue(ClientQuestState.isXorClosed(chapter, blue));
        assertTrue(ClientQuestState.isFailed(chapter, blue));
        assertEquals(TileVisual.FAILED, ClientQuestState.visual(chapter, blue, false, ""));
        assertEquals(TileVisual.COMPLETED, ClientQuestState.visual(chapter, chapter.tile("red").orElseThrow(), false, ""));
    }

    @Test
    void xorClosedShowsWhenSiblingIsNotFailed() {
        Chapter chapter = xorChapter();
        Tile blue = chapter.tile("blue").orElseThrow();
        ClientQuestState.progress = snapshot(
                Set.of(),
                Set.of("questqueen:xor/hub", "questqueen:xor/red"));

        assertTrue(ClientQuestState.isXorClosed(chapter, blue));
        assertFalse(ClientQuestState.isFailed(chapter, blue));
        assertEquals(TileVisual.CLOSED, ClientQuestState.visual(chapter, blue, false, ""));
    }

    @Test
    void unresolvedXorIsPackWideAndNeedsChoiceUntilChildCompletes() {
        Chapter chapter = xorChapter();
        ClientQuestState.pack = new QuestPack(List.of(chapter), List.of());
        ClientQuestState.progress = snapshot(Set.of(), Set.of("questqueen:xor/hub"));

        assertTrue(ClientQuestState.needsXorChoice(chapter, chapter.tile("hub").orElseThrow()));
        assertEquals(List.of("hub"), ClientQuestState.unresolvedXorParents(chapter));
        assertEquals(List.of("questqueen:xor/hub"), ClientQuestState.unresolvedXorKeys());
        assertTrue(ClientQuestState.isOpenXorBranch(chapter, chapter.tile("red").orElseThrow()));
        assertTrue(ClientQuestState.isOpenXorBranch(chapter, chapter.tile("blue").orElseThrow()));

        ClientQuestState.progress = snapshot(Set.of(), Set.of("questqueen:xor/hub", "questqueen:xor/red"));
        assertFalse(ClientQuestState.needsXorChoice(chapter, chapter.tile("hub").orElseThrow()));
        assertTrue(ClientQuestState.unresolvedXorKeys().isEmpty());
        assertFalse(ClientQuestState.isOpenXorBranch(chapter, chapter.tile("blue").orElseThrow()));
    }

    @Test
    void gotchaModalCapThenFadeDoesNotIncrement() {
        assertEquals("", ClientQuestState.lastLockedCueKind);
        assertTrue(ClientQuestState.takeGotchaModalSlot());
        assertEquals(1, ClientQuestState.gotchaShowCount);
        assertEquals("modal", ClientQuestState.lastLockedCueKind);
        assertTrue(ClientQuestState.takeGotchaModalSlot());
        assertEquals(2, ClientQuestState.gotchaShowCount);
        assertEquals("modal", ClientQuestState.lastLockedCueKind);
        assertFalse(ClientQuestState.takeGotchaModalSlot());
        assertEquals(2, ClientQuestState.gotchaShowCount);
        assertEquals("fade", ClientQuestState.lastLockedCueKind);
        assertFalse(ClientQuestState.takeGotchaModalSlot());
        assertEquals(2, ClientQuestState.gotchaShowCount);
        assertEquals("fade", ClientQuestState.lastLockedCueKind);
    }

    @Test
    void hiddenChapterStaysOffTheListUntilUnlocked() {
        Chapter hidden = Chapter.blank(ResourceLocation.parse("questqueen:secret")).withHideUntilUnlocked(true);
        Chapter locked = Chapter.blank(ResourceLocation.parse("questqueen:locked"));
        ClientQuestState.progress = new ProgressSnapshot(
                "team",
                false,
                Map.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of("questqueen:open"),
                Set.of(),
                List.of(),
                Optional.empty(),
                Optional.empty()
        );

        assertFalse(ClientQuestState.isChapterUnlocked(hidden));
        assertFalse(ClientQuestState.isChapterListed(hidden));
        assertFalse(ClientQuestState.isChapterUnlocked(locked));
        assertTrue(ClientQuestState.isChapterListed(locked));

        ClientQuestState.progress = new ProgressSnapshot(
                "team",
                false,
                Map.of(),
                Set.of(),
                Set.of(),
                Set.of(),
                Set.of("questqueen:open", "questqueen:secret"),
                Set.of(),
                List.of(),
                Optional.empty(),
                Optional.empty()
        );
        assertTrue(ClientQuestState.isChapterUnlocked(hidden));
        assertTrue(ClientQuestState.isChapterListed(hidden));
    }

    private static Chapter xorChapter() {
        return new Chapter(
                ResourceLocation.parse("questqueen:xor"),
                "Xor",
                List.of(Tile.blank("hub", 0, 0), Tile.blank("red", 1, 0), Tile.blank("blue", 2, 0)),
                List.of(new Link("hub", "red", Gate.XOR), new Link("hub", "blue", Gate.XOR))
        );
    }

    private static ProgressSnapshot snapshot(Set<String> completedTasks, Set<String> completedTiles) {
        return new ProgressSnapshot(
                "team",
                false,
                Map.of(),
                completedTasks,
                completedTiles,
                Set.of(),
                Set.of(),
                Set.of(),
                List.of(),
                Optional.empty(),
                Optional.empty()
        );
    }
}
