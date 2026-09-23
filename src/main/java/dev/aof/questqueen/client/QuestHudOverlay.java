package dev.aof.questqueen.client;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public final class QuestHudOverlay {
    /** Pin key the slide-in was armed for, and when it was armed. One pin exists at a time. */
    private static String fxPinKey = "";
    private static long fxPinAt;

    private QuestHudOverlay() {
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.screen instanceof QuestBookScreen) {
            return;
        }
        if (ClientQuestState.progress.pinChapter().isEmpty() || ClientQuestState.progress.pinTile().isEmpty()) {
            fxPinKey = "";
            return;
        }
        var chapter = ClientQuestState.chapter(net.minecraft.resources.ResourceLocation.parse(ClientQuestState.progress.pinChapter().get()));
        if (chapter.isEmpty()) {
            return;
        }
        var tile = chapter.get().tile(ClientQuestState.progress.pinTile().get());
        if (tile.isEmpty()) {
            return;
        }
        String title = tile.get().title().isBlank() ? tile.get().id() : tile.get().title();
        String task = ClientQuestState.taskProgressLabel(chapter.get(), tile.get());
        boolean done = ClientQuestState.progress.tileCompleted(chapter.get().id().toString(), tile.get().id());
        boolean claimed = ClientQuestState.rewardsClaimed(chapter.get(), tile.get());
        int width = Math.max(120, minecraft.font.width(title.toUpperCase()) + 16);
        int x = graphics.guiWidth() / 2 - width / 2;

        // Slide in when the pinned quest changes, so a new pin announces itself.
        String pinKey = ClientQuestState.progress.pinChapter().get() + "/" + ClientQuestState.progress.pinTile().get();
        if (!pinKey.equals(fxPinKey)) {
            fxPinKey = pinKey;
            fxPinAt = UiFx.nowMs();
        }
        float enter = UiFx.easeOut(UiFx.progress(fxPinAt, 320L));
        int y = 8 - Math.round((1f - enter) * 14f);

        UiDraw.begin(graphics);
        UiDraw.fill(graphics, x, y, x + width, y + 22, QuestColors.CARD);
        int edge = done ? QuestColors.COMPLETED : QuestColors.CURRENT;
        if (!done) {
            // Soft breathing border while the pinned task is still unfinished: it is the "look here" cue.
            edge = UiFx.withAlpha(edge, 0.55f + 0.45f * UiFx.wave(2100L, 0L));
        }
        UiDraw.frame(graphics, x, y, width, 22, edge, 1);
        UiDraw.end();
        graphics.drawString(minecraft.font, Component.literal(title.toUpperCase()), x + 4, y + 2, QuestColors.COMPLETED, false);
        String status = claimed ? "CLAIMED" : (done ? "DONE" : task.toUpperCase());
        graphics.drawString(minecraft.font, Component.literal(status), x + 4, y + 12,
                claimed || done ? QuestColors.CURRENT : QuestColors.TEXT, false);
    }
}
