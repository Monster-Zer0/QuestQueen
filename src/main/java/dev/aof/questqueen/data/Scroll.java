package dev.aof.questqueen.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

public record Scroll(ResourceLocation id, String title, String text) {
    public static final Codec<Scroll> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("id").forGetter(Scroll::id),
            Codec.STRING.fieldOf("title").forGetter(Scroll::title),
            Codec.STRING.fieldOf("text").forGetter(Scroll::text)
    ).apply(instance, Scroll::new));
}
