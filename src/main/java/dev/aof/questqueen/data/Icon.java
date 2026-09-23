package dev.aof.questqueen.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Optional;

/**
 * Tile/chapter icon: a Minecraft item, a QuestQueen glyph, or both.
 * If both are set, the glyph wins at draw time.
 */
public record Icon(Optional<ResourceLocation> item, Optional<String> glyph) {
    public static final Codec<Icon> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.optionalFieldOf("item").forGetter(Icon::item),
            Codec.STRING.optionalFieldOf("glyph").forGetter(Icon::glyph)
    ).apply(instance, Icon::new));

    public Icon {
        item = item == null ? Optional.empty() : item;
        glyph = glyph == null ? Optional.empty() : glyph;
    }

    public static Icon of(String itemId) {
        return of(ResourceLocation.parse(itemId));
    }

    public static Icon of(ResourceLocation itemId) {
        return new Icon(Optional.of(itemId), Optional.empty());
    }

    public static Icon glyph(String id) {
        return new Icon(Optional.empty(), Optional.of(id));
    }

    public Optional<String> glyphId() {
        return glyph.map(value -> value.toLowerCase(Locale.ROOT).trim()).filter(QuestGlyphIds::isKnown);
    }

    public boolean usesGlyph() {
        return glyphId().isPresent();
    }
}
