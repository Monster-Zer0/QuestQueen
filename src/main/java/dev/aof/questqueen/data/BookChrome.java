package dev.aof.questqueen.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record BookChrome(
        String sidebarTitle,
        int titleColor,
        float titleScale,
        boolean titleShadow,
        boolean titleUppercase
) {
    public static final BookChrome DEFAULT = new BookChrome("CHAPTERS", 0xFF8A7A9A, 1.0f, false, true);

    public static final Codec<BookChrome> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.optionalFieldOf("sidebarTitle", DEFAULT.sidebarTitle()).forGetter(BookChrome::sidebarTitle),
            Codec.INT.optionalFieldOf("titleColor", DEFAULT.titleColor()).forGetter(BookChrome::titleColor),
            Codec.FLOAT.optionalFieldOf("titleScale", DEFAULT.titleScale()).forGetter(BookChrome::titleScale),
            Codec.BOOL.optionalFieldOf("titleShadow", DEFAULT.titleShadow()).forGetter(BookChrome::titleShadow),
            Codec.BOOL.optionalFieldOf("titleUppercase", DEFAULT.titleUppercase()).forGetter(BookChrome::titleUppercase)
    ).apply(instance, BookChrome::new));

    public BookChrome {
        if (sidebarTitle == null || sidebarTitle.isBlank()) {
            sidebarTitle = DEFAULT.sidebarTitle();
        }
        titleScale = Math.max(0.5f, Math.min(2.0f, titleScale));
    }

    public BookChrome withTitle(String next) {
        return new BookChrome(next, titleColor, titleScale, titleShadow, titleUppercase);
    }

    public BookChrome withColor(int next) {
        return new BookChrome(sidebarTitle, next, titleScale, titleShadow, titleUppercase);
    }

    public BookChrome withScale(float next) {
        return new BookChrome(sidebarTitle, titleColor, next, titleShadow, titleUppercase);
    }

    public BookChrome withShadow(boolean next) {
        return new BookChrome(sidebarTitle, titleColor, titleScale, next, titleUppercase);
    }

    public String displayTitle() {
        return titleUppercase ? sidebarTitle.toUpperCase(java.util.Locale.ROOT) : sidebarTitle;
    }
}