package dev.aof.questqueen.data.task;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record AdvancementTask(ResourceLocation advancement) implements Task {
    public static final MapCodec<AdvancementTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("advancement").forGetter(AdvancementTask::advancement)
    ).apply(instance, AdvancementTask::new));

    @Override
    public String type() {
        return "advancement";
    }

    @Override
    public Optional<ResourceLocation> advancementId() {
        return Optional.of(advancement);
    }

    @Override
    public Task withCount(int newCount) {
        return this;
    }
}
