package dev.aof.questqueen.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Optional;

/** Per-chapter board background: solid color and/or GUI texture. */
public record ChapterBackground(
        String mode,
        Optional<String> color,
        Optional<ResourceLocation> image,
        float opacity
) {
    public static final String MODE_COLOR = "color";
    public static final String MODE_IMAGE = "image";

    public static final ChapterBackground DEFAULT = new ChapterBackground(MODE_COLOR, Optional.empty(), Optional.empty(), 1f);

    public static final Codec<ChapterBackground> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("mode", MODE_COLOR).forGetter(ChapterBackground::mode),
            Codec.STRING.optionalFieldOf("color").forGetter(ChapterBackground::color),
            ResourceLocation.CODEC.optionalFieldOf("image").forGetter(ChapterBackground::image),
            Codec.FLOAT.optionalFieldOf("opacity", 1f).forGetter(ChapterBackground::opacity)
    ).apply(instance, ChapterBackground::new));

    public ChapterBackground {
        String normalized = mode == null || mode.isBlank() ? MODE_COLOR : mode.trim().toLowerCase(Locale.ROOT);
        mode = MODE_IMAGE.equals(normalized) ? MODE_IMAGE : MODE_COLOR;
        color = color == null ? Optional.empty() : color;
        image = image == null ? Optional.empty() : image;
        opacity = Math.max(0f, Math.min(1f, opacity));
    }

    public boolean useImage() {
        return MODE_IMAGE.equals(mode) && image.isPresent();
    }

    /** Parse `#RRGGBB` / `#AARRGGBB` / `0xAARRGGBB` to ARGB, or empty if invalid. */
    public Optional<Integer> parsedColor() {
        if (color.isEmpty()) {
            return Optional.empty();
        }
        String raw = color.get().trim();
        if (raw.startsWith("#")) {
            raw = raw.substring(1);
        } else if (raw.startsWith("0x") || raw.startsWith("0X")) {
            raw = raw.substring(2);
        }
        try {
            if (raw.length() == 6) {
                return Optional.of(0xFF000000 | Integer.parseInt(raw, 16));
            }
            if (raw.length() == 8) {
                return Optional.of((int) Long.parseLong(raw, 16));
            }
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
        return Optional.empty();
    }
}
