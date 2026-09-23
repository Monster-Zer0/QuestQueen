package dev.aof.questqueen.data;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the XOR exclusivity fix (1.1.155).
 *
 * <p>{@code evaluate(XOR, ...)} counts inbound trues on the <em>target</em>. In the fork shape used by the
 * demo pack — {@code hub -xor-> left} and {@code hub -xor-> right} — each target has a single inbound edge,
 * so after the hub completes {@code count(true) == 1} and the losing branch stayed unlocked and
 * completable. Exclusivity now lives in {@link GateEvaluator#unlocked} via
 * {@link GateEvaluator#xorSiblingCompleted}, so both branches cannot be completed.
 */
class XorForkExclusivityTest {
    private static Chapter fork() {
        // upsertLink keeps op per target, which is exactly how the demo packs author a fork.
        return new Chapter(
                ResourceLocation.parse("questqueen:fork"),
                "Fork",
                List.of(Tile.blank("hub", 0, 0), Tile.blank("left", 1, 0), Tile.blank("right", 2, 0)),
                List.of()
        ).upsertLink("hub", "left", GateOp.XOR).upsertLink("hub", "right", GateOp.XOR);
    }

    @Test
    void bothBranchesOpenWhileHubIncomplete() {
        Chapter chapter = fork();
        assertFalse(GateEvaluator.unlocked(chapter, "left", Set.of(), condition -> true));
        assertFalse(GateEvaluator.unlocked(chapter, "right", Set.of(), condition -> true));
    }

    @Test
    void bothBranchesOpenOnceHubCompletes() {
        Chapter chapter = fork();
        Set<String> done = Set.of("hub");
        assertTrue(GateEvaluator.unlocked(chapter, "left", done, condition -> true));
        assertTrue(GateEvaluator.unlocked(chapter, "right", done, condition -> true));
    }

    @Test
    void completingOneBranchLocksTheOtherForever() {
        Chapter chapter = fork();
        Set<String> leftTaken = Set.of("hub", "left");
        assertFalse(GateEvaluator.unlocked(chapter, "right", leftTaken, condition -> true),
                "the losing XOR branch must not be completable");
        assertTrue(GateEvaluator.xorSiblingCompleted(chapter, "right", leftTaken));
        assertFalse(GateEvaluator.xorSiblingCompleted(chapter, "left", leftTaken),
                "the branch that was taken is not itself closed");

        Set<String> rightTaken = Set.of("hub", "right");
        assertFalse(GateEvaluator.unlocked(chapter, "left", rightTaken, condition -> true));
    }

    @Test
    void siblingCheckIgnoresNonXorLinks() {
        Chapter plain = new Chapter(
                ResourceLocation.parse("questqueen:plain"),
                "Plain",
                List.of(Tile.blank("root", 0, 0), Tile.blank("a", 1, 0), Tile.blank("b", 2, 0)),
                List.of()
        ).upsertLink("root", "a", GateOp.OR).upsertLink("root", "b", GateOp.OR);
        Set<String> done = Set.of("root", "a");
        assertFalse(GateEvaluator.xorSiblingCompleted(plain, "b", done),
                "OR siblings must both remain available");
        assertTrue(GateEvaluator.unlocked(plain, "b", done, condition -> true));
    }
}
