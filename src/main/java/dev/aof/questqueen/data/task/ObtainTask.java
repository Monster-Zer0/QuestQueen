package dev.aof.questqueen.data.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record ObtainTask(ResourceLocation item, int count) implements Task {
    public static final MapCodec<ObtainTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("item").forGetter(ObtainTask::item),
            TaskCounts.COUNT.optionalFieldOf("count", 1).forGetter(ObtainTask::count)
    ).apply(instance, ObtainTask::new));

    @Override
    public String type() {
        return "obtain";
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
        return new ObtainTask(item, newCount);
    }
}
