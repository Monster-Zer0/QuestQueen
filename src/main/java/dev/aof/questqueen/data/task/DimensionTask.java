package dev.aof.questqueen.data.task;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record DimensionTask(ResourceLocation dimension) implements Task {
    public static final MapCodec<DimensionTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("dimension").forGetter(DimensionTask::dimension)
    ).apply(instance, DimensionTask::new));

    @Override
    public String type() {
        return "visit_dimension";
    }

    @Override
    public Optional<ResourceLocation> dimensionId() {
        return Optional.of(dimension);
    }

    @Override
    public Task withCount(int newCount) {
        return this;
    }
}
