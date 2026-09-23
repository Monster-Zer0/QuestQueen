package dev.aof.questqueen.data.task;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record BiomeTask(ResourceLocation biome) implements Task {
    public static final MapCodec<BiomeTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("biome").forGetter(BiomeTask::biome)
    ).apply(instance, BiomeTask::new));

    @Override
    public String type() {
        return "visit_biome";
    }

    @Override
    public Optional<ResourceLocation> biomeId() {
        return Optional.of(biome);
    }

    @Override
    public Task withCount(int newCount) {
        return this;
    }
}
