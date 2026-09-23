package dev.aof.questqueen.net;

import dev.aof.questqueen.data.QuestPack;
import dev.aof.questqueen.progress.ProgressSnapshot;

import java.util.function.Consumer;

public final class ClientSync {
    public static Consumer<QuestPack> DEFINITIONS = pack -> {
    };
    public static Consumer<ProgressSnapshot> PROGRESS = snapshot -> {
    };
    public static Consumer<Boolean> EDITOR = enabled -> {
    };
    public static OpenBook OPEN_BOOK = (tileId, expanded, chapter) -> {
    };

    @FunctionalInterface
    public interface OpenBook {
        void open(String tileId, boolean expanded, String chapter);
    }

    private ClientSync() {
    }

    public static void applyDefinitions(QuestPack pack) {
        DEFINITIONS.accept(pack);
    }

    public static void applyProgress(ProgressSnapshot snapshot) {
        PROGRESS.accept(snapshot);
    }

    public static void applyEditor(boolean enabled) {
        EDITOR.accept(enabled);
    }

    public static void applyOpenBook(String tileId, boolean expanded, String chapter) {
        OPEN_BOOK.open(tileId, expanded, chapter);
    }
}
