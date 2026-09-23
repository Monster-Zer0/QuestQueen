package dev.aof.questqueen.data;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChapterTreeTest {
    @Test
    void nestsChildrenUnderParentsByOrder() {
        Chapter root = Chapter.blank(net.minecraft.resources.ResourceLocation.parse("questqueen:root"))
                .withMeta(java.util.Optional.empty(), 0, java.util.Optional.empty(), Gate.AND);
        Chapter childB = Chapter.blank(net.minecraft.resources.ResourceLocation.parse("questqueen:child_b"))
                .withMeta(java.util.Optional.of(root.id()), 20, java.util.Optional.empty(), Gate.AND);
        Chapter childA = Chapter.blank(net.minecraft.resources.ResourceLocation.parse("questqueen:child_a"))
                .withMeta(java.util.Optional.of(root.id()), 10, java.util.Optional.empty(), Gate.AND);

        List<ChapterTree.Node> roots = ChapterTree.roots(List.of(childB, childA, root));
        assertEquals(1, roots.size());
        assertEquals("questqueen:root", roots.getFirst().chapter().id().toString());
        assertEquals(List.of("questqueen:child_a", "questqueen:child_b"),
                roots.getFirst().children().stream().map(node -> node.chapter().id().toString()).toList());
        assertTrue(ChapterTree.hasChildren(roots, root.id()));
        assertFalse(ChapterTree.hasChildren(roots, childA.id()));
    }

    @Test
    void unlockRequiresParentAndConditions() {
        Chapter root = Chapter.blank(net.minecraft.resources.ResourceLocation.parse("questqueen:root"));
        Chapter child = Chapter.blank(net.minecraft.resources.ResourceLocation.parse("questqueen:child"))
                .withMeta(
                        java.util.Optional.of(root.id()),
                        1,
                        java.util.Optional.empty(),
                        new Gate(GateOp.AND, List.of(new GateCondition("quest_complete", "questqueen:root/start")))
                );

        AtomicBoolean parentUnlocked = new AtomicBoolean(false);
        assertFalse(ChapterTree.isUnlocked(child, List.of(root, child), condition -> true, id -> parentUnlocked.get()));
        parentUnlocked.set(true);
        assertTrue(ChapterTree.isUnlocked(child, List.of(root, child), condition -> true, id -> parentUnlocked.get()));
        assertFalse(ChapterTree.isUnlocked(child, List.of(root, child), condition -> false, id -> parentUnlocked.get()));
    }

    @Test
    void conditionsMetSupportsOr() {
        Gate gate = new Gate(GateOp.OR, List.of(
                new GateCondition("quest_complete", "a"),
                new GateCondition("quest_complete", "b")
        ));
        assertTrue(GateEvaluator.conditionsMet(gate, condition -> condition.id().equals("b")));
        assertFalse(GateEvaluator.conditionsMet(gate, condition -> false));
        assertTrue(GateEvaluator.conditionsMet(Gate.AND, condition -> false));
    }
}
