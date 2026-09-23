package dev.aof.questqueen.progress;

import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.ChapterTree;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Whether a {@code chapter_complete} gate target is done.
 * Tile-less parent acts are complete only when every descendant board (chapter with tiles) is done.
 */
public final class ChapterCompletion {
    private ChapterCompletion() {
    }

    public static boolean fullyComplete(String chapterId, Collection<Chapter> chapters, Set<String> completedTiles) {
        ResourceLocation id;
        try {
            id = ResourceLocation.parse(chapterId);
        } catch (Exception ignored) {
            return false;
        }
        List<Chapter> list = List.copyOf(chapters);
        Optional<Chapter> chapter = list.stream().filter(entry -> entry.id().equals(id)).findFirst();
        if (chapter.isEmpty()) {
            return false;
        }
        return fullyComplete(chapter.get(), ChapterTree.roots(list), completedTiles);
    }

    public static boolean fullyComplete(Chapter chapter, List<ChapterTree.Node> roots, Set<String> completedTiles) {
        if (!chapter.tiles().isEmpty()) {
            return allTilesComplete(chapter, completedTiles);
        }
        Optional<ChapterTree.Node> node = ChapterTree.find(roots, chapter.id());
        if (node.isEmpty() || node.get().children().isEmpty()) {
            return false;
        }
        List<Chapter> boards = new ArrayList<>();
        collectDescendantBoards(node.get(), boards);
        if (boards.isEmpty()) {
            return false;
        }
        for (Chapter board : boards) {
            if (!allTilesComplete(board, completedTiles)) {
                return false;
            }
        }
        return true;
    }

    private static boolean allTilesComplete(Chapter chapter, Set<String> completedTiles) {
        return chapter.tiles().stream().allMatch(tile ->
                completedTiles.contains(ProgressSnapshot.questKey(chapter.id(), tile.id())));
    }

    private static void collectDescendantBoards(ChapterTree.Node node, List<Chapter> boards) {
        for (ChapterTree.Node child : node.children()) {
            if (!child.chapter().tiles().isEmpty()) {
                boards.add(child.chapter());
            }
            collectDescendantBoards(child, boards);
        }
    }
}
