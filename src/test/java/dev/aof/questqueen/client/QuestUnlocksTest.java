package dev.aof.questqueen.client;

import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.Gate;
import dev.aof.questqueen.data.GridPos;
import dev.aof.questqueen.data.HiddenUntil;
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

/** The card's UNLOCKS row: which quests a quest leads to, and what else each of them still waits on. */
class QuestUnlocksTest {
    private static final ResourceLocation ID = ResourceLocation.parse("questqueen:unlocks");

    @AfterEach
    void reset() {
        ClientQuestState.pack = QuestPack.empty();
        ClientQuestState.progress = ProgressSnapshot.empty();
    }

    @Test
    void listsChildrenAndNamesTheOtherParentsAnAndGateWaitsOn() {
        Chapter chapter = chapter(
                List.of(titled("iron", "Iron Age"), titled("bucket", "Bucket"), titled("lava", "Lava"),
                        titled("nether", "Nether")),
                List.of(new Link("iron", "bucket"), new Link("bucket", "nether"), new Link("iron", "nether"),
                        new Link("lava", "nether")));
        ClientQuestState.progress = completed(Set.of("lava"));

        List<ClientQuestState.Unlock> unlocks = ClientQuestState.unlocks(chapter, chapter.tile("iron").orElseThrow());

        assertEquals(List.of("bucket", "nether"), unlocks.stream().map(u -> u.tile().id()).toList());
        assertEquals(List.of(), unlocks.get(0).alsoNeeds(), "finishing Iron Age alone opens Bucket");
        assertEquals(List.of("Bucket"), unlocks.get(1).alsoNeeds(), "a finished parent is not listed");
        assertFalse(unlocks.get(1).open());
    }

    @Test
    void anOrChildNeedsNothingElse() {
        Chapter chapter = chapter(
                List.of(Tile.blank("a", 0, 0), Tile.blank("b", 1, 0), Tile.blank("c", 2, 0)),
                List.of(new Link("a", "c", Gate.OR), new Link("b", "c", Gate.OR)));

        List<ClientQuestState.Unlock> unlocks = ClientQuestState.unlocks(chapter, chapter.tile("a").orElseThrow());

        assertEquals(1, unlocks.size());
        assertTrue(unlocks.getFirst().alsoNeeds().isEmpty());
    }

    @Test
    void notChildrenAndHiddenChildrenAreLeftOut() {
        Tile secret = new Tile("secret", new GridPos(2, 0), "Secret", "", Optional.empty(), List.of(), List.of(),
                List.of(), Optional.empty(), Optional.of(new HiddenUntil("stage", "never")), Optional.empty());
        Chapter chapter = chapter(
                List.of(Tile.blank("a", 0, 0), Tile.blank("pacifist", 1, 0), secret),
                List.of(new Link("a", "pacifist", Gate.NOT), new Link("a", "secret")));

        assertTrue(ClientQuestState.unlocks(chapter, chapter.tile("a").orElseThrow()).isEmpty());
    }

    @Test
    void aHiddenOtherParentIsNotNamed() {
        Tile secret = new Tile("secret", new GridPos(1, 0), "Secret", "", Optional.empty(), List.of(), List.of(),
                List.of(), Optional.empty(), Optional.of(new HiddenUntil("stage", "never")), Optional.empty());
        Chapter chapter = chapter(
                List.of(Tile.blank("a", 0, 0), secret, Tile.blank("c", 2, 0)),
                List.of(new Link("a", "c"), new Link("secret", "c")));

        List<ClientQuestState.Unlock> unlocks = ClientQuestState.unlocks(chapter, chapter.tile("a").orElseThrow());

        assertEquals(List.of("a hidden quest"), unlocks.getFirst().alsoNeeds());
    }

    @Test
    void aChildAlreadyReachableReadsAsOpen() {
        Chapter chapter = chapter(
                List.of(titled("a", "A"), titled("b", "B")),
                List.of(new Link("a", "b")));
        ClientQuestState.progress = completed(Set.of("a"));

        assertTrue(ClientQuestState.unlocks(chapter, chapter.tile("a").orElseThrow()).getFirst().open());
    }

    private static Tile titled(String id, String title) {
        return Tile.blank(id, 0, 0).withTitle(title);
    }

    private static Chapter chapter(List<Tile> tiles, List<Link> links) {
        return new Chapter(ID, "Unlocks", tiles, links);
    }

    private static ProgressSnapshot completed(Set<String> tileIds) {
        Set<String> keys = new java.util.HashSet<>();
        tileIds.forEach(id -> keys.add(ID + "/" + id));
        return new ProgressSnapshot("team", false, Map.of(), Set.of(), keys, Set.of(), Set.of(), Set.of(),
                List.of(), Optional.empty(), Optional.empty());
    }
}
