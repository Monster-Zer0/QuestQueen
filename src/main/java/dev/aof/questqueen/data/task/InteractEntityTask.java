package dev.aof.questqueen.data.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record InteractEntityTask(ResourceLocation entity, int count) implements Task {
    public static final MapCodec<InteractEntityTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("entity").forGetter(InteractEntityTask::entity),
            TaskCounts.COUNT.optionalFieldOf("count", 1).forGetter(InteractEntityTask::count)
    ).apply(instance, InteractEntityTask::new));

    @Override
    public String type() {
        return "interact_entity";
    }

    @Override
    public int required() {
        return count;
    }

    @Override
    public Optional<ResourceLocation> entityId() {
        return Optional.of(entity);
    }

    @Override
    public Task withCount(int newCount) {
        return new InteractEntityTask(entity, newCount);
    }
}
