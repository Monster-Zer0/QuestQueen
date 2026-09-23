package dev.aof.questqueen.data;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StartNodesTest {
    @Test
    void tilesWithoutInboundLinksAreStarts() {
        Tile a = Tile.blank("make_chest", 0, 0);
        Tile b = Tile.blank("fight_zombie", 2, 0);
        Tile c = Tile.blank("tribute", 2, 2);
        List<Link> links = List.of(new Link("make_chest", "fight_zombie"), new Link("fight_zombie", "tribute"));
        Set<String> starts = StartNodes.of(List.of(a, b, c), links);
        assertEquals(Set.of("make_chest"), starts);
    }

    @Test
    void deletingAStartPromotesTheNextRoots() {
        Tile a = Tile.blank("make_chest", 0, 0);
        Tile b = Tile.blank("fight_zombie", 2, 0);
        Tile c = Tile.blank("side", 0, 2);
        List<Tile> remaining = List.of(b, c);
        List<Link> links = List.of(new Link("make_chest", "fight_zombie"));
        List<Link> leftover = links.stream()
                .filter(link -> remaining.stream().anyMatch(tile -> tile.id().equals(link.from()))
                        && remaining.stream().anyMatch(tile -> tile.id().equals(link.to())))
                .toList();
        Set<String> starts = StartNodes.of(remaining, leftover);
        assertTrue(starts.contains("fight_zombie"));
        assertTrue(starts.contains("side"));
        assertFalse(starts.contains("make_chest"));
    }
}
