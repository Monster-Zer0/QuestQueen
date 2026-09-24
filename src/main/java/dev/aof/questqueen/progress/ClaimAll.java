package dev.aof.questqueen.progress;

import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.Tile;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * Which tiles in one chapter a CLAIM ALL can grant. Pure: a chapter and a progress snapshot, no player.
 * Unlock is a caller predicate so the client and the server can apply their own gate check.
 * Choice rewards are never granted here; they stay on the quest as TAKE A / TAKE B.
 */
public final class ClaimAll {
    private ClaimAll() {
    }

    public static boolean hasPayoff(Tile tile) {
        return tile != null && (!tile.rewards().isEmpty() || !tile.scrolls().isEmpty());
    }

    public static boolean claimed(Chapter chapter, Tile tile, ProgressSnapshot snap) {
        if (chapter == null || tile == null || snap == null) {
            return false;
        }
        return snap.taskCompleted(ProgressSnapshot.questKey(chapter.id(), tile.id()), "claimed");
    }

    public static boolean grantable(Chapter chapter, Tile tile, ProgressSnapshot snap, Predicate<Tile> unlocked) {
        if (!ready(chapter, tile, snap, unlocked)) {
            return false;
        }
        if (ProgressService.hasChoiceReward(tile)) {
            return false;
        }
        return hasPayoff(tile);
    }

    /** Finished choice quests the player still has to pick on the card. */
    public static boolean pickOnQuest(Chapter chapter, Tile tile, ProgressSnapshot snap, Predicate<Tile> unlocked) {
        return ready(chapter, tile, snap, unlocked) && ProgressService.hasChoiceReward(tile);
    }

    public static List<Tile> grantable(Chapter chapter, ProgressSnapshot snap, Predicate<Tile> unlocked) {
        return matching(chapter, tile -> grantable(chapter, tile, snap, unlocked));
    }

    public static List<Tile> pickOnQuest(Chapter chapter, ProgressSnapshot snap, Predicate<Tile> unlocked) {
        return matching(chapter, tile -> pickOnQuest(chapter, tile, snap, unlocked));
    }

    private static boolean ready(Chapter chapter, Tile tile, ProgressSnapshot snap, Predicate<Tile> unlocked) {
        if (chapter == null || tile == null || snap == null || unlocked == null || !unlocked.test(tile)) {
            return false;
        }
        if (!snap.tileCompleted(chapter.id().toString(), tile.id())) {
            return false;
        }
        return !claimed(chapter, tile, snap);
    }

    private static List<Tile> matching(Chapter chapter, Predicate<Tile> test) {
        List<Tile> out = new ArrayList<>();
        if (chapter == null) {
            return out;
        }
        for (Tile tile : chapter.tiles()) {
            if (test.test(tile)) {
                out.add(tile);
            }
        }
        return out;
    }
}
