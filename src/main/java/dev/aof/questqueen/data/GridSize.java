package dev.aof.questqueen.data;

/**
 * Chapter grid extents. Defaults match the original book layout; max is 128×128.
 */
public final class GridSize {
    public static final int DEFAULT_WIDTH = 12;
    public static final int DEFAULT_HEIGHT = 10;
    public static final int MIN = 1;
    public static final int MAX = 128;

    private GridSize() {
    }

    public static int clamp(int value) {
        return Math.max(MIN, Math.min(MAX, value));
    }

    public static int clampWidth(int width) {
        return clamp(width <= 0 ? DEFAULT_WIDTH : width);
    }

    public static int clampHeight(int height) {
        return clamp(height <= 0 ? DEFAULT_HEIGHT : height);
    }
}
