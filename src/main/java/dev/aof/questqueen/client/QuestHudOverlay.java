package dev.aof.questqueen.client;

import dev.aof.questqueen.QuestConfig;
import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.Tile;
import dev.aof.questqueen.data.reward.ChoiceReward;
import dev.aof.questqueen.data.reward.Reward;
import net.minecraft.ChatFormatting;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class QuestHudOverlay {
    /** Pin key the slide-in was armed for, and when it was armed. One pin exists at a time. */
    private static String fxPinKey = "";
    private static long fxPinAt;
    static final int MARGIN = 8;
    static final int HEIGHT = 22;
    private static final int TOOLTIP_W = 220;
    private static final int DESCRIPTION_LINES = 8;
    /** Where the pin was drawn last frame, and for which quest, so a screen drawn over it can show its hover. */
    private static int lastX, lastY, lastW;
    private static Chapter lastChapter;
    private static Tile lastTile;
    private static boolean lastDone, lastClaimed;
    private static String lastTitle = "";
    /** Dev runs only: a cursor position read from {@code qq-hud-hover.json}, since test drivers cannot move the cursor. */
    private static int[] testHover;
    private static long testHoverReadAt;

    /** Where the pinned quest goes; {@link #parse} reads the config word. */
    enum Anchor {
        TOP_LEFT, TOP_CENTER, TOP_RIGHT, MIDDLE_LEFT, MIDDLE_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

        static Anchor parse(String raw) {
            if (raw == null) {
                return TOP_LEFT;
            }
            try {
                return valueOf(raw.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_'));
            } catch (IllegalArgumentException unknown) {
                return TOP_LEFT;
            }
        }

        boolean top() {
            return this == TOP_LEFT || this == TOP_CENTER || this == TOP_RIGHT;
        }

        boolean bottom() {
            return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
        }
    }

    private QuestHudOverlay() {
    }

    /**
     * Top-left of a {@code w}×{@code h} box at {@code anchor} on a {@code guiW}×{@code guiH} screen. Offsets move it
     * away from its edge (toward the middle); the box is kept on screen.
     */
    static int[] place(Anchor anchor, int guiW, int guiH, int w, int h, int offX, int offY) {
        int x = switch (anchor) {
            case TOP_LEFT, MIDDLE_LEFT, BOTTOM_LEFT -> MARGIN + offX;
            case TOP_CENTER -> guiW / 2 - w / 2 + offX;
            case TOP_RIGHT, MIDDLE_RIGHT, BOTTOM_RIGHT -> guiW - MARGIN - w - offX;
        };
        int y = anchor.top() ? MARGIN + offY : anchor.bottom() ? guiH - MARGIN - h - offY : guiH / 2 - h / 2 + offY;
        x = Math.max(0, Math.min(guiW - w, x));
        y = Math.max(0, Math.min(guiH - h, y));
        return new int[]{x, y};
    }

    public static void render(GuiGraphics graphics, DeltaTracker delta) {
        Minecraft minecraft = Minecraft.getInstance();
        lastChapter = null;
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
        String status = claimed ? "CLAIMED" : (done ? "DONE" : pinStatus(chapter.get(), tile.get(), task).toUpperCase(Locale.ROOT));
        int width = Math.max(120, Math.max(minecraft.font.width(title.toUpperCase()), minecraft.font.width(status)) + 16);

        Anchor anchor = Anchor.TOP_LEFT;
        int offX = 0;
        int offY = 0;
        try {
            anchor = Anchor.parse(QuestConfig.PIN_POSITION.get());
            offX = QuestConfig.PIN_OFFSET_X.get();
            offY = QuestConfig.PIN_OFFSET_Y.get();
        } catch (IllegalStateException notLoaded) {
            // Config not loaded yet: top left.
        }
        int[] at = place(anchor, graphics.guiWidth(), graphics.guiHeight(), width, HEIGHT, offX, offY);
        int x = at[0];

        // Slide in from its edge when the pinned quest changes, so a new pin announces itself.
        String pinKey = ClientQuestState.progress.pinChapter().get() + "/" + ClientQuestState.progress.pinTile().get();
        if (!pinKey.equals(fxPinKey)) {
            fxPinKey = pinKey;
            fxPinAt = UiFx.nowMs();
        }
        float enter = UiFx.easeOut(UiFx.progress(fxPinAt, 320L));
        int slide = Math.round((1f - enter) * 14f);
        int y = at[1] + (anchor.bottom() ? slide : -slide);

        UiDraw.begin(graphics);
        UiDraw.fill(graphics, x, y, x + width, y + HEIGHT, QuestColors.CARD);
        int edge = done ? QuestColors.COMPLETED : QuestColors.CURRENT;
        if (!done) {
            // Soft breathing border while the pinned task is still unfinished: it is the "look here" cue.
            edge = UiFx.withAlpha(edge, 0.55f + 0.45f * UiFx.wave(2100L, 0L));
        }
        UiDraw.frame(graphics, x, y, width, HEIGHT, edge, 1);
        UiDraw.end();
        graphics.drawString(minecraft.font, Component.literal(title.toUpperCase()), x + 4, y + 2, QuestColors.COMPLETED, false);
        graphics.drawString(minecraft.font, Component.literal(status), x + 4, y + 12,
                claimed || done ? QuestColors.CURRENT : QuestColors.TEXT, false);

        lastX = x;
        lastY = y;
        lastW = width;
        lastChapter = chapter.get();
        lastTile = tile.get();
        lastDone = done;
        lastClaimed = claimed;
        lastTitle = title;
    }

    /**
     * With the cursor free (chat, the inventory or any other screen), hovering the pin shows the quest's details.
     * Drawn after the screen, so the screen's dim does not cover it.
     */
    public static void onScreenRender(net.neoforged.neoforge.client.event.ScreenEvent.Render.Post event) {
        if (lastChapter == null || event.getScreen() instanceof QuestBookScreen) {
            return;
        }
        int mx = event.getMouseX();
        int my = event.getMouseY();
        int[] test = testHover();
        if (test != null) {
            mx = test[0];
            my = test[1];
        }
        if (mx < lastX || mx >= lastX + lastW || my < lastY || my >= lastY + HEIGHT) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 400);
        Font font = Minecraft.getInstance().font;
        graphics.renderTooltip(font, details(font, lastChapter, lastTile, lastTitle, lastDone, lastClaimed), mx, my);
        pose.popPose();
    }

    private static int[] testHover() {
        if (!QuestBookScreen.TEST_HOOKS) {
            return null;
        }
        long now = System.currentTimeMillis();
        if (now - testHoverReadAt > 250) {
            testHoverReadAt = now;
            testHover = null;
            try {
                var path = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "qq-hud-hover.json");
                if (java.nio.file.Files.exists(path)) {
                    var json = com.google.gson.JsonParser.parseString(java.nio.file.Files.readString(path)).getAsJsonObject();
                    testHover = new int[]{json.get("x").getAsInt(), json.get("y").getAsInt()};
                }
            } catch (Exception ignored) {
                testHover = null;
            }
        }
        return testHover;
    }

    /**
     * The pin's second line: the task progress, except that an advancement task says what the advancement is
     * ("Have any Logs") instead of the bare word ADVANCEMENT.
     */
    static String pinStatus(Chapter chapter, Tile tile, String progress) {
        for (int i = 0; i < tile.tasks().size(); i++) {
            var task = tile.tasks().get(i);
            if (ClientQuestState.progress.taskCompleted(chapter.id() + "/" + tile.id(), Integer.toString(i))) {
                continue;
            }
            var id = task.advancementId();
            if (id.isPresent()) {
                return ClientQuestState.advancementInfo(id.get()).map(info -> info.title().getString()).orElse(progress);
            }
            return progress;
        }
        return progress;
    }

    /** The pinned quest's hover: title, chapter, description, each task with its count, rewards, state. */
    static List<FormattedCharSequence> details(Font font, Chapter chapter, Tile tile, String title, boolean done, boolean claimed) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        lines.add(Component.literal(title).withStyle(ChatFormatting.YELLOW).getVisualOrderText());
        String chapterName = chapter.title().isBlank() ? chapter.id().toString() : chapter.title();
        lines.add(Component.literal(chapterName).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        String description = RichText.plain(QuestBookScreen.inspectBody(tile));
        if (!description.isBlank()) {
            List<FormattedCharSequence> body = new ArrayList<>();
            for (String paragraph : description.split("\n")) {
                if (!paragraph.isBlank()) {
                    body.addAll(font.split(Component.literal(paragraph.trim()).withStyle(ChatFormatting.GRAY), TOOLTIP_W));
                }
            }
            if (body.size() > DESCRIPTION_LINES) {
                body = new ArrayList<>(body.subList(0, DESCRIPTION_LINES));
                body.add(Component.literal("…").withStyle(ChatFormatting.GRAY).getVisualOrderText());
            }
            lines.addAll(body);
        }
        for (int i = 0; i < tile.tasks().size(); i++) {
            var task = tile.tasks().get(i);
            String count = ClientQuestState.taskCount(chapter, tile, i);
            String verb = task.advancementId().flatMap(ClientQuestState::advancementInfo)
                    .map(info -> info.title().getString()).orElse(ClientQuestState.taskVerb(task, tile));
            String row = count.isEmpty() ? verb : verb + "  " + count;
            boolean taskDone = done || ClientQuestState.progress.taskCompleted(chapter.id() + "/" + tile.id(), Integer.toString(i));
            lines.add(Component.literal((taskDone ? "✔ " : "• ") + row)
                    .withStyle(taskDone ? ChatFormatting.GREEN : ChatFormatting.WHITE).getVisualOrderText());
        }
        List<String> rewards = new ArrayList<>();
        for (Reward reward : tile.rewards()) {
            rewards.add(reward instanceof ChoiceReward ? reward.describe() : RewardFaces.label(reward));
        }
        if (!rewards.isEmpty()) {
            lines.addAll(font.split(Component.literal("Rewards: " + String.join(", ", rewards)).withStyle(ChatFormatting.GOLD), TOOLTIP_W));
        }
        String state = claimed ? "Claimed" : done ? "Done: open the quest book to claim" : "Open the quest book for more";
        lines.add(Component.literal(state).withStyle(ChatFormatting.DARK_GRAY).getVisualOrderText());
        return lines;
    }
}
