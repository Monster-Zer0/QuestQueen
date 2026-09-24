package dev.aof.questqueen.progress;

import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.Tile;
import dev.aof.questqueen.data.reward.ChoiceReward;
import dev.aof.questqueen.data.reward.ItemReward;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClaimAllTest {
    private static final ResourceLocation CHAPTER = ResourceLocation.parse("questqueen:camp");

    private static ItemReward diamond() {
        return new ItemReward(ResourceLocation.parse("minecraft:diamond"), 1);
    }

    private static Tile loot(String id) {
        return Tile.blank(id, 0, 0).withRewards(List.of(diamond()));
    }

    private static ProgressSnapshot snap(Set<String> completedTasks, Set<String> completedTiles) {
        return new ProgressSnapshot("team", false, Map.of(), completedTasks, completedTiles,
                Set.of(), Set.of(), Set.of(), List.of(), Optional.empty(), Optional.empty());
    }

    private static String tileKey(String id) {
        return CHAPTER + "/" + id;
    }

    @Test
    void readyLootIsIncluded() {
        Tile ready = loot("ready");
        Chapter chapter = new Chapter(CHAPTER, "Camp", List.of(ready), List.of());
        ProgressSnapshot snap = snap(Set.of(), Set.of(tileKey("ready")));
        assertTrue(ClaimAll.grantable(chapter, ready, snap, tile -> true));
        assertEquals(List.of("ready"), ClaimAll.grantable(chapter, snap, tile -> true).stream().map(Tile::id).toList());
    }

    @Test
    void alreadyClaimedIsExcluded() {
        Tile claimed = loot("claimed");
        Chapter chapter = new Chapter(CHAPTER, "Camp", List.of(claimed), List.of());
        ProgressSnapshot snap = snap(Set.of(tileKey("claimed") + "/claimed"), Set.of(tileKey("claimed")));
        assertTrue(ClaimAll.claimed(chapter, claimed, snap));
        assertTrue(ClaimAll.grantable(chapter, snap, tile -> true).isEmpty());
    }

    @Test
    void choiceIsExcludedFromTheGrantAndListedAsAPick() {
        Tile pick = Tile.blank("pick", 0, 0).withRewards(List.of(new ChoiceReward(List.of(
                diamond(), new ItemReward(ResourceLocation.parse("minecraft:emerald"), 8)))));
        Chapter chapter = new Chapter(CHAPTER, "Camp", List.of(pick), List.of());
        ProgressSnapshot snap = snap(Set.of(), Set.of(tileKey("pick")));
        assertFalse(ClaimAll.grantable(chapter, pick, snap, tile -> true));
        assertEquals(List.of("pick"), ClaimAll.pickOnQuest(chapter, snap, tile -> true).stream().map(Tile::id).toList());
    }

    @Test
    void incompleteIsExcluded() {
        Tile open = loot("open");
        Chapter chapter = new Chapter(CHAPTER, "Camp", List.of(open), List.of());
        ProgressSnapshot snap = snap(Set.of(), Set.of());
        assertTrue(ClaimAll.grantable(chapter, snap, tile -> true).isEmpty());
        assertTrue(ClaimAll.pickOnQuest(chapter, snap, tile -> true).isEmpty());
    }

    @Test
    void lockedTileIsExcluded() {
        Tile ready = loot("ready");
        Chapter chapter = new Chapter(CHAPTER, "Camp", List.of(ready), List.of());
        ProgressSnapshot snap = snap(Set.of(), Set.of(tileKey("ready")));
        assertTrue(ClaimAll.grantable(chapter, snap, tile -> false).isEmpty());
    }

    @Test
    void scrollOnlyTileIsIncluded() {
        Tile note = Tile.blank("note", 0, 0).withTitle("Note");
        note = new Tile(note.id(), note.pos(), note.title(), note.description(), note.icon(), note.tasks(),
                note.rewards(), List.of(ResourceLocation.parse("questqueen:note")), note.target(),
                note.hiddenUntil(), note.requiredStage());
        Chapter chapter = new Chapter(CHAPTER, "Camp", List.of(note), List.of());
        ProgressSnapshot snap = snap(Set.of(), Set.of(tileKey("note")));
        assertEquals(List.of("note"), ClaimAll.grantable(chapter, snap, tile -> true).stream().map(Tile::id).toList());
    }
}
