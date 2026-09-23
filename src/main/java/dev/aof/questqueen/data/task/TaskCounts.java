package dev.aof.questqueen.data.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * Bounded count codec for the counted task types.
 *
 * <p>Kept out of {@link Task} on purpose: {@code Task.CODECS} initializes every task codec, each of which
 * reads this constant, so a constant declared on {@code Task} itself would still be null while
 * {@code Task.<clinit>} was running — a class-initialization cycle that poisoned the whole reward/task
 * class graph.
 *
 * <p>{@code required() <= 0} would complete a task on its first increment
 * ({@code min(required, current + amount) >= required} is trivially true), so packs are held to >= 1.
 */
public final class TaskCounts {
    public static final Codec<Integer> COUNT = Codec.INT.validate(count ->
            count < 1
                    ? DataResult.error(() -> "count must be >= 1, was " + count)
                    : DataResult.success(count));

    private TaskCounts() {
    }
}
