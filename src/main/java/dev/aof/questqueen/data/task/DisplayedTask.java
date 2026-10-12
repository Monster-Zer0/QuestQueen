package dev.aof.questqueen.data.task;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/**
 * A task with a display item ({@code "icon": "minecraft:ender_eye"}): it behaves exactly like {@code task} and only
 * adds the item the book shows on its row. Made by the task codec when the field is present, so no task type had to
 * grow a field of its own. Code that needs the concrete type uses {@link Task#unwrap(Task)}.
 */
public record DisplayedTask(Task task, String icon) implements Task {
    public DisplayedTask {
        task = Task.unwrap(task);
        icon = icon == null ? "" : icon.trim();
    }

    @Override
    public Optional<String> displayItem() {
        return icon.isEmpty() ? Optional.empty() : Optional.of(icon);
    }

    @Override
    public String type() {
        return task.type();
    }

    @Override
    public int required() {
        return task.required();
    }

    @Override
    public Optional<ResourceLocation> itemId() {
        return task.itemId();
    }

    @Override
    public Optional<ResourceLocation> tagId() {
        return task.tagId();
    }

    @Override
    public Optional<ResourceLocation> entityId() {
        return task.entityId();
    }

    @Override
    public Optional<ResourceLocation> advancementId() {
        return task.advancementId();
    }

    @Override
    public Optional<ResourceLocation> structureId() {
        return task.structureId();
    }

    @Override
    public Optional<ResourceLocation> blockId() {
        return task.blockId();
    }

    @Override
    public Optional<ResourceLocation> statId() {
        return task.statId();
    }

    @Override
    public Optional<String> npcId() {
        return task.npcId();
    }

    @Override
    public Optional<String> triggerId() {
        return task.triggerId();
    }

    @Override
    public Optional<String> locationKey() {
        return task.locationKey();
    }

    @Override
    public Optional<ResourceLocation> dimensionId() {
        return task.dimensionId();
    }

    @Override
    public Optional<ResourceLocation> biomeId() {
        return task.biomeId();
    }

    @Override
    public Optional<ResourceLocation> fluidId() {
        return task.fluidId();
    }

    @Override
    public Task withCount(int count) {
        return new DisplayedTask(task.withCount(count), icon);
    }

    @Override
    public String describe() {
        return task.describe();
    }
}
