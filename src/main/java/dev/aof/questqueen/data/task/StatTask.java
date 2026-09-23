package dev.aof.questqueen.data.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record StatTask(ResourceLocation stat, int count) implements Task {
    public static final MapCodec<StatTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("stat").forGetter(StatTask::stat),
            TaskCounts.COUNT.optionalFieldOf("count", 1).forGetter(StatTask::count)
    ).apply(instance, StatTask::new));

    @Override
    public String type() {
        return "stat";
    }

    @Override
    public int required() {
        return count;
    }

    @Override
    public Optional<ResourceLocation> statId() {
        return Optional.of(stat);
    }

    @Override
    public Task withCount(int newCount) {
        return new StatTask(stat, newCount);
    }
}
