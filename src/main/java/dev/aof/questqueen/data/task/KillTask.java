package dev.aof.questqueen.data.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record KillTask(Optional<ResourceLocation> entity, Optional<ResourceLocation> tag, int count) implements Task {
    public static final MapCodec<KillTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("entity").forGetter(KillTask::entity),
            ResourceLocation.CODEC.optionalFieldOf("tag").forGetter(KillTask::tag),
            TaskCounts.COUNT.optionalFieldOf("count", 1).forGetter(KillTask::count)
    ).apply(instance, KillTask::new));

    public KillTask(ResourceLocation entity, int count) {
        this(Optional.of(entity), Optional.empty(), count);
    }

    @Override
    public String type() {
        return "kill";
    }

    @Override
    public int required() {
        return count;
    }

    @Override
    public Optional<ResourceLocation> entityId() {
        return entity;
    }

    @Override
    public Optional<ResourceLocation> tagId() {
        return tag;
    }

    @Override
    public Task withCount(int newCount) {
        return new KillTask(entity, tag, newCount);
    }
}
