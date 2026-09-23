package dev.aof.questqueen.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** Optional act/parent intro: one PNG plus markup body. */
public record ChapterIntro(Optional<ResourceLocation> image, String body) {
    public static final Codec<ChapterIntro> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("image").forGetter(ChapterIntro::image),
            Codec.STRING.optionalFieldOf("body", "").forGetter(ChapterIntro::body)
    ).apply(instance, ChapterIntro::new));

    public ChapterIntro {
        image = image == null ? Optional.empty() : image;
        body = body == null ? "" : body;
    }

    public static ChapterIntro of(String body) {
        return new ChapterIntro(Optional.empty(), body);
    }
}
