package dev.aof.questqueen.data;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Shared glyph id list (JSON / editor / runtime). Keep in sync with {@code QuestGlyphs}. */
public final class QuestGlyphIds {
    public static final List<String> ALL = List.of(
            "sword", "pickaxe", "hammer", "shield",
            "bread", "flask", "chest", "key",
            "map", "flag", "rocket", "flame",
            "book", "star", "skull", "speech"
    );

    private static final Set<String> KNOWN = Set.copyOf(ALL);

    private QuestGlyphIds() {
    }

    public static boolean isKnown(String id) {
        return id != null && KNOWN.contains(id.toLowerCase(Locale.ROOT).trim());
    }
}
