package dev.aof.questqueen.data;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GateEvaluatorTest {
    @Test
    void andRequiresEveryIncomingTile() {
        assertTrue(GateEvaluator.evaluate(GateOp.AND, List.of(true, true)));
        assertFalse(GateEvaluator.evaluate(GateOp.AND, List.of(true, false)));
    }

    @Test
    void orXorNotMatchTheirTables() {
        assertTrue(GateEvaluator.evaluate(GateOp.OR, List.of(false, true)));
        assertFalse(GateEvaluator.evaluate(GateOp.OR, List.of(false, false)));
        assertTrue(GateEvaluator.evaluate(GateOp.XOR, List.of(true, false)));
        assertFalse(GateEvaluator.evaluate(GateOp.XOR, List.of(true, true)));
        assertTrue(GateEvaluator.evaluate(GateOp.NOT, List.of(false, false)));
        assertFalse(GateEvaluator.evaluate(GateOp.NOT, List.of(true)));
    }

    @Test
    void startTilesUnlockWithoutInboundLinks() {
        Chapter chapter = new Chapter(
                net.minecraft.resources.ResourceLocation.parse("questqueen:test"),
                "Test",
                List.of(Tile.blank("root", 0, 0), Tile.blank("child", 1, 0)),
                List.of(new Link("root", "child"))
        );
        assertTrue(GateEvaluator.unlocked(chapter, "root", Set.of(), condition -> true));
        assertFalse(GateEvaluator.unlocked(chapter, "child", Set.of(), condition -> true));
        assertTrue(GateEvaluator.unlocked(chapter, "child", Set.of("root"), condition -> true));
    }

    @Test
    void multiParentAndOrXor() {
        Chapter andChapter = base().upsertLink("a", "child", GateOp.AND).upsertLink("b", "child", GateOp.AND);
        assertFalse(GateEvaluator.unlocked(andChapter, "child", Set.of("a"), condition -> true));
        assertTrue(GateEvaluator.unlocked(andChapter, "child", Set.of("a", "b"), condition -> true));

        Chapter orChapter = base().upsertLink("a", "child", GateOp.OR).upsertLink("b", "child", GateOp.OR);
        assertTrue(GateEvaluator.unlocked(orChapter, "child", Set.of("a"), condition -> true));
        assertFalse(GateEvaluator.unlocked(orChapter, "child", Set.of(), condition -> true));

    }

    @Test
    void xorIsAForkNotAJoin() {
        // a forks into child and other; child also needs b (AND). The fork edge is an ordinary parent for the join.
        Chapter chapter = new Chapter(
                net.minecraft.resources.ResourceLocation.parse("questqueen:fork"),
                "Fork",
                List.of(Tile.blank("a", 0, 0), Tile.blank("b", 1, 0), Tile.blank("child", 2, 0), Tile.blank("other", 3, 0)),
                List.of())
                .upsertLink("a", "child", GateOp.XOR)
                .upsertLink("a", "other", GateOp.XOR)
                .upsertLink("b", "child", GateOp.AND);
        assertEquals(GateOp.AND, GateEvaluator.joinOp(chapter.links().stream().filter(l -> l.to().equals("child")).toList()));
        assertFalse(GateEvaluator.unlocked(chapter, "child", Set.of("a"), condition -> true), "b is still required");
        assertTrue(GateEvaluator.unlocked(chapter, "child", Set.of("a", "b"), condition -> true));
        assertFalse(GateEvaluator.unlocked(chapter, "child", Set.of("a", "b", "other"), condition -> true),
                "the sibling took the fork");
    }

    @Test
    void settingXorForksTheParentAndLeavesTheChildsOtherArrows() {
        Chapter chapter = base()
                .upsertLink("a", "child", GateOp.AND)
                .upsertLink("b", "child", GateOp.AND)
                .upsertLink("a", "b", GateOp.AND)
                .upsertLink("a", "child", GateOp.XOR);
        assertEquals(GateOp.XOR, op(chapter, "a", "child"));
        assertEquals(GateOp.XOR, op(chapter, "a", "b"), "every arrow out of a joins the fork");
        assertEquals(GateOp.AND, op(chapter, "b", "child"), "the child's other parent is untouched");
    }

    @Test
    void settledCoversForksAndNotGates() {
        Chapter chapter = new Chapter(
                net.minecraft.resources.ResourceLocation.parse("questqueen:settle"),
                "Settle",
                List.of(Tile.blank("hub", 0, 0), Tile.blank("left", 1, 0), Tile.blank("right", 1, 1), Tile.blank("before", 2, 0)),
                List.of())
                .upsertLink("hub", "left", GateOp.XOR)
                .upsertLink("hub", "right", GateOp.XOR)
                .upsertLink("hub", "before", GateOp.NOT);
        Set<String> done = Set.of("hub", "left");
        assertTrue(GateEvaluator.settled(chapter, "left", done));
        assertTrue(GateEvaluator.closed(chapter, "right", done), "the fork's other branch");
        assertTrue(GateEvaluator.closed(chapter, "before", done), "NOT shut for good once hub is done");
        assertFalse(GateEvaluator.settled(chapter, "right", Set.of("hub")), "still open while no branch is taken");
        assertFalse(GateEvaluator.settled(chapter, "before", Set.of()), "NOT is open while hub is unfinished");
    }

    private static GateOp op(Chapter chapter, String from, String to) {
        return chapter.links().stream().filter(l -> l.from().equals(from) && l.to().equals(to)).findFirst().orElseThrow().gate().op();
    }

    @Test
    void upsertLinkNormalizesInboundOps() {
        Chapter chapter = base()
                .upsertLink("a", "child", GateOp.AND)
                .upsertLink("b", "child", GateOp.OR);
        assertTrue(chapter.links().stream().filter(l -> l.to().equals("child")).allMatch(l -> l.gate().op() == GateOp.OR));
        assertTrue(GateEvaluator.unlocked(chapter, "child", Set.of("a"), condition -> true));
    }

    private static Chapter base() {
        return new Chapter(
                net.minecraft.resources.ResourceLocation.parse("questqueen:gates"),
                "Gates",
                List.of(Tile.blank("a", 0, 0), Tile.blank("b", 1, 0), Tile.blank("child", 2, 0)),
                List.of()
        );
    }
}
