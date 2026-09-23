package dev.aof.questqueen.data.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

public record TriggerTask(String trigger) implements Task {
    public static final MapCodec<TriggerTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("trigger").forGetter(TriggerTask::trigger)
    ).apply(instance, TriggerTask::new));

    @Override
    public String type() {
        return "trigger";
    }

    @Override
    public Optional<String> triggerId() {
        return Optional.of(trigger);
    }

    @Override
    public Task withCount(int newCount) {
        return this;
    }
}
