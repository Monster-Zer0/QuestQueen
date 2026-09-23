package dev.aof.questqueen.data.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record RaidTask(int waves) implements Task {
    public static final MapCodec<RaidTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            TaskCounts.COUNT.optionalFieldOf("waves", 1).forGetter(RaidTask::waves)
    ).apply(instance, RaidTask::new));

    @Override
    public String type() {
        return "raid";
    }

    @Override
    public int required() {
        return waves;
    }

    @Override
    public Task withCount(int newCount) {
        return new RaidTask(newCount);
    }
}
