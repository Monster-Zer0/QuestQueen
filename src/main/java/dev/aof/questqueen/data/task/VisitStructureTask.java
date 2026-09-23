package dev.aof.questqueen.data.task;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

public record VisitStructureTask(ResourceLocation structure) implements Task {
    public static final MapCodec<VisitStructureTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("structure").forGetter(VisitStructureTask::structure)
    ).apply(instance, VisitStructureTask::new));

    @Override
    public String type() {
        return "visit_structure";
    }

    @Override
    public Optional<ResourceLocation> structureId() {
        return Optional.of(structure);
    }

    @Override
    public Task withCount(int newCount) {
        return this;
    }
}
