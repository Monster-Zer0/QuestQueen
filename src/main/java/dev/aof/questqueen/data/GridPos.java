package dev.aof.questqueen.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record GridPos(int x, int y) {
    public static final Codec<GridPos> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.INT.fieldOf("x").forGetter(GridPos::x),
            Codec.INT.fieldOf("y").forGetter(GridPos::y)
    ).apply(instance, GridPos::new));

    /** Same row or column, not the same cell. */
    public boolean cardinalTo(GridPos other) {
        return (x == other.x) != (y == other.y);
    }

    /** Shares an edge — up, down, left, or right. */
    public boolean cardinalAdjacent(GridPos other) {
        return Math.abs(x - other.x) + Math.abs(y - other.y) == 1;
    }
}
