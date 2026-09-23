package dev.aof.questqueen.data.task;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record ObservationTask(Optional<ResourceLocation> block, Optional<ResourceLocation> entity) implements Task {
    public static final MapCodec<ObservationTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("block").forGetter(ObservationTask::block),
            ResourceLocation.CODEC.optionalFieldOf("entity").forGetter(ObservationTask::entity)
    ).apply(instance, ObservationTask::new));

    @Override
    public String type() {
        return "observation";
    }

    @Override
    public Optional<ResourceLocation> blockId() {
        return block;
    }

    @Override
    public Optional<ResourceLocation> entityId() {
        return entity;
    }

    @Override
    public Task withCount(int newCount) {
        return this;
    }
}
