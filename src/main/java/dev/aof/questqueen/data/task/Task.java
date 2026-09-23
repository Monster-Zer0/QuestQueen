package dev.aof.questqueen.data.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public interface Task {
    Codec<Task> CODEC = Codec.STRING.dispatch("type", Task::type, Task::codecFor);

    String type();

    default int required() {
        return 1;
    }

    default Optional<ResourceLocation> itemId() {
        return Optional.empty();
    }

    default Optional<ResourceLocation> tagId() {
        return Optional.empty();
    }

    default Optional<ResourceLocation> entityId() {
        return Optional.empty();
    }

    default Optional<ResourceLocation> advancementId() {
        return Optional.empty();
    }

    default Optional<ResourceLocation> structureId() {
        return Optional.empty();
    }

    default Optional<ResourceLocation> blockId() {
        return Optional.empty();
    }

    default Optional<ResourceLocation> statId() {
        return Optional.empty();
    }

    default Optional<String> npcId() {
        return Optional.empty();
    }

    default Optional<String> triggerId() {
        return Optional.empty();
    }

    default Optional<String> locationKey() {
        return Optional.empty();
    }

    default Optional<ResourceLocation> dimensionId() {
        return Optional.empty();
    }

    default Optional<ResourceLocation> biomeId() {
        return Optional.empty();
    }

    default Optional<ResourceLocation> fluidId() {
        return Optional.empty();
    }

    Task withCount(int count);

    default String describe() {
        return type() + itemId().map(id -> " " + id.getPath()).orElse("")
                + entityId().map(id -> " " + id.getPath()).orElse("")
                + (required() > 1 ? " x" + required() : "");
    }

    private static MapCodec<? extends Task> codecFor(String type) {
        return TaskCodecs.forType(type);
    }
}
