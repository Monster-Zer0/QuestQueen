package dev.aof.questqueen.data.task;

import com.mojang.serialization.MapCodec;

public record CheckmarkTask() implements Task {
    public static final MapCodec<CheckmarkTask> CODEC = MapCodec.unit(new CheckmarkTask());

    @Override
    public String type() {
        return "checkmark";
    }

    @Override
    public Task withCount(int newCount) {
        return this;
    }
}
