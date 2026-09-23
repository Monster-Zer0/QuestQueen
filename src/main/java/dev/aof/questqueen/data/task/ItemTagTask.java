package dev.aof.questqueen.data.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record ItemTagTask(ResourceLocation tag, int count) implements Task {
    public static final MapCodec<ItemTagTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("tag").forGetter(ItemTagTask::tag),
            TaskCounts.COUNT.optionalFieldOf("count", 1).forGetter(ItemTagTask::count)
    ).apply(instance, ItemTagTask::new));

    @Override
    public String type() {
        return "item_tag";
    }

    @Override
    public int required() {
        return count;
    }

    @Override
    public Optional<ResourceLocation> tagId() {
        return Optional.of(tag);
    }

    @Override
    public Task withCount(int newCount) {
        return new ItemTagTask(tag, newCount);
    }
}
