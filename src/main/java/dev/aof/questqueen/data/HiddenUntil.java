package dev.aof.questqueen.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record HiddenUntil(String type, String id) {
    public static final Codec<HiddenUntil> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("type").forGetter(HiddenUntil::type),
            Codec.STRING.fieldOf("id").forGetter(HiddenUntil::id)
    ).apply(instance, HiddenUntil::new));
}
