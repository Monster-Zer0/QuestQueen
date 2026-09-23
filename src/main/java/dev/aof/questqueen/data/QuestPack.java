package dev.aof.questqueen.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

public record QuestPack(List<Chapter> chapters, List<Scroll> scrolls, BookChrome chrome) {
    public static final Codec<QuestPack> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Chapter.CODEC.listOf().fieldOf("chapters").forGetter(QuestPack::chapters),
            Scroll.CODEC.listOf().fieldOf("scrolls").forGetter(QuestPack::scrolls),
            BookChrome.CODEC.optionalFieldOf("chrome", BookChrome.DEFAULT).forGetter(QuestPack::chrome)
    ).apply(instance, QuestPack::new));

    public QuestPack {
        chapters = List.copyOf(chapters);
        scrolls = List.copyOf(scrolls);
        if (chrome == null) {
            chrome = BookChrome.DEFAULT;
        }
    }

    public QuestPack(List<Chapter> chapters, List<Scroll> scrolls) {
        this(chapters, scrolls, BookChrome.DEFAULT);
    }

    public static QuestPack empty() {
        return new QuestPack(List.of(), List.of(), BookChrome.DEFAULT);
    }

    public QuestPack withChrome(BookChrome next) {
        return new QuestPack(chapters, scrolls, next == null ? BookChrome.DEFAULT : next);
    }
}