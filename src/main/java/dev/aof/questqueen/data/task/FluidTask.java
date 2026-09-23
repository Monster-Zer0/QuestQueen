package dev.aof.questqueen.data.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record FluidTask(ResourceLocation fluid, int count) implements Task {
    public static final MapCodec<FluidTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("fluid").forGetter(FluidTask::fluid),
            TaskCounts.COUNT.optionalFieldOf("count", 1).forGetter(FluidTask::count)
    ).apply(instance, FluidTask::new));

    @Override
    public String type() {
        return "fluid";
    }

    @Override
    public int required() {
        return count;
    }

    @Override
    public Optional<ResourceLocation> fluidId() {
        return Optional.of(fluid);
    }

    @Override
    public Task withCount(int newCount) {
        return new FluidTask(fluid, newCount);
    }
}
