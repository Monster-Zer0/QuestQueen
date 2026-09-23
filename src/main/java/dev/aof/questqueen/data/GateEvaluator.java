package dev.aof.questqueen.data;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;

public final class GateEvaluator {
    private GateEvaluator() {
    }

    public static boolean unlocked(Chapter chapter, String tileId, Set<String> completedTiles, Predicate<GateCondition> extras) {
        // An XOR fork is exclusive: once a sibling under the same hub is completed, this tile is settled
        // and can never be completed, whatever its own inbound op evaluates to. Enforced here so the
        // server and the client CLOSED chrome share one rule — a per-tile count of inbound trues cannot
        // express exclusivity when the fork is authored as one edge per target.
        if (xorSiblingCompleted(chapter, tileId, completedTiles)) {
            return false;
        }
        List<Link> inbound = chapter.links().stream().filter(link -> link.to().equals(tileId)).toList();
        if (inbound.isEmpty()) {
            return extrasPass(List.of(), extras);
        }
        List<Boolean> incoming = new ArrayList<>();
        List<GateCondition> conditions = new ArrayList<>();
        // Authoring normalizes inbound ops on a child; first edge is authoritative if packs differ.
        GateOp op = inbound.getFirst().gate().op();
        for (Link link : inbound) {
            incoming.add(completedTiles.contains(link.from()));
            conditions.addAll(link.gate().conditions());
        }
        return evaluate(op, incoming) && extrasPass(conditions, extras);
    }

    /**
     * True when some XOR sibling of {@code tileId} is already completed: another tile reachable from the
     * same fork source by an XOR link.
     */
    public static boolean xorSiblingCompleted(Chapter chapter, String tileId, Set<String> completedTiles) {
        for (Link link : chapter.links()) {
            if (link.gate().op() != GateOp.XOR || !link.to().equals(tileId)) {
                continue;
            }
            if (!completedTiles.contains(link.from())) {
                continue;
            }
            for (Link sibling : chapter.links()) {
                if (sibling.gate().op() != GateOp.XOR
                        || !sibling.from().equals(link.from())
                        || sibling.to().equals(tileId)) {
                    continue;
                }
                if (completedTiles.contains(sibling.to())) {
                    return true;
                }
            }
        }
        return false;
    }

    public static boolean evaluate(GateOp op, List<Boolean> incoming) {
        return switch (op) {
            case AND -> incoming.stream().allMatch(Boolean::booleanValue);
            case OR -> incoming.stream().anyMatch(Boolean::booleanValue);
            case NOT -> incoming.stream().noneMatch(Boolean::booleanValue);
            case XOR -> incoming.stream().filter(Boolean::booleanValue).count() == 1;
        };
    }

    /** Evaluate a standalone gate (chapter unlocks) using only its conditions list. */
    public static boolean conditionsMet(Gate gate, Predicate<GateCondition> extras) {
        List<GateCondition> conditions = gate.conditions();
        if (conditions.isEmpty()) {
            return true;
        }
        List<Boolean> results = new ArrayList<>();
        for (GateCondition condition : conditions) {
            results.add(extras.test(condition));
        }
        return evaluate(gate.op(), results);
    }

    private static boolean extrasPass(List<GateCondition> conditions, Predicate<GateCondition> extras) {
        for (GateCondition condition : conditions) {
            if (!extras.test(condition)) {
                return false;
            }
        }
        return true;
    }
}
