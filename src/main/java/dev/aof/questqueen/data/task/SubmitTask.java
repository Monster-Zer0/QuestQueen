package dev.aof.questqueen.data.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record SubmitTask(ResourceLocation item, int count) implements Task {
    public static final MapCodec<SubmitTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("item").forGetter(SubmitTask::item),
            TaskCounts.COUNT.optionalFieldOf("count", 1).forGetter(SubmitTask::count)
    ).apply(instance, SubmitTask::new));

    @Override
    public String type() {
        return "submit";
    }

    @Override
    public int required() {
        return count;
    }

    @Override
    public Optional<ResourceLocation> itemId() {
        return Optional.of(item);
    }

    @Override
    public Task withCount(int newCount) {
        return new SubmitTask(item, newCount);
    }
}
