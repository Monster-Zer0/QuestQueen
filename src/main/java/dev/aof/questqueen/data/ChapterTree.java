package dev.aof.questqueen.data;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import net.minecraft.resources.ResourceLocation;

/** Builds a sorted parent→children tree from flat chapter metadata. */
public final class ChapterTree {
    public record Node(Chapter chapter, List<Node> children) {
    }

    private ChapterTree() {
    }

    public static List<Node> roots(List<Chapter> chapters) {
        Map<String, Chapter> byId = new HashMap<>();
        for (Chapter chapter : chapters) {
            byId.put(chapter.id().toString(), chapter);
        }
        Map<String, List<Chapter>> children = new HashMap<>();
        List<Chapter> roots = new ArrayList<>();
        for (Chapter chapter : chapters) {
            if (chapter.parent().isEmpty()) {
                roots.add(chapter);
                continue;
            }
            String parentId = chapter.parent().get().toString();
            if (!byId.containsKey(parentId) || wouldCycle(byId, chapter.id().toString(), parentId)) {
                roots.add(chapter);
            } else {
                children.computeIfAbsent(parentId, key -> new ArrayList<>()).add(chapter);
            }
        }
        roots.sort(orderThenId());
        List<Node> nodes = new ArrayList<>();
        Set<String> visiting = new HashSet<>();
        for (Chapter root : roots) {
            nodes.add(build(root, children, visiting));
        }
        return nodes;
    }

    public static List<Chapter> flatten(List<Node> roots) {
        List<Chapter> ordered = new ArrayList<>();
        for (Node root : roots) {
            walk(root, ordered);
        }
        return ordered;
    }

    private static void walk(Node node, List<Chapter> out) {
        out.add(node.chapter());
        for (Node child : node.children()) {
            walk(child, out);
        }
    }

    private static Node build(Chapter chapter, Map<String, List<Chapter>> children, Set<String> visiting) {
        String id = chapter.id().toString();
        if (!visiting.add(id)) {
            return new Node(chapter, List.of());
        }
        List<Chapter> kids = new ArrayList<>(children.getOrDefault(id, List.of()));
        kids.sort(orderThenId());
        List<Node> childNodes = new ArrayList<>();
        for (Chapter kid : kids) {
            childNodes.add(build(kid, children, visiting));
        }
        visiting.remove(id);
        return new Node(chapter, List.copyOf(childNodes));
    }

    private static boolean wouldCycle(Map<String, Chapter> byId, String chapterId, String parentId) {
        String cursor = parentId;
        Set<String> seen = new HashSet<>();
        while (cursor != null) {
            if (cursor.equals(chapterId) || !seen.add(cursor)) {
                return true;
            }
            Chapter next = byId.get(cursor);
            cursor = next == null || next.parent().isEmpty() ? null : next.parent().get().toString();
        }
        return false;
    }

    private static Comparator<Chapter> orderThenId() {
        return Comparator.comparingInt(Chapter::order).thenComparing(chapter -> chapter.id().toString());
    }

    public static boolean hasChildren(List<Node> roots, ResourceLocation id) {
        return find(roots, id).map(node -> !node.children().isEmpty()).orElse(false);
    }

    public static Optional<Node> find(List<Node> roots, ResourceLocation id) {
        if (id == null) {
            return Optional.empty();
        }
        for (Node node : roots) {
            if (node.chapter().id().equals(id)) {
                return Optional.of(node);
            }
            Optional<Node> nested = find(node.children(), id);
            if (nested.isPresent()) {
                return nested;
            }
        }
        return Optional.empty();
    }

    public static boolean isUnlocked(Chapter chapter, List<Chapter> all, Predicate<GateCondition> extras, Predicate<String> chapterUnlocked) {
        if (chapter.parent().isPresent()) {
            String parentId = chapter.parent().get().toString();
            if (!chapterUnlocked.test(parentId)) {
                return false;
            }
        }
        return GateEvaluator.conditionsMet(chapter.unlock(), extras);
    }
}
