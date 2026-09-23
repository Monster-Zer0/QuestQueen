package dev.aof.questqueen.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record GateCondition(String type, String id) {
    public static final Codec<GateCondition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(GateCondition::type),
            Codec.STRING.fieldOf("id").forGetter(GateCondition::id)
    ).apply(instance, GateCondition::new));
}
