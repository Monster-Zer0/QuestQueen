package dev.aof.questqueen.client;

import java.util.List;
import java.util.Optional;

/**
 * Pure keyboard helpers for the quest book: which point an arrow key moves to, and whether a key press matches a
 * binding with its modifier. Kept free of Minecraft so the rules are unit-tested.
 */
public final class KeyNav {
    public enum Dir { UP, DOWN, LEFT, RIGHT }

    /** Something that can be selected: a tile (grid cell) or a part of the card (pixel centre). */
    public record Point(String id, int x, int y) {
    }

    /** GLFW modifier bits, as {@code keyPressed} receives them. */
    public static final int SHIFT = 1;
    public static final int CONTROL = 2;
    public static final int ALT = 4;

    private KeyNav() {
    }

    /**
     * The nearest point in direction {@code dir} from {@code from}: it must lie on that side (strictly), and the
     * score is the distance along the arrow plus twice the sideways distance, so a point straight ahead beats one
     * slightly closer but off to the side. Ties go to the point listed first. Empty when nothing is that way.
     */
    public static Optional<Point> next(List<Point> points, Point from, Dir dir) {
        Point best = null;
        long bestScore = Long.MAX_VALUE;
        for (Point p : points) {
            if (p.id().equals(from.id())) {
                continue;
            }
            int dx = p.x() - from.x();
            int dy = p.y() - from.y();
            long main;
            long side;
            switch (dir) {
                case UP -> { main = -dy; side = Math.abs(dx); }
                case DOWN -> { main = dy; side = Math.abs(dx); }
                case LEFT -> { main = -dx; side = Math.abs(dy); }
                default -> { main = dx; side = Math.abs(dy); }
            }
            if (main <= 0) {
                continue;
            }
            long score = main + 2 * side;
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return Optional.ofNullable(best);
    }

    /**
     * Does a press of {@code pressedKey} with GLFW {@code mods} match a binding to {@code boundKey} that needs
     * {@code needed} (one of {@link #SHIFT}, {@link #CONTROL}, {@link #ALT}, or 0 for none)? A binding with no
     * modifier does not fire while Shift, Ctrl or Alt is held, so Shift+C and C are different commands.
     */
    public static boolean matches(int boundKey, int needed, int pressedKey, int mods) {
        if (boundKey != pressedKey || boundKey < 0) {
            return false;
        }
        int held = mods & (SHIFT | CONTROL | ALT);
        return needed == 0 ? held == 0 : (held & needed) != 0;
    }
}
