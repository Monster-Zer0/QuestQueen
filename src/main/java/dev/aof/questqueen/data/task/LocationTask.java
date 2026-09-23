package dev.aof.questqueen.data.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

public record LocationTask(int x, int y, int z, int radius, String dimension) implements Task {
    public static final MapCodec<LocationTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.fieldOf("x").forGetter(LocationTask::x),
            Codec.INT.fieldOf("y").forGetter(LocationTask::y),
            Codec.INT.fieldOf("z").forGetter(LocationTask::z),
            Codec.INT.optionalFieldOf("radius", 4).forGetter(LocationTask::radius),
            Codec.STRING.optionalFieldOf("dimension", "").forGetter(LocationTask::dimension)
    ).apply(instance, LocationTask::new));

    @Override
    public String type() {
        return "location";
    }

    @Override
    public Optional<String> locationKey() {
        return Optional.of(x + "," + y + "," + z + "," + radius + "," + dimension);
    }

    @Override
    public Task withCount(int newCount) {
        return this;
    }
}
