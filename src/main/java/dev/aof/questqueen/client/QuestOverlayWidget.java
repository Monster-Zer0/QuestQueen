package dev.aof.questqueen.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;

/**
 * Inspector / modal chrome. Fills after {@code super.render} are dropped;
 * fills inside this widget pass stay visible.
 */
public final class QuestOverlayWidget extends AbstractWidget {
    public record Chip(int x, int y, int w, int h, int color) {
    }

    private boolean shown;
    private int face = QuestColors.CARD;
    private int edge = QuestColors.CURRENT;
    private boolean headerBar = true;
    private int headerW = 72;
    private boolean pluses;
    private boolean closeX = true;
    private boolean dimBackdrop;
    private int dimLeft;
    private List<Chip> chips = List.of();
    /** Enter/exit motion: alpha in [0,1] and a small Y settle (geometry only — hue untouched). */
    private float fxAlpha = 1f;
    private int fxOffsetY;

    public QuestOverlayWidget() {
        super(0, 0, 1, 1, Component.empty());
        this.active = false;
        this.shown = false;
    }

    public void setFx(float alpha, int offsetY) {
        this.fxAlpha = UiFx.clamp01(alpha);
        this.fxOffsetY = offsetY;
    }

    public void sync(boolean shown, int x, int y, int w, int h, int face, int edge, boolean headerBar, boolean pluses,
                     List<Chip> chips) {
        sync(shown, x, y, w, h, face, edge, headerBar, Math.min(72, Math.max(40, w / 3)), pluses, true, false, chips);
    }

    public void sync(boolean shown, int x, int y, int w, int h, int face, int edge, boolean headerBar, int headerW,
                     boolean pluses, List<Chip> chips) {
        sync(shown, x, y, w, h, face, edge, headerBar, headerW, pluses, true, false, chips);
    }

    public void sync(boolean shown, int x, int y, int w, int h, int face, int edge, boolean headerBar, int headerW,
                     boolean pluses, boolean closeX, List<Chip> chips) {
        sync(shown, x, y, w, h, face, edge, headerBar, headerW, pluses, closeX, false, chips);
    }

    public void sync(boolean shown, int x, int y, int w, int h, int face, int edge, boolean headerBar, int headerW,
                     boolean pluses, boolean closeX, boolean dimBackdrop, List<Chip> chips) {
        this.shown = shown;
        this.face = face;
        this.edge = edge;
        this.headerBar = headerBar;
        this.headerW = headerW;
        this.pluses = pluses;
        this.closeX = closeX;
        this.dimBackdrop = dimBackdrop;
        this.dimLeft = 0;
        this.chips = chips;
        setPosition(x, y);
        setSize(Math.max(1, w), Math.max(1, h));
        visible = shown;
    }

    public void setDimLeft(int dimLeft) {
        this.dimLeft = Math.max(0, dimLeft);
    }

    public int dimLeft() {
        return dimLeft;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!shown || fxAlpha <= 0.01f) {
            return;
        }
        graphics.setColor(1f, 1f, 1f, 1f);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 250);
        if (dimBackdrop) {
            // Dim after tiles (this widget is added last) so the quest grid stays under GOTCHA/XOR.
            Minecraft mc = Minecraft.getInstance();
            int sw = mc.getWindow().getGuiScaledWidth();
            int sh = mc.getWindow().getGuiScaledHeight();
            // Clip dim to the board — full-screen wipe erased sidebar chapter rows (Jerry).
            int dimA = Math.round(0xAA * fxAlpha);
            graphics.fill(Math.max(0, dimLeft), 0, sw, sh, (dimA << 24) | 0x0D0A14);
        }
        int x = getX();
        int y = getY() + fxOffsetY;
        int w = getWidth();
        int h = getHeight();
        int faceA = UiFx.scaleAlpha(face, fxAlpha);
        int edgeA = UiFx.scaleAlpha(edge, fxAlpha);
        if (headerBar) {
            MockChrome.panel(graphics, x, y, w, h, faceA, edgeA, headerW);
        } else {
            MockChrome.box(graphics, x, y, w, h, faceA);
            if (edge != 0) {
                MockChrome.frame(graphics, x, y, w, h, edgeA);
            }
        }
        if (closeX) {
            MockChrome.closeX(graphics, x + w - 12, y + 3, UiFx.scaleAlpha(MockChrome.tagWhite(), fxAlpha));
        }
        if (pluses) {
            // Mock: small crosses on the exterior bottom-left / bottom-right frame corners.
            MockChrome.bottomCornerPluses(graphics, x, y, w, h, edgeA);
        }
        for (Chip chip : chips) {
            MockChrome.box(graphics, chip.x, chip.y + fxOffsetY, chip.w, chip.h, UiFx.scaleAlpha(chip.color, fxAlpha));
        }
        pose.popPose();
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
    }
}