package dev.aof.questqueen.data.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record InteractBlockTask(ResourceLocation block, int count) implements Task {
    public static final MapCodec<InteractBlockTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("block").forGetter(InteractBlockTask::block),
            TaskCounts.COUNT.optionalFieldOf("count", 1).forGetter(InteractBlockTask::count)
    ).apply(instance, InteractBlockTask::new));

    @Override
    public String type() {
        return "interact_block";
    }

    @Override
    public int required() {
        return count;
    }

    @Override
    public Optional<ResourceLocation> blockId() {
        return Optional.of(block);
    }

    @Override
    public Task withCount(int newCount) {
        return new InteractBlockTask(block, newCount);
    }
}
