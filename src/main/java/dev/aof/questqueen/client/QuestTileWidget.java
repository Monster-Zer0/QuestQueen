package dev.aof.questqueen.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.ToIntFunction;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/** Closed-tile chrome + labels in the vanilla widget pass so the inspector can overlay them. */
public final class QuestTileWidget extends AbstractWidget {
    private int face;
    private int edge;
    private int headerW;
    private boolean ornaments;
    private boolean expand;
    private int[][] ports = new int[0][];
    private boolean locked;
    private String header = "";
    private int headerInk = QuestColors.TEXT;
    private ItemStack icon = ItemStack.EMPTY;
    private String title1 = "";
    private String title2 = "";
    private String title3 = "";
    private boolean titleBeside;
    private ItemStack reward = ItemStack.EMPTY;
    private List<ItemStack> rewards = List.of();
    private boolean rewardPlus;
    static final int REWARD_FACE_CAP = 3;
    private boolean xor;
    private String progress = "";
    private int progressInk = QuestColors.TEXT;
    private boolean clipOverlay;
    private int overlayX;
    private int overlayY;
    private int overlayW;
    private int overlayH;
    private boolean clipBoard;
    private int clipL;
    private int clipT;
    private int clipR;
    private int clipB;
    private int hoverX = Integer.MIN_VALUE;
    private int hoverY = Integer.MIN_VALUE;
    private String hoverLock = "";

    private String glyph = "";
    /** Soft breathing selection halo (alpha only). */
    private boolean selectionHalo;

    /** Ledger footer: the count text and bar of an open quest, or the CLAIM cue of a finished one. */
    private String countText = "";
    private boolean showBar;
    private final BarTween bar = new BarTween();
    private boolean claimable;
    /** Seeds the claim glint's phase so a row of ready tiles does not flash in step. */
    private int shineSeed;
    private boolean decorSet;
    /** When a LOCKED tile opened while the book was showing it; MIN_VALUE when no reveal is running. */
    private long unlockAt = Long.MIN_VALUE;
    /** Hover lift, eased toward 1 while the pointer is over the tile and back to 0 after. */
    private float lift;
    private long liftFrameMs = Long.MIN_VALUE;

    /** Locked tiles draw their content at this alpha; the reveal eases it up to 1. */
    static final float LOCKED_ALPHA = 0.6f;
    /** The reveal waits for the gate pip to run down the arrow before the tile opens. */
    static final long UNLOCK_DELAY_MS = 450L;
    static final long UNLOCK_MS = 500L;
    static final long LIFT_MS = 120L;
    static final long SHINE_PERIOD_MS = 3000L;

    public QuestTileWidget(int x, int y, int size, int face, int edge, int headerW) {
        super(x, y, size, size, Component.empty());
        this.face = face;
        this.edge = edge;
        this.headerW = headerW;
        this.active = false;
    }

    public void setChrome(int x, int y, int size, int face, int edge, int headerW, boolean ornaments, boolean expand) {
        setPosition(x, y);
        setSize(size, size);
        this.face = face;
        this.edge = edge;
        this.headerW = headerW;
        this.ornaments = ornaments;
        this.expand = expand;
    }

    public void setDecor(boolean locked, String header, int headerInk, ItemStack icon, String title1, String title2,
                         boolean titleBeside, ItemStack reward, boolean rewardPlus, boolean xor) {
        setDecor(locked, header, headerInk, icon, title1, title2, "", titleBeside, reward, rewardPlus, xor);
    }

    public void setDecor(boolean locked, String header, int headerInk, ItemStack icon, String title1, String title2,
                         String title3, boolean titleBeside, ItemStack reward, boolean rewardPlus, boolean xor) {
        if (decorSet && this.locked && !locked && UiFx.enabled()) {
            unlockAt = UiFx.nowMs() + UNLOCK_DELAY_MS;
        }
        decorSet = true;
        this.locked = locked;
        this.header = header == null ? "" : header;
        this.headerInk = headerInk;
        this.icon = icon == null ? ItemStack.EMPTY : icon;
        this.title1 = title1 == null ? "" : title1;
        this.title2 = title2 == null ? "" : title2;
        this.title3 = title3 == null ? "" : title3;
        this.titleBeside = titleBeside;
        this.reward = reward == null ? ItemStack.EMPTY : reward;
        this.rewards = this.reward.isEmpty() ? List.of() : List.of(this.reward);
        this.rewardPlus = rewardPlus;
        this.xor = xor;
    }

    public void setGlyph(String glyph) {
        this.glyph = glyph == null ? "" : glyph;
    }

    private boolean hasIconFace() {
        return !icon.isEmpty() || !glyph.isEmpty();
    }

    public void setRewardFaces(List<ItemStack> faces, boolean plus) {
        List<ItemStack> copy = new ArrayList<>();
        if (faces != null) {
            for (ItemStack stack : faces) {
                if (stack != null && !stack.isEmpty()) {
                    copy.add(stack);
                }
            }
        }
        this.rewards = List.copyOf(copy);
        this.reward = this.rewards.isEmpty() ? ItemStack.EMPTY : this.rewards.getFirst();
        this.rewardPlus = plus;
    }

    public void setProgress(String progress, int progressInk) {
        this.progress = progress == null ? "" : progress;
        this.progressInk = progressInk;
    }

    /**
     * Ledger footer state. {@code fraction} below 0 hides the bar; a new fraction eases in from the drawn one.
     * {@code claimable} swaps the REWARDS caption for a CLAIM chip.
     */
    public void setLedger(String countText, float fraction, boolean claimable, int shineSeed) {
        this.countText = countText == null ? "" : countText;
        this.showBar = fraction >= 0f;
        if (showBar) {
            bar.retarget(fraction, UiFx.nowMs());
        }
        this.claimable = claimable;
        this.shineSeed = shineSeed;
    }

    public void setSelectionHalo(boolean selectionHalo) {
        this.selectionHalo = selectionHalo;
    }

    public void setShown(boolean shown) {
        this.visible = shown;
    }

    public void setPorts(int[][] ports) {
        this.ports = ports == null ? new int[0][] : ports;
    }

    public void setBoardClip(int left, int top, int right, int bottom) {
        this.clipBoard = true;
        this.clipL = left;
        this.clipT = top;
        this.clipR = right;
        this.clipB = bottom;
    }

    public void setOverlayClip(boolean clip, int x, int y, int w, int h) {
        this.clipOverlay = clip;
        this.overlayX = x;
        this.overlayY = y;
        this.overlayW = w;
        this.overlayH = h;
    }

    public void setHover(int mouseX, int mouseY) {
        this.hoverX = mouseX;
        this.hoverY = mouseY;
    }

    /** "open" green lock, "locked" red lock, or empty when the pointer is off the path arrow. */
    public String hoverLock() {
        return hoverLock == null ? "" : hoverLock;
    }

    @Override
    protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (clipBoard && (getX() + getWidth() <= clipL || getY() + getHeight() <= clipT
                || getX() >= clipR || getY() >= clipB)) {
            return;
        }
        if (clipBoard) {
            graphics.enableScissor(clipL, clipT, clipR, clipB);
        }
        try {
        graphics.setColor(1f, 1f, 1f, 1f);
        int x = getX();
        int y = getY();
        int size = getWidth();
        int hx = hoverX != Integer.MIN_VALUE ? hoverX : mouseX;
        int hy = hoverY != Integer.MIN_VALUE ? hoverY : mouseY;
        long now = UiFx.nowMs();
        boolean underOverlay = clipOverlay && overlaps(hx, hy, 1, 1, overlayX, overlayY, overlayW, overlayH);
        boolean hovered = !locked && !underOverlay && hx >= x && hy >= y && hx < x + size && hy < y + size;
        stepLift(hovered, now);
        float reveal = revealProgress(now);
        // Hover lift: the tile (not its path arrows) rides 1px up and its rail thickens by one.
        int liftPx = lift > 0.5f ? 1 : 0;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, -liftPx, 0);
        drawLedgerChrome(graphics, x, y, size, reveal, liftPx);
        if (selectionHalo && UiFx.enabled() && edge != 0) {
            float breath = UiFx.wave(2100L, (x * 31L) ^ (y * 17L));
            int halo = UiFx.withAlpha(edge, 0.22f + 0.38f * breath);
            MockChrome.frame(graphics, x - 1, y - 1, size + 2, size + 2, halo);
        }
        if (showsExpand(locked, expand) && edge != 0 && size >= 22) {
            // Data D3: LOCKED never gets the expand glyph, even if chrome asked for it.
            MockChrome.expandIcon(graphics, x + size - 4 - 7, y + 2, edge);
        }
        pose.popPose();
        hoverLock = "";
        for (int[] port : ports) {
            if (port.length < 6) {
                continue;
            }
            int dx = port[4];
            int dy = port[5];
            int lockX = port.length >= 8 ? port[6] : port[0];
            int lockY = port.length >= 8 ? port[7] : port[1];
            if (itemHidden(lockX - 4, lockY - 4, 8, 8)) {
                continue;
            }
            boolean inbound = port.length >= 9 && port[8] != 0;
            if (inbound) {
                continue;
            }
            boolean destLocked = port[3] != 0;
            int color = port.length >= 3 ? port[2] : MockChrome.pathArrowColor(destLocked);
            boolean hover = MockChrome.pathHoverHit(hx, hy, lockX, lockY);
            if (hover) {
                hoverLock = destLocked ? "locked" : "open";
            }
            MockChrome.pathGate(graphics, lockX, lockY, dx, dy, destLocked, hover, color, UiFx.flowAt(lockX, lockY));
        }
        float contentAlpha = locked ? LOCKED_ALPHA : UiFx.lerp(LOCKED_ALPHA, 1f, reveal);
        pose.pushPose();
        pose.translate(0, -liftPx, 0);
        if (contentAlpha < 0.999f) {
            graphics.setColor(1f, 1f, 1f, contentAlpha);
        }
        drawDecor(graphics, x, y, size, reveal, now);
        graphics.setColor(1f, 1f, 1f, 1f);
        pose.popPose();
        } finally {
            if (clipBoard) {
                graphics.disableScissor();
            }
        }
    }

    /** Data D3: expand glyph is off on LOCKED everywhere. */
    static boolean showsExpand(boolean locked, boolean expand) {
        return expand && !locked;
    }

    /**
     * Ledger tile chrome, as on the quest card: the face, a hairline frame and a rail down the left edge in the
     * state colour. While a reveal runs the rail cross-fades from locked red to the new state colour by alpha
     * alone, so neither colour is ever mixed into a third.
     */
    private void drawLedgerChrome(GuiGraphics graphics, int x, int y, int size, float reveal, int extraRail) {
        MockChrome.box(graphics, x, y, size, size, face);
        MockChrome.frame(graphics, x, y, size, size, QuestColors.SIDEBAR_EDGE);
        if (edge == 0) {
            return;
        }
        int rail = railPx(size) + extraRail;
        if (reveal < 1f) {
            MockChrome.box(graphics, x, y, rail, size, QuestColors.LOCKED_EDGE);
            MockChrome.box(graphics, x, y, rail, size, UiFx.withAlpha(edge, reveal));
        } else {
            MockChrome.box(graphics, x, y, rail, size, edge);
        }
    }

    private void stepLift(boolean hovered, long now) {
        if (!UiFx.enabled()) {
            lift = 0f;
            liftFrameMs = now;
            return;
        }
        long dt = liftFrameMs == Long.MIN_VALUE ? 0L : Math.max(0L, Math.min(100L, now - liftFrameMs));
        liftFrameMs = now;
        float step = dt / (float) LIFT_MS;
        lift = hovered ? Math.min(1f, lift + step) : Math.max(0f, lift - step);
    }

    /** 0 when a reveal has just been armed, 1 when none is running or it has finished. */
    private float revealProgress(long now) {
        if (unlockAt == Long.MIN_VALUE || !UiFx.enabled()) {
            return 1f;
        }
        if (now < unlockAt) {
            return 0f;
        }
        float t = UiFx.easeOut(UiFx.clamp01((now - unlockAt) / (float) UNLOCK_MS));
        if (t >= 1f) {
            unlockAt = Long.MIN_VALUE;
        }
        return t;
    }

    /** Width of the state rail: 3px, or 2px on the small zoom rungs. */
    static int railPx(int size) {
        return size >= 40 ? 3 : 2;
    }

    /** Left inset of tile content, clear of the rail (and of the rail's hover width). */
    static int contentInset(int size) {
        return Math.max(padPx(size), railPx(size) + 2);
    }

    private static final String SCRAMBLE = "?#%&*+=";

    /**
     * The title as it settles during an unlock reveal: the first {@code t} of it is real, the rest is noise that
     * changes every 60ms. Spaces stay spaces so the words keep their shape.
     */
    static String descramble(String text, float t, int seed, long now) {
        if (text == null || text.isEmpty() || t >= 1f) {
            return text == null ? "" : text;
        }
        int keep = Math.round(text.length() * UiFx.clamp01(t));
        int bucket = (int) (now / 60L);
        StringBuilder out = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (i < keep || c == ' ') {
                out.append(c);
            } else {
                int pick = (int) (UiFx.hash01(seed + bucket, i) * SCRAMBLE.length());
                out.append(SCRAMBLE.charAt(Math.min(SCRAMBLE.length() - 1, pick)));
            }
        }
        return out.toString();
    }

    static int iconPx(int size) {
        int px = Math.max(8, Math.min(16, Math.round(16f * size / 64f)));
        return px & ~1;
    }

    /** Even inset so 0.5-scale labels and 1px frames stay on the pixel grid. */
    static int padPx(int size) {
        return size >= 56 ? 4 : 2;
    }

    static boolean compact(int size) {
        return size < 48;
    }

    static boolean stacked(int size, boolean hasIcon) {
        return hasIcon && size < 64;
    }

    /** Half-scale captions: hard-cap at 2 lines (ellipsize). */
    static int titleLines(int size) {
        return compact(size) ? 1 : 2;
    }

    /**
     * Caption line budget. Always {@link #titleLines(int)} — max two half-scale lines.
     * The old third-line grant is retired (ellipsize instead of shrinking).
     */
    static int captionBudget(int size, boolean facesPresent, boolean xorPresent) {
        return titleLines(size);
    }

    static int titleBudget(int size, boolean hasIcon) {
        int pad = padPx(size);
        int icon = hasIcon && !stacked(size, hasIcon) ? iconPx(size) + 2 : 0;
        return Math.max(20, (size - pad - contentInset(size) - icon) * 2);
    }

    static int rewardPx(int size) {
        return Math.max(6, iconPx(size) > 12 ? 10 : 8);
    }

    /** Top of the reward icon row; the REWARDS caption sits 6px above it. */
    static int rewardRowY(int y, int size) {
        return y + size - padPx(size) - rewardPx(size);
    }

    private void drawDecor(GuiGraphics graphics, int x, int y, int size, float reveal, long now) {
        Font font = Minecraft.getInstance().font;
        int pad = padPx(size);
        int inset = contentInset(size);
        int iconSize = iconPx(size);
        boolean compact = compact(size);
        boolean stacked = stacked(size, titleBeside && hasIconFace());
        if (!header.isEmpty()) {
            // The state word sits where the filled corner tab used to be, in the state colour.
            tiny(graphics, font, header, x + inset, y + 2, edge != 0 ? edge : headerInk);
        }
        int iconX = x + inset;
        int iconY = y + Math.max(8, size >= 56 ? 12 : 10);
        if (!glyph.isEmpty()) {
            QuestGlyphs.draw(graphics, glyph, iconX, iconY, iconSize, locked ? QuestColors.LOCKED_TEXT : QuestColors.TEXT);
        } else if (!icon.isEmpty()) {
            drawScaledItem(graphics, icon, iconX, iconY, iconSize);
        }
        int titleX = hasIconFace() && !stacked ? iconX + iconSize + 2 : iconX;
        int titleMax = x + size - pad;
        int titleY;
        if (hasIconFace() && stacked) {
            titleY = iconY + iconSize + 1;
        } else if (compact) {
            titleY = iconY + Math.max(0, (iconSize - 6) / 2);
        } else {
            titleY = titleBeside && !stacked ? y + 16 : y + 20;
        }
        int ink = locked ? QuestColors.LOCKED_TEXT : CAPTION_INK;
        // Hoisted so the caption budget (F7 fix B) is decided from the renderer's own conditions.
        List<ItemStack> faces = rewards.isEmpty() && !reward.isEmpty() ? List.of(reward) : rewards;
        String fullTitle = descramble(joinTitle(title1, title2, title3), reveal, x * 31 + y, now);
        if (!fullTitle.isEmpty()) {
            int budget = captionBudget(size, !faces.isEmpty(), xor);
            // When reward icons are present, leave a clear band above REWARDS so title lines
            // cannot paint through the caption.
            if (!faces.isEmpty() && size >= 40 && !compact) {
                int rewardsLabelY = rewardRowY(y, size) - LINE_STEP;
                int fit = Math.max(1, (rewardsLabelY - titleY) / LINE_STEP);
                budget = Math.min(budget, fit);
            }
            List<String> lines = wrapTiny(font, fullTitle, Math.max(8, titleMax - titleX), budget);
            for (int i = 0; i < lines.size(); i++) {
                tiny(graphics, font, lines.get(i), titleX, titleY + i * LINE_STEP, ink);
            }
        }
        if (showBar && !locked && size >= 40) {
            drawProgressBar(graphics, x, y, size, now);
        } else if (!progress.isEmpty() && size >= 40) {
            MockChrome.box(graphics, x + railPx(size), y + size - 3, size - railPx(size) - 1, 2, edge);
            if (title1.isEmpty() && title2.isEmpty()) {
                List<String> prog = wrapTiny(font, progress, Math.max(8, titleMax - (x + inset)), 1);
                if (!prog.isEmpty()) {
                    tiny(graphics, font, prog.getFirst(), x + inset, y + size - 10, progressInk);
                }
            }
        }
        if (!countText.isEmpty() && !locked && size >= 40 && !compact) {
            // Beside the XOR badge when there is one, and only when it clears the reward icons: the bar below
            // still carries the progress when the row is too crowded for the number.
            int countX = xor && size >= 48 ? x + inset + 17 : x + inset;
            int countW = (int) Math.ceil(font.width(countText) * TINY_SCALE);
            if (countFits(countX, countW, rewardStripLeft(x, size, faces.size()))) {
                tiny(graphics, font, countText, countX, y + size - 10, QuestColors.MUTED);
            }
        }
        if (!faces.isEmpty() && size >= 40) {
            int rewardPx = rewardPx(size);
            int show = Math.min(faces.size(), REWARD_FACE_CAP);
            int gap = show > 1 ? 1 : 0;
            int totalW = show * rewardPx + Math.max(0, show - 1) * gap;
            int rx = x + size - pad - totalW;
            int ry = rewardRowY(y, size);
            if (!compact && claimable) {
                drawClaimChip(graphics, font, x + size - pad, ry - LINE_STEP - 2, now);
            } else if (!compact) {
                // Slightly smaller than tile captions so it reads as chrome, not body text.
                tinySmall(graphics, font, "REWARDS", x + size - 26, ry - LINE_STEP, REWARDS_INK);
            }
            for (int i = 0; i < show; i++) {
                ItemStack face = faces.get(i);
                int ix = rx + i * (rewardPx + gap);
                drawScaledItem(graphics, face, ix, ry, rewardPx);
                // Count drawn over the icon (bottom-right) so the amount reads
                if (face.getCount() > 1) {
                    var pose = graphics.pose();
                    pose.pushPose();
                    pose.translate(0, 0, 200);
                    tiny(graphics, font, face.getCount() + "x", ix + 1, ry + Math.max(0, rewardPx - 5), QuestColors.TEXT);
                    pose.popPose();
                }
            }
        }
        if (xor && size >= 48) {
            int inner = x + inset;
            MockChrome.box(graphics, inner, y + size - 11, 15, 6, QuestColors.COMPLETED);
            tiny(graphics, font, "XOR", inner + 2, y + size - 10, MockChrome.INK);
        }
    }

    /** Left edge of the reward icon strip at the bottom-right ({@code x + size} when there are none). */
    static int rewardStripLeft(int x, int size, int faces) {
        if (faces <= 0) {
            return x + size;
        }
        int rewardPx = rewardPx(size);
        int show = Math.min(faces, REWARD_FACE_CAP);
        int gap = show > 1 ? 1 : 0;
        return x + size - padPx(size) - (show * rewardPx + Math.max(0, show - 1) * gap);
    }

    /** The count fits when it ends at least 2px short of the reward strip. */
    static boolean countFits(int countX, int countW, int stripLeft) {
        return countX + countW <= stripLeft - 2;
    }

    /** 2px bar along the bottom, inside the frame and clear of the rail, easing to each new fraction. */
    private void drawProgressBar(GuiGraphics graphics, int x, int y, int size, long now) {
        int left = x + railPx(size);
        int w = size - railPx(size) - 1;
        int barY = y + size - 3;
        MockChrome.box(graphics, left, barY, w, 2, UiFx.withAlpha(QuestColors.CELL_LINE, 0.7f));
        int fill = Math.round(w * bar.value(now));
        MockChrome.box(graphics, left, barY, fill, 2, edge != 0 ? edge : QuestColors.CURRENT);
        float glow = bar.edgeAlpha(now);
        if (glow > 0f && fill > 0) {
            MockChrome.box(graphics, left + fill - 1, barY - 1, 1, 4, UiFx.withAlpha(QuestColors.TEXT, glow));
        }
    }

    static final int CLAIM_CHIP_H = 7;

    /** Filled CLAIM chip, right edge at {@code right}; a thin glint crosses it now and then. */
    private void drawClaimChip(GuiGraphics graphics, Font font, int right, int top, long now) {
        int textW = (int) Math.ceil(font.width("CLAIM") * TINY_SCALE);
        int w = textW + 4;
        int left = right - w;
        MockChrome.box(graphics, left, top, w, CLAIM_CHIP_H, QuestColors.COMPLETED);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(left + 2, top + 1, 0);
        pose.scale(TINY_SCALE, TINY_SCALE, 1f);
        graphics.drawString(font, "CLAIM", 0, 0, MockChrome.INK, false);
        pose.popPose();
        int glint = claimShineX(UiFx.phase(SHINE_PERIOD_MS, shineSeed * 389L), w);
        if (glint >= 0) {
            MockChrome.box(graphics, left + glint, top, Math.min(2, w - glint), CLAIM_CHIP_H, 0x8CFFFFFF);
        }
    }

    /**
     * Glint column inside a chip {@code w} wide at loop {@code phase}: it crosses in the first third of the loop
     * and is absent (-1) for the rest, so the cue is a passing gleam rather than a blink.
     */
    static int claimShineX(float phase, int w) {
        float sweep = 0.33f;
        if (phase >= sweep || w <= 0) {
            return -1;
        }
        int x = Math.round(phase / sweep * (w + 2)) - 2;
        return x < 0 || x >= w ? -1 : x;
    }

    private void drawScaledItem(GuiGraphics graphics, ItemStack stack, int x, int y, int px) {
        if (stack.isEmpty() || itemHidden(x, y, px, px)) {
            return;
        }
        int[] vis = itemVisible(x, y, px, px);
        if (vis == null) {
            return;
        }
        boolean scissor = vis[0] != x || vis[1] != y || vis[2] != px || vis[3] != px;
        if (scissor) {
            graphics.enableScissor(vis[0], vis[1], vis[0] + vis[2], vis[1] + vis[3]);
        }
        float itemScale = px / 16f;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(itemScale, itemScale, 1f);
        graphics.renderItem(stack, 0, 0);
        pose.popPose();
        if (scissor) {
            graphics.disableScissor();
        }
    }

    /** Hide icons that sit on the inspector or overlap the sidebar/top bar (ImmediatelyFast flushes late). */
    private boolean itemHidden(int x, int y, int w, int h) {
        if (clipBoard && (x < clipL || y < clipT)) {
            return true;
        }
        if (!clipOverlay) {
            return false;
        }
        int cx = x + w / 2;
        int cy = y + h / 2;
        return cx >= overlayX && cy >= overlayY && cx < overlayX + overlayW && cy < overlayY + overlayH;
    }

    private int[] itemVisible(int x, int y, int w, int h) {
        int vx = x;
        int vy = y;
        int vw = w;
        int vh = h;
        if (clipBoard) {
            int ix = Math.max(x, clipL);
            int iy = Math.max(y, clipT);
            int ix2 = Math.min(x + w, clipR);
            int iy2 = Math.min(y + h, clipB);
            if (ix >= ix2 || iy >= iy2) {
                return null;
            }
            vx = ix;
            vy = iy;
            vw = ix2 - ix;
            vh = iy2 - iy;
        }
        if (!clipOverlay || !overlaps(vx, vy, vw, vh, overlayX, overlayY, overlayW, overlayH)) {
            return new int[]{vx, vy, vw, vh};
        }
        return largestRemainder(vx, vy, vw, vh, overlayX, overlayY, overlayW, overlayH);
    }

    private static boolean overlaps(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
        return ax < bx + bw && ax + aw > bx && ay < by + bh && ay + ah > by;
    }

    private static int[] largestRemainder(int ax, int ay, int aw, int ah, int bx, int by, int bw, int bh) {
        int ix = Math.max(ax, bx);
        int iy = Math.max(ay, by);
        int ix2 = Math.min(ax + aw, bx + bw);
        int iy2 = Math.min(ay + ah, by + bh);
        if (ix >= ix2 || iy >= iy2) {
            return new int[]{ax, ay, aw, ah};
        }
        int[] best = null;
        int bestArea = 0;
        best = consider(best, bestArea, ax, ay, aw, iy - ay);
        bestArea = area(best);
        best = consider(best, bestArea, ax, iy2, aw, ay + ah - iy2);
        bestArea = area(best);
        best = consider(best, bestArea, ax, iy, ix - ax, iy2 - iy);
        bestArea = area(best);
        best = consider(best, bestArea, ix2, iy, ax + aw - ix2, iy2 - iy);
        return area(best) <= 0 ? null : best;
    }

    private static int[] consider(int[] best, int bestArea, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return best;
        }
        int area = w * h;
        if (best == null || area > bestArea) {
            return new int[]{x, y, w, h};
        }
        return best;
    }

    private static int area(int[] rect) {
        return rect == null || rect.length < 4 ? 0 : Math.max(0, rect[2]) * Math.max(0, rect[3]);
    }

    private static String joinTitle(String a, String b, String c) {
        StringBuilder out = new StringBuilder();
        appendWord(out, a);
        appendWord(out, b);
        appendWord(out, c);
        return out.toString();
    }

    private static void appendWord(StringBuilder out, String part) {
        if (part == null || part.isBlank()) {
            return;
        }
        if (!out.isEmpty()) {
            out.append(' ');
        }
        out.append(part.trim());
    }

    /** House truncation marker - matches {@code ellipsize()} and {@code drawWrapped}'s body trim. */
    static final String TRUNCATION_MARKER = "...";

    /** Word-wrap at half-scale. Last line clips without a hyphen (no "PICK A PATH-"). */
    /**
     * Caption scale vs default font. Half-scale. Pixel-snap + MC shadow; no outline.
     * Kept as a named constant so {@link #wrapTiny} maxW stays aligned with draw.
     */
    static final float TINY_SCALE = 0.5f;

    /** Title / header cream: readable on dark tile faces without soft bloom. */
    static final int CAPTION_INK = 0xFFF5E6C8;

    /** REWARDS label — same band as titles, one cream step warmer/dimmer. */
    static final int REWARDS_INK = 0xFFE8D4A8;

    /** Half-scale line advance (visual ~5px glyph + gap → 6px step). */
    static final int LINE_STEP = 6;

    static List<String> wrapTiny(Font font, String text, int visualMax, int maxLines) {
        return wrapTiny(font::width, text, visualMax, maxLines);
    }

    /**
     * Marked truncation. Marker reserved before the shrink so the last line
     * ends with {@link #TRUNCATION_MARKER}. Width-function seam for headless tests.
     */
    static List<String> wrapTiny(ToIntFunction<String> widthOf, String text, int visualMax, int maxLines) {
        String upper = text == null ? "" : text.toUpperCase(Locale.ROOT).trim();
        if (upper.isEmpty()) {
            return List.of();
        }
        int maxW = Math.max(8, Math.round(Math.max(8, visualMax) / TINY_SCALE));
        int keep = Math.max(1, maxLines);
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : upper.split(" +")) {
            if (word.isEmpty()) {
                continue;
            }
            String next = line.isEmpty() ? word : line + " " + word;
            if (widthOf.applyAsInt(next) > maxW && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        if (lines.size() > keep) {
            StringBuilder last = new StringBuilder(lines.get(keep - 1));
            for (int i = keep; i < lines.size(); i++) {
                last.append(' ').append(lines.get(i));
            }
            lines = new ArrayList<>(lines.subList(0, keep));
            lines.set(keep - 1, last.toString());
        }
        if (!lines.isEmpty()) {
            String last = lines.get(lines.size() - 1);
            if (widthOf.applyAsInt(last) > maxW) {
                int markerW = widthOf.applyAsInt(TRUNCATION_MARKER);
                String cut = last;
                while (cut.length() > 1 && widthOf.applyAsInt(cut) + markerW > maxW) {
                    cut = cut.substring(0, cut.length() - 1);
                }
                lines.set(lines.size() - 1, cut + TRUNCATION_MARKER);
            }
        }
        return lines;
    }

    /**
     * Half-scale caption: floor snap, pose.scale(0.5), MC chat shadow only. No cardinal outline.
     */
    private static void tiny(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        var pose = graphics.pose();
        pose.pushPose();
        // Integer snap before half-scale so glyphs stay on the pixel grid (Linux/Hyprland).
        pose.translate(Math.floor(x), Math.floor(y), 0);
        pose.scale(TINY_SCALE, TINY_SCALE, 1f);
        // Minecraft chat shadow only — single (+1,+1). No cardinal outline (1.1.190 blob).
        graphics.drawString(font, text, 0, 0, color, true);
        pose.popPose();
    }

    /** Smaller than half-scale captions — chrome labels like REWARDS. */
    private static void tinySmall(GuiGraphics graphics, Font font, String text, int x, int y, int color) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(Math.floor(x), Math.floor(y), 0);
        pose.scale(0.35f, 0.35f, 1f);
        graphics.drawString(font, text, 0, 0, color, true);
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
