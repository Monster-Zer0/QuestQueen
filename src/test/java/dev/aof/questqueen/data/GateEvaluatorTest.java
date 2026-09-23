package dev.aof.questqueen.data;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

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

        Chapter xorChapter = base().upsertLink("a", "child", GateOp.XOR).upsertLink("b", "child", GateOp.XOR);
        assertTrue(GateEvaluator.unlocked(xorChapter, "child", Set.of("a"), condition -> true));
        assertFalse(GateEvaluator.unlocked(xorChapter, "child", Set.of("a", "b"), condition -> true));
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
