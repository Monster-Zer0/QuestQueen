package dev.aof.questqueen.data.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record XpLevelTask(int levels) implements Task {
    public static final MapCodec<XpLevelTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            TaskCounts.COUNT.optionalFieldOf("levels", 1).forGetter(XpLevelTask::levels)
    ).apply(instance, XpLevelTask::new));

    @Override
    public String type() {
        return "xp_levels";
    }

    @Override
    public int required() {
        return levels;
    }

    @Override
    public Task withCount(int newCount) {
        return new XpLevelTask(newCount);
    }
}
