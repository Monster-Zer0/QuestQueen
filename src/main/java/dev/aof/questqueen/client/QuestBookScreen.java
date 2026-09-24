package dev.aof.questqueen.client;

import com.mojang.serialization.JsonOps;
import dev.aof.questqueen.QuestConfig;
import dev.aof.questqueen.QuestQueen;
import dev.aof.questqueen.data.BookChrome;
import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.ChapterBackground;
import dev.aof.questqueen.data.ChapterIntro;
import dev.aof.questqueen.data.ChapterTree;
import dev.aof.questqueen.data.GateOp;
import dev.aof.questqueen.data.GridPos;
import dev.aof.questqueen.data.Icon;
import dev.aof.questqueen.data.Link;
import dev.aof.questqueen.data.QuestDefinitions;
import dev.aof.questqueen.data.Scroll;
import dev.aof.questqueen.data.StartNodes;
import dev.aof.questqueen.data.Tile;
import dev.aof.questqueen.data.reward.ChoiceReward;
import dev.aof.questqueen.data.reward.Reward;
import dev.aof.questqueen.data.reward.RewardFactory;
import dev.aof.questqueen.data.reward.RewardIcons;
import dev.aof.questqueen.data.task.LocationTask;
import dev.aof.questqueen.data.task.Task;
import dev.aof.questqueen.data.task.TaskFactory;
import dev.aof.questqueen.data.task.TaskVerbs;
import dev.aof.questqueen.net.AuthorChromeC2S;
import dev.aof.questqueen.net.AuthorSaveC2S;
import dev.aof.questqueen.net.ClaimAllC2S;
import dev.aof.questqueen.net.ClaimChoiceC2S;
import dev.aof.questqueen.net.ClaimRewardsC2S;
import dev.aof.questqueen.net.PinC2S;
import dev.aof.questqueen.net.QuestNetwork;
import dev.aof.questqueen.net.SubmitTaskC2S;
import dev.aof.questqueen.progress.ClaimAll;
import dev.aof.questqueen.progress.ProgressSnapshot;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.lwjgl.glfw.GLFW;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public class QuestBookScreen extends Screen {
    public static final int TILE = 64;
    /** Mock density. Gutters stay empty; path arrows are border-port wedges. TILE_STEPS keep GAP*zoom integer. */
    public static final int GAP = 12;
    public static final int STRIDE = TILE + GAP;
    /** Integer-friendly tile sizes so GAP*zoom and 1px frames stay on-pixel. */
    private static final int[] TILE_STEPS = {48, 64, 80, 96, 112, 128};

    /**
     * Dev-only preview rung for Troi's pre-registered 0.5-tier criterion (ledger 4739; Picard's queue
     * addendum 10): 32 px tiles under a 0.5 fit floor, reachable ONLY through the probe channel -
     * {@code qq-click.json} {@code {"previewZoom":0.5}} - the same dev path the count-mode comparison flag
     * uses. OFF until asked: an ordinary session keeps the shipped ladder and the 0.75 floor, so this
     * ships nothing. 0.25 stays refused (ledger 3975). A 0.5 rung is legal on the ladder's own rule -
     * GAP * step / TILE = 12 * 32 / 64 = 6 is integer, so the 1 px frames stay on-pixel.
     */
    private static volatile boolean previewHalf;
    private static final int[] PREVIEW_TILE_STEPS = {32, 48, 64, 80, 96, 112, 128};

    /** The rung ladder in force: the shipped one, or the preview ladder after the probe has asked for 0.5. */
    private static int[] tileSteps() {
        return previewHalf ? PREVIEW_TILE_STEPS : TILE_STEPS;
    }

    /** The fit floor in force: 0.75 shipped; 0.5 only while the probe-held preview is on. */
    private static float fitFloor() {
        return previewHalf ? 0.5f : 0.75f;
    }
    public static final int SIDEBAR = 148;
    private static final int SIDEBAR_MIN = 96;
    private static final int SIDEBAR_MAX = 260;
    static final int TAB_W = 12;
    static final int TAB_H = 36;
    static final int TOP_H = 26;
    private static final int FIT_S = 14;
    private static final int LOG_W = 48;
    private static int savedSidebarW = SIDEBAR;
    private static boolean savedSidebarCollapsed;
    public static final ResourceLocation FONT_ID = QuestQueen.id("questqueen");

    private Chapter chapter;
    private double cameraX = -2 * STRIDE;
    private double cameraY = -2 * STRIDE;
    private float zoom = 1f;
    private String selectedId = "";
    private boolean expanded;
    private boolean logOpen;
    private boolean authoring;
    private float bounce;
    private boolean dragging;
    private boolean resizingSidebar;
    private int sidebarW = savedSidebarW;
    private boolean sidebarCollapsed = savedSidebarCollapsed;
    private int sidebarScroll;
    private int sidebarListH;
    private int introBodyScroll;
    private int introBodyContentH;
    private String sidebarHoverTitle = "";
    private final Map<ResourceLocation, int[]> introTexSize = new HashMap<>();
    private double lastMx;
    private double lastMy;
    private EditBox search;
    private final List<String> searchHits = new ArrayList<>();
    private int searchIndex;
    private String linkFrom = "";
    private GateOp pendingGate = GateOp.AND;
    private final ItemPickerOverlay picker = new ItemPickerOverlay();
    private boolean editingTitle;
    private boolean editingBody;
    private String titleBuffer = "";
    private String bodyBuffer = "";
    private boolean chromeEditorOpen;
    private boolean editingChromeTitle;
    private String chromeTitleBuffer = "";
    private static final int[] CHROME_COLOR_PRESETS = new int[]{
            QuestColors.SIDEBAR_HEADER,
            QuestColors.SIDEBAR_TEXT,
            QuestColors.CURRENT,
            QuestColors.NEW,
            QuestColors.COMPLETED,
            QuestColors.EDIT,
            0xFFFFFFFF,
            QuestColors.MUTED
    };
    private ModalKind modal = ModalKind.NONE;
    private String modalTileId = "";
    private final List<SidebarRow> sidebarRows = new ArrayList<>();
    private boolean bookScaleApplied;
    private boolean closing;

    /**
     * True until this instance's FIRST init() has run. A fresh open creates a new instance; every later
     * init() on it is a re-entry (a child screen returning, or a resize) and must not re-run the FIT or
     * wipe the player's place. Init-count aware (F8 clause 7): stable across any number of re-entries.
     */
    private boolean freshOpen = true;

    /** Probe-only (F8 clause 7): total init() invocations in this client, for the init-count test. */
    private static int INIT_COUNT;
    private final List<QuestTileWidget> tileWidgets = new ArrayList<>();
    private final QuestOverlayWidget inspectPanel = new QuestOverlayWidget();
    private final QuestOverlayWidget modalPanel = new QuestOverlayWidget();
    private static String pendingTileId = "";
    private static boolean pendingExpanded;
    private static final Set<String> collapsedChapters = new HashSet<>();
    /** Persisted inspect drag offset while a quest is pinned (chapter/tile key). */
    private static final Map<String, int[]> PINNED_CARD_OFFSET = new HashMap<>();
    private boolean draggingInspect;
    private int inspectOffX;
    private int inspectOffY;
    /** XOR parents whose modal was dismissed. Fork debt stays until a child completes. */
    private final Set<String> xorDismissed = new HashSet<>();
    /** Marionette / probe hover — sticky until cleared so a screenshot can catch the lock. */
    private static final int[][] EMPTY_PORTS = new int[0][];
    private boolean widgetsDirty = true;
    private int lastSyncCamSx = Integer.MIN_VALUE;
    private int lastSyncCamSy = Integer.MIN_VALUE;
    private int lastSyncTilePx = -1;
    private int lastSyncBoardLeft = -1;
    private int lastSyncWidth = -1;
    private int lastSyncHeight = -1;
    private int lastSyncInspectOffX = Integer.MIN_VALUE;
    private int lastSyncInspectOffY = Integer.MIN_VALUE;
    private ProgressSnapshot lastSyncProgress;
    private ResourceLocation lastSyncChapterId;
    private String lastSyncSelected = "";
    private ModalKind lastSyncModal = ModalKind.NONE;
    private boolean lastSyncAuthoring;
    private boolean lastSyncLogOpen;
    private boolean lastSyncExpanded;
    private final Set<Long> occupiedCells = new HashSet<>();
    private ProgressSnapshot occupiedProgress;
    private ResourceLocation occupiedChapterId;
    private boolean occupiedAuthoring;
    private int occupiedTileCount = -1;
    private int hoverOverrideX = Integer.MIN_VALUE;
    private int hoverOverrideY = Integer.MIN_VALUE;
    /** Soft fade-lock padlock after GOTCHA cap (screen-space, tile center or sidebar row). */
    private int fadeLockX;
    private int fadeLockY;
    private long fadeLockStartTick = -1L;
    private int fadeLockMs = 1000;

    // --- animation state (see UiFx) -----------------------------------------------------------------
    /** Last observed visual per tile, so one-shot effects fire on a real transition and not on re-render. */
    private final Map<String, TileVisual> fxSeenVisuals = new HashMap<>();
    /** tile id -> start ms of its completion flare. */
    private final Map<String, Long> fxFlareAt = new HashMap<>();
    /** tile id -> start ms of its FAILED crack. */
    private final Map<String, Long> fxCrackAt = new HashMap<>();
    /** link key -> start ms of its unlock ignition pip. */
    private final Map<String, Long> fxPipAt = new HashMap<>();
    /** Sidebar active-row highlight, interpolated so selecting a chapter slides rather than jumps. */
    private float fxSidebarBarY = Float.NaN;
    private float fxSidebarBarH = Float.NaN;
    private long fxSidebarBarMovedAt;
    /** Millis of the last sidebar scroll input, for the scroll-thumb fade. */
    private long fxScrollActivityAt;
    /** Millis the current chapter was opened, for the intro typewriter. */
    private long fxChapterOpenedAt;
    private ResourceLocation fxChapterOpenedId;
    /** GOTCHA modal entrance, so its shimmer starts with the modal rather than mid-sweep. */
    private long fxModalOpenedAt;
    /** Tiles unlocked as of the previous frame, so a newly opened gate can fire an ignition pip. */
    private final Set<String> fxWasUnlocked = new HashSet<>();
    private boolean fxUnlockBaselineReady;

    // --- catalog motion (1.1.159) -------------------------------------------------------------------
    private boolean fxInspectWanted;
    private long fxInspectMovedAt;
    private boolean fxInspectExiting;
    private String fxInspectExitTileId = "";
    private String fxInspectLiveId = "";
    private long fxChoiceAt = -1L;
    private String fxChoiceTileId = "";
    private boolean fxChoiceWasPending;
    private long fxClaimSparkAt = -1L;
    private int fxClaimSparkX;
    private int fxClaimSparkY;
    private final Map<String, Long> fxSidebarRevealAt = new HashMap<>();
    private final Set<String> fxSidebarSeenListed = new HashSet<>();
    private boolean fxSidebarBaselineReady;
    private boolean fxLogClosing;
    private long fxLogAt = -1L;
    private long fxPinFlashAt = -1L;
    private boolean fxPinFlashPinned;
    private long fxSearchFlashAt = -1L;
    private String fxSearchFlashId = "";
    private final Map<String, Float> fxTaskFill = new HashMap<>();

    private static final long FLARE_MS = 620L;
    private static final long PIP_MS = 900L;
    private static final long CRACK_MS = 320L;
    private static final long SIDEBAR_SLIDE_MS = 160L;
    private static final long SCROLL_FADE_MS = 1100L;
    private static final long TYPEWRITER_MS = 900L;
    private static final long INSPECT_MS = 180L;
    private static final long BOARD_FADE_MS = 200L;
    private static final long INTRO_IMAGE_MS = 500L;
    private static final long CHOICE_MS = 280L;
    private static final long CLAIM_SPARK_MS = 520L;
    private static final long SIDEBAR_REVEAL_MS = 250L;
    private static final long LOG_DRAWER_MS = 220L;
    private static final long PIN_FLASH_MS = 320L;
    private static final long TASK_FILL_MS = 300L;
    /** Target icons painted on the inspect card this frame: drives their tooltip and the JEI click. */
    private final List<TaskIconHit> taskIconHits = new ArrayList<>();
    /** Cap on task rows drawn on the inspect card; matches the authoring cap. */
    private static final int MAX_TASK_ROWS = 6;
    /** Most tag members drawn as a mosaic in one task row; the rest become the {@code +N} chip. */
    static final int MOSAIC_MAX = 6;
    /** One mosaic cell, i.e. a 16x16 item drawn at half scale so six of them fit inside one row. */
    static final int MOSAIC_CELL = 8;
    /** Horizontal gap between mosaic cells. */
    static final int MOSAIC_GAP = 1;
    /** Cell pitch, so {@code n} cells span {@code n * MOSAIC_PITCH - MOSAIC_GAP} pixels. */
    static final int MOSAIC_PITCH = MOSAIC_CELL + MOSAIC_GAP;
    /** Gap between the target icons and the task label. */
    static final int STRIP_LABEL_GAP = 4;
    /** Right edge of the task-label band, as an inset from the card's right edge. */
    static final int LABEL_RIGHT_MARGIN = 74;
    /** Left inset of the icon strip, and the vertical inset that centres an 8px mosaic in a 12px row. */
    static final int STRIP_X = 10;
    private static final int MOSAIC_INSET_Y = 4;
    /** Task rows actually drawn on the last card paint, after the space budget was applied. */
    private int drawnTaskRows;
    /** Body-line budget the last card paint used, reported by the probe. */
    private int lastBodyBudget;
    /** Log view scroll offset in pixels, and the measured content height that bounds it. */
    private int logScroll;
    private int logContentH;
    /** Scroll offset for the CLAIM ALL preview list. */
    private int claimAllScroll;

    private enum ModalKind { NONE, GOTCHA, XOR, CLAIM_ALL }

    private record SidebarRow(ResourceLocation id, int y, int depth, boolean locked, boolean hasChildren,
                              boolean collapsed, int caretX) {
    }

    private record PathArrow(String from, String to, int x, int y, boolean locked, int color) {
    }

    /**
     * One painted line of the progress log. {@code y} is in scroll-independent content space, so the same
     * layout can be measured and drawn at any offset. The icon is an {@code Optional} rather than an
     * {@code ItemStack.EMPTY} sentinel so the layout stays constructible without bootstrapping Minecraft
     * (touching {@code ItemStack.EMPTY} runs its static initialiser, which needs the game registries).
     */
    private record LogRow(int y, String text, int color, Optional<ItemStack> stack, int inset) {
    }

    /**
     * A task row's target icons, recorded during the inspect draw so the tooltip and the JEI click can use
     * the exact same rectangle that was painted.
     *
     * <p>Widened from a single {@code ItemStack} to a strip of them: an {@code item_tag} task accepts any
     * member of a tag ({@code minecraft:logs} is 199 items), so one representative icon lied to the player.
     * {@code stacks} holds the members actually painted (at most {@link #MOSAIC_MAX}); {@code tagMembers} is
     * the tag's true size, or 0 when this is not a tag row, so the tooltip can say how many were hidden.
     */
    private record TaskIconHit(int x, int y, int w, int h, int taskIndex, List<ItemStack> stacks, int tagMembers) {
        boolean over(double mouseX, double mouseY) {
            return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        }

        /** Centre of the painted strip, for the harness's synthetic click. */
        int centreX() {
            return x + Math.max(1, w) / 2;
        }

        int centreY() {
            return y + Math.max(1, h) / 2;
        }

        /** The one item this row stands for: the first painted member. */
        ItemStack stack() {
            return stacks.isEmpty() ? ItemStack.EMPTY : stacks.getFirst();
        }

        boolean isTag() {
            return tagMembers > 0;
        }

        /** Tag members this row could not show, i.e. the {@code +N} chip. */
        int overflow() {
            return Math.max(0, tagMembers - stacks.size());
        }
    }

    public QuestBookScreen() {
        super(Component.translatable("screen.questqueen.book"));
        this.chapter = ClientQuestState.rememberedChapter().orElseGet(ClientQuestState::firstChapter);
        ClientQuestState.rememberChapter(this.chapter.id());
    }

    @Override
    protected void init() {
        // A fresh open creates this instance; every init after that is a re-entry (a child screen
        // returning, or a resize re-init). `closing` is set by removed() and was never cleared, so on
        // the way back the scale re-apply below early-returned (F8: the book drew at the player's
        // scale) and focusStartTile() re-ran the FIT and wiped the selection. Gate on the INSTANCE
        // flag (freshOpen), NOT on `closing`, so the behaviour survives any number of re-entries
        // (F8 clause 7 - the init count goes above one); re-assert the place after every writer.
        boolean fresh = freshOpen;
        freshOpen = false;
        closing = false;
        String keepSelected = selectedId;
        boolean keepExpanded = expanded;
        INIT_COUNT++;
        QuestQueen.LOGGER.info("[questqueen] book init #{} fresh={}", INIT_COUNT, fresh);
        applyBookGuiScale();
        search = new EditBox(font, searchX(), 7, searchW(), 12, Component.literal("Search"));
        // EditBox draws the hint in the text colour, so an unstyled hint looked like typed text.
        search.setHint(Component.literal("Search quests").withColor(QuestColors.SIDEBAR_HEADER & 0x00FFFFFF));
        search.setBordered(false);
        search.setTextColor(QuestColors.SIDEBAR_TEXT);
        search.setTextColorUneditable(QuestColors.SIDEBAR_HEADER);
        search.setCanLoseFocus(true);
        search.setResponder(this::onSearch);
        addRenderableWidget(search);
        layoutSearch();
        setFocused(null);
        authoring = false;
        if (fresh) {
            focusStartTile();
        }
        applyPendingFocus();
        restorePinnedCardIfNeeded();
        if (!fresh && keepSelected != null && !keepSelected.isEmpty()) {
            // After all writers of selectedId/expanded: the pre-trip place wins on a re-entry.
            selectedId = keepSelected;
            expanded = keepExpanded;
        }
        rebuildTileWidgets();
        raiseOverlays();
    }

    /**
     * Network entry point: open the book, optionally on an explicit chapter, optionally focused on a tile.
     *
     * <p>The displayed chapter is client-side state that normally only a sidebar click sets, which is why a
     * command could open the book but never move it. {@code chapter} is explicit (Worf D-3) and is applied
     * only if this client already considers that chapter available — the unlock gate is never bypassed.
     *
     * <p>A chapter change needs a NEW screen rather than an in-place focus: a live instance is bound to the
     * chapter it was built for, so focusing a tile belonging to another chapter against it can never match.
     * That is why the tile argument never focused anything either.
     */
    public static void openFromNetwork(String tileId, boolean expanded, String chapter) {
        ClientQuestState.ChapterSelect outcome = ClientQuestState.selectChapter(chapter);
        switch (outcome) {
            case NO_ID -> {
                // The ordinary open. Deliberately silent: the common path, not an event.
            }
            case SWITCHED -> QuestQueen.LOGGER.info("[questqueen] book: chapter -> '{}'", chapter);
            case ALREADY_CURRENT -> QuestQueen.LOGGER.info("[questqueen] book: chapter '{}' already displayed", chapter);
            case LOCKED -> QuestQueen.LOGGER.warn(
                    "[questqueen] book: chapter '{}' is locked for this player - refusing, state unchanged", chapter);
            case UNKNOWN -> QuestQueen.LOGGER.warn(
                    "[questqueen] book: chapter '{}' is not in the loaded pack - no-op", chapter);
            case MALFORMED -> QuestQueen.LOGGER.warn(
                    "[questqueen] book: '{}' is not a valid chapter id - no-op", chapter);
        }
        pendingTileId = tileId == null ? "" : tileId;
        pendingExpanded = expanded;
        Minecraft minecraft = Minecraft.getInstance();
        boolean switched = outcome == ClientQuestState.ChapterSelect.SWITCHED;
        if (minecraft.screen instanceof QuestBookScreen book && !switched) {
            book.applyPendingFocus();
            book.rebuildTileWidgets();
            return;
        }
        minecraft.setScreen(new QuestBookScreen());
        if (switched) {
            // The outgoing screen's removed() hook re-remembers ITS OWN chapter, which would otherwise leave
            // savedChapterId one step behind the chapter we just displayed — and make the next
            // "already displayed" report a lie. Re-assert ours after the screen swap.
            ClientQuestState.rememberChapter(ResourceLocation.tryParse(chapter));
        }
    }

    /** XOR forks pop as soon as the completed parent syncs, like FTB Quests. */
    public static void onProgressSynced() {
        if (Minecraft.getInstance().screen instanceof QuestBookScreen book) {
            book.afterProgressSync();
        }
    }

    public static void onPinLocalChanged() {
        if (Minecraft.getInstance().screen instanceof QuestBookScreen book) {
            book.afterPinLocalChanged();
        }
    }

    private void afterProgressSync() {
        xorDismissed.removeIf(key -> ClientQuestState.unresolvedXorKeys().stream().noneMatch(key::equals));
        if (!authoring && !selectedId.isEmpty()) {
            chapter.tile(selectedId).ifPresent(tile -> {
                if (ClientQuestState.needsXorChoice(chapter, tile)
                        && !xorDismissed.contains(ClientQuestState.xorKey(chapter, tile.id()))) {
                    expanded = false;
                    openModal(ModalKind.XOR, tile.id());
                }
            });
        }
        restorePinnedCardIfNeeded();
        rebuildTileWidgets();
        markWidgetsDirty();
    }

    private void afterPinLocalChanged() {
        restorePinnedCardIfNeeded();
        markWidgetsDirty();
    }

    private void restorePinnedCardIfNeeded() {
        if (isIntroChapter()) {
            selectedId = "";
            expanded = false;
            return;
        }
        if (!ClientQuestState.hasPin()) {
            return;
        }
        String pinChapter = ClientQuestState.progress.pinChapter().orElse("");
        String pinTile = ClientQuestState.progress.pinTile().orElse("");
        if (!pinChapter.equals(chapter.id().toString()) || pinTile.isEmpty()) {
            return;
        }
        selectedId = pinTile;
        expanded = true;
        int[] offset = PINNED_CARD_OFFSET.get(pinKey(pinChapter, pinTile));
        if (offset != null) {
            inspectOffX = offset[0];
            inspectOffY = offset[1];
        }
    }

    private static String pinKey(String chapterId, String tileId) {
        return chapterId + "/" + tileId;
    }

    private boolean isPinnedTile(Tile tile) {
        return ClientQuestState.isPinned(chapter, tile);
    }

    private String inspectTileId() {
        if (ClientQuestState.hasPin()
                && ClientQuestState.progress.pinChapter().orElse("").equals(chapter.id().toString())) {
            String pinTile = ClientQuestState.progress.pinTile().orElse("");
            if (!pinTile.isEmpty() && chapter.tile(pinTile).isPresent()) {
                return pinTile;
            }
        }
        return selectedId;
    }

    private boolean showInspectPanel() {
        return !isIntroChapter() && !logOpen && modal == ModalKind.NONE && !inspectTileId().isEmpty()
                && (expanded || (ClientQuestState.hasPin()
                && ClientQuestState.progress.pinChapter().orElse("").equals(chapter.id().toString())));
    }

    private boolean isIntroChapter() {
        return ClientQuestState.isIntroChapter(chapter);
    }

    private void dismissModal() {
        if (modal == ModalKind.XOR && !modalTileId.isEmpty()) {
            xorDismissed.add(ClientQuestState.xorKey(chapter, modalTileId));
        }
        modal = ModalKind.NONE;
        modalTileId = "";
        claimAllScroll = 0;
    }

    /** Locked sidebar / probe chapter — GOTCHA or fade-lock after the cap. */
    private void showLockedChapterCue() {
        int[] pos = lockedChapterCuePos(null);
        cueLocked("", pos[0], pos[1]);
    }

    private void showLockedChapterCue(ResourceLocation chapterId) {
        int[] pos = lockedChapterCuePos(chapterId);
        cueLocked("", pos[0], pos[1]);
    }

    /**
     * One combined locked cue: first {@link ClientQuestState#GOTCHA_MODAL_CAP} hits
     * open the existing GOTCHA modal; later hits fade a small red padlock.
     */
    private void cueLocked(String tileIdOrEmpty, int drawX, int drawY) {
        expanded = false;
        String tileId = tileIdOrEmpty == null ? "" : tileIdOrEmpty;
        if (ClientQuestState.takeGotchaModalSlot()) {
            openModal(ModalKind.GOTCHA, tileId);
            fadeLockStartTick = -1L;
            return;
        }
        modal = ModalKind.NONE;
        modalTileId = "";
        fadeLockX = drawX;
        fadeLockY = drawY;
        var mc = Minecraft.getInstance();
        fadeLockStartTick = mc.level != null ? mc.level.getGameTime() : 0L;
        fadeLockMs = 1000;
    }

    private int[] lockedChapterCuePos(ResourceLocation chapterId) {
        for (SidebarRow row : sidebarRows) {
            if (chapterId != null && !row.id().equals(chapterId)) {
                continue;
            }
            if (chapterId == null && !row.locked()) {
                continue;
            }
            int x = 6 + row.depth() * 10;
            if (row.hasChildren()) {
                x += 10;
            }
            return new int[]{x + 4, row.y() + 8};
        }
        return new int[]{16, 40};
    }

    private int[] tileCuePos(Tile tile) {
        int[] s = screen(tile.pos().x(), tile.pos().y());
        int size = tilePx();
        return new int[]{s[0] + size / 2, s[1] + size / 2};
    }

    private void applyPendingFocus() {
        if (pendingTileId.isEmpty() && !pendingExpanded) {
            return;
        }
        String tileId = pendingTileId;
        boolean open = pendingExpanded;
        pendingTileId = "";
        pendingExpanded = false;
        if (tileId.isEmpty() || chapter.tile(tileId).isEmpty()) {
            return;
        }
        selectedId = tileId;
        Tile tile = chapter.tile(tileId).orElse(null);
        if (tile != null && !authoring) {
            TileVisual visual = ClientQuestState.visual(chapter, tile, false, tileId);
            if (visual == TileVisual.LOCKED) {
                int[] pos = tileCuePos(tile);
                cueLocked(tileId, pos[0], pos[1]);
                centerOn(tileId);
                return;
            }
            if (ClientQuestState.needsXorChoice(chapter, tile)) {
                expanded = false;
                openModal(ModalKind.XOR, tileId);
                centerOn(tileId);
                return;
            }
        }
        modal = ModalKind.NONE;
        modalTileId = "";
        expanded = open;
        if (open) {
            centerOn(tileId);
        }
    }

    @Override
    public void resize(Minecraft minecraft, int width, int height) {
        if (closing) {
            super.resize(minecraft, width, height);
            return;
        }
        applyBookGuiScale();
        Window window = minecraft.getWindow();
        super.resize(minecraft, window.getGuiScaledWidth(), window.getGuiScaledHeight());
    }

    @Override
    public void removed() {
        closing = true;
        if (chapter != null) {
            ClientQuestState.rememberChapter(chapter.id());
        }
        super.removed();
        if (minecraft != null && bookScaleApplied) {
            bookScaleApplied = false;
            minecraft.resizeDisplay();
        }
    }

    /** More GUI pixels so 64px tiles and a chapter actually fit. Restored on close. */
    private void applyBookGuiScale() {
        if (minecraft == null || closing) {
            return;
        }
        Window window = minecraft.getWindow();
        int want = pickBookScale(window);
        window.setGuiScale(want);
        this.width = window.getGuiScaledWidth();
        this.height = window.getGuiScaledHeight();
        bookScaleApplied = true;
    }

    /** Chunky integer scale: 64px tiles stay readable like the mock (never drop to scale 1 on a 720p+ window). */
    private static int pickBookScale(Window window) {
        int fbW = window.getWidth();
        int fbH = window.getHeight();
        int chosen = 2;
        for (int s = 2; s <= 4; s++) {
            if (fbW / s >= 480 && fbH / s >= 280) {
                chosen = s;
            }
        }
        if (fbW / chosen < 400 || fbH / chosen < 240) {
            chosen = Math.max(1, chosen - 1);
        }
        return chosen;
    }

    private void focusStartTile() {
        if (!isIntroChapter()) {
            fitAllContent();
        }
        expanded = false;
        selectedId = "";
    }

    /**
     * What FIT optimises for, and the flag Troi asked for so both targets can be compared in frames.
     *
     * <p>The type is {@link FitCamera.Target} rather than a second enum declared here: two enums for one
     * concept drift apart the moment either gains a value. The default is ANCHOR and must stay so, on her
     * explicit condition that the comparison cannot become the shipped behaviour by inaction.
     */
    private static volatile FitCamera.Target fitTarget = FitCamera.Target.ANCHOR;

    public static FitCamera.Target fitTarget() {
        return fitTarget;
    }

    public static void setFitTarget(FitCamera.Target next) {
        fitTarget = next == null ? FitCamera.Target.ANCHOR : next;
    }

    /**
     * Zoom and pan so the player's place in the chapter is on screen.
     *
     * <p>The zoom still fits the whole chapter where that is legible. The camera is then chosen by
     * {@link FitCamera}, which holds the anchor inside a margin band and treats the tile count only as its
     * tie-breaker. The floor below is deliberately unchanged: Troi's T2 sets it by caption legibility
     * rather than by tile count, and she would not sign a floor that makes tile text unreadable (0.25 is
     * out, and 0.5 would be a bridge ruling). Tile count is not bought with legibility here. The ONE
     * exception is her ordered dev-only 0.5 preview: while the probe holds {@link #previewHalf} the floor
     * is 0.5 and the ladder gains its 32 px rung, so the frame her criterion is judged on can exist at
     * all. That is an evidence build - it changes no default and ships nothing.
     */
    private void fitAllContent() {
        int minX = chapter.tiles().stream().mapToInt(t -> t.pos().x()).min().orElse(0);
        int minY = chapter.tiles().stream().mapToInt(t -> t.pos().y()).min().orElse(0);
        int maxX = chapter.tiles().stream().mapToInt(t -> t.pos().x()).max().orElse(0);
        int maxY = chapter.tiles().stream().mapToInt(t -> t.pos().y()).max().orElse(0);
        double worldLeft = minX * (double) STRIDE;
        double worldTop = minY * (double) STRIDE;
        double worldW = (maxX - minX) * (double) STRIDE + TILE;
        double worldH = (maxY - minY) * (double) STRIDE + TILE;
        int pad = 12;
        int viewW = Math.max(80, contentWidth() - pad * 2);
        int viewH = Math.max(80, contentHeight() - pad * 2);
        float fit = Math.min(viewW / (float) worldW, viewH / (float) worldH);
        setZoom(Math.max(fitFloor(), Math.min(2f, fit)));
        FitCamera.Choice choice = FitCamera.choose(fitCells(), anchorCell(), fitTarget, fitView());
        if (choice.total() == 0) {
            // No placed tiles at all: keep the old bounding-box framing so an empty chapter still centres.
            cameraX = worldLeft - (contentWidth() - worldW * zoom) / (2.0 * zoom);
            cameraY = worldTop - (TOP_H + (contentHeight() - worldH * zoom) / 2.0) / zoom;
        } else if (contentFitsView(minX, minY, maxX, maxY)) {
            // The whole chapter fits at this zoom, so there is nothing for the anchor rule to trade off: centre
            // the content. The anchor rule alone parked small chapters against one edge with the rest of the
            // board empty.
            FitCamera.View view = fitView();
            int stride = view.stridePx();
            int left = minX * stride;
            int right = maxX * stride + view.tilePx();
            int top = minY * stride;
            int bottom = maxY * stride + view.tilePx();
            cameraX = ((left + right) / 2.0 - view.contentWidth() / 2.0) / zoom;
            cameraY = ((top + bottom) / 2.0 - view.topH() - view.contentHeight() / 2.0) / zoom;
        } else {
            // FitCamera works in screen pixels; the screen's camera is in world units.
            cameraX = choice.camSx() / zoom;
            cameraY = choice.camSy() / zoom;
        }
        // cam is the WORLD-UNIT camera the drag and probe paths also write; camSx/camSy are the rounded
        // SCREEN pixels the renderer places tiles with and the hit test maps against. Logging only one of
        // the two is what made the earlier fit lines impossible for a reviewer to check (Troi's E1).
        QuestQueen.LOGGER.info("Quest book fit chapter={} zoom={} cam=({}, {}) [world px; screen px {}x{}]"
                        + " tiles=({},{})-({},{}) gui={}x{} boardLeft={} anchor=({},{}) anchored={} rule={}"
                        + " visible={}/{} inview={}/{} inset={} stride={} tile={} anchorArea={} floor={}"
                        + " shift=({},{}) visibleBefore={}",
                chapter.id(), zoom, cameraX, cameraY, camSx(), camSy(),
                minX, minY, maxX, maxY, width, height, boardLeft(),
                choice.anchorX(), choice.anchorY(), choice.anchored(), choice.rule(),
                choice.visible(), choice.total(), inViewTileCount(), choice.total(), fitView().inset(),
                // Worf's F3: the acceptance criterion is the PREDICATE (anchorArea * 2 >= tile * tile), not the
                // literal 81 her worked example produced, so the line carries every input the test needs and
                // holds at any zoom rung. F8: floor says which state it landed in, so a floor that stopped
                // being satisfiable would be visible in the record instead of silently absent.
                fitView().stridePx(), fitView().tilePx(),
                choice.floor() == null ? -1 : choice.floor().area(),
                choice.floor() == null ? "none" : choice.floor().state(),
                choice.floor() == null ? 0 : choice.floor().shiftSx(),
                choice.floor() == null ? 0 : choice.floor().shiftSy(),
                choice.floor() == null ? -1 : choice.floor().visibleBefore());
    }

    /**
     * Where the "tiles in view" cue goes: the first board corner (bottom-left, bottom-right, top-left, top-right)
     * whose box is clear of every tile, so it never prints over a quest. Returns {x, y, clear?1:0}; when every
     * corner is covered it falls back to bottom-left and the caller draws a more opaque backing.
     */
    private int[] cueSpot(int cueW) {
        int left = boardLeft() + 6;
        int right = boardLeft() + contentWidth() - cueW - 6;
        int top = TOP_H + 8;
        int bottom = TOP_H + contentHeight() - 16;
        int[][] corners = {{left, bottom}, {right, bottom}, {left, top}, {right, top}};
        int size = tilePx();
        for (int[] c : corners) {
            int x0 = c[0] - 4;
            int y0 = c[1] - 4;
            int x1 = c[0] + cueW + 4;
            int y1 = c[1] + 7;
            boolean clear = true;
            if (drawingInspect()) {
                int cx = cardX();
                int cy = cardY();
                if (cx < x1 && cx + cardW() > x0 && cy < y1 && cy + cardH() > y0) {
                    continue;
                }
            }
            for (Tile tile : chapter.tiles()) {
                int[] s = screen(tile.pos().x(), tile.pos().y());
                if (s[0] < x1 && s[0] + size > x0 && s[1] < y1 && s[1] + size > y0) {
                    clear = false;
                    break;
                }
            }
            if (clear) {
                return new int[]{c[0], c[1], 1};
            }
        }
        return new int[]{left, bottom, 0};
    }

    /** True when the tile bounding box, plus the clamp inset on every side, fits inside the board viewport. */
    private boolean contentFitsView(int minX, int minY, int maxX, int maxY) {
        FitCamera.View view = fitView();
        int w = (maxX - minX) * view.stridePx() + view.tilePx() + view.inset() * 2;
        int h = (maxY - minY) * view.stridePx() + view.tilePx() + view.inset() * 2;
        return w <= view.contentWidth() && h <= view.contentHeight();
    }

    /** The renderer's viewport in integer pixels, which is the space {@link FitCamera} works in. */
    private FitCamera.View fitView() {
        return new FitCamera.View(boardLeft(), contentWidth(), contentHeight(), TOP_H, tilePx(), gapPx());
    }

    private List<FitCamera.Cell> fitCells() {
        List<FitCamera.Cell> cells = new ArrayList<>();
        for (Tile tile : chapter.tiles()) {
            cells.add(new FitCamera.Cell(tile.pos().x(), tile.pos().y()));
        }
        return cells;
    }

    /**
     * The anchor for FIT, in Troi's order: the tile the player is on, else the chapter's entry tile.
     *
     * <p>There is no third fallback written here. A chapter with placed tiles always has an entry tile, so
     * her "restore the chapter's last camera" clause is unreachable by construction, and inventing a
     * camera history to satisfy it would be faking a case rather than implementing one.
     */
    private FitCamera.Cell anchorCell() {
        if (selectedId != null && !selectedId.isEmpty()) {
            Optional<Tile> selected = chapter.tile(selectedId);
            if (selected.isPresent()) {
                return new FitCamera.Cell(selected.get().pos().x(), selected.get().pos().y());
            }
            // A stale id from another chapter simply does not resolve here, which is the intended fallthrough.
        }
        Optional<Tile> entry = entryTile();
        if (entry.isPresent()) {
            return new FitCamera.Cell(entry.get().pos().x(), entry.get().pos().y());
        }
        if (chapter.tiles().isEmpty()) {
            return null;
        }
        Tile first = chapter.tiles().get(0);
        return new FitCamera.Cell(first.pos().x(), first.pos().y());
    }

    /**
     * The chapter's entry tile: the tile no link points at, lowest (y, x) when the graph has several roots.
     *
     * <p>Deliberately the graph root rather than the first tile in document order. Troi rejected the
     * top-left target partly because it collapses to wherever the first tile happens to be authored, so
     * using document order as the anchor would reintroduce the thing she rejected.
     */
    private Optional<Tile> entryTile() {
        java.util.Set<String> targets = new java.util.HashSet<>();
        for (Link link : chapter.links()) {
            targets.add(link.to());
        }
        return chapter.tiles().stream()
                .filter(tile -> !targets.contains(tile.id()))
                .min((a, b) -> a.pos().y() != b.pos().y()
                        ? Integer.compare(a.pos().y(), b.pos().y())
                        : Integer.compare(a.pos().x(), b.pos().x()));
    }

    /**
     * Tiles with ANY visible area under the current camera: the count the player perceives, so the count
     * the cue reports (Troi's F4 follow-up 2). {@link FitCamera#visibleCount} keeps the stricter
     * wholly-visible meaning used for scoring.
     */
    private int inViewTileCount() {
        return FitCamera.inViewCount(fitCells(), camSx(), camSy(), fitView());
    }

    private void setZoom(float next) {
        int want = Math.round(TILE * next);
        int[] steps = tileSteps();
        int chosen = steps[0];
        for (int step : steps) {
            if (step <= Math.max(steps[0], want)) {
                chosen = step;
            }
        }
        zoom = chosen / (float) TILE;
    }

    private void stepZoom(int dir) {
        int px = tilePx();
        int[] steps = tileSteps();
        int idx = 0;
        for (int i = 0; i < steps.length; i++) {
            if (steps[i] == px) {
                idx = i;
                break;
            }
        }
        idx = Math.max(0, Math.min(steps.length - 1, idx + dir));
        zoom = steps[idx] / (float) TILE;
    }

    private int tilePx() {
        int raw = Math.round(TILE * zoom);
        int[] steps = tileSteps();
        int chosen = steps[0];
        int best = Integer.MAX_VALUE;
        for (int step : steps) {
            int d = Math.abs(step - raw);
            if (d < best) {
                best = d;
                chosen = step;
            }
        }
        return chosen;
    }

    /**
     * GAP * tilePx / TILE is integer for TILE=64, GAP=12, and the TILE_STEPS ladder: 12*step/64 = 3*step/16,
     * which is an integer exactly when step % 16 == 0, as every rung is. (This comment said GAP=8, which is
     * not a constant in this class, on the line that justifies the whole ladder. Worf caught it reviewing
     * F4; it is the reason the legal rungs below 48 are 32 and 16 and nothing between.)
     */
    private int gapPx() {
        return GAP * tilePx() / TILE;
    }

    private int stridePx() {
        return tilePx() + gapPx();
    }

    private int camSx() {
        return (int) Math.round(cameraX * zoom);
    }

    private int camSy() {
        return (int) Math.round(cameraY * zoom);
    }

    private void rebuildTileWidgets() {
        for (QuestTileWidget widget : tileWidgets) {
            removeWidget(widget);
        }
        tileWidgets.clear();
        if (isIntroChapter()) {
            raiseOverlays();
            captureWidgetSyncState();
            return;
        }
        int size = tilePx();
        for (Tile tile : chapter.tiles()) {
            TileVisual visual = ClientQuestState.visual(chapter, tile, authoring, selectedId);
            int[] s = screen(tile.pos().x(), tile.pos().y());
            int[] chrome = tileChrome(tile, visual, size);
            QuestTileWidget widget = new QuestTileWidget(s[0], s[1], size, chrome[0], chrome[1], chrome[2]);
            widget.setChrome(s[0], s[1], size, chrome[0], chrome[1], chrome[2],
                    false, showExpandGlyph(visual, chrome[1]));
            applyTileDecor(widget, tile, visual);
            widget.setBoardClip(boardLeft(), TOP_H, width, height);
            boolean hide = !authoring && ClientQuestState.isConcealed(chapter, tile);
            widget.setShown(!hide);
            widget.setPorts(hide || modal != ModalKind.NONE ? EMPTY_PORTS : tilePorts(tile, visual, s[0], s[1], size));
            tileWidgets.add(widget);
            addRenderableWidget(widget);
        }
        raiseOverlays();
        captureWidgetSyncState();
    }

    private void syncTileWidgetBounds() {
        if (isIntroChapter()) {
            if (!tileWidgets.isEmpty()) {
                rebuildTileWidgets();
            }
            return;
        }
        if (tileWidgets.size() != chapter.tiles().size()) {
            rebuildTileWidgets();
            captureWidgetSyncState();
            return;
        }
        if (!widgetsDirty && !widgetLayoutChanged()) {
            // Keep inspect/modal chrome in sync every frame while shown *or* exiting.
            // Closing only clears `expanded`, so showInspectPanel() is false during the exit fade —
            // skipping sync here left QuestOverlayWidget stuck as an empty gold frame (1.1.162).
            if (showInspectPanel() || drawingInspect() || inspectPanel.visible
                    || modal != ModalKind.NONE || modalPanel.visible) {
                syncOverlays();
            }
            return;
        }
        int size = tilePx();
        int i = 0;
        for (Tile tile : chapter.tiles()) {
            TileVisual visual = ClientQuestState.visual(chapter, tile, authoring, selectedId);
            int[] s = screen(tile.pos().x(), tile.pos().y());
            int[] chrome = tileChrome(tile, visual, size);
            tileWidgets.get(i).setChrome(s[0], s[1], size, chrome[0], chrome[1], chrome[2],
                    false, showExpandGlyph(visual, chrome[1]));
            applyTileDecor(tileWidgets.get(i), tile, visual);
            tileWidgets.get(i).setBoardClip(boardLeft(), TOP_H, width, height);
            boolean hide = !authoring && ClientQuestState.isConcealed(chapter, tile);
            tileWidgets.get(i).setShown(!hide);
            tileWidgets.get(i).setPorts(hide || modal != ModalKind.NONE ? EMPTY_PORTS : tilePorts(tile, visual, s[0], s[1], size));
            i++;
        }
        syncOverlays();
        captureWidgetSyncState();
    }

    private boolean widgetLayoutChanged() {
        return tilePx() != lastSyncTilePx
                || camSx() != lastSyncCamSx
                || camSy() != lastSyncCamSy
                || boardLeft() != lastSyncBoardLeft
                || width != lastSyncWidth
                || height != lastSyncHeight
                || inspectOffX != lastSyncInspectOffX
                || inspectOffY != lastSyncInspectOffY
                || lastSyncChapterId == null
                || !chapter.id().equals(lastSyncChapterId)
                || !selectedId.equals(lastSyncSelected)
                || modal != lastSyncModal
                || authoring != lastSyncAuthoring
                || logOpen != lastSyncLogOpen
                || expanded != lastSyncExpanded
                || ClientQuestState.progress != lastSyncProgress;
    }

    private void captureWidgetSyncState() {
        widgetsDirty = false;
        lastSyncTilePx = tilePx();
        lastSyncCamSx = camSx();
        lastSyncCamSy = camSy();
        lastSyncBoardLeft = boardLeft();
        lastSyncWidth = width;
        lastSyncHeight = height;
        lastSyncInspectOffX = inspectOffX;
        lastSyncInspectOffY = inspectOffY;
        lastSyncChapterId = chapter.id();
        lastSyncSelected = selectedId;
        lastSyncModal = modal;
        lastSyncAuthoring = authoring;
        lastSyncLogOpen = logOpen;
        lastSyncExpanded = expanded;
        lastSyncProgress = ClientQuestState.progress;
    }

    private void markWidgetsDirty() {
        widgetsDirty = true;
    }

    private void raiseOverlays() {
        removeWidget(inspectPanel);
        removeWidget(modalPanel);
        addRenderableWidget(inspectPanel);
        addRenderableWidget(modalPanel);
        syncOverlays();
    }

    private void syncOverlays() {
        String inspectId = drawingInspectTileId();
        boolean showInspect = drawingInspect();
        int accent = QuestColors.CURRENT;
        boolean pluses = false;
        if (showInspect) {
            Tile tile = chapter.tile(inspectId).orElse(null);
            if (tile != null) {
                TileVisual visual = authoring ? TileVisual.EDIT : ClientQuestState.visual(chapter, tile, false, tile.id());
                accent = visual == TileVisual.EDIT ? QuestColors.EDIT : borderColor(visual);
                if (accent == 0) {
                    accent = QuestColors.CURRENT;
                }
                // Jerry 1.1.130: no exterior bottom crosses on CURRENT description frames.
                pluses = false;
            }
        }
        int cx = cardX();
        int cy = cardY();
        int cw = cardW();
        int ch = cardH();
        List<QuestOverlayWidget.Chip> inspectChips = List.of();
        int inspectHeaderW = 72;
        if (showInspect) {
            String label = authoring ? "EDIT MODE" : "CURRENT";
            Tile selected = chapter.tile(inspectId).orElse(null);
            if (selected != null && !authoring) {
                label = headerLabel(ClientQuestState.visual(chapter, selected, false, selected.id()), selected);
            }
            inspectHeaderW = tabWidth(label, cw - 18);
            if (!authoring && selected != null) {
                PlayBar bar = playBar(selected, cx, cy, cw, ch);
                List<QuestOverlayWidget.Chip> chips = new ArrayList<>();
                if (bar.action()) {
                    chips.add(new QuestOverlayWidget.Chip(bar.actionX(), bar.y(), bar.actionW(), 12, QuestColors.CURRENT));
                } else if (bar.claimedBadge()) {
                    chips.add(new QuestOverlayWidget.Chip(bar.actionX(), bar.y(), bar.actionW(), 12, QuestColors.COMPLETED));
                }
                if (bar.choice()) {
                    int choiceW = bar.choiceW();
                    if (UiFx.enabled() && fxChoiceAt >= 0L && fxChoiceTileId.equals(selected.id())) {
                        float cp = UiFx.easeOut(UiFx.progress(fxChoiceAt, CHOICE_MS));
                        choiceW = Math.max(8, Math.round(UiFx.lerp(choiceW * 0.55f, choiceW, cp)));
                    }
                    chips.add(new QuestOverlayWidget.Chip(bar.choiceAX(), bar.y(), choiceW, 12, QuestColors.CURRENT));
                    chips.add(new QuestOverlayWidget.Chip(bar.choiceBX(), bar.y(), choiceW, 12, QuestColors.EDIT));
                }
                if (bar.pin()) {
                    int pinColor = bar.pinned() ? QuestColors.EDIT : QuestColors.CURRENT;
                    if (UiFx.enabled() && fxPinFlashAt >= 0L) {
                        float flash = 1f - UiFx.progress(fxPinFlashAt, PIN_FLASH_MS);
                        pinColor = UiFx.withAlpha(QuestColors.EDIT, 0.55f + 0.45f * flash);
                    }
                    chips.add(new QuestOverlayWidget.Chip(bar.pinX(), bar.y(), bar.pinW(), 12, pinColor));
                }
                inspectChips = chips;
            } else {
                inspectChips = List.of(
                        new QuestOverlayWidget.Chip(cx + 10, cy + ch - 16, 52, 12, QuestColors.EDIT),
                        new QuestOverlayWidget.Chip(cx + 66, cy + ch - 16, 52, 12, QuestColors.NEW),
                        new QuestOverlayWidget.Chip(cx + 122, cy + ch - 16, 52, 12, QuestColors.PORT_RED)
                );
            }
        }
        inspectPanel.sync(showInspect, cx, cy, cw, ch, QuestColors.CARD, accent, true, inspectHeaderW, pluses, inspectChips);
        inspectPanel.setFx(inspectFxAlpha(), inspectFxOffsetY());
        boolean showModal = modal != ModalKind.NONE;
        List<QuestOverlayWidget.Chip> modalChips = List.of();
        int modalX = 0;
        int modalY = 0;
        int modalW = 1;
        int modalH = 1;
        int modalEdge = QuestColors.MODAL_PINK;
        int modalHeaderW = 40;
        if (showModal) {
            ModalLayout box = modalLayout();
            modalX = box.x();
            modalY = box.y();
            modalW = box.w();
            modalH = box.h();
            modalEdge = box.edge();
            modalHeaderW = box.headerW();
            if (box.cancelW() > 0) {
                modalChips = List.of(
                        new QuestOverlayWidget.Chip(box.btnX(), box.btnY(), box.btnW(), box.btnH(), QuestColors.CURRENT),
                        new QuestOverlayWidget.Chip(box.cancelX(), box.btnY(), box.cancelW(), box.btnH(), QuestColors.SIDEBAR_EDGE));
            } else {
                modalChips = List.of(new QuestOverlayWidget.Chip(box.btnX(), box.btnY(), box.btnW(), box.btnH(), QuestColors.CURRENT));
            }
        }
        modalPanel.sync(showModal, modalX, modalY, modalW, modalH, QuestColors.CARD, modalEdge, true, modalHeaderW,
                false, true, true, modalChips);
        // After sync — sync() resets dimLeft to 0 (Data).
        modalPanel.setDimLeft(boardLeft());
        int i = 0;
        for (Tile tile : chapter.tiles()) {
            if (i >= tileWidgets.size()) {
                break;
            }
            boolean conceal = !authoring && ClientQuestState.isConcealed(chapter, tile);
            tileWidgets.get(i).setShown(!drawingLog() && !conceal);
            tileWidgets.get(i).setOverlayClip(showInspect, cx, cy + inspectFxOffsetY(), cw, ch);
            tileWidgets.get(i).setSelectionHalo(!authoring && tile.id().equals(selectedId)
                    && !drawingLog() && !isIntroChapter());
            i++;
        }
    }

    private int[] tileChrome(Tile tile, TileVisual visual, int size) {
        String header = tileHeader(tile, visual);
        int edge = headerEdge(tile, visual);
        int face = QuestColors.CARD;
        int headerW = 0;
        if (edge != 0 && !header.isEmpty()) {
            headerW = tabWidth(header, size - 14);
        }
        return new int[]{face, edge, headerW};
    }

    private String tileHeader(Tile tile, TileVisual visual) {
        if (showsChoosePath(tile, visual)) {
            return "CHOOSE";
        }
        int edge = borderColor(visual);
        return edge != 0 ? headerLabel(visual, tile) : "";
    }

    private int headerEdge(Tile tile, TileVisual visual) {
        if (showsChoosePath(tile, visual)) {
            return QuestColors.XOR_EDGE;
        }
        return borderColor(visual);
    }

    private boolean showsChoosePath(Tile tile, TileVisual visual) {
        return !authoring
                && visual != TileVisual.LOCKED
                && visual != TileVisual.CLOSED
                && visual != TileVisual.FAILED
                && visual != TileVisual.COMPLETED
                && visual != TileVisual.EDIT
                && ClientQuestState.isOpenXorBranch(chapter, tile);
    }

    private int tabWidth(String label, int max) {
        // Tab labels go through tinyString (0.6 scale, with shadow), not half scale. Sizing the tab for half
        // scale let longer words such as CURRENT run past the tab onto the card face, where they vanished.
        return MockChrome.tabWidth((int) Math.ceil(font.width(label) * 0.6f) + 2, max);
    }

    private int pillW(String label) {
        return Math.max(36, font.width(label) + 12);
    }

    private void onSearch(String value) {
        searchHits.clear();
        searchIndex = 0;
        if (value.isBlank()) {
            return;
        }
        String needle = value.toLowerCase(Locale.ROOT);
        for (Tile tile : chapter.tiles()) {
            if (tile.title().toLowerCase(Locale.ROOT).contains(needle) || tile.id().contains(needle)
                    || tile.description().toLowerCase(Locale.ROOT).contains(needle)) {
                searchHits.add(tile.id());
            }
        }
        if (!searchHits.isEmpty()) {
            fxSearchFlashAt = UiFx.nowMs();
            fxSearchFlashId = searchHits.getFirst();
            centerOn(searchHits.getFirst());
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (bounce < 1f) {
            bounce = Math.min(1f, bounce + 0.15f);
        }
        authoring = false;
        if (!editingTitle && !editingBody) {
            ClientQuestState.chapter(chapter.id()).ifPresent(updated -> chapter = updated);
        }
        layoutSearch();
        drainTestClick();
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        bindChapterTheme();
        graphics.fill(0, 0, width, height, QuestColors.VOID);
        if (!isIntroChapter()) {
            drawBoardBackgroundImage(graphics);
        }
        if (!drawingLog()) {
            if (!isIntroChapter()) {
                drawQuietCells(graphics);
                drawThemeAmbience(graphics);
            }
            drawSidebarPanels(graphics);
            drawSidebarTab(graphics);
        }
        drawTopBar(graphics);
        // Connectors are edge ports on QuestTileWidget (widget pass). No spanning lines.
        if (drawingLog()) {
            float lp = logFxProgress();
            int slide = Math.round((1f - lp) * Math.max(24, width / 5f));
            int x = 16 + slide;
            int y = 32;
            int alphaFace = UiFx.withAlpha(QuestColors.CARD, Math.max(0.15f, lp));
            int alphaEdge = UiFx.withAlpha(QuestColors.NEW, Math.max(0.15f, lp));
            MockChrome.box(graphics, x, y, width - 32, height - 48, alphaFace);
            MockChrome.frame(graphics, x, y, width - 32, height - 48, alphaEdge);
        }
        // Modal dim is drawn by modalPanel (after tiles) so the grid stays visible underneath.

    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        try {
            bindChapterTheme();
            updateVisualEffects();
            syncTileWidgetBounds();
            applyPathHover(mouseX, mouseY);
            super.render(graphics, mouseX, mouseY, partialTick);
            graphics.setColor(1f, 1f, 1f, 1f);
            // Board-level one-shots sit above the tiles but below the sidebar and inspect card.
            drawEffectOverlays(graphics);
            drawBoardCrossfade(graphics);
            // Troi's T2 condition 2: a partial frame must READ as partial. Without this the board shows a
            // field of empty slots and "this chapter is empty" is indistinguishable from "there is more off
            // screen", so a player cannot tell whether to pan. Drawn above the tiles, below the sidebar.
            if (!drawingLog() && !isIntroChapter() && !authoring && !chapter.tiles().isEmpty()) {
                int placed = chapter.tiles().size();
                // Troi's F4 follow-up (2): count tiles with ANY visible area, and word it "in view". A
                // count of wholly-visible tiles disagreed with the frame the player is looking at, which
                // reads as a bug - on the Sustenance capture 8 whole plus 2 clipped were on screen.
                int inView = inViewTileCount();
                if (inView < placed) {
                    String cue = inView + " / " + placed + " tiles in view - drag to pan";
                    // tinyString renders at 0.6 scale; backing box uses font width * 0.6.
                    int cueW = Math.round(font.width(cue) * 0.6f);
                    int[] spot = cueSpot(cueW);
                    int cueX = spot[0];
                    int cueY = spot[1];
                    graphics.fill(cueX - 4, cueY - 4, cueX + cueW + 4, cueY + 7, spot[2] == 1 ? 0x66000000 : 0xD0100C16);
                    tinyString(graphics, cue, cueX, cueY, QuestColors.MUTED);
                }
            }
            if (!drawingLog() && isIntroChapter()) {
                drawIntro(graphics);
            }
            if (!drawingLog()) {
                if (authoring) {
                    drawAddGhosts(graphics);
                }
                // Widgets draw after the background sidebar; paint it again so leaked tiles cannot sit on the list.
                drawSidebarPanels(graphics, false);
                drawSidebarTab(graphics);
                drawSidebarLabels(graphics, mouseX, mouseY);
            }

            if (!drawingLog() && drawingInspect()) {
                var pose = graphics.pose();
                pose.pushPose();
                pose.translate(0, inspectFxOffsetY(), 260);
                float ia = inspectFxAlpha();
                if (ia < 0.99f) {
                    // Content rides the same settle; widgets already apply alpha to the chrome.
                    graphics.setColor(1f, 1f, 1f, ia);
                }
                chapter.tile(drawingInspectTileId()).ifPresent(tile -> drawExpanded(graphics, tile, mouseX, mouseY));
                graphics.setColor(1f, 1f, 1f, 1f);
                pose.popPose();
                if (showInspectPanel()) {
                    pose.pushPose();
                    pose.translate(0, 0, 500);
                    drawInspectTooltips(graphics, mouseX, mouseY);
                    pose.popPose();
                }
            }
            if (drawingLog()) {
                drawLog(graphics);
            }
            drawChrome(graphics);
        drawChromeEditor(graphics);
            if (modal != ModalKind.NONE) {
                // Above modalPanel widget face (ImmediatelyFast can flush the card after the widget pass).
                var pose = graphics.pose();
                pose.pushPose();
                pose.translate(0, 0, 400);
                drawModal(graphics);
                pose.popPose();
                // Sidebar must stay readable over modal dim (Jerry).
                pose.pushPose();
                pose.translate(0, 0, 420);
                drawSidebarPanels(graphics, false);
                drawSidebarTab(graphics);
                drawSidebarLabels(graphics, mouseX, mouseY);
                drawSidebarTitle(graphics);
                pose.popPose();
            }
            drawFadeLock(graphics, partialTick);
            picker.render(graphics, font, boardLeft() + 16, 40, mouseX, mouseY);
        } catch (Throwable t) {
            QuestQueen.LOGGER.error("Quest book render failed", t);
            graphics.drawString(font, "QUEST BOOK RENDER ERROR - see log", boardLeft() + 8, 40, 0xFFFF5555, true);
        }
    }

    private void applyTileDecor(QuestTileWidget widget, Tile tile, TileVisual visual) {
        boolean locked = visual == TileVisual.LOCKED && !authoring;
        int edge = headerEdge(tile, visual);
        String header = tileHeader(tile, visual);
        ItemStack icon = ItemStack.EMPTY;
        String glyph = tile.icon().flatMap(Icon::glyphId).orElse("");
        // Troi P1: keep dimmed icon on LOCKED so the card is not a hollow ??? slab (spoilers stay via ??? title).
        if (glyph.isEmpty() && tile.icon().isPresent()) {
            icon = tile.icon().flatMap(id -> id.item().flatMap(BuiltInRegistries.ITEM::getOptional))
                    .map(ItemStack::new).orElse(ItemStack.EMPTY);
        }
        boolean hasIcon = tile.icon().isPresent();
        String objective = locked ? "???" : tileObjective(tile);
        String title2 = locked ? "COMPLETE PREVIOUS" : "";
        String progress = "";
        if (visual == TileVisual.FAILED) {
            progress = ClientQuestState.taskProgressLabel(chapter, tile);
            if (!progress.isBlank()) {
                objective = progress;
                title2 = "";
            }
        }
        ItemStack reward = ItemStack.EMPTY;
        boolean rewardPlus = false;
        List<ItemStack> faces = List.of();
        String title3 = "";
        if (!locked && !tile.rewards().isEmpty()) {
            faces = collectRewardFaces(tile);
            reward = faces.isEmpty() ? ItemStack.EMPTY : faces.getFirst();
            rewardPlus = showsRewardPlus(tile);
            title3 = ""; // Jerry: no prose caption — Nx on icons
        }
        boolean xor = !locked && ClientQuestState.hasXorBadge(chapter, tile);
        widget.setDecor(locked, header, edge != 0 ? MockChrome.tagInk(edge) : MockChrome.tagWhite(), icon, objective, title2,
                title3, hasIcon, reward, rewardPlus, xor);
        widget.setGlyph(glyph);
        widget.setRewardFaces(faces, rewardPlus);
        widget.setProgress(progress, visual == TileVisual.FAILED ? QuestColors.FAILED : QuestColors.TEXT);
    }

    static boolean showsRewardPlus(Tile tile) {
        if (tile == null || tile.rewards().isEmpty()) {
            return false;
        }
        if (tile.rewards().size() > 1) {
            return true;
        }
        return tile.rewards().getFirst() instanceof ChoiceReward;
    }

    static String inspectBody(Tile tile) {
        if (tile == null) {
            return "No description yet.";
        }
        String rawBody = tile.description().isBlank() ? "No description yet." : tile.description();
        // Drop trailing "Rewards: …" prose from description (Jerry — counts on icons only).
        String body = rawBody.replaceAll("(?is)\\s*Rewards?:\\s*.*$", "").trim();
        return body.isEmpty() ? "No description yet." : body;
    }

    static String rewardsCaption(Tile tile) {
        if (tile == null || tile.rewards().isEmpty()) {
            return "";
        }
        List<String> parts = new ArrayList<>();
        for (Reward reward : tile.rewards()) {
            if (reward instanceof ChoiceReward choice) {
                for (var option : choice.options()) {
                    parts.add(option.describe());
                }
            } else {
                parts.add(reward.describe());
            }
        }
        return parts.isEmpty() ? "" : "Rewards: " + String.join(", ", parts);
    }

    private record RewardSlot(Reward reward, ItemStack face) {
    }

    private List<RewardSlot> collectRewardSlots(Tile tile) {
        List<RewardSlot> slots = new ArrayList<>();
        if (tile == null) {
            return slots;
        }
        for (Reward reward : tile.rewards()) {
            if (reward instanceof ChoiceReward choice) {
                for (var option : choice.options()) {
                    addRewardSlot(slots, option);
                }
            } else {
                addRewardSlot(slots, reward);
            }
        }
        return slots;
    }

    private static void addRewardSlot(List<RewardSlot> slots, Reward reward) {
        ItemStack face = rewardFace(reward);
        if (!face.isEmpty()) {
            slots.add(new RewardSlot(reward, face));
        }
    }

    private List<ItemStack> collectRewardFaces(Tile tile) {
        List<ItemStack> faces = new ArrayList<>();
        for (RewardSlot slot : collectRewardSlots(tile)) {
            faces.add(slot.face());
        }
        return faces;
    }

    /** Board / inspect / log face — RewardIcons so XP / toast / stage / loot never leave a blank strip. */
    private static ItemStack rewardFace(Reward reward) {
        if (reward == null) {
            return ItemStack.EMPTY;
        }
        return RewardIcons.iconId(reward)
                .flatMap(BuiltInRegistries.ITEM::getOptional)
                .map(item -> new ItemStack(item, Math.min(64, Math.max(1, reward.count()))))
                .orElse(ItemStack.EMPTY);
    }

    private void applyPathHover(int mouseX, int mouseY) {
        int hx = hoverOverrideX != Integer.MIN_VALUE ? hoverOverrideX : mouseX;
        int hy = hoverOverrideY != Integer.MIN_VALUE ? hoverOverrideY : mouseY;
        for (QuestTileWidget widget : tileWidgets) {
            widget.setHover(hx, hy);
        }
    }

    private void drawSidebarPanels(GuiGraphics graphics) {
        drawSidebarPanels(graphics, true);
    }

    private void drawSidebarPanels(GuiGraphics graphics, boolean rebuildRows) {
        if (sidebarCollapsed) {
            if (rebuildRows) {
                sidebarRows.clear();
                sidebarListH = 0;
            }
            return;
        }
        int bar = sidebarWidth();
        MockChrome.box(graphics, 0, TOP_H, bar, height - TOP_H, QuestColors.SIDEBAR);
        MockChrome.box(graphics, bar - 1, TOP_H, 1, height - TOP_H, QuestColors.SIDEBAR_EDGE);
        if (rebuildRows || sidebarRows.isEmpty()) {
            rebuildSidebarRows();
        }
        graphics.enableScissor(0, TOP_H, bar, height);
        // The active-row highlight slides to its row instead of jumping, so switching chapters reads as
        // movement. It is drawn once, after the loop, because only one row can be active.
        int targetY = Integer.MIN_VALUE;
        for (SidebarRow row : sidebarRows) {
            if (!sidebarRowVisible(row)) {
                continue;
            }
            if (row.id().equals(chapter.id())) {
                targetY = row.y() - 1;
                break;
            }
        }
        drawSidebarActiveBar(graphics, targetY);
        graphics.disableScissor();
        drawSidebarScrollThumb(graphics);
    }

    private static final int SIDEBAR_ROW_H = 16;

    /** Slides the active-chapter highlight toward {@code targetY} (MIN_VALUE = nothing selected). */
    private void drawSidebarActiveBar(GuiGraphics graphics, int targetY) {
        if (targetY == Integer.MIN_VALUE) {
            fxSidebarBarY = Float.NaN;
            fxSidebarBarH = Float.NaN;
            return;
        }
        if (Float.isNaN(fxSidebarBarY)) {
            // First paint for this chapter: land on the row, no travel.
            fxSidebarBarY = targetY;
            fxSidebarBarH = SIDEBAR_ROW_H;
            fxSidebarBarMovedAt = UiFx.nowMs();
        } else if (Math.abs(targetY - fxSidebarBarY) > 0.5f) {
            float t = UiFx.easeInOut(UiFx.progress(fxSidebarBarMovedAt, SIDEBAR_SLIDE_MS));
            fxSidebarBarY = UiFx.lerpInt(Math.round(fxSidebarBarY), targetY, t);
            fxSidebarBarH = UiFx.lerpInt(Math.round(fxSidebarBarH), SIDEBAR_ROW_H, t);
        }
        int y = Math.round(fxSidebarBarY);
        int h = Math.max(2, Math.round(fxSidebarBarH));
        MockChrome.box(graphics, 0, y, sidebarWidth(), h, QuestColors.SIDEBAR_ACTIVE);
        MockChrome.box(graphics, 0, y, 2, h, QuestColors.SIDEBAR_HEADER);
    }

    private void drawSidebarLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        if (sidebarCollapsed) {
            return;
        }
        sidebarHoverTitle = "";
        if (sidebarRows.isEmpty()) {
            graphics.drawString(font, "No chapters", 8, 32, QuestColors.MUTED, true);
            return;
        }
        graphics.enableScissor(0, TOP_H, sidebarWidth(), height);
        for (SidebarRow row : sidebarRows) {
            if (!sidebarRowVisible(row)) {
                continue;
            }
            ClientQuestState.chapter(row.id()).ifPresent(entry -> {
                boolean locked = row.locked();
                boolean active = entry.id().equals(chapter.id());
                int x = 6 + row.depth() * 10;
                int rowY = row.y();
                float reveal = 1f;
                Long revealAt = fxSidebarRevealAt.get(entry.id().toString());
                if (UiFx.enabled() && revealAt != null) {
                    reveal = UiFx.easeOut(UiFx.progress(revealAt, SIDEBAR_REVEAL_MS));
                }
                int color = locked ? QuestColors.LOCKED_TEXT : active ? QuestColors.TEXT : QuestColors.SIDEBAR_TEXT;
                color = UiFx.scaleAlpha(color, Math.max(0.15f, reveal));
                if (row.hasChildren()) {
                    MockChrome.caret(graphics, row.caretX() + 1, rowY + 5, !row.collapsed(),
                            UiFx.scaleAlpha(QuestColors.SIDEBAR_HEADER, Math.max(0.15f, reveal)));
                    x += 10;
                }
                String fullTitle = entry.title().isBlank() ? entry.id().getPath() : entry.title();
                boolean iconOnly;
                int textX = x;
                if (locked) {
                    MockChrome.padlock(graphics, x + 4, rowY + 8, UiFx.scaleAlpha(QuestColors.LOCKED_EDGE, Math.max(0.15f, reveal)));
                    textX = x + 12;
                } else {
                    Optional<String> glyph = entry.icon().flatMap(Icon::glyphId);
                    if (glyph.isPresent()) {
                        QuestGlyphs.draw(graphics, glyph.get(), x, rowY, 16, color);
                        textX = x + 18;
                    } else {
                        var iconItem = entry.icon().flatMap(icon -> icon.item().flatMap(BuiltInRegistries.ITEM::getOptional));
                        if (iconItem.isPresent()) {
                            var pose = graphics.pose();
                            pose.pushPose();
                            pose.translate(x, rowY + 2, 0);
                            pose.scale(0.6f, 0.6f, 1f);
                            graphics.renderItem(new ItemStack(iconItem.get()), 0, 0);
                            pose.popPose();
                            textX = x + 14;
                        }
                    }
                }
                int maxPx = sidebarWidth() - textX - SIDEBAR_THUMB_PAD;
                iconOnly = maxPx < SIDEBAR_LABEL_MIN;
                if (!iconOnly) {
                    String label = ellipsize(fullTitle, maxPx);
                    if (!label.isEmpty()) {
                        graphics.drawString(font, label, textX, rowY + 4, color, false);
                    }
                }
                if (UiFx.enabled() && reveal < 1f) {
                    int barW = Math.max(2, Math.round(sidebarWidth() * reveal));
                    MockChrome.box(graphics, 0, rowY + 13, barW, 2, UiFx.withAlpha(QuestColors.SIDEBAR_HEADER, Math.max(0.35f, reveal)));
                }
                if (iconOnly && mouseY >= rowY - 2 && mouseY < rowY + 16 && mouseX >= 0 && mouseX < sidebarWidth()) {
                    sidebarHoverTitle = fullTitle;
                }
            });
        }
        graphics.disableScissor();
        if (!sidebarHoverTitle.isEmpty()) {
            int tw = font.width(sidebarHoverTitle);
            int tx = Math.min(Math.max(sidebarWidth() + 4, mouseX + 8), Math.max(4, width - tw - 8));
            int ty = Math.max(TOP_H + 2, mouseY - 12);
            MockChrome.box(graphics, tx - 3, ty - 3, tw + 6, 14, QuestColors.CARD);
            MockChrome.frame(graphics, tx - 3, ty - 3, tw + 6, 14, QuestColors.SIDEBAR_EDGE);
            graphics.drawString(font, sidebarHoverTitle, tx, ty, QuestColors.TEXT, false);
        }
    }

    static final int SIDEBAR_LABEL_MIN = 28;
    private static final int SIDEBAR_THUMB_PAD = 8;

    private String ellipsize(String text, int maxPx) {
        if (text == null || text.isEmpty() || maxPx < SIDEBAR_LABEL_MIN) {
            return "";
        }
        if (font.width(text) <= maxPx) {
            return text;
        }
        String dots = "...";
        int dotsW = font.width(dots);
        if (maxPx <= dotsW) {
            return "";
        }
        int lo = 0;
        int hi = text.length();
        while (lo < hi) {
            int mid = (lo + hi + 1) / 2;
            if (font.width(text.substring(0, mid)) + dotsW <= maxPx) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo <= 0 ? "" : text.substring(0, lo) + dots;
    }

    private boolean sidebarRowVisible(SidebarRow row) {
        return sidebarRowFullyInside(row.y(), 16, TOP_H, height);
    }

    private int sidebarMaxScroll() {
        return sidebarMaxScrollFor(sidebarListH, height);
    }

    private void clampSidebarScroll() {
        sidebarScroll = Math.max(0, Math.min(sidebarScroll, sidebarMaxScroll()));
    }

    private boolean overSidebarList(double mouseX, double mouseY) {
        return !sidebarCollapsed && mouseX >= 0 && mouseX < sidebarWidth() && mouseY >= TOP_H && mouseY < height;
    }

    private void drawSidebarScrollThumb(GuiGraphics graphics) {
        int max = sidebarMaxScroll();
        if (max <= 0) {
            return;
        }
        int trackX = sidebarWidth() - 3;
        int trackY = TOP_H + 4;
        int trackH = Math.max(8, height - TOP_H - 8);
        int view = Math.max(1, height - TOP_H - 8);
        int thumbH = Math.max(12, trackH * view / Math.max(view + max, 1));
        int thumbY = trackY + (int) ((trackH - thumbH) * (sidebarScroll / (double) max));
        MockChrome.box(graphics, trackX, trackY, 2, trackH, QuestColors.SIDEBAR_EDGE);
        // Fade the thumb out once the list has been idle so it stops competing with the rows.
        float idle = UiFx.elapsed(fxScrollActivityAt) / (float) SCROLL_FADE_MS;
        float thumbAlpha = sidebarThumbAlphaFor(idle);
        // Persistent by design: the floor keeps the thumb readable when idle - a scrollbar that can
        // vanish has no affordance value (C spec, t_5a9d5cde).
        MockChrome.box(graphics, trackX, thumbY, 2, thumbH, UiFx.withAlpha(QuestColors.SIDEBAR_HEADER, thumbAlpha));
        if (moreBelow(sidebarScroll, max)) {
            // Non-colour-only affordance: text AND a down-chevron.
            int cx = sidebarWidth() - 8;
            int cy = height - 14;
            for (int i = 0; i < 4; i++) {
                graphics.fill(cx - (3 - i), cy + i, cx + (3 - i) + 1, cy + i + 1, QuestColors.MUTED);
            }
            graphics.drawString(font, "MORE", sidebarWidth() - 44, height - 16, QuestColors.MUTED, false);
        }
    }

    private void rebuildSidebarRows() {
        sidebarRows.clear();
        int y = TOP_H + 8 - sidebarScroll;
        for (ChapterTree.Node node : ClientQuestState.chapterTreeRoots()) {
            y = drawSidebarNodePanel(node, 0, y);
        }
        sidebarListH = y + sidebarScroll - (TOP_H + 8);
        int before = sidebarScroll;
        clampSidebarScroll();
        if (sidebarScroll != before) {
            sidebarRows.clear();
            y = TOP_H + 8 - sidebarScroll;
            for (ChapterTree.Node node : ClientQuestState.chapterTreeRoots()) {
                y = drawSidebarNodePanel(node, 0, y);
            }
            sidebarListH = y + sidebarScroll - (TOP_H + 8);
        }
    }

    private int drawSidebarNodePanel(ChapterTree.Node node, int depth, int y) {
        Chapter entry = node.chapter();
        boolean locked = !ClientQuestState.isChapterUnlocked(entry) && !authoring;
        if (locked && entry.hideUntilUnlocked()) {
            return y;
        }
        boolean hasChildren = false;
        for (ChapterTree.Node child : node.children()) {
            if (authoring || ClientQuestState.isChapterListed(child.chapter())) {
                hasChildren = true;
                break;
            }
        }
        boolean collapsed = hasChildren && collapsedChapters.contains(entry.id().toString());
        int rowH = 16;
        final int rowY = y;
        int caretX = 6 + depth * 10;
        sidebarRows.add(new SidebarRow(entry.id(), rowY, depth, locked, hasChildren, collapsed, caretX));
        y = rowY + rowH + 3;
        if (!collapsed) {
            for (ChapterTree.Node child : node.children()) {
                y = drawSidebarNodePanel(child, depth + 1, y);
            }
        }
        return y;
    }

    private void drawTopBar(GuiGraphics graphics) {
        MockChrome.box(graphics, 0, 0, width, TOP_H, QuestColors.SIDEBAR);
        int rule = QuestColors.SIDEBAR_EDGE;
        BookChrome chrome = resolveChrome();
        if (UiFx.enabled() && TitleMarkup.pulsing(chrome.sidebarTitle())) {
            float alpha = 0.22f + 0.4f * UiFx.wave(2200L, 0L);
            rule = UiFx.withAlpha(chrome.titleColor(), alpha);
        }
        MockChrome.box(graphics, 0, TOP_H - 1, width, 1, rule);
        drawSidebarTitle(graphics);
        if (sidebarTitleGearShown()) {
            MockChrome.box(graphics, sidebarTitleGearX(), 7, 12, 12, QuestColors.CARD);
            MockChrome.frame(graphics, sidebarTitleGearX(), 7, 12, 12, QuestColors.EDIT);
            graphics.drawString(font, "*", sidebarTitleGearX() + 3, 9, QuestColors.EDIT, false);
        }
        if (!logOpen) {
            MockChrome.box(graphics, searchX() - 2, 6, searchW() + 4, 14, QuestColors.CARD);
            MockChrome.frame(graphics, searchX() - 2, 6, searchW() + 4, 14, QuestColors.SIDEBAR_EDGE);
            if (!isIntroChapter()) {
                MockChrome.box(graphics, fitX(), 6, FIT_S, FIT_S, QuestColors.CARD);
                MockChrome.frame(graphics, fitX(), 6, FIT_S, FIT_S, QuestColors.SIDEBAR_EDGE);
            }
            if (claimAllShown()) {
                MockChrome.box(graphics, claimAllX(), 6, claimAllW(), 14, QuestColors.CARD);
                MockChrome.frame(graphics, claimAllX(), 6, claimAllW(), 14, QuestColors.CURRENT);
            }
        }
        MockChrome.box(graphics, logX(), 6, LOG_W, 14, QuestColors.CARD);
        MockChrome.frame(graphics, logX(), 6, LOG_W, 14, QuestColors.SIDEBAR_EDGE);
    }

    private BookChrome packChrome() {
        return ClientQuestState.pack == null ? BookChrome.DEFAULT : ClientQuestState.pack.chrome();
    }

    private BookChrome resolveChrome() {
        BookChrome pack = packChrome();
        String title = QuestConfig.SIDEBAR_TITLE.get();
        if (title == null || title.isBlank()) {
            title = pack.sidebarTitle();
        }
        int color = pack.titleColor();
        String hex = QuestConfig.SIDEBAR_TITLE_COLOR.get();
        if (hex != null && !hex.isBlank()) {
            try {
                String clean = hex.startsWith("#") ? hex.substring(1) : hex;
                long parsed = Long.parseLong(clean, 16);
                color = (int) parsed;
                if (clean.length() <= 6) {
                    color |= 0xFF000000;
                }
            } catch (Exception ignored) {
            }
        }
        float scale = pack.titleScale();
        double cfgScale = QuestConfig.SIDEBAR_TITLE_SCALE.get();
        if (cfgScale > 0) {
            scale = (float) cfgScale;
        }
        boolean shadow = pack.titleShadow();
        int shadowOv = QuestConfig.SIDEBAR_TITLE_SHADOW.get();
        if (shadowOv == 0) {
            shadow = false;
        } else if (shadowOv == 1) {
            shadow = true;
        }
        return new BookChrome(title, color, scale, shadow, pack.titleUppercase());
    }

    private void applyChrome(BookChrome next) {
        BookChrome chrome = next == null ? BookChrome.DEFAULT : next;
        ClientQuestState.pack = ClientQuestState.pack.withChrome(chrome);
        QuestConfig.SIDEBAR_TITLE.set(chrome.sidebarTitle());
        QuestConfig.SIDEBAR_TITLE_COLOR.set(String.format("%08X", chrome.titleColor()));
        QuestConfig.SIDEBAR_TITLE_SCALE.set((double) chrome.titleScale());
        QuestConfig.SIDEBAR_TITLE_SHADOW.set(chrome.titleShadow() ? 1 : 0);
        // The server owns the pack: it re-applies it for every player and writes book.json to the authored pack.
        // Calling QuestDefinitions from here ran server work on the render thread and did nothing on a remote
        // server. Without author rights the edit stays local (ClientQuestState + config).
        if (ClientQuestState.progress.canAuthor()) {
            QuestNetwork.sendToServer(AuthorChromeC2S.of(chrome));
        }
    }

    private boolean sidebarTitleGearShown() {
        if (!authoring || logOpen) {
            return false;
        }
        if (!sidebarCollapsed) {
            return true;
        }
        return sidebarTitleGearX() + 12 <= searchX() - 2;
    }

    private int sidebarTitleMaxW() {
        return Math.max(24, searchX() - 28);
    }

    private String visibleTitle(BookChrome chrome) {
        if (editingChromeTitle) {
            String text = chromeTitleBuffer.isEmpty() ? "_" : chromeTitleBuffer + "_";
            return chrome.titleUppercase() ? text.toUpperCase(Locale.ROOT) : text;
        }
        String shown = TitleMarkup.visible(chrome.sidebarTitle(), chrome.titleUppercase());
        return shown.isEmpty() ? chrome.sidebarTitle() : shown;
    }

    private int sidebarTitleGearX() {
        BookChrome chrome = resolveChrome();
        int tw = Math.round(titleInkWidth(chrome) * chrome.titleScale());
        return Math.min(8 + tw + 4, Math.max(8, searchX() - 16));
    }

    private int titleInkWidth(BookChrome chrome) {
        if (editingChromeTitle) {
            return font.width(visibleTitle(chrome));
        }
        TitleMarkup.Parsed parsed = TitleMarkup.parse(chrome.sidebarTitle(), chrome.titleColor(), chrome.titleUppercase());
        int width = parsed.glyph() == null ? 0 : 10;
        for (TitleMarkup.Run run : parsed.runs()) {
            width += font.width(run.text());
        }
        return Math.max(width, 1);
    }

    private void drawSidebarTitle(GuiGraphics graphics) {
        BookChrome chrome = resolveChrome();
        float scale = chrome.titleScale();
        int maxW = sidebarTitleMaxW();
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(8, 9, 0);
        pose.scale(scale, scale, 1f);
        if (editingChromeTitle) {
            graphics.drawString(font, fitTitle(visibleTitle(chrome), maxW, scale), 0, 0, chrome.titleColor(), chrome.titleShadow());
        } else {
            drawParsedTitle(graphics, chrome, maxW, scale);
        }
        pose.popPose();
    }

    private void drawParsedTitle(GuiGraphics graphics, BookChrome chrome, int maxW, float scale) {
        TitleMarkup.Parsed parsed = TitleMarkup.parse(chrome.sidebarTitle(), chrome.titleColor(), chrome.titleUppercase());
        int x = 0;
        int budget = Math.max(8, Math.round(maxW / Math.max(0.5f, scale)));
        if (parsed.glyph() != null) {
            if (x + 10 <= budget) {
                QuestGlyphs.draw(graphics, parsed.glyph(), x, -1, 8, chrome.titleColor());
                x += 10;
            }
        }
        float wave = UiFx.enabled() ? UiFx.wave(2200L, 0L) : 1f;
        for (int i = 0; i < parsed.runs().size(); i++) {
            TitleMarkup.Run run = parsed.runs().get(i);
            String text = run.text();
            boolean last = i == parsed.runs().size() - 1;
            int room = budget - x;
            if (room <= 4) {
                break;
            }
            if (font.width(text) > room) {
                text = fitTitle(text, Math.round(room * scale), scale);
                last = true;
            }
            if (text.isEmpty()) {
                continue;
            }
            int color = run.color();
            if (run.pulse() && UiFx.enabled()) {
                color = UiFx.withAlpha(color, 0.55f + 0.45f * wave);
            }
            if (run.glow() && UiFx.enabled()) {
                graphics.drawString(font, text, x + 1, 1, UiFx.withAlpha(color, 0.35f), false);
            }
            graphics.drawString(font, text, x, 0, color, chrome.titleShadow());
            x += font.width(text);
            if (last && font.width(run.text()) > room) {
                break;
            }
        }
    }

    private String fitTitle(String text, int maxW, float scale) {
        int tw = Math.round(font.width(text) * scale);
        while (tw > maxW && text.length() > 1) {
            text = text.substring(0, text.length() - 1);
            tw = Math.round(font.width(text + "…") * scale);
            if (tw <= maxW || text.length() <= 1) {
                text = text + "…";
                break;
            }
        }
        return text;
    }

    private void drawClaimBracket(GuiGraphics graphics, int x, int y, int w, int h) {
        int ink = QuestColors.CURRENT;
        MockChrome.box(graphics, x, y, 3, 1, ink);
        MockChrome.box(graphics, x, y, 1, 3, ink);
        MockChrome.box(graphics, x + w - 3, y, 3, 1, ink);
        MockChrome.box(graphics, x + w - 1, y, 1, 3, ink);
        MockChrome.box(graphics, x, y + h - 1, 3, 1, ink);
        MockChrome.box(graphics, x, y + h - 3, 1, 3, ink);
        MockChrome.box(graphics, x + w - 3, y + h - 1, 3, 1, ink);
        MockChrome.box(graphics, x + w - 1, y + h - 3, 1, 3, ink);
    }

    private void drawChromeEditor(GuiGraphics graphics) {
        if (!chromeEditorOpen) {
            return;
        }
        BookChrome chrome = resolveChrome();
        int x = 8;
        int y = TOP_H + 4;
        int w = Math.min(220, Math.max(160, sidebarWidth() - 12));
        int h = 96;
        MockChrome.box(graphics, x, y, w, h, QuestColors.CARD);
        MockChrome.frame(graphics, x, y, w, h, QuestColors.EDIT);
        graphics.drawString(font, "BOOK TITLE", x + 6, y + 4, QuestColors.EDIT, false);
        String shown = editingChromeTitle ? chromeTitleBuffer + "_" : chrome.sidebarTitle();
        graphics.drawString(font, shown, x + 6, y + 16, QuestColors.TEXT, false);
        drawButtonLabel(graphics, x + 6, y + 32, 28, 12, "A-", QuestColors.SIDEBAR_TEXT);
        drawButtonLabel(graphics, x + 38, y + 32, 28, 12, "A+", QuestColors.SIDEBAR_TEXT);
        drawButtonLabel(graphics, x + 70, y + 32, 44, 12, "COLOR", QuestColors.SIDEBAR_TEXT);
        drawButtonLabel(graphics, x + 118, y + 32, 44, 12, chrome.titleShadow() ? "SHAD ON" : "SHAD OFF", QuestColors.SIDEBAR_TEXT);
        String glyph = TitleMarkup.glyphOf(chrome.sidebarTitle());
        drawButtonLabel(graphics, x + 6, y + 48, 52, 12, glyph == null ? "GLYPH" : glyph, QuestColors.SIDEBAR_TEXT);
        drawButtonLabel(graphics, x + 62, y + 48, 44, 12, TitleMarkup.glowing(chrome.sidebarTitle()) ? "GLOW ON" : "GLOW", QuestColors.SIDEBAR_TEXT);
        drawButtonLabel(graphics, x + 110, y + 48, 52, 12, TitleMarkup.pulsing(chrome.sidebarTitle()) ? "PULSE ON" : "PULSE", QuestColors.SIDEBAR_TEXT);
        drawButtonLabel(graphics, x + 6, y + 68, 40, 14, "DONE", QuestColors.EDIT);
        drawButtonLabel(graphics, x + 50, y + 68, 40, 14, "RESET", QuestColors.MUTED);
        graphics.drawString(font, String.format("%.2f  #%08X", chrome.titleScale(), chrome.titleColor()), x + 96, y + 70, QuestColors.MUTED, false);
    }

    private boolean clickChromeEditor(double mouseX, double mouseY) {
        if (!chromeEditorOpen) {
            return false;
        }
        int x = 8;
        int y = TOP_H + 4;
        int w = Math.min(220, Math.max(160, sidebarWidth() - 12));
        int h = 96;
        if (!over(x, y, w, h, mouseX, mouseY)) {
            chromeEditorOpen = false;
            editingChromeTitle = false;
            return true;
        }
        BookChrome chrome = resolveChrome();
        if (over(x + 6, y + 14, w - 12, 14, mouseX, mouseY)) {
            editingChromeTitle = true;
            chromeTitleBuffer = chrome.sidebarTitle();
            return true;
        }
        if (over(x + 6, y + 32, 28, 12, mouseX, mouseY)) {
            applyChrome(chrome.withScale(chrome.titleScale() - 0.25f));
            return true;
        }
        if (over(x + 38, y + 32, 28, 12, mouseX, mouseY)) {
            applyChrome(chrome.withScale(chrome.titleScale() + 0.25f));
            return true;
        }
        if (over(x + 70, y + 32, 44, 12, mouseX, mouseY)) {
            int idx = 0;
            for (int i = 0; i < CHROME_COLOR_PRESETS.length; i++) {
                if (CHROME_COLOR_PRESETS[i] == chrome.titleColor()) {
                    idx = (i + 1) % CHROME_COLOR_PRESETS.length;
                    break;
                }
                idx = 1;
            }
            applyChrome(chrome.withColor(CHROME_COLOR_PRESETS[idx]));
            return true;
        }
        if (over(x + 118, y + 32, 44, 12, mouseX, mouseY)) {
            applyChrome(chrome.withShadow(!chrome.titleShadow()));
            return true;
        }
        if (over(x + 6, y + 48, 52, 12, mouseX, mouseY)) {
            String next = TitleMarkup.cycleGlyph(editingChromeTitle ? chromeTitleBuffer : chrome.sidebarTitle());
            chromeTitleBuffer = next;
            applyChrome(chrome.withTitle(next));
            return true;
        }
        if (over(x + 62, y + 48, 44, 12, mouseX, mouseY)) {
            String next = TitleMarkup.toggleWrap(editingChromeTitle ? chromeTitleBuffer : chrome.sidebarTitle(), "{glow}", "{/glow}");
            chromeTitleBuffer = next;
            applyChrome(chrome.withTitle(next));
            return true;
        }
        if (over(x + 110, y + 48, 52, 12, mouseX, mouseY)) {
            String next = TitleMarkup.toggleWrap(editingChromeTitle ? chromeTitleBuffer : chrome.sidebarTitle(), "{pulse}", "{/pulse}");
            chromeTitleBuffer = next;
            applyChrome(chrome.withTitle(next));
            return true;
        }
        if (over(x + 6, y + 68, 40, 14, mouseX, mouseY)) {
            if (editingChromeTitle) {
                applyChrome(chrome.withTitle(chromeTitleBuffer.isBlank() ? "CHAPTERS" : chromeTitleBuffer));
            }
            editingChromeTitle = false;
            chromeEditorOpen = false;
            return true;
        }
        if (over(x + 50, y + 68, 40, 14, mouseX, mouseY)) {
            applyChrome(BookChrome.DEFAULT);
            chromeTitleBuffer = BookChrome.DEFAULT.sidebarTitle();
            return true;
        }
        return true;
    }

    private void drawChrome(GuiGraphics graphics) {
        if (!logOpen) {
            MockChrome.chevron(graphics, tabX() + 3, tabY() + TAB_H / 2 - 2, !sidebarCollapsed, QuestColors.SIDEBAR_HEADER);
            if (!isIntroChapter()) {
                MockChrome.fitIcon(graphics, fitX(), 6, QuestColors.SIDEBAR_TEXT);
            }
        }
        drawButtonLabel(graphics, logX(), 6, LOG_W, 14, logOpen ? "BOOK" : "LOG", QuestColors.SIDEBAR_TEXT);
        if (claimAllShown()) {
            drawButtonLabel(graphics, claimAllX(), 6, claimAllW(), 14, "CLAIM ALL", QuestColors.TEXT);
            drawClaimBracket(graphics, claimAllX(), 6, claimAllW(), 14);
        }
        if (!QuestConfig.GITHUB_ISSUES_URL.get().isBlank() && !selectedId.isEmpty()) {
            drawButton(graphics, width - 70, height - 22, 62, 14, "REPORT", QuestColors.PORT_RED);
        }
        drawXorPathCue(graphics);
    }

    /** Persistent choose-path banner. UNDERSTOOD never commits a branch. */
    private void drawXorPathCue(GuiGraphics graphics) {
        if (authoring || logOpen || modal != ModalKind.NONE || isIntroChapter() || !ClientQuestState.hasUnresolvedXor()) {
            return;
        }
        List<String> keys = ClientQuestState.unresolvedXorKeys();
        String focus = keys.isEmpty() ? "" : keys.getFirst();
        String label = focus.isEmpty() ? "CHOOSE PATH" : "CHOOSE PATH · " + shortXorLabel(focus);
        int w = Math.min(contentWidth() - 16, pillW(label) + 16);
        int x = boardLeft() + Math.max(8, (contentWidth() - w) / 2);
        int y = height - 22;
        MockChrome.box(graphics, x, y, w, 14, QuestColors.CARD);
        MockChrome.frame(graphics, x, y, w, 14, QuestColors.XOR_EDGE);
        drawButtonLabel(graphics, x, y, w, 14, label, QuestColors.TEXT);
    }

    private static String shortXorLabel(String key) {
        int slash = key.lastIndexOf('/');
        return slash < 0 ? key : key.substring(slash + 1).toUpperCase(Locale.ROOT);
    }

    private void drawGrid(GuiGraphics graphics) {
        int gw = chapter.gridWidth();
        int gh = chapter.gridHeight();
        int minGx = (int) Math.floor(cameraX / STRIDE) - 1;
        int minGy = (int) Math.floor(cameraY / STRIDE) - 1;
        int maxGx = (int) Math.ceil((cameraX + (double) contentWidth() / zoom) / STRIDE) + 1;
        int maxGy = (int) Math.ceil((cameraY + (double) height / zoom) / STRIDE) + 1;
        minGx = Math.max(0, Math.min(minGx, gw));
        minGy = Math.max(0, Math.min(minGy, gh));
        maxGx = Math.max(0, Math.min(maxGx, gw));
        maxGy = Math.max(0, Math.min(maxGy, gh));
        maxGx = Math.min(maxGx, minGx + 18);
        maxGy = Math.min(maxGy, minGy + 12);
        for (int gx = minGx; gx < maxGx; gx++) {
            for (int gy = minGy; gy < maxGy; gy++) {
                int[] s = screen(gx, gy);
                UiDraw.tile64(graphics, UiDraw.CELL, s[0], s[1]);
            }
        }
    }

    private void drawClosedTileFace(GuiGraphics graphics, Tile tile) {
        int[] s = screen(tile.pos().x(), tile.pos().y());
        TileVisual visual = ClientQuestState.visual(chapter, tile, authoring, selectedId);
        UiDraw.tile64(graphics, UiDraw.tileFace(visual), s[0], s[1]);
    }

    private void drawClosedTileDecor(GuiGraphics graphics, Tile tile, boolean onPath) {
        int[] s = screen(tile.pos().x(), tile.pos().y());
        int x = s[0];
        int y = s[1];
        TileVisual visual = ClientQuestState.visual(chapter, tile, authoring, selectedId);
        graphics.setColor(1f, 1f, 1f, 1f);
        int size = tilePx();
        if (UiFx.enabled() && tile.id().equals(fxSearchFlashId) && fxSearchFlashAt >= 0L
                && !UiFx.finished(fxSearchFlashAt, 420L)) {
            float left = 1f - UiFx.easeOut(UiFx.progress(fxSearchFlashAt, 420L));
            MockChrome.frame(graphics, x, y, size, size, UiFx.withAlpha(QuestColors.EDIT, 0.25f + 0.75f * left));
        }
        int inner = x + 4;
        int edge = borderColor(visual);
        if (edge != 0) {
            tinyString(graphics, headerLabel(visual, tile), x + 2, y + 1, MockChrome.tagInk(edge));
        }
        if (visual == TileVisual.FAILED) {
            String progress = ClientQuestState.taskProgressLabel(chapter, tile);
            if (!progress.isBlank()) {
                MockChrome.box(graphics, x + 1, y + size - 3, size - 2, 2, edge);
            }
        }
        tile.icon().flatMap(Icon::glyphId).ifPresentOrElse(
                glyph -> QuestGlyphs.draw(graphics, glyph, inner, y + 13, QuestColors.TEXT),
                () -> tile.icon().flatMap(Icon::item).flatMap(BuiltInRegistries.ITEM::getOptional).ifPresent(item ->
                        graphics.renderItem(new ItemStack(item), inner, y + 13)));
        String rawTitle = tileObjective(tile);
        boolean titleBeside = tile.icon().isPresent();
        int maxTitle = (titleBeside ? size - 26 : size - 10) * 2;
        List<String> titleLines = wrapTitle(rawTitle, maxTitle);
        int titleX = titleBeside ? inner + 18 : inner;
        int titleY = titleBeside ? y + 16 : y + 20;
        int ry = y + size - 15;
        int lineCap = 2;
        if (!tile.rewards().isEmpty()) {
            // Keep the title above the REWARDS caption at ry - 6.
            lineCap = Math.max(1, Math.min(lineCap, (ry - 6 - titleY) / 6));
        }
        for (int i = 0; i < Math.min(lineCap, titleLines.size()); i++) {
            tinyString(graphics, titleLines.get(i), titleX, titleY + i * 6, QuestColors.TEXT);
        }
        if (!tile.rewards().isEmpty()) {
            tinyString(graphics, "REWARDS", x + size - 28, ry - 6, QuestColors.MUTED);
            List<ItemStack> faces = collectRewardFaces(tile);
            int show = Math.min(faces.size(), QuestTileWidget.REWARD_FACE_CAP);
            for (int i = 0; i < show; i++) {
                ItemStack face = faces.get(i);
                var pose = graphics.pose();
                pose.pushPose();
                pose.translate(x + size - 15 - i * 10, ry - 1, 0);
                pose.scale(0.6f, 0.6f, 1f);
                graphics.renderItem(face, 0, 0);
                pose.popPose();
            }
            if (showsRewardPlus(tile)) {
                tinyString(graphics, "+", x + size - 6, ry - 7, QuestColors.CURRENT);
            }
        }
        if (ClientQuestState.hasXorBadge(chapter, tile)) {
            MockChrome.box(graphics, inner, y + size - 11, 15, 6, QuestColors.COMPLETED);
            tinyString(graphics, "XOR", inner + 2, y + size - 10, MockChrome.INK);
        }
    }

    /** Mock cards use the quest title; progress only when the task is counted. */
    private String tileObjective(Tile tile) {
        String title = tile.title().isBlank() ? tile.id() : tile.title();
        if (tile.tasks().isEmpty()) {
            return title.toUpperCase(Locale.ROOT);
        }
        Task task = tile.tasks().getFirst();
        int need = Math.max(1, task.required());
        if (need > 1) {
            String questId = chapter.id() + "/" + tile.id();
            int value = ClientQuestState.progress.value(questId, "0");
            // The BOARD half of the raw-verb defect. This used to uppercase task.type() directly, so the
            // board behind a tile card still read ITEM_TAG 16/16 while the card's own task rows read
            // COLLECT 16/16 — two surfaces, two labels, same task. It now goes through the same shared verb
            // as the card, so the two surfaces cannot disagree again.
            return objectiveLabel(ClientQuestState.taskVerb(task, tile), value, need);
        }
        return title.toUpperCase(Locale.ROOT);
    }

    /**
     * The board's {@code <VERB> <value>/<need>} label. Pure, and package-private so a test can pin its shape
     * without standing up a screen. The verb is a parameter and never a type id: the board and the tile
     * card's own task rows now render the same string for the same task, which is the property that was
     * broken when the board read {@code ITEM_TAG 16/16} while the card above it read {@code COLLECT 16/16}.
     */
    static String objectiveLabel(String verb, int value, int need) {
        return verb + " " + value + "/" + need;
    }

    /** 0.6-scale board labels — mock tile text is ~5–7px tall. */
    private void tinyString(GuiGraphics graphics, String text, int x, int y, int color) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(0.6f, 0.6f, 1f);
        graphics.drawString(font, text, 0, 0, color, true);
        pose.popPose();
    }

    /** Thin mock link lines + heads (background pass so fills stick). */
    private void drawLinkLines(GuiGraphics graphics) {
        int size = tilePx();
        for (Link link : chapter.links()) {
            chapter.tile(link.from()).ifPresent(from -> chapter.tile(link.to()).ifPresent(to -> {
                TileVisual fromV = ClientQuestState.visual(chapter, from, authoring, selectedId);
                TileVisual toV = ClientQuestState.visual(chapter, to, authoring, selectedId);
                if (fromV == TileVisual.LOCKED && !authoring) {
                    return;
                }
                int[] a = screen(from.pos().x(), from.pos().y());
                int[] b = screen(to.pos().x(), to.pos().y());
                int dx = Integer.compare(to.pos().x(), from.pos().x());
                int dy = Integer.compare(to.pos().y(), from.pos().y());
                int x1 = a[0] + (dx > 0 ? size : dx < 0 ? 0 : size / 2);
                int y1 = a[1] + (dy > 0 ? size : dy < 0 ? 0 : size / 2);
                int x2 = b[0] + (dx > 0 ? 0 : dx < 0 ? size : size / 2);
                int y2 = b[1] + (dy > 0 ? 0 : dy < 0 ? size : size / 2);
                boolean destLocked = toV == TileVisual.LOCKED && !authoring;
                int color = destLocked ? QuestColors.PORT_RED
                        : (borderColor(fromV) == 0 ? QuestColors.NEW : borderColor(fromV));
                MockChrome.linkElbow(graphics, x1, y1, x2, y2, color);
                int mx = x1 + (x2 - x1) / 2;
                int my = y1 + (y2 - y1) / 2;
                int tipX = x2 - (dx != 0 ? dx * 3 : 0);
                int tipY = y2 - (dy != 0 ? dy * 3 : 0);
                int adx = dx == 0 && dy == 0 ? 1 : dx;
                MockChrome.arrowHead(graphics, tipX, tipY, adx, dy, destLocked ? QuestColors.PORT_RED : color);
            }));
        }
    }

    /**
     * Mock card: contained — face + header tab. 2px font frame only if U+E000 is 1–3px.
     */
    private void drawGlyphCard(GuiGraphics graphics, int x, int y, TileVisual visual) {
        int size = tilePx();
        int lh = Math.max(8, font.lineHeight);
        int edge = borderColor(visual);
        int face = QuestColors.CARD;
        if (edge != 0) {
            PixelPaint.fill(graphics, font, x + 1, y + 1, size - 2, size - 2, face);
            graphics.flush();
            String label = headerLabel(visual);
            int headerW = tabWidth(label, size - 14);
            PixelPaint.cardChrome(graphics, font, x, y, size, size, edge, headerW);
        } else {
            PixelPaint.fill(graphics, font, x, y, size, size, face);
        }
    }

    /** Quiet viewport squares — recessed mock grid cells, no LOCKED label. */
    private int drawQuietCells(GuiGraphics graphics) {
        ensureOccupiedCells();
        int gw = chapter.gridWidth();
        int gh = chapter.gridHeight();
        int minGx = Math.max(0, (int) Math.floor(cameraX / STRIDE) - 1);
        int minGy = Math.max(0, (int) Math.floor(cameraY / STRIDE) - 1);
        int maxGx = Math.min(gw, (int) Math.ceil((cameraX + contentWidth() / zoom) / STRIDE) + 1);
        int maxGy = Math.min(gh, (int) Math.ceil((cameraY + height / zoom) / STRIDE) + 1);
        int size = tilePx();
        int left = boardLeft();
        int drawn = 0;
        graphics.enableScissor(left, TOP_H, width, height);
        try {
        for (int gx = minGx; gx < maxGx; gx++) {
            for (int gy = minGy; gy < maxGy; gy++) {
                if (occupiedCells.contains(packPos(gx, gy))) {
                    continue;
                }
                int[] s = screen(gx, gy);
                if (s[0] + size < left || s[1] + size < TOP_H || s[0] > width || s[1] > height) {
                    continue;
                }
                if (authoring) {
                    MockChrome.cell(graphics, s[0], s[1], size);
                } else {
                    MockChrome.quietCell(graphics, s[0], s[1], size);
                }
                drawn++;
            }
        }
        } finally {
            graphics.disableScissor();
        }
        return drawn;
    }

    private void ensureOccupiedCells() {
        if (occupiedChapterId != null
                && occupiedChapterId.equals(chapter.id())
                && occupiedProgress == ClientQuestState.progress
                && occupiedAuthoring == authoring
                && occupiedTileCount == chapter.tiles().size()) {
            return;
        }
        occupiedCells.clear();
        for (Tile tile : chapter.tiles()) {
            if (!authoring && ClientQuestState.isConcealed(chapter, tile)) {
                continue;
            }
            occupiedCells.add(packPos(tile.pos().x(), tile.pos().y()));
        }
        occupiedChapterId = chapter.id();
        occupiedProgress = ClientQuestState.progress;
        occupiedAuthoring = authoring;
        occupiedTileCount = chapter.tiles().size();
    }

    private void stampFill(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int cw = Math.max(1, font.width("\u2588"));
        int step = Math.max(1, font.lineHeight - 1);
        String row = "\u2588".repeat(Math.max(1, w / cw));
        for (int yy = 0; yy < h; yy += step) {
            graphics.drawString(font, row, x, y + yy, color, true);
        }
    }

    private void stampThinFrame(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        int step = Math.max(1, font.width("-") / 2);
        for (int i = 0; i < w; i += step) {
            graphics.drawString(font, "-", x + i, y - 4, color, true);
            graphics.drawString(font, "-", x + i, y + h - 5, color, true);
        }
        for (int i = 0; i < h; i += 2) {
            graphics.drawString(font, "|", x, y + i, color, true);
            graphics.drawString(font, "|", x + w - 2, y + i, color, true);
        }
    }

    /**
     * SOURCE tile edge midpoint in the travel direction. Never the gutter center.
     * lockX/lockY and the path-arrow origin are this pixel.
     */
    static int[] sourceEdgeMid(int ox, int oy, int size, int dx, int dy) {
        int x = dx > 0 ? ox + size - 1 : dx < 0 ? ox : ox + size / 2;
        int y = dy > 0 ? oy + size - 1 : dy < 0 ? oy : oy + size / 2;
        return new int[]{x, y};
    }

    private int[][] tilePorts(Tile tile, TileVisual visual, int ox, int oy, int size) {
        List<int[]> ports = new ArrayList<>();
        boolean lockedPlay = visual == TileVisual.LOCKED && !authoring;
        if (!lockedPlay) {
            for (Link link : chapter.links()) {
                if (!link.from().equals(tile.id())) {
                    continue;
                }
                chapter.tile(link.to()).ifPresent(to -> {
                    if (!authoring && ClientQuestState.isConcealed(chapter, to)) {
                        return;
                    }
                    if (!tile.pos().cardinalTo(to.pos())) {
                        return;
                    }
                    int dx = Integer.compare(to.pos().x(), tile.pos().x());
                    int dy = Integer.compare(to.pos().y(), tile.pos().y());
                    TileVisual dest = ClientQuestState.visual(chapter, to, authoring, selectedId);
                    boolean gated = dest == TileVisual.LOCKED && !authoring;
                    int[] edge = sourceEdgeMid(ox, oy, size, dx, dy);
                    int color = MockChrome.pathPortColor(visual, gated);
                    ports.add(new int[]{
                            edge[0],
                            edge[1],
                            color,
                            gated ? 1 : 0,
                            dx,
                            dy,
                            edge[0],
                            edge[1],
                            0
                    });
                });
            }
        }
        return ports.toArray(new int[0][]);
    }

    private List<PathArrow> collectPathArrows() {
        List<PathArrow> arrows = new ArrayList<>();
        int size = tilePx();
        for (Tile tile : chapter.tiles()) {
            TileVisual visual = ClientQuestState.visual(chapter, tile, authoring, selectedId);
            if (visual == TileVisual.LOCKED && !authoring) {
                continue;
            }
            if (!authoring && ClientQuestState.isConcealed(chapter, tile)) {
                continue;
            }
            int[] s = screen(tile.pos().x(), tile.pos().y());
            int[][] ports = tilePorts(tile, visual, s[0], s[1], size);
            int i = 0;
            for (Link link : chapter.links()) {
                if (!link.from().equals(tile.id())) {
                    continue;
                }
                var dest = chapter.tile(link.to());
                if (dest.isEmpty()) {
                    continue;
                }
                Tile to = dest.get();
                if (!authoring && ClientQuestState.isConcealed(chapter, to)) {
                    continue;
                }
                if (!tile.pos().cardinalTo(to.pos())) {
                    continue;
                }
                if (i >= ports.length) {
                    break;
                }
                int[] port = ports[i++];
                if (port.length >= 9 && port[8] != 0) {
                    continue;
                }
                arrows.add(new PathArrow(tile.id(), to.id(), port[6], port[7], port[3] != 0, port[2]));
            }
        }
        return arrows;
    }

    private List<String> wrapTitle(String raw, int maxW) {
        String upper = raw == null ? "" : raw.toUpperCase(Locale.ROOT).trim();
        if (upper.isEmpty()) {
            return List.of();
        }
        if (font.width(upper) <= maxW) {
            return List.of(upper);
        }
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : upper.split(" +")) {
            if (word.isEmpty()) {
                continue;
            }
            if (font.width(word) > maxW) {
                if (!line.isEmpty()) {
                    lines.add(line.toString());
                    line = new StringBuilder();
                }
                lines.add(fitWidth(word, maxW));
                continue;
            }
            String next = line.isEmpty() ? word : line + " " + word;
            if (font.width(next) > maxW && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        if (lines.isEmpty()) {
            return List.of(fitWidth(upper, maxW));
        }
        return lines;
    }

    /** Clip a line to the half-scale budget without a hyphen (avoids "PICK A PATH-"). */
    private String fitWidth(String text, int maxW) {
        if (font.width(text) <= maxW) {
            return text;
        }
        String cut = text;
        while (cut.length() > 1 && font.width(cut) > maxW) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut;
    }

    /** Faint corner dots only — full empty-cell boxes turned the book into a spreadsheet. */
    private void drawCellDots(GuiGraphics graphics) {
        Set<Long> occupied = new java.util.HashSet<>();
        for (Tile tile : chapter.tiles()) {
            occupied.add(packPos(tile.pos().x(), tile.pos().y()));
        }
        int gw = chapter.gridWidth();
        int gh = chapter.gridHeight();
        int minGx = Math.max(0, (int) Math.floor(cameraX / STRIDE) - 1);
        int minGy = Math.max(0, (int) Math.floor(cameraY / STRIDE) - 1);
        int maxGx = Math.min(gw, minGx + 8);
        int maxGy = Math.min(gh, minGy + 6);
        for (int gx = minGx; gx < maxGx; gx++) {
            for (int gy = minGy; gy < maxGy; gy++) {
                if (occupied.contains(packPos(gx, gy))) {
                    continue;
                }
                int[] s = screen(gx, gy);
                if (s[0] < boardLeft() || s[1] < 26 || s[0] + 8 >= width || s[1] + 8 >= height) {
                    continue;
                }
                graphics.drawString(font, "+", s[0], s[1], QuestColors.CELL_LINE, true);
            }
        }
    }

    /** Thin +---+ sized so font.width(hz) <= TILE (earlier n made hzW=72 > 64). */
    private void drawAsciiBox(GuiGraphics graphics, int x, int y, int color, boolean fillFace, int size) {
        int lh = Math.max(8, font.lineHeight);
        int dashW = Math.max(1, font.width("-"));
        int n = Math.max(2, (size - font.width("+") * 2) / dashW);
        String hz = "+" + "-".repeat(n) + "+";
        while (n > 2 && font.width(hz) > size) {
            n--;
            hz = "+" + "-".repeat(n) + "+";
        }
        if (fillFace) {
            String px = "\u2588";
            int cw = Math.max(1, font.width(px));
            String fill = px.repeat(Math.max(2, size / cw));
            for (int row = 0; row < size; row += Math.max(1, lh - 1)) {
                graphics.drawString(font, fill, x, y + row, QuestColors.CARD, true);
            }
        }
        graphics.drawString(font, hz, x, y, color, true);
        for (int row = lh; row < size - lh; row += lh) {
            graphics.drawString(font, "|", x, y + row, color, true);
            graphics.drawString(font, "|", x + Math.max(0, font.width(hz) - font.width("|")), y + row, color, true);
        }
        graphics.drawString(font, hz, x, y + size - lh, color, true);
    }

    private void drawPortsLocal(GuiGraphics graphics, Tile tile, TileVisual visual, int ox, int oy) {
        int size = tilePx();
        int portColor = borderColor(visual) == 0 ? QuestColors.PORT_DIM : borderColor(visual);
        for (Link link : chapter.links()) {
            if (link.from().equals(tile.id())) {
                chapter.tile(link.to()).ifPresent(to -> {
                    int dx = Integer.compare(to.pos().x(), tile.pos().x());
                    int dy = Integer.compare(to.pos().y(), tile.pos().y());
                    int px = ox + size / 2 + dx * (size / 2);
                    int py = oy + size / 2 + dy * (size / 2);
                    MockChrome.diamond(graphics, px, py, QuestColors.PORT_RED);
                });
            }
            if (link.to().equals(tile.id())) {
                chapter.tile(link.from()).ifPresent(from -> {
                    int dx = Integer.compare(from.pos().x(), tile.pos().x());
                    int dy = Integer.compare(from.pos().y(), tile.pos().y());
                    int px = ox + size / 2 + dx * (size / 2);
                    int py = oy + size / 2 + dy * (size / 2);
                    int color = visual == TileVisual.LOCKED ? QuestColors.PORT_DIM : portColor;
                    MockChrome.diamond(graphics, px, py, color);
                });
            }
        }
    }

    private void drawAddGhosts(GuiGraphics graphics) {
        Set<Long> occupied = new java.util.HashSet<>();
        for (Tile tile : chapter.tiles()) {
            occupied.add(packPos(tile.pos().x(), tile.pos().y()));
        }
        for (Tile tile : chapter.tiles()) {
            int gx = tile.pos().x();
            int gy = tile.pos().y() + 1;
            if (!chapter.inBounds(gx, gy) || occupied.contains(packPos(gx, gy))) {
                continue;
            }
            int[] s = screen(gx, gy);
            int x = s[0];
            int y = s[1];
            int size = tilePx();
            drawFrame(graphics, x + 8, y + 8, size - 16, size - 16, QuestColors.ADD);
            graphics.drawString(font, "+", x + size / 2 - 3, y + size / 2 - 10, QuestColors.ADD, false);
            graphics.drawString(font, "ADD", x + size / 2 - 8, y + size / 2 + 2, QuestColors.ADD, false);
        }
    }

    private void drawPorts(GuiGraphics graphics, Tile tile, TileVisual visual) {
        int[] s = screen(tile.pos().x(), tile.pos().y());
        int size = tilePx();
        int portColor = borderColor(visual);
        if (portColor == 0) {
            portColor = QuestColors.PORT_DIM;
        }
        final int color = portColor;
        for (Link link : chapter.links()) {
            if (link.from().equals(tile.id())) {
                chapter.tile(link.to()).ifPresent(to -> {
                    int dx = Integer.compare(to.pos().x(), tile.pos().x());
                    int dy = Integer.compare(to.pos().y(), tile.pos().y());
                    int px = s[0] + size / 2 + dx * (size / 2);
                    int py = s[1] + size / 2 + dy * (size / 2);
                    drawDiamond(graphics, px, py, color);
                });
            }
            if (link.to().equals(tile.id())) {
                chapter.tile(link.from()).ifPresent(from -> {
                    int dx = Integer.compare(from.pos().x(), tile.pos().x());
                    int dy = Integer.compare(from.pos().y(), tile.pos().y());
                    int px = s[0] + size / 2 + dx * (size / 2);
                    int py = s[1] + size / 2 + dy * (size / 2);
                    boolean lockedEdge = visual == TileVisual.LOCKED
                            || !ClientQuestState.progress.tileCompleted(chapter.id().toString(), from.id());
                    drawDiamond(graphics, px, py, lockedEdge && visual == TileVisual.LOCKED ? QuestColors.PORT_DIM : color);
                    if (visual == TileVisual.LOCKED && !ClientQuestState.progress.tileCompleted(chapter.id().toString(), from.id())) {
                        drawLock(graphics, px - 3, py - 10, QuestColors.PORT_RED);
                    }
                });
            }
        }
    }

    // --- effects engine (see UiFx) ------------------------------------------------------------------

    /**
     * Detect per-tile state transitions once per frame and arm the matching one-shot effects. Keyed off
     * {@link ClientQuestState#visual} so it uses exactly the same rules the chrome does — no second
     * opinion about what "completed" or "failed" means.
     *
     * <p>Visuals only change when a progress snapshot lands, so this is a cheap map walk between syncs.
     */
    private void updateVisualEffects() {
        if (!UiFx.enabled()) {
            finishLogCloseIfNeeded();
            fxInspectWanted = showInspectPanel();
            fxInspectExiting = false;
            fxInspectExitTileId = "";
            return;
        }
        long now = UiFx.nowMs();
        String chapterKey = chapter.id().toString();
        Set<String> unlockedNow = new HashSet<>();

        for (Tile tile : chapter.tiles()) {
            TileVisual visual;
            try {
                visual = ClientQuestState.visual(chapter, tile, authoring, selectedId);
            } catch (RuntimeException ignored) {
                continue;
            }
            TileVisual before = fxSeenVisuals.put(tile.id(), visual);
            if (visual == TileVisual.COMPLETED && before != null && before != TileVisual.COMPLETED) {
                fxFlareAt.put(tile.id(), now);
            }
            if (visual == TileVisual.FAILED && before != null && before != TileVisual.FAILED) {
                fxCrackAt.put(tile.id(), now);
            }
            if (visual != TileVisual.LOCKED && visual != TileVisual.CLOSED) {
                unlockedNow.add(tile.id());
            }
        }

        // Ignition pips: a link whose destination just opened this frame.
        if (fxUnlockBaselineReady) {
            for (Link link : chapter.links()) {
                if (!unlockedNow.contains(link.to()) || fxWasUnlocked.contains(link.to())) {
                    continue;
                }
                if (!unlockedNow.contains(link.from())) {
                    continue;
                }
                fxPipAt.put(chapterKey + "|" + link.from() + ">" + link.to(), now);
            }
        }
        fxWasUnlocked.clear();
        fxWasUnlocked.addAll(unlockedNow);
        fxUnlockBaselineReady = true;

        // Drop finished one-shots so the maps cannot grow with the session.
        fxFlareAt.values().removeIf(start -> UiFx.finished(start, FLARE_MS));
        fxCrackAt.values().removeIf(start -> UiFx.finished(start, CRACK_MS));
        fxPipAt.values().removeIf(start -> UiFx.finished(start, PIP_MS));
        fxSidebarRevealAt.values().removeIf(start -> UiFx.finished(start, SIDEBAR_REVEAL_MS));
        if (fxClaimSparkAt >= 0L && UiFx.finished(fxClaimSparkAt, CLAIM_SPARK_MS)) {
            fxClaimSparkAt = -1L;
        }
        if (fxPinFlashAt >= 0L && UiFx.finished(fxPinFlashAt, PIN_FLASH_MS)) {
            fxPinFlashAt = -1L;
        }
        if (fxChoiceAt >= 0L && UiFx.finished(fxChoiceAt, CHOICE_MS)) {
            fxChoiceAt = -1L;
            fxChoiceTileId = "";
        }

        updateInspectFx();
        updateChoiceFx(now);
        updateSidebarRevealFx(now);
        finishLogCloseIfNeeded();

        if (fxChapterOpenedId == null || !fxChapterOpenedId.equals(chapter.id())) {
            fxChapterOpenedId = chapter.id();
            fxChapterOpenedAt = now;
            fxSeenVisuals.clear();
            fxFlareAt.clear();
            fxCrackAt.clear();
            fxPipAt.clear();
            fxWasUnlocked.clear();
            fxUnlockBaselineReady = false;
            fxTaskFill.clear();
        }
    }

    private void updateInspectFx() {
        boolean want = showInspectPanel();
        long now = UiFx.nowMs();
        if (want) {
            String id = inspectTileId();
            if (!id.isEmpty()) {
                fxInspectLiveId = id;
            }
            if (!fxInspectWanted) {
                fxInspectMovedAt = now;
            }
            fxInspectWanted = true;
            fxInspectExiting = false;
            fxInspectExitTileId = "";
            return;
        }
        if (fxInspectWanted) {
            if (inspectPinned()) {
                fxInspectWanted = false;
                fxInspectExiting = false;
                fxInspectExitTileId = "";
                return;
            }
            fxInspectWanted = false;
            fxInspectExiting = true;
            fxInspectExitTileId = fxInspectLiveId;
            fxInspectMovedAt = now;
        }
        if (fxInspectExiting && (fxInspectExitTileId.isEmpty() || UiFx.finished(fxInspectMovedAt, INSPECT_MS))) {
            fxInspectExiting = false;
            fxInspectExitTileId = "";
        }
    }

    private float inspectFxAlpha() {
        if (!UiFx.enabled()) {
            return showInspectPanel() ? 1f : 0f;
        }
        float p = UiFx.easeOut(UiFx.progress(fxInspectMovedAt, INSPECT_MS));
        if (fxInspectExiting) {
            return 1f - p;
        }
        if (showInspectPanel()) {
            return Math.max(0.01f, p <= 0f ? 1f : p);
        }
        return 0f;
    }

    private int inspectFxOffsetY() {
        if (!UiFx.enabled() || fxInspectExiting || !showInspectPanel()) {
            return 0;
        }
        float p = UiFx.easeOut(UiFx.progress(fxInspectMovedAt, INSPECT_MS));
        return Math.round(UiFx.lerp(6f, 0f, p));
    }

    private boolean drawingInspect() {
        return showInspectPanel() || (fxInspectExiting && !fxInspectExitTileId.isEmpty());
    }

    private String drawingInspectTileId() {
        if (showInspectPanel()) {
            return inspectTileId();
        }
        return fxInspectExitTileId;
    }

    private void updateChoiceFx(long now) {
        String id = inspectTileId();
        if (id.isEmpty()) {
            fxChoiceWasPending = false;
            return;
        }
        Optional<Tile> tile = chapter.tile(id);
        if (tile.isEmpty()) {
            fxChoiceWasPending = false;
            return;
        }
        PlayBar bar = playBar(tile.get(), 0, 0, 200, 80);
        boolean pending = bar.choice();
        if (pending && !fxChoiceWasPending) {
            fxChoiceAt = now;
            fxChoiceTileId = id;
        }
        fxChoiceWasPending = pending;
    }

    private void updateSidebarRevealFx(long now) {
        Set<String> listed = new HashSet<>();
        for (Chapter entry : ClientQuestState.pack.chapters()) {
            if (ClientQuestState.isChapterListed(entry) && ClientQuestState.isChapterUnlocked(entry)) {
                listed.add(entry.id().toString());
            }
        }
        if (fxSidebarBaselineReady) {
            for (String id : listed) {
                if (!fxSidebarSeenListed.contains(id)) {
                    fxSidebarRevealAt.put(id, now);
                }
            }
        }
        fxSidebarSeenListed.clear();
        fxSidebarSeenListed.addAll(listed);
        fxSidebarBaselineReady = true;
    }

    private void finishLogCloseIfNeeded() {
        if (fxLogClosing && (fxLogAt < 0L || UiFx.finished(fxLogAt, LOG_DRAWER_MS) || !UiFx.enabled())) {
            fxLogClosing = false;
            logOpen = false;
            fxLogAt = -1L;
        }
    }

    private boolean drawingLog() {
        return logOpen || fxLogClosing;
    }

    private float logFxProgress() {
        if (!UiFx.enabled() || fxLogAt < 0L) {
            return 1f;
        }
        float p = UiFx.easeInOut(UiFx.progress(fxLogAt, LOG_DRAWER_MS));
        return fxLogClosing ? 1f - p : p;
    }

    /** Completion flares, FAILED cracks, and gate-ignition pips — drawn over the board, under overlays. */
    private void drawEffectOverlays(GuiGraphics graphics) {
        if (!UiFx.enabled() || isIntroChapter() || modal != ModalKind.NONE) {
            return;
        }
        if (!fxFlareAt.isEmpty()) {
            for (var entry : fxFlareAt.entrySet()) {
                chapter.tile(entry.getKey()).ifPresent(tile -> drawCompletionFlare(graphics, tile, entry.getValue()));
            }
        }
        if (!fxCrackAt.isEmpty()) {
            for (var entry : fxCrackAt.entrySet()) {
                chapter.tile(entry.getKey()).ifPresent(tile -> drawFailedCrack(graphics, tile, entry.getValue()));
            }
        }
        if (!fxPipAt.isEmpty()) {
            int size = tilePx();
            for (var entry : fxPipAt.entrySet()) {
                String key = entry.getKey();
                int sep = key.indexOf('|');
                if (sep < 0) {
                    continue;
                }
                int gt = key.indexOf('>', sep);
                if (gt < 0) {
                    continue;
                }
                String fromId = key.substring(sep + 1, gt);
                String toId = key.substring(gt + 1);
                Optional<Tile> from = chapter.tile(fromId);
                Optional<Tile> to = chapter.tile(toId);
                if (from.isEmpty() || to.isEmpty()) {
                    continue;
                }
                drawGatePip(graphics, from.get(), to.get(), size, entry.getValue());
            }
        }
        drawClaimSpark(graphics);
    }

    /** Board dim that eases out after a chapter switch so the new grid does not pop. */
    private void drawBoardCrossfade(GuiGraphics graphics) {
        if (!UiFx.enabled() || isIntroChapter() || drawingLog() || modal != ModalKind.NONE) {
            return;
        }
        float p = UiFx.progress(fxChapterOpenedAt, BOARD_FADE_MS);
        if (p >= 1f) {
            return;
        }
        float alpha = (1f - UiFx.easeOut(p)) * 0.55f;
        if (alpha <= 0.02f) {
            return;
        }
        int left = boardLeft();
        MockChrome.box(graphics, left, TOP_H, Math.max(1, width - left), Math.max(1, height - TOP_H),
                UiFx.withAlpha(QuestColors.VOID, alpha));
    }

    private void drawClaimSpark(GuiGraphics graphics) {
        if (!UiFx.enabled() || fxClaimSparkAt < 0L) {
            return;
        }
        float p = UiFx.easeOut(UiFx.progress(fxClaimSparkAt, CLAIM_SPARK_MS));
        if (p >= 1f) {
            return;
        }
        for (int i = 0; i < 8; i++) {
            float ang = (i / 8f) * (float) Math.PI * 2f + p * 0.6f;
            float dist = 4f + 18f * p;
            int px = fxClaimSparkX + Math.round((float) Math.cos(ang) * dist);
            int py = fxClaimSparkY + Math.round((float) Math.sin(ang) * dist);
            float fade = 1f - p;
            MockChrome.box(graphics, px, py, 1, 1, UiFx.withAlpha(QuestColors.REWARD, 0.15f + 0.55f * fade));
        }
    }

    /** Expanding square ring of pixels out of a just-completed tile. */
    private void drawCompletionFlare(GuiGraphics graphics, Tile tile, long startMs) {
        float p = UiFx.easeOut(UiFx.progress(startMs, FLARE_MS));
        if (p >= 1f) {
            return;
        }
        int[] s = screen(tile.pos().x(), tile.pos().y());
        int size = tilePx();
        int grow = Math.round(3f + 9f * p);
        int x = s[0] - grow;
        int y = s[1] - grow;
        int w = size + grow * 2;
        float alpha = (1f - p) * 0.75f;
        int color = UiFx.withAlpha(QuestColors.COMPLETED, alpha);
        // Ring only: four 1px edges, drawn as 1:1 blits like every other frame in the book.
        MockChrome.box(graphics, x, y, w, 1, color);
        MockChrome.box(graphics, x, y + w - 1, w, 1, color);
        MockChrome.box(graphics, x, y, 1, w, color);
        MockChrome.box(graphics, x + w - 1, y, 1, w, color);
    }

    /** Short red diagonal drawn into a tile the moment it fails. */
    private void drawFailedCrack(GuiGraphics graphics, Tile tile, long startMs) {
        float p = UiFx.progress(startMs, CRACK_MS);
        if (p >= 1f) {
            return;
        }
        int[] s = screen(tile.pos().x(), tile.pos().y());
        int size = tilePx();
        int steps = Math.max(2, Math.round(size * 0.7f * p));
        int color = UiFx.withAlpha(QuestColors.FAILED, 0.85f);
        for (int i = 0; i < steps; i++) {
            int px = s[0] + 4 + i;
            int py = s[1] + size - 6 - i;
            MockChrome.box(graphics, px, py, 1, 1, color);
        }
    }

    /** Bright pip travelling from the source tile's port to the destination port when a gate opens. */
    private void drawGatePip(GuiGraphics graphics, Tile from, Tile to, int size, long startMs) {
        float p = UiFx.easeOut(UiFx.progress(startMs, PIP_MS));
        if (p >= 1f) {
            return;
        }
        int[] a = screen(from.pos().x(), from.pos().y());
        int[] b = screen(to.pos().x(), to.pos().y());
        if (!from.pos().cardinalTo(to.pos())) {
            return;
        }
        int ax = a[0] + size / 2;
        int ay = a[1] + size / 2;
        int bx = b[0] + size / 2;
        int by = b[1] + size / 2;
        int px = UiFx.lerpInt(ax, bx, p);
        int py = UiFx.lerpInt(ay, by, p);
        float alpha = 1f - p * 0.6f;
        int color = UiFx.withAlpha(QuestColors.NEW, alpha);
        MockChrome.box(graphics, px - 1, py - 1, 3, 3, color);
        MockChrome.box(graphics, px - 2, py - 2, 5, 5, UiFx.withAlpha(QuestColors.NEW, alpha * 0.35f));
    }

    /** Restrained diagonal shimmer over the GOTCHA modal chrome; text stays on top of it. */
    private void drawModalShimmer(GuiGraphics graphics, int x, int y, int w, int h) {
        if (!UiFx.enabled() || w <= 0 || h <= 0) {
            return;
        }
        long period = 2600L;
        float sweep = UiFx.phase(period, UiFx.elapsed(fxModalOpenedAt));
        int bandW = 26;
        int travel = w + h + bandW * 2;
        int head = Math.round(sweep * travel) - bandW;
        int color = UiFx.withAlpha(QuestColors.MODAL_PINK, 0.22f);
        // One 2px diagonal band; bounded work, deliberately subtle so the copy stays readable.
        for (int i = 0; i < bandW; i += 2) {
            int d = head - i;
            int sx = Math.max(x, Math.min(x + w - 2, x + d));
            int sy = Math.max(y, Math.min(y + h - 2, y + (d - w) + h));
            if (d < 0 || d > w + h) {
                continue;
            }
            MockChrome.box(graphics, sx, sy, 8, 2, color);
        }
    }

    private void drawModal(GuiGraphics graphics) {
        ModalLayout box = modalLayout();
        if (modal == ModalKind.CLAIM_ALL) {
            drawClaimAllModal(graphics, box);
            return;
        }
        tinyString(graphics, box.tag(), box.x() + 6, box.y() + 2, MockChrome.tagInk(box.edge()));
        drawCentered(graphics, box.title(), box.x(), box.y() + 16, box.w(), QuestColors.COMPLETED);
        drawCentered(graphics, box.sub(), box.x(), box.y() + 30, box.w(), QuestColors.TEXT);
        int bodyY = box.y() + 46;
        for (String row : box.body()) {
            drawCentered(graphics, row, box.x(), bodyY, box.w(), QuestColors.MUTED);
            bodyY += 10;
        }
        if (modal == ModalKind.GOTCHA) {
            MockChrome.padlock(graphics, box.x() + box.w() - 14, box.y() + 56, QuestColors.MODAL_PINK);
            // Slow diagonal sweep so the soft "no" reads as deliberate chrome, not a static error box.
            drawModalShimmer(graphics, box.x(), box.y(), box.w(), box.h());
        } else if (modal == ModalKind.XOR) {
            drawModalShimmer(graphics, box.x(), box.y(), box.w(), box.h());
        }
        drawButtonLabel(graphics, box.btnX(), box.btnY(), box.btnW(), box.btnH(), box.btn());
    }

    /** Small red padlock at the locked click, fading out over {@link #fadeLockMs}. */
    private void drawFadeLock(GuiGraphics graphics, float partialTick) {
        if (fadeLockStartTick < 0L) {
            return;
        }
        var mc = Minecraft.getInstance();
        float now = (mc.level != null ? mc.level.getGameTime() : 0L) + partialTick;
        float elapsedMs = (now - fadeLockStartTick) * 50f;
        float alpha = 1f - elapsedMs / Math.max(1, fadeLockMs);
        if (alpha <= 0f) {
            fadeLockStartTick = -1L;
            return;
        }
        int a = Math.max(0, Math.min(255, (int) (alpha * 255f)));
        int color = (a << 24) | (QuestColors.LOCKED_EDGE & 0x00FFFFFF);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 430);
        graphics.setColor(1f, 1f, 1f, alpha);
        MockChrome.pathPadlock(graphics, fadeLockX, fadeLockY, color);
        graphics.setColor(1f, 1f, 1f, 1f);
        pose.popPose();
    }

    private record ModalLayout(int x, int y, int w, int h, int btnX, int btnY, int btnW, int btnH,
                               int cancelX, int cancelW,
                               int headerW, int edge, String tag, String title, String sub, String btn,
                               List<String> body, int bodyTop, int bodyH) {
    }

    private ModalLayout modalLayout() {
        if (modal == ModalKind.CLAIM_ALL) {
            return claimAllLayout();
        }
        boolean xor = modal == ModalKind.XOR;
        int edge = xor ? QuestColors.XOR_EDGE : QuestColors.MODAL_PINK;
        String tag = xor ? "XOR QUEST COMPLETE" : "LOCKED";
        // Troi: LOCKED tab + CONGRATULATIONS was copy dissonance — GOTCHA is a soft no, XOR keeps the win line.
        String title = xor ? "CONGRATULATIONS" : "NOT YET";
        String sub = xor ? "YOU TOTALLY SMASHED THAT QUEST (YAY)" : "I BET YOU ALWAYS WIN AT PEA-KNUCKLE";
        int lockers = 0;
        if (!xor && chapter != null && modalTileId != null && !modalTileId.isEmpty()) {
            var lockedTile = chapter.tile(modalTileId);
            if (lockedTile.isPresent()) {
                lockers = ClientQuestState.incompleteParents(chapter, lockedTile.get()).size();
            }
        }
        List<String> paras = xor
                ? List.of("NOW IT'S TIME TO MAKE AN IMPORTANT DECISION",
                "THERE IS A FORK IN THE QUESTLINE, YOU MAY ONLY CHOOSE ONE DIRECTION (THE OTHER WILL BE LOCKED)")
                : (modalTileId == null || modalTileId.isEmpty())
                ? List.of("THIS CHAPTER IS STILL LOCKED", "COMPLETE THE QUESTS THAT UNLOCK IT FIRST")
                : List.of("TO UNLOCK THE NEXT QUEST YOU MUST COMPLETE ALL OF THE QUESTS LOCKING IT (" + lockers + ")");
        int w = xor ? 252 : 228;
        int inner = w - 28;
        List<String> body = new ArrayList<>();
        for (String para : paras) {
            body.addAll(wrapLines(para, inner));
        }
        String btn = xor ? "UNDERSTOOD" : "GOTCHA";
        int btnW = pillW(btn);
        int btnH = 12;
        int headerW = tabWidth(tag, w - 20);
        int h = 16 + 14 + 12 + body.size() * 10 + 10 + btnH + 12;
        // Jerry: slide modal into the board when sidebar is open — never under CHAPTERS.
        int x = boardLeft() + Math.max(8, (contentWidth() - w) / 2);
        // Right-clamp: on a narrow GUI with a wide sidebar the max(8,..) branch wins and the frame, tab
        // tail and close X would be drawn off-screen.
        x = Math.max(4, Math.min(x, Math.max(4, width - w - 4)));
        int y = (height - h) / 2;
        return new ModalLayout(x, y, w, h, x + (w - btnW) / 2, y + h - 8 - btnH, btnW, btnH,
                0, 0, headerW, edge, tag, title, sub, btn, body, 0, 0);
    }

    private boolean claimAllUnlocked(Tile tile) {
        return chapter != null && ClientQuestState.isUnlocked(chapter, tile);
    }

    private boolean claimAllShown() {
        return !authoring && !logOpen && !isIntroChapter() && chapter != null
                && !ClaimAll.grantable(chapter, ClientQuestState.progress, this::claimAllUnlocked).isEmpty();
    }

    private void openClaimAll() {
        claimAllScroll = 0;
        modal = ModalKind.CLAIM_ALL;
        modalTileId = "";
    }

    private void confirmClaimAll() {
        if (chapter != null && !ClaimAll.grantable(chapter, ClientQuestState.progress, this::claimAllUnlocked).isEmpty()) {
            QuestNetwork.sendToServer(new ClaimAllC2S(chapter.id()));
        }
        dismissModal();
    }

    private record ClaimLine(String text, ItemStack icon, boolean header) {
    }

    private List<ClaimLine> claimAllLines() {
        List<ClaimLine> lines = new ArrayList<>();
        if (chapter == null) {
            return lines;
        }
        ProgressSnapshot snap = ClientQuestState.progress;
        for (Tile tile : ClaimAll.grantable(chapter, snap, this::claimAllUnlocked)) {
            lines.add(new ClaimLine(claimAllTileName(tile), ItemStack.EMPTY, true));
            lines.addAll(claimAllPayoff(tile, false));
        }
        List<Tile> picks = ClaimAll.pickOnQuest(chapter, snap, this::claimAllUnlocked);
        if (!picks.isEmpty()) {
            lines.add(new ClaimLine("PICK ON THE QUEST", ItemStack.EMPTY, true));
            for (Tile tile : picks) {
                lines.add(new ClaimLine(claimAllTileName(tile), ItemStack.EMPTY, true));
                lines.addAll(claimAllPayoff(tile, true));
            }
        }
        return lines;
    }

    private static String claimAllTileName(Tile tile) {
        String title = tile.title().isBlank() ? tile.id() : tile.title();
        return title.toUpperCase(Locale.ROOT);
    }

    private List<ClaimLine> claimAllPayoff(Tile tile, boolean choice) {
        List<ClaimLine> lines = new ArrayList<>();
        for (Reward reward : tile.rewards()) {
            if (reward instanceof ChoiceReward pick) {
                if (choice) {
                    for (Reward option : pick.options()) {
                        lines.add(new ClaimLine(option.describe(), rewardFace(option), false));
                    }
                }
            } else {
                lines.add(new ClaimLine(reward.describe(), rewardFace(reward), false));
            }
        }
        for (ResourceLocation scroll : tile.scrolls()) {
            String name = QuestDefinitions.scroll(scroll).map(Scroll::title).filter(title -> !title.isBlank())
                    .orElse(scroll.getPath());
            lines.add(new ClaimLine("scroll  " + name, ItemStack.EMPTY, false));
        }
        if (lines.isEmpty()) {
            lines.add(new ClaimLine("no reward", ItemStack.EMPTY, false));
        }
        return lines;
    }

    private int claimAllContentH(List<ClaimLine> lines) {
        int h = 0;
        for (ClaimLine line : lines) {
            h += line.header() ? 12 : 18;
        }
        return h;
    }

    private ModalLayout claimAllLayout() {
        List<ClaimLine> lines = claimAllLines();
        int w = 268;
        int btnH = 12;
        int claimW = pillW("CLAIM");
        int cancelW = pillW("CANCEL");
        int headerBlock = 46;
        int footer = 8 + btnH + 8;
        int content = claimAllContentH(lines);
        int maxH = Math.max(headerBlock + footer + 36, Math.min(height - 24, 240));
        int bodyH = Math.max(36, Math.min(content, maxH - headerBlock - footer));
        int h = headerBlock + bodyH + footer;
        int x = boardLeft() + Math.max(8, (contentWidth() - w) / 2);
        x = Math.max(4, Math.min(x, Math.max(4, width - w - 4)));
        int y = Math.max(8, (height - h) / 2);
        int pair = claimW + 8 + cancelW;
        int claimX = x + (w - pair) / 2;
        int cancelX = claimX + claimW + 8;
        String sub = chapter == null || chapter.title().isBlank()
                ? ""
                : chapter.title().toUpperCase(Locale.ROOT);
        int headerW = tabWidth("REWARDS", w - 20);
        return new ModalLayout(x, y, w, h, claimX, y + h - 8 - btnH, claimW, btnH, cancelX, cancelW,
                headerW, QuestColors.CURRENT, "REWARDS", "CLAIM ALL", sub, "CLAIM", List.of(),
                y + headerBlock, bodyH);
    }

    private void drawClaimAllModal(GuiGraphics graphics, ModalLayout box) {
        tinyString(graphics, box.tag(), box.x() + 6, box.y() + 2, MockChrome.tagInk(box.edge()));
        drawCentered(graphics, box.title(), box.x(), box.y() + 16, box.w(), QuestColors.TEXT);
        if (!box.sub().isEmpty()) {
            drawCentered(graphics, box.sub(), box.x(), box.y() + 30, box.w(), QuestColors.MUTED);
        }
        List<ClaimLine> lines = claimAllLines();
        int content = claimAllContentH(lines);
        int maxScroll = Math.max(0, content - box.bodyH());
        claimAllScroll = Math.max(0, Math.min(claimAllScroll, maxScroll));
        int clipTop = box.bodyTop();
        int clipBottom = clipTop + box.bodyH();
        graphics.enableScissor(box.x() + 8, clipTop, box.x() + box.w() - 8, clipBottom);
        try {
            int ly = clipTop - claimAllScroll;
            for (ClaimLine line : lines) {
                int rowH = line.header() ? 12 : 18;
                if (ly + rowH >= clipTop && ly <= clipBottom) {
                    if (line.header()) {
                        graphics.drawString(font, line.text(), box.x() + 12, ly + 1, QuestColors.SIDEBAR_HEADER, false);
                    } else {
                        if (!line.icon().isEmpty()) {
                            graphics.renderItem(line.icon(), box.x() + 12, ly);
                            drawRewardCountOver(graphics, line.icon(), box.x() + 12, ly);
                        }
                        int textX = line.icon().isEmpty() ? box.x() + 12 : box.x() + 32;
                        graphics.drawString(font, ellipsize(line.text(), box.w() - (textX - box.x()) - 16),
                                textX, ly + 4, QuestColors.TEXT, false);
                    }
                }
                ly += rowH;
            }
        } finally {
            graphics.disableScissor();
        }
        if (maxScroll > 0) {
            int trackX = box.x() + box.w() - 6;
            int thumbH = Math.max(12, box.bodyH() * box.bodyH() / Math.max(content, 1));
            int thumbY = clipTop + (int) ((box.bodyH() - thumbH) * (claimAllScroll / (double) maxScroll));
            MockChrome.box(graphics, trackX, clipTop, 2, box.bodyH(), QuestColors.SIDEBAR_EDGE);
            MockChrome.box(graphics, trackX, thumbY, 2, thumbH, QuestColors.SIDEBAR_HEADER);
        }
        drawButtonLabel(graphics, box.btnX(), box.btnY(), box.btnW(), box.btnH(), box.btn());
        drawButtonLabel(graphics, box.cancelX(), box.btnY(), box.cancelW(), box.btnH(), "CANCEL", QuestColors.TEXT);
    }


    /** Jerry: amount as Nx painted over the reward icon (readable). */
    private void drawRewardCountOver(GuiGraphics graphics, ItemStack face, int ix, int iy) {
        if (face == null || face.isEmpty() || face.getCount() <= 1) {
            return;
        }
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 200);
        tinyString(graphics, face.getCount() + "x", ix + 1, iy + 10, QuestColors.TEXT);
        pose.popPose();
    }
    private void drawCentered(GuiGraphics graphics, String text, int x, int y, int w, int color) {
        int tw = font.width(text);
        graphics.drawString(font, text, x + Math.max(8, (w - tw) / 2), y, color, false);
    }

    private static int borderColor(TileVisual visual) {
        return switch (visual) {
            case CURRENT -> QuestColors.CURRENT;
            case NEW -> QuestColors.NEW;
            case EDIT -> QuestColors.EDIT;
            case COMPLETED -> QuestColors.COMPLETED;
            case FAILED -> QuestColors.FAILED;
            case CLOSED -> QuestColors.CLOSED;
            case LOCKED -> QuestColors.LOCKED_EDGE;
        };
    }

    /** 1 Minecraft hour = 1000 ticks. NEW stays blue; label becomes OPEN after this. */
    private static final long NEW_TO_OPEN_TICKS = 1000L;

    private String headerLabel(TileVisual visual) {
        return headerLabel(visual, null);
    }

    private String headerLabel(TileVisual visual, Tile tile) {
        return switch (visual) {
            case CURRENT -> "CURRENT";
            case NEW -> (tile != null && ClientQuestState.isNewStale(chapter, tile, NEW_TO_OPEN_TICKS)) ? "OPEN" : "NEW";
            case EDIT -> "EDIT";
            case COMPLETED -> "COMPLETED";
            case FAILED -> "FAILED";
            case CLOSED -> "CLOSED";
            case LOCKED -> "LOCKED";
        };
    }

    private static long packPos(int x, int y) {
        return (((long) x) << 32) ^ (y & 0xffffffffL);
    }

    private void drawLink(GuiGraphics graphics, Link link, Tile from, Tile to, boolean highlight) {
        int[] a = screen(from.pos().x(), from.pos().y());
        int[] b = screen(to.pos().x(), to.pos().y());
        int dx = Integer.compare(to.pos().x(), from.pos().x());
        int dy = Integer.compare(to.pos().y(), from.pos().y());
        int size = tilePx();
        int x1 = a[0] + (dx > 0 ? size : dx < 0 ? 0 : size / 2);
        int y1 = a[1] + (dy > 0 ? size : dy < 0 ? 0 : size / 2);
        int x2 = b[0] + (dx > 0 ? 0 : dx < 0 ? size : size / 2);
        int y2 = b[1] + (dy > 0 ? 0 : dy < 0 ? size : size / 2);
        int color = highlight ? QuestColors.EDIT : gateColor(link.gate().op());
        int dashW = Math.max(1, font.width("-"));
        int lh = Math.max(8, font.lineHeight);
        if (x1 == x2) {
            int top = Math.min(y1, y2);
            int bot = Math.max(y1, y2);
            for (int y = top; y + lh <= bot; y += lh) {
                graphics.drawString(font, "|", x1 - 1, y, color, true);
            }
        } else if (y1 == y2) {
            int left = Math.min(x1, x2);
            int gap = Math.abs(x2 - x1);
            int span = Math.max(1, gap / dashW);
            String dashes = "-".repeat(span);
            while (span > 1 && font.width(dashes) > gap) {
                span--;
                dashes = "-".repeat(span);
            }
            graphics.drawString(font, dashes, left, y1 - 4, color, true);
        } else {
            int left = Math.min(x1, x2);
            int gap = Math.abs(x2 - x1);
            int span = Math.max(1, gap / dashW);
            String dashes = "-".repeat(span);
            while (span > 1 && font.width(dashes) > gap) {
                span--;
                dashes = "-".repeat(span);
            }
            graphics.drawString(font, dashes, left, y1 - 4, color, true);
            int top = Math.min(y1, y2);
            int bot = Math.max(y1, y2);
            for (int y = top; y + lh <= bot; y += lh) {
                graphics.drawString(font, "|", x2 - 1, y, color, true);
            }
        }
        if (authoring || zoom >= 1.5f) {
            int mx = x1 == x2 ? x1 + 4 : (x1 + x2) / 2 - 6;
            int my = y1 == y2 ? y1 - 8 : y1 - 8;
            graphics.drawString(font, link.gate().op().getSerializedName().toUpperCase(Locale.ROOT), mx, my, color, true);
        }
    }

    /** Data D3: LOCKED expand glyph off everywhere, including authoring. */
    static boolean showExpandGlyph(TileVisual visual, int edge) {
        return edge != 0 && visual != TileVisual.LOCKED;
    }

    private static int gateColor(GateOp op) {
        return switch (op) {
            case AND -> QuestColors.GATE_AND;
            case OR -> QuestColors.GATE_OR;
            case XOR -> QuestColors.GATE_XOR;
            case NOT -> QuestColors.GATE_NOT;
        };
    }

    private static void drawThickH(GuiGraphics graphics, int y, int x1, int x2, int t, int color) {
        int left = Math.min(x1, x2);
        int right = Math.max(x1, x2);
        UiDraw.fill(graphics, left, y - t / 2, right + 1, y - t / 2 + t, color);
    }

    private static void drawThickV(GuiGraphics graphics, int x, int y1, int y2, int t, int color) {
        int top = Math.min(y1, y2);
        int bottom = Math.max(y1, y2);
        UiDraw.fill(graphics, x - t / 2, top, x - t / 2 + t, bottom + 1, color);
    }

    private static void drawArrowHead(GuiGraphics graphics, int x, int y, int dx, int dy, int color) {
        if (dx == 0 && dy == 0) {
            return;
        }
        for (int i = 0; i < 5; i++) {
            int px = x - dx * i;
            int py = y - dy * i;
            int spread = i;
            if (dx != 0) {
                UiDraw.fill(graphics, px, py - spread, px + 1, py + spread + 1, color);
            } else {
                UiDraw.fill(graphics, px - spread, py, px + spread + 1, py + 1, color);
            }
        }
    }

    private void drawExpanded(GuiGraphics graphics, Tile tile, int mouseX, int mouseY) {
        TileVisual visual = authoring ? TileVisual.EDIT : ClientQuestState.visual(chapter, tile, false, tile.id());
        int color = visual == TileVisual.EDIT ? QuestColors.EDIT : borderColor(visual);
        if (color == 0) {
            color = QuestColors.CLOSED;
        }
        int w = cardW();
        int h = cardH();
        int x = cardX();
        int y = cardY();
        String header = visual == TileVisual.EDIT ? "EDIT MODE" : headerLabel(visual, tile);
        // Tag text (close X is drawn by overlay widget). Inset with panel status tab (FRAME).
        tinyString(graphics, header, x + 4 + MockChrome.FRAME, y + 2 + MockChrome.FRAME, MockChrome.tagInk(color));

        if (authoring) {
            drawExpandedAuthor(graphics, tile, x, y, w, h, mouseX, mouseY);
            return;
        }

        // Play inspector: mock compact card — title, body, then objective + rewards footer.
        // Clip to the card: cardH() is capped, so uncapped text used to spill over the reward row, the
        // action bar and the board tiles below. try/finally so a throw inside the card body cannot leak an
        // enabled scissor into the rest of the frame (sidebar, modal, log, HUD).
        graphics.enableScissor(x, y, x + w, y + h);
        try {
            drawCardPlay(graphics, tile, x, y, w, h, mouseX, mouseY);
        } finally {
            graphics.disableScissor();
        }
    }

    /** The play-mode inspect card contents, drawn inside the card's clip rect. */
    private void drawCardPlay(GuiGraphics graphics, Tile tile, int x, int y, int w, int h, int mouseX, int mouseY) {
        String title = tile.title().isBlank() ? tile.id().toUpperCase(Locale.ROOT) : tile.title().toUpperCase(Locale.ROOT);
        int cursor = drawWrapped(graphics, title, x + 10, y + 14, w - 20, QuestColors.TEXT);
        String body = inspectBody(tile);
        int titlePush = cursor - y;
        // Rows we intend to show, never more than fit above the action bar. Computed from the shared helper
        // so the body budget, the draw and inspectFootY cannot drift apart.
        int taskSlots = cardTaskSlots(tile, cursor, y, h);
        lastBodyBudget = bodyLineBudget(h, taskSlots, titlePush);
        cursor = drawWrapped(graphics, body, x + 10, cursor + 4, w - 20, QuestColors.MUTED,
                lastBodyBudget) + 6;
        // Jerry: no Rewards: prose — counts live on icons as Nx


        String questId = chapter.id() + "/" + tile.id();
        // Keep REWARDS/icons below body: when cursor wins, leave an 8px band for the label
        // (footY-6) so title/body never paint through REWARDS.
        int footY = Math.max(cursor + 8, y + h - 40);
        taskIconHits.clear();
        // Reset every frame: a tile with no tasks (or none drawn) must not leave the previous tile's count
        // behind, or the click handler would offer rows that are not on screen.
        drawnTaskRows = 0;
        if (taskSlots > 0) {
            int shownTasks = taskSlots;
            int blockTop = Math.max(y + 16, y + h - 42 - shownTasks * 12);
            drawnTaskRows = shownTasks;
            int rowY = blockTop;
            tinyString(graphics, "TASK", x + 10, rowY - 9, QuestColors.MUTED);
            boolean claimed = ClientQuestState.rewardsClaimed(chapter, tile);
            for (int i = 0; i < shownTasks; i++) {
                Task task = tile.tasks().get(i);
                final int thisRowY = rowY;
                boolean done = ClientQuestState.progress.taskCompleted(questId, Integer.toString(i));
                String label = ClientQuestState.taskProgressLabel(chapter, tile, i);
                // A tag task accepts ANY member of a tag — minecraft:logs is 199 items — so drawing one
                // representative icon reads as "bring me oak logs" and hides the task from a player whose
                // tree is a different mod's. Draw a small mosaic of up to six members plus a "+N" chip, the
                // way JEI/EMI render a tag ingredient. The strip yields to the label, never the other way
                // round, so a long verb can never be ellipsized to make room for icons.
                List<Item> tagItems = taskTagItems(task);
                int naturalLabel = font.width(label);
                int stripBudget = labelStripBudget(x, w, naturalLabel);
                int icons = mosaicIcons(tagItems.size(), stripBudget);
                int overflow = mosaicOverflow(tagItems.size(), icons);
                if (overflow > 0) {
                    // The "+N" chip shares the strip, so take its width off the budget and re-fit.
                    int withChip = Math.max(0, stripBudget - overflowChipSpan(overflow));
                    icons = mosaicIcons(tagItems.size(), withChip);
                    overflow = mosaicOverflow(tagItems.size(), icons);
                }
                int textX;
                if (icons > 0) {
                    int stripY = thisRowY + MOSAIC_INSET_Y;
                    var pose = graphics.pose();
                    pose.pushPose();
                    pose.translate(x + STRIP_X, stripY, 0f);
                    // Half scale, so a cell is MOSAIC_CELL px on screen and the pitch doubles in pose units.
                    pose.scale(0.5f, 0.5f, 1f);
                    for (int m = 0; m < icons; m++) {
                        graphics.renderItem(new ItemStack(tagItems.get(m)), m * MOSAIC_PITCH * 2, 0);
                    }
                    pose.popPose();
                    int span = mosaicStripWidth(icons);
                    if (overflow > 0) {
                        tinyString(graphics, "+" + overflow, x + STRIP_X + span + 1, stripY + 1, QuestColors.MUTED);
                        span += overflowChipSpan(overflow);
                    }
                    if (task.required() > 1) {
                        // Count badge sits on the strip's own baseline, matching the single-icon convention.
                        drawCountOver(graphics, task.required(), x + STRIP_X + 1, stripY + 5);
                    }
                    List<ItemStack> faces = tagItems.subList(0, icons).stream()
                            .map(item -> new ItemStack(item, 1))
                            .toList();
                    taskIconHits.add(new TaskIconHit(x + STRIP_X, stripY, span, MOSAIC_CELL, i, faces,
                            tagItems.size()));
                    textX = x + STRIP_X + span + STRIP_LABEL_GAP;
                } else {
                    // Every task gets its own target icon so a multi-item quest is readable at a glance and
                    // each target can be hovered for its name and clicked through to JEI.
                    ItemStack stack = taskStack(task);
                    if (!stack.isEmpty()) {
                        graphics.renderItem(stack, x + STRIP_X, thisRowY);
                        // Count badge for "get N", matching the reward icon convention.
                        if (task.required() > 1) {
                            drawRewardCountOver(graphics, stack, x + STRIP_X, thisRowY);
                        }
                        taskIconHits.add(new TaskIconHit(x + STRIP_X, thisRowY, 16, 16, i, List.of(stack), 0));
                    } else {
                        // No concrete item (kill/raid/stat/...): fall back to the tile icon on the first row.
                        Optional<String> glyph = tile.icon().flatMap(Icon::glyphId);
                        if (i == 0 && glyph.isPresent()) {
                            QuestGlyphs.draw(graphics, glyph.get(), x + STRIP_X, thisRowY, QuestColors.TEXT);
                        } else if (i == 0) {
                            tile.icon().flatMap(Icon::item).flatMap(BuiltInRegistries.ITEM::getOptional)
                                    .ifPresent(item -> graphics.renderItem(new ItemStack(item), x + STRIP_X, thisRowY));
                        }
                    }
                    textX = x + (stack.isEmpty() && i > 0 ? STRIP_X : 28);
                }
                // Keep the label clear of the SUBMIT/DONE pill on the right. The floor is the sidebar
                // label minimum: ellipsize() returns "" below it, so a smaller floor would silently
                // delete the label instead of trimming it.
                int labelW = Math.max(SIDEBAR_LABEL_MIN, (x + w - LABEL_RIGHT_MARGIN) - textX);
                pixel(graphics, Component.literal(ellipsize(label, labelW)), textX, thisRowY + 4,
                        claimed || done ? QuestColors.CURRENT : QuestColors.TEXT);
                if (task.required() > 1) {
                    drawTaskFill(graphics, questId, i, task.required(), textX, thisRowY + 12, Math.min(labelW, 64));
                }
                rowY += 12;
            }
        }
        if (!tile.rewards().isEmpty()) {
            List<ItemStack> faces = collectRewardFaces(tile);
            int show = Math.max(1, Math.min(faces.size(), 6));
            int rowW = show * 17;
            // "REWARDS" shares its band with the last task label, and the band does not move with the row
            // count, so the collision is purely horizontal. Test the actual extents rather than whether the
            // task block was truncated — the old test hid the caption on most cards and still overprinted
            // on the wide ones.
            int captionX = x + w - Math.max(48, rowW + 10);
            int lastRowY = y + h - 54;
            // The caption is ~21px wide at 0.5 scale and the label is clipped to end at x+w-74, so they can
            // only overprint in the window between. Suppress just that overlap instead of the whole caption.
            boolean captionCollides = drawnTaskRows > 0
                    && captionX < x + w - 72
                    && captionX + 22 > x + w - 74 - 60;
            if (!captionCollides) {
                tinyString(graphics, "REWARDS", captionX, footY - 6, QuestColors.MUTED);
            }
            if (faces.isEmpty()) {
                ItemStack one = rewardFace(tile.rewards().getFirst());
                graphics.renderItem(one, x + w - 26, footY);
                drawRewardCountOver(graphics, one, x + w - 26, footY);
            } else {
                for (int i = 0; i < show; i++) {
                    ItemStack face = faces.get(show - 1 - i);
                    int ox = x + w - 12 - 16 - i * 17;
                    graphics.renderItem(face, ox, footY);
                    drawRewardCountOver(graphics, face, ox, footY);
                }
            }
        }
        PlayBar bar = playBar(tile, x, y, w, h);
        if (bar.action()) {
            drawButtonLabel(graphics, bar.actionX(), bar.y(), bar.actionW(), 12, bar.actionLabel());
            // Breathing highlight while there is something to claim/turn in, so the one actionable
            // button on the card draws the eye. Alpha only — the button colour stays semantic.
            if (UiFx.enabled()) {
                float breath = UiFx.wave(1900L, 0L);
                int halo = UiFx.withAlpha(QuestColors.CURRENT, 0.18f + 0.32f * breath);
                MockChrome.box(graphics, bar.actionX() - 1, bar.y() - 1, bar.actionW() + 2, 1, halo);
                MockChrome.box(graphics, bar.actionX() - 1, bar.y() + 12, bar.actionW() + 2, 1, halo);
                MockChrome.box(graphics, bar.actionX() - 1, bar.y() - 1, 1, 13, halo);
                MockChrome.box(graphics, bar.actionX() + bar.actionW(), bar.y() - 1, 1, 13, halo);
            }
            if ("CLAIM".equals(bar.actionLabel())) {
                drawClaimBracket(graphics, bar.actionX(), bar.y(), bar.actionW(), 12);
            }
        } else if (bar.claimedBadge()) {
            drawButtonLabel(graphics, bar.actionX(), bar.y(), bar.actionW(), 12, "CLAIMED");
        }
        if (bar.choice()) {
            if (UiFx.enabled() && fxChoiceAt >= 0L && fxChoiceTileId.equals(tile.id())) {
                float cp = UiFx.easeOut(UiFx.progress(fxChoiceAt, CHOICE_MS));
                int halo = UiFx.withAlpha(QuestColors.CURRENT, (0.35f + 0.45f * (1f - cp)) * Math.max(0.2f, 1f - cp * 0.4f));
                MockChrome.box(graphics, bar.choiceAX() - 1, bar.y() - 1, bar.choiceW() + 2, 14, halo);
                MockChrome.box(graphics, bar.choiceBX() - 1, bar.y() - 1, bar.choiceW() + 2, 14,
                        UiFx.withAlpha(QuestColors.EDIT, (0.35f + 0.45f * (1f - cp)) * Math.max(0.2f, 1f - cp * 0.4f)));
            }
            drawButtonLabel(graphics, bar.choiceAX(), bar.y(), bar.choiceW(), 12, "TAKE A");
            drawButtonLabel(graphics, bar.choiceBX(), bar.y(), bar.choiceW(), 12, "TAKE B");
        }
        if (bar.pin()) {
            drawPinButton(graphics, bar);
        }
    }

    /**
     * How many 10px body lines fit between the title and the footer inside a card of height {@code h} that
     * will draw {@code taskRows} task rows. Reserving per-card rather than always for {@link #MAX_TASK_ROWS}
     * stops the body being needlessly truncated on the common single-task quest. {@code BODY_RESERVED_PX} is
     * the title block, the reward row, the action bar and the 10px TASK caption.
     */
    private static final int BODY_RESERVED_PX = 20 + 4 + 36 + 18 + 10;

    private void drawTaskFill(GuiGraphics graphics, String questId, int taskIndex, int required, int x, int y, int maxW) {
        int need = Math.max(1, required);
        int value = ClientQuestState.progress.value(questId, Integer.toString(taskIndex));
        float target = UiFx.clamp01(value / (float) need);
        String key = questId + "/" + taskIndex;
        float shown = fxTaskFill.getOrDefault(key, target);
        if (UiFx.enabled()) {
            // Approach the live value each frame so SUBMIT ticks feel continuous rather than stepped.
            shown = UiFx.lerp(shown, target, UiFx.clamp01(16f / TASK_FILL_MS));
        } else {
            shown = target;
        }
        fxTaskFill.put(key, shown);
        int trackW = Math.max(8, maxW);
        int fillW = Math.max(0, Math.round(trackW * shown));
        MockChrome.box(graphics, x, y, trackW, 1, UiFx.withAlpha(QuestColors.MUTED, 0.35f));
        if (fillW > 0) {
            MockChrome.box(graphics, x, y, fillW, 1, UiFx.withAlpha(QuestColors.CURRENT, 0.85f));
        }
    }

    private void drawInspectTooltips(GuiGraphics graphics, int mouseX, int mouseY) {
        if (authoring) {
            return;
        }
        chapter.tile(inspectTileId()).ifPresent(tile -> {
            if (drawTaskItemTooltip(graphics, mouseX, mouseY)) {
                return;
            }
            if (drawRewardItemTooltip(graphics, tile, mouseX, mouseY)) {
                return;
            }
            drawDoneTooltip(graphics, tile, mouseX, mouseY);
        });
    }

    /**
     * Hovering a task's target icon names it, exactly as a reward icon does. The hint line tells the player
     * that clicking goes to JEI, so the affordance is discoverable without a keybind legend.
     *
     * <p>A tag row is named by how many members the task accepts and which of them are on screen, because
     * the icon alone only ever shows the first few: "Any of 199 items" / "Oak Log, Birch Log, …, …".
     */
    private boolean drawTaskItemTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        for (TaskIconHit hit : taskIconHits) {
            if (!hit.over(mouseX, mouseY) || hit.stack().isEmpty()) {
                continue;
            }
            List<Component> lines = new ArrayList<>();
            if (hit.isTag()) {
                lines.add(Component.literal(
                        TaskVerbs.translate("questqueen.tag.any", "Any of %s items", hit.tagMembers())));
                List<String> names = hit.stacks().stream().map(stack -> stack.getHoverName().getString()).toList();
                String sample = tagSampleLine(names, hit.overflow());
                if (!sample.isEmpty()) {
                    lines.add(Component.literal(sample).withStyle(ChatFormatting.GRAY));
                }
            } else {
                lines.add(hit.stack().getHoverName());
            }
            if (RecipeViewer.available()) {
                Optional<Task> task = taskAtIndex(hit.taskIndex());
                boolean recipe = task.map(QuestBookScreen::opensRecipe).orElse(false);
                String hint = hit.isTag()
                        ? TaskVerbs.translate("questqueen.tag.hint", "Click: look one of them up in %s", RecipeViewer.label())
                        : (recipe ? "Click: how to make it" : "Click: where it comes from");
                lines.add(Component.literal(hint).withStyle(ChatFormatting.DARK_GRAY));
            }
            graphics.renderTooltip(font, lines, Optional.empty(), mouseX, mouseY);
            return true;
        }
        return false;
    }

    /**
     * The sample line under a tag tooltip: the members actually painted, with an ellipsis when some were
     * left off. Returns "" when the row painted nothing, so an empty line is never added.
     */
    static String tagSampleLine(List<String> shownNames, int hidden) {
        if (shownNames == null || shownNames.isEmpty()) {
            return "";
        }
        String joined = String.join(", ", shownNames);
        return hidden > 0 ? joined + ", …" : joined;
    }

    private Optional<Task> taskAtIndex(int taskIndex) {
        return chapter.tile(inspectTileId())
                .filter(tile -> taskIndex >= 0 && taskIndex < tile.tasks().size())
                .map(tile -> tile.tasks().get(taskIndex));
    }

    /** Open the clicked task's target in the pack's recipe viewer: recipes for "make/find", usages otherwise. */
    private void openInJei(int taskIndex) {
        Optional<Task> task = taskAtIndex(taskIndex);
        if (task.isEmpty()) {
            return;
        }
        ItemStack stack = taskStack(task.get());
        if (stack.isEmpty()) {
            return;
        }
        if (!RecipeViewer.show(stack, opensRecipe(task.get())) && minecraft != null && minecraft.player != null) {
            String name = RecipeViewer.label();
            minecraft.player.displayClientMessage(
                    Component.literal(RecipeViewer.loaded()
                            ? name + " could not open that item right now."
                            : "Install EMI, REI, or JEI to look up how to make this."),
                    true);
        }
    }

    private boolean drawRewardItemTooltip(GuiGraphics graphics, Tile tile, int mouseX, int mouseY) {
        int x = cardX();
        int y = cardY();
        int w = cardW();
        int h = cardH();
        int footY = inspectFootY(tile, x, y, w, h);
        List<RewardSlot> slots = collectRewardSlots(tile);
        if (slots.isEmpty()) {
            if (tile.rewards().isEmpty()) {
                return false;
            }
            Reward first = tile.rewards().getFirst();
            ItemStack one = rewardFace(first);
            if (one.isEmpty() || !over(x + w - 26, footY, 16, 16, mouseX, mouseY)) {
                return false;
            }
            showRewardHover(graphics, first, one, mouseX, mouseY, x + w - 26, footY, y);
            return true;
        }
        int show = Math.max(1, Math.min(slots.size(), 6));
        for (int i = 0; i < show; i++) {
            RewardSlot slot = slots.get(show - 1 - i);
            int ox = x + w - 12 - 16 - i * 17;
            if (over(ox, footY, 16, 16, mouseX, mouseY)) {
                showRewardHover(graphics, slot.reward(), slot.face(), mouseX, mouseY, ox, footY, y);
                return true;
            }
        }
        return false;
    }

    private void showRewardHover(GuiGraphics graphics, Reward reward, ItemStack face, int mouseX, int mouseY,
                                 int iconX, int iconY, int cardTop) {
        if (reward.itemId().isPresent() && !face.isEmpty()) {
            graphics.renderTooltip(font, face, mouseX, mouseY);
            return;
        }
        drawCompactTooltip(graphics, Component.literal(reward.describe()), 140, iconX, cardTop + 14, iconY);
    }

    private void drawDoneTooltip(GuiGraphics graphics, Tile tile, int mouseX, int mouseY) {
        int x = cardX();
        int y = cardY();
        int w = cardW();
        int h = cardH();
        PlayBar bar = playBar(tile, x, y, w, h);
        boolean onButton = bar.action() && over(bar.actionX(), bar.y(), bar.actionW(), 12, mouseX, mouseY);
        if (!onButton) {
            return;
        }
        boolean tileDone = ClientQuestState.progress.tileCompleted(chapter.id().toString(), tile.id());
        Component tip = Component.translatable(
                tileDone ? "questqueen.done.claim_tooltip" : "questqueen.done.tooltip");
        int rewardLeft = x + w - 8;
        if (!tile.rewards().isEmpty()) {
            int show = Math.max(1, Math.min(collectRewardSlots(tile).size(), 6));
            rewardLeft = x + w - 12 - show * 17 - 6;
        }
        int wrap = Math.max(80, Math.min(160, rewardLeft - bar.actionX() - 6));
        drawCompactTooltip(graphics, tip, wrap, bar.actionX(), y + 14, bar.y());
    }

    private void drawCompactTooltip(GuiGraphics graphics, Component tip, int wrap, int tx, int minY, int anchorY) {
        List<FormattedCharSequence> lines = font.split(tip, wrap);
        int tw = 0;
        for (FormattedCharSequence line : lines) {
            tw = Math.max(tw, font.width(line));
        }
        int th = lines.size() * 10 + 6;
        int ty = anchorY - th - 4;
        if (ty < minY) {
            ty = minY;
        }
        MockChrome.box(graphics, tx - 3, ty - 3, tw + 6, th, QuestColors.CARD);
        MockChrome.frame(graphics, tx - 3, ty - 3, tw + 6, th, QuestColors.SIDEBAR_EDGE);
        int ly = ty;
        for (FormattedCharSequence line : lines) {
            graphics.drawString(font, line, tx, ly, QuestColors.TEXT, false);
            ly += 10;
        }
    }

    private boolean claimable(Tile tile) {
        PlayBar bar = playBar(tile, 0, 0, 200, 80);
        return bar.action();
    }

    private boolean overClaimSurface(Tile tile, int x, int y, int w, int h, double mouseX, double mouseY) {
        if (!claimable(tile)) {
            return false;
        }
        // Never treat the JEI target icon as a claim click.
        for (TaskIconHit hit : taskIconHits) {
            if (hit.over(mouseX, mouseY)) {
                return false;
            }
        }
        int footY = inspectFootY(tile, x, y, w, h);
        if (!tile.tasks().isEmpty() && over(x + 28, footY - 2, Math.max(56, w / 2 - 34), 18, mouseX, mouseY)) {
            return true;
        }
        if (!tile.rewards().isEmpty()) {
            int show = Math.max(1, Math.min(collectRewardFaces(tile).size(), 6));
            int rowW = show * 17 + 8;
            return over(x + w - 12 - rowW, footY - 2, rowW, 18, mouseX, mouseY);
        }
        return false;
    }

    /**
     * Y of the reward footer / claim band for the inspect card. Must mirror {@link #drawCardPlay} exactly,
     * including the body line cap, or the claim surface and reward tooltips sit at a different height than
     * the icons they belong to. Title case matters too: the draw uses {@code toUpperCase}, which wraps
     * wider than the raw title.
     */
    private int inspectFootY(Tile tile, int x, int y, int w, int h) {
        String title = tile.title().isBlank() ? tile.id() : tile.title();
        String upper = title.toUpperCase(Locale.ROOT);
        int titlePush = 14 + wrapLineCount(upper, w - 20) * 10;
        int cursorAfterTitle = y + titlePush;
        int taskSlots = cardTaskSlots(tile, cursorAfterTitle, y, h);
        int bodyLines = drawWrappedLineCount(inspectBody(tile), w - 20,
                bodyLineBudget(h, taskSlots, titlePush));
        return Math.max(y + titlePush + 4 + bodyLines * 10 + 6 + 8, y + h - 40);
    }

    /** Wrapped line count after the {@code maxLines} cap used by {@link #drawWrapped}. */
    private int drawWrappedLineCount(String text, int max, int maxLines) {
        return Math.min(Math.max(1, wrapLines(text, max).size()), Math.max(1, maxLines));
    }

    /**
     * Task rows the card will actually draw: bounded by the task count, {@link #MAX_TASK_ROWS}, and the
     * space left above the action bar. Shared by the body budget and the draw so they cannot drift.
     */
    private static int cardTaskSlots(Tile tile, int cursorAfterTitle, int y, int h) {
        if (tile.tasks().isEmpty()) {
            return 0;
        }
        return Math.min(Math.min(tile.tasks().size(), MAX_TASK_ROWS),
                Math.max(0, (y + h - 42 - Math.max(cursorAfterTitle + 2, y + 16)) / 12));
    }

    private void drawPinButton(GuiGraphics graphics, PlayBar bar) {
        boolean pinned = bar.pinned();
        int ink = pinned ? MockChrome.INK : QuestColors.TEXT;
        if (UiFx.enabled() && fxPinFlashAt >= 0L) {
            float flash = 1f - UiFx.progress(fxPinFlashAt, PIN_FLASH_MS);
            ink = UiFx.withAlpha(QuestColors.EDIT, 0.45f + 0.55f * flash);
        }
        // Chip face is drawn by overlay; label + icon sit on top.
        MockChrome.pinIcon(graphics, bar.pinX() + 3, bar.y() + 1, ink);
        String label = pinned || fxPinFlashPinned && fxPinFlashAt >= 0L ? "PINNED" : "PIN";
        if (UiFx.enabled() && fxPinFlashAt >= 0L && !UiFx.finished(fxPinFlashAt, PIN_FLASH_MS / 2)) {
            label = fxPinFlashPinned ? "PINNED" : "PIN";
        }
        int textW = font.width(label);
        int tx = bar.pinX() + Math.max(12, (bar.pinW() - textW + 8) / 2);
        graphics.drawString(font, label, tx, bar.y() + Math.max(1, (12 - 8) / 2), ink, false);
    }

    private record PlayBar(boolean action, boolean choice, boolean pin, boolean pinned, String actionLabel, boolean claimedBadge,
                           int y, int actionX, int actionW,
                           int choiceAX, int choiceBX, int choiceW, int pinX, int pinW) {
    }

    private boolean inspectPinned() {
        return chapter.tile(inspectTileId()).map(this::isPinnedTile).orElse(false);
    }

    private boolean pinAllowed(Tile tile) {
        TileVisual visual = ClientQuestState.visual(chapter, tile, false, tile.id());
        return visual != TileVisual.COMPLETED && visual != TileVisual.FAILED
                && visual != TileVisual.LOCKED && visual != TileVisual.CLOSED;
    }

    /**
     * False for a card the player may look at but not act on — LOCKED, XOR-CLOSED, or still concealed.
     * Such a card must not offer SUBMIT / CLAIM / TAKE A / PIN: the server refuses all of them, so the
     * buttons looked live and silently did nothing (and, before the guard in {@code TaskHooks.trySubmit},
     * cost the player their items).
     */
    private boolean tileInteractive(Tile tile) {
        if (authoring) {
            return true;
        }
        TileVisual visual = ClientQuestState.visual(chapter, tile, false, tile.id());
        return visual != TileVisual.LOCKED && visual != TileVisual.CLOSED
                && !ClientQuestState.isConcealed(chapter, tile);
    }

    private PlayBar playBar(Tile tile, int x, int y, int w, int h) {
        if (!tileInteractive(tile)) {
            return new PlayBar(false, false, false, false, "", false, y + h - 16, x + 10, 0, x + 10, x + 14, 0, x + 10, 0);
        }
        String questId = chapter.id() + "/" + tile.id();
        boolean tileDone = ClientQuestState.progress.tileCompleted(chapter.id().toString(), tile.id());
        boolean claimed = ClientQuestState.progress.taskCompleted(questId, "choice");
        boolean rewardsClaimed = ClientQuestState.progress.taskCompleted(questId, "claimed");
        boolean action = false;
        String label = "SUBMIT";
        for (int i = 0; i < tile.tasks().size(); i++) {
            Task task = tile.tasks().get(i);
            if (needsClaim(task.type()) && !ClientQuestState.progress.taskCompleted(questId, Integer.toString(i))) {
                action = true;
                label = actionLabel(task.type());
                break;
            }
        }
        boolean hasLoot = !tile.rewards().isEmpty() || !tile.scrolls().isEmpty();
        boolean pendingChoice = tileDone && !claimed && tile.rewards().stream().anyMatch(reward -> reward instanceof ChoiceReward);
        // Choice tiles use TAKE A / TAKE B only — never a CLAIM that would hide the picker.
        if (!action && tileDone && hasLoot && !rewardsClaimed && !pendingChoice) {
            action = true;
            label = "CLAIM";
        }
        boolean choice = pendingChoice;
        boolean claimedBadge = tileDone && hasLoot && rewardsClaimed && !pendingChoice;
        boolean pinned = isPinnedTile(tile);
        boolean pin = pinAllowed(tile) || pinned;
        int actionW = pillW(claimedBadge && !action ? "CLAIMED" : label);
        int choiceW = pillW("TAKE A");
        int pinW = pillW(pinned ? "PINNED" : "PIN") + 10;
        int pinX = x + 10;
        if (action || claimedBadge) {
            pinX = x + 12 + actionW;
        } else if (choice) {
            pinX = x + 16 + choiceW * 2;
        }
        return new PlayBar(action, choice, pin, pinned, label, claimedBadge, y + h - 16, x + 10, actionW, x + 10, x + 14 + choiceW, choiceW, pinX,
                pinW);
    }

    private void drawExpandedAuthor(GuiGraphics graphics, Tile tile, int x, int y, int w, int h, int mouseX, int mouseY) {
        // EDIT MODE mock: yellow title, description, task chips, circled reward.
        String title = editingTitle ? titleBuffer + "_" : (tile.title().isBlank() ? "TITLE" : tile.title().toUpperCase(Locale.ROOT));
        pixel(graphics, Component.literal(title), x + 10, y + 16, QuestColors.COMPLETED);
        String body = editingBody ? bodyBuffer + "_" : (tile.description().isBlank() ? "Click to describe this quest." : tile.description());
        int bodyEnd = drawWrapped(graphics, body.toUpperCase(Locale.ROOT), x + 10, y + 32, w - 20, QuestColors.EDIT);

        int taskY = Math.max(bodyEnd + 8, y + 62);
        if (!tile.tasks().isEmpty()) {
            Task task = tile.tasks().getFirst();
            // Mock-style field chips: TYPE | COUNT | TARGET
            drawOutlinedButton(graphics, x + 10, taskY, 72, 14, task.type().toUpperCase(Locale.ROOT), QuestColors.EDIT);
            drawOutlinedButton(graphics, x + 86, taskY, 48, 14, "x" + Math.max(1, task.required()), QuestColors.EDIT);
            final int iconY = taskY - 1;
            task.itemId().flatMap(BuiltInRegistries.ITEM::getOptional).ifPresent(item ->
                    graphics.renderItem(new ItemStack(item), x + 140, iconY));
            taskY += 18;
            pixel(graphics, Component.literal(task.describe().toUpperCase(Locale.ROOT)), x + 10, taskY, QuestColors.TEXT);
            taskY += 14;
        }
        drawButton(graphics, x + 10, taskY, 56, 12, "+TASK", QuestColors.EDIT);
        taskY += 18;

        pixel(graphics, Component.literal("REWARD"), x + 10, taskY, QuestColors.MUTED);
        taskY += 12;
        if (!tile.rewards().isEmpty()) {
            Reward reward = tile.rewards().getFirst();
            int cx = x + 26;
            int cy = taskY + 10;
            MockChrome.box(graphics, cx - 12, cy - 12, 24, 24, QuestColors.CARD);
            MockChrome.frame(graphics, cx - 12, cy - 12, 24, 24, QuestColors.EDIT);
            MockChrome.box(graphics, cx - 10, cy - 1, 20, 2, QuestColors.EDIT);
            graphics.renderItem(rewardFace(reward), cx - 8, cy - 8);
            pixel(graphics, Component.literal(reward.describe().toUpperCase(Locale.ROOT)), x + 48, taskY + 6, QuestColors.TEXT);
            taskY += 28;
        } else {
            drawButton(graphics, x + 10, taskY, 72, 12, "+REWARD", QuestColors.EDIT);
            taskY += 16;
        }

        taskY = drawQuestlineSection(graphics, tile, x, w, taskY);
        drawButtonLabel(graphics, x + 10, y + h - 16, 52, 12, "SAVE");
        drawButtonLabel(graphics, x + 66, y + h - 16, 52, 12, "ICON");
        drawButtonLabel(graphics, x + 122, y + h - 16, 52, 12, "DEL");
    }

    private int drawQuestlineSection(GuiGraphics graphics, Tile tile, int x, int w, int y) {
        pixel(graphics, Component.literal("QUESTLINE"), x + 10, y, QuestColors.MUTED);
        y += 10;
        List<Link> inbound = chapter.links().stream().filter(link -> link.to().equals(tile.id())).toList();
        List<Link> outbound = chapter.links().stream().filter(link -> link.from().equals(tile.id())).toList();
        if (inbound.isEmpty() && outbound.isEmpty()) {
            pixel(graphics, Component.literal("Shift+click tiles to link"), x + 10, y, QuestColors.MUTED);
            return y + 14;
        }
        for (Link link : inbound) {
            int color = gateColor(link.gate().op());
            pixel(graphics, Component.literal("IN  " + link.from()), x + 10, y, QuestColors.TEXT);
            drawButton(graphics, x + w - 70, y - 2, 32, 10, link.gate().op().getSerializedName().toUpperCase(Locale.ROOT), color);
            drawButton(graphics, x + w - 34, y - 2, 12, 10, "x", QuestColors.PORT_RED);
            y += 12;
        }
        for (Link link : outbound) {
            int color = gateColor(link.gate().op());
            pixel(graphics, Component.literal("OUT " + link.to()), x + 10, y, QuestColors.TEXT);
            drawButton(graphics, x + w - 70, y - 2, 32, 10, link.gate().op().getSerializedName().toUpperCase(Locale.ROOT), color);
            drawButton(graphics, x + w - 34, y - 2, 12, 10, "x", QuestColors.PORT_RED);
            y += 12;
        }
        return y + 4;
    }

    private void drawLog(GuiGraphics graphics) {
        float lp = logFxProgress();
        int slide = Math.round((1f - lp) * Math.max(24, width / 5f));
        int x = 16 + slide;
        int y = 32;
        int w = width - 32;
        List<LogRow> rows = logLayout(16, y, w);
        int maxScroll = logMaxScrollFor(rows, y);
        logScroll = Math.max(0, Math.min(logScroll, maxScroll));

        int clipTop = logClipTop(y);
        int clipBottom = logClipBottom(height);
        // try/finally for the same reason as the inspect card: a throw while drawing rows must not leave an
        // active scissor over the chrome, modal and fade-lock that are painted after the log.
        graphics.enableScissor(Math.max(0, x + 2), clipTop, logClipRight(width), clipBottom);
        try {
            for (LogRow row : rows) {
                int ry = row.y() - logScroll;
                if (!logRowFits(ry, font.lineHeight, clipTop, clipBottom)) {
                    continue;
                }
                ItemStack face = row.stack().orElse(ItemStack.EMPTY);
                if (!face.isEmpty()) {
                    var pose = graphics.pose();
                    pose.pushPose();
                    pose.translate(x + 16, ry - 2, 0);
                    pose.scale(0.6f, 0.6f, 1f);
                    graphics.renderItem(face, 0, 0);
                    pose.popPose();
                }
                int ink = UiFx.scaleAlpha(row.color(), Math.max(0.2f, lp));
                graphics.drawString(font, row.text(), x + row.inset(), ry, ink, false);
            }
        } finally {
            graphics.disableScissor();
        }
        int viewH = logViewH(height, y);
        drawLogScrollThumb(graphics, clipTop, clipBottom, maxScroll, viewH);
    }

    /** How far the log can scroll, measured from a fresh layout so the wheel cannot disagree with the draw. */
    private int logMaxScroll() {
        return logMaxScrollFor(logLayout(16, 32, width - 32), 32);
    }

    private int logMaxScrollFor(List<LogRow> rows, int y) {
        logContentH = rows.isEmpty() ? 0 : rows.get(rows.size() - 1).y() + 12 - y;
        int viewH = logViewH(height, y);
        return Math.max(0, logContentH - VIEW_SCROLL_PAD - viewH + 12);
    }

    /** Extra slack so the last row is not flush against the card edge. */
    private static final int VIEW_SCROLL_PAD = 12;

    /**
     * Flatten the progress + scrolls report into rows. Split out from the draw so the wheel handler can
     * measure the content without rendering it.
     */
    private List<LogRow> logLayout(int x, int y, int w) {
        List<LogRow> rows = new ArrayList<>();
        int ly = y + 24;
        rows.add(new LogRow(ly, "PROGRESS", QuestColors.SIDEBAR_HEADER, Optional.empty(), 8));
        ly += 12;
        int done = 0;
        for (Chapter entry : ClientQuestState.pack.chapters()) {
            if (!ClientQuestState.isChapterListed(entry)) {
                continue;
            }
            List<LogRow> block = completedTileRows(entry, ly,
                    tile -> ClientQuestState.progress.tileCompleted(entry.id().toString(), tile.id()),
                    QuestBookScreen::rewardFace);
            rows.addAll(block);
            if (!block.isEmpty()) {
                ly = block.get(block.size() - 1).y() + 11;
                done++;
            }
        }
        if (done == 0) {
            rows.add(new LogRow(ly, "No quests completed yet.", QuestColors.MUTED, Optional.empty(), 8));
            ly += 16;
        }
        ly += 8;
        rows.add(new LogRow(ly, "SCROLLS", QuestColors.SIDEBAR_HEADER, Optional.empty(), 8));
        ly += 12;
        for (Scroll scroll : ClientQuestState.ownedScrolls()) {
            List<LogRow> block = scrollRows(scroll, ly, w);
            rows.addAll(block);
            ly = block.get(block.size() - 1).y() + 10;
        }
        if (ClientQuestState.ownedScrolls().isEmpty()) {
            rows.add(new LogRow(ly, "No scrolls yet.", QuestColors.MUTED, Optional.empty(), 8));
        }
        return rows;
    }

    /**
     * Rows for one chapter's completed tiles, starting at {@code startY}. Pure in (chapter, predicate) so it
     * is unit-testable without a game — only {@link Reward#describe()} is touched, never a registry.
     */
    static List<LogRow> completedTileRows(Chapter entry, int startY, java.util.function.Predicate<Tile> isDone,
            java.util.function.Function<Reward, ItemStack> iconResolver) {
        List<LogRow> rows = new ArrayList<>();
        int ly = startY;
        String chapterName = entry.title().isBlank() ? entry.id().getPath() : entry.title();
        for (Tile tile : entry.tiles()) {
            if (!isDone.test(tile)) {
                continue;
            }
            String title = tile.title().isBlank() ? tile.id() : tile.title();
            rows.add(new LogRow(ly,
                    chapterName.toUpperCase(Locale.ROOT) + "  /  " + title.toUpperCase(Locale.ROOT),
                    QuestColors.COMPLETED, Optional.empty(), 8));
            ly += 11;
            if (tile.rewards().isEmpty()) {
                rows.add(new LogRow(ly, "no reward", QuestColors.MUTED, Optional.empty(), 30));
                ly += 11;
            } else {
                for (Reward reward : tile.rewards()) {
                    // ofNullable so a resolver may legitimately report "no icon" (tests pass null).
                    ItemStack icon = iconResolver.apply(reward);
                    rows.add(new LogRow(ly, "reward  " + reward.describe(), QuestColors.TEXT,
                            Optional.ofNullable(icon), 30));
                    ly += 12;
                }
            }
            ly += 4;
        }
        return rows;
    }

    /** Rows for one owned scroll: title plus its wrapped body. */
    private List<LogRow> scrollRows(Scroll scroll, int startY, int w) {
        List<LogRow> rows = new ArrayList<>();
        int ly = startY;
        rows.add(new LogRow(ly, scroll.title().toUpperCase(Locale.ROOT), QuestColors.TEXT, Optional.empty(), 8));
        ly += 12;
        for (String line : wrapLines(scroll.text(), logWrapWidth(width, 8))) {  // 8 = the body rows' own inset below
            rows.add(new LogRow(ly, line, QuestColors.MUTED, Optional.empty(), 8));
            ly += 10;
        }
        return rows;
    }

    /** Thin scrollbar for the log, fading out once the list has been idle. */
    private void drawLogScrollThumb(GuiGraphics graphics, int clipTop, int clipBottom, int maxScroll, int viewH) {
        if (maxScroll <= 0) {
            return;
        }
        int trackX = width - 8;
        int trackH = Math.max(8, clipBottom - clipTop);
        int thumbH = Math.max(12, trackH * viewH / Math.max(viewH + maxScroll, 1));
        int thumbY = clipTop + (int) ((trackH - thumbH) * (logScroll / (double) maxScroll));
        MockChrome.box(graphics, trackX, clipTop, 2, trackH, QuestColors.SIDEBAR_EDGE);
        float idle = UiFx.elapsed(fxScrollActivityAt) / (float) SCROLL_FADE_MS;
        float alpha = 1f - UiFx.clamp01((idle - 1f) / 1.5f);
        if (alpha > 0.02f) {
            MockChrome.box(graphics, trackX, thumbY, 2, thumbH,
                    UiFx.withAlpha(QuestColors.SIDEBAR_HEADER, alpha));
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        blurSearchIfOutside(mouseX, mouseY);
        if (picker.open && picker.click(boardLeft() + 16, 40, (int) mouseX, (int) mouseY)) {
            return true;
        }
        if (modal != ModalKind.NONE) {
            ModalLayout box = modalLayout();
            if (modal == ModalKind.CLAIM_ALL) {
                if (over(box.btnX(), box.btnY(), box.btnW(), box.btnH(), mouseX, mouseY)) {
                    confirmClaimAll();
                    return true;
                }
                if (over(box.cancelX(), box.btnY(), box.cancelW(), box.btnH(), mouseX, mouseY)
                        || over(box.x() + box.w() - 14, box.y() + 1, 12, 12, mouseX, mouseY)) {
                    dismissModal();
                    return true;
                }
                return true;
            }
            if (over(box.btnX(), box.btnY(), box.btnW(), box.btnH(), mouseX, mouseY)) {
                dismissModal();
                return true;
            }
            return true;
        }
        if (clickChromeEditor(mouseX, mouseY)) {
            return true;
        }
        if (authoring && !logOpen && button == 0) {
            BookChrome chrome = resolveChrome();
            String label = visibleTitle(chrome);
            int tw = Math.round(font.width(label) * chrome.titleScale());
            boolean hitTitle = over(8, 6, Math.min(sidebarTitleMaxW(), Math.max(tw, 48)), 14, mouseX, mouseY);
            boolean hitGear = sidebarTitleGearShown() && over(sidebarTitleGearX(), 7, 12, 12, mouseX, mouseY);
            if (hitTitle || hitGear) {
                chromeEditorOpen = true;
                editingChromeTitle = true;
                chromeTitleBuffer = chrome.sidebarTitle();
                return true;
            }
        }
        if (overTab(mouseX, mouseY)) {
            toggleSidebar();
            return true;
        }
        if (!sidebarCollapsed && overResize(mouseX, mouseY) && button == 0) {
            resizingSidebar = true;
            lastMx = mouseX;
            return true;
        }
        if (button == 1) {
            if (expanded && over(cardX(), cardY(), cardW(), cardH(), mouseX, mouseY)) {
                return true;
            }
            dragging = true;
            lastMx = mouseX;
            lastMy = mouseY;
            return true;
        }
        if (drawingLog()) {
            if (over(logX(), 6, LOG_W, 14, mouseX, mouseY)) {
                if (fxLogClosing) {
                    fxLogClosing = false;
                    logOpen = true;
                    fxLogAt = UiFx.nowMs();
                } else if (UiFx.enabled()) {
                    fxLogClosing = true;
                    fxLogAt = UiFx.nowMs();
                } else {
                    logOpen = false;
                }
                return true;
            }
            return true;
        }
        if (overSidebarList(mouseX, mouseY)) {
            for (SidebarRow row : sidebarRows) {
                if (!sidebarRowVisible(row)) {
                    continue;
                }
                if (mouseY >= row.y() - 2 && mouseY < row.y() + 16) {
                    if (row.hasChildren() && over(row.caretX(), row.y(), 10, 14, mouseX, mouseY)) {
                        String key = row.id().toString();
                        if (!collapsedChapters.add(key)) {
                            collapsedChapters.remove(key);
                        }
                        return true;
                    }
                    if (row.locked() && !authoring) {
                        int x = 6 + row.depth() * 10;
                        if (row.hasChildren()) {
                            x += 10;
                        }
                        cueLocked("", x + 4, row.y() + 8);
                        return true;
                    }
                    openChapter(row.id());
                    return true;
                }
            }
            return true;
        }
        if (claimAllShown() && over(claimAllX(), 6, claimAllW(), 14, mouseX, mouseY)) {
            openClaimAll();
            return true;
        }
        if (over(logX(), 6, LOG_W, 14, mouseX, mouseY)) {
            if (fxLogClosing) {
                // Re-open mid-close: cancel the exit and slide back in.
                fxLogClosing = false;
                logOpen = true;
                fxLogAt = UiFx.nowMs();
            } else if (!logOpen) {
                logOpen = true;
                fxLogClosing = false;
                fxLogAt = UiFx.nowMs();
                logScroll = 0;
            } else if (UiFx.enabled()) {
                fxLogClosing = true;
                fxLogAt = UiFx.nowMs();
            } else {
                logOpen = false;
            }
            return true;
        }
        if (!logOpen && !isIntroChapter() && over(fitX(), 6, FIT_S, FIT_S, mouseX, mouseY)) {
            fitAllContent();
            return true;
        }
        if (!QuestConfig.GITHUB_ISSUES_URL.get().isBlank() && !selectedId.isEmpty()
                && over(width - 70, height - 22, 62, 14, mouseX, mouseY)) {
            openReport();
            return true;
        }
        if ((expanded && !selectedId.isEmpty()) || showInspectPanel()) {
            int w = cardW();
            int h = cardH();
            int x = cardX();
            int y = cardY();
            String inspectId = inspectTileId();
            if (over(x + w - 14, y, 12, 12, mouseX, mouseY)) {
                Tile pinned = chapter.tile(inspectId).orElse(null);
                if (pinned != null && isPinnedTile(pinned)) {
                    togglePin(pinned);
                }
                expanded = false;
                return true;
            }
            Tile tile = chapter.tile(inspectId).orElse(null);
            if (tile != null && handleCardClick(tile, x, y, w, h, mouseX, mouseY)) {
                return true;
            }
            if (over(x, y, w, 14, mouseX, mouseY) && button == 0) {
                draggingInspect = true;
                lastMx = mouseX;
                lastMy = mouseY;
                return true;
            }
            if (over(x, y, w, h, mouseX, mouseY)) {
                return true;
            }
        }
        if (!isIntroChapter()) {
        for (Tile tile : chapter.tiles()) {
            if (!authoring && ClientQuestState.isConcealed(chapter, tile)) {
                continue;
            }
            int[] s = screen(tile.pos().x(), tile.pos().y());
            if (mouseX >= s[0] && mouseY >= s[1] && mouseX < s[0] + tilePx() && mouseY < s[1] + tilePx()) {
                if (authoring && hasShiftDown()) {
                    if (linkFrom.isEmpty()) {
                        linkFrom = tile.id();
                    } else if (linkFrom.equals(tile.id())) {
                        linkFrom = "";
                    } else if (chapter.hasLink(linkFrom, tile.id())) {
                        chapter = chapter.removeLink(linkFrom, tile.id());
                        linkFrom = "";
                        saveChapter();
                    } else if (chapter.tile(linkFrom).map(src -> src.pos().cardinalAdjacent(tile.pos())).orElse(false)) {
                        chapter = chapter.upsertLink(linkFrom, tile.id(), pendingGate);
                        linkFrom = "";
                        saveChapter();
                    }
                    return true;
                }
                TileVisual visual = ClientQuestState.visual(chapter, tile, authoring, selectedId);
                if (!authoring && visual == TileVisual.LOCKED) {
                    List<Link> incomplete = ClientQuestState.incompleteParents(chapter, tile);
                    if (incomplete.size() >= 1) {
                        int[] pos = tileCuePos(tile);
                        cueLocked(tile.id(), pos[0], pos[1]);
                        selectedId = tile.id();
                        expanded = false;
                        return true;
                    }
                }
                if (!authoring && visual == TileVisual.CLOSED) {
                    return true;
                }
                if (expanded && selectedId.equals(tile.id())) {
                    if (isPinnedTile(tile)) {
                        return true;
                    }
                    expanded = false;
                    return true;
                }
                selectedId = tile.id();
                bounce = 0f;
                if (!authoring && ClientQuestState.needsXorChoice(chapter, tile)) {
                    expanded = false;
                    openModal(ModalKind.XOR, tile.id());
                } else {
                    // Keep the description panel open; pinned card stays at its screen position.
                    expanded = true;
                    modal = ModalKind.NONE;
                    modalTileId = "";
                }
                return true;
            }
        }
        }
        if (button == 0 && showInspectPanel() && !inspectPinned()
                && mouseY >= TOP_H && mouseX >= sidebarWidth()) {
            expanded = false;
            return true;
        }
        if (authoring && !isIntroChapter()) {
            int gx = gridX(mouseX);
            int gy = gridY(mouseY);
            if (!chapter.inBounds(gx, gy)) {
                return true;
            }
            boolean empty = chapter.tiles().stream().noneMatch(t -> t.pos().x() == gx && t.pos().y() == gy);
            if (empty) {
                String id = "tile_" + gx + "_" + gy;
                chapter = chapter.replaceTile(Tile.blank(id, gx, gy));
                selectedId = id;
                expanded = true;
                saveChapter();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingInspect && ClientQuestState.hasPin()
                && ClientQuestState.progress.pinChapter().orElse("").equals(chapter.id().toString())) {
            String pinTile = ClientQuestState.progress.pinTile().orElse("");
            if (!pinTile.isEmpty()) {
                PINNED_CARD_OFFSET.put(pinKey(chapter.id().toString(), pinTile), new int[]{inspectOffX, inspectOffY});
            }
        }
        dragging = false;
        resizingSidebar = false;
        draggingInspect = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (resizingSidebar) {
            setSidebarWidth((int) Math.round(mouseX));
            return true;
        }
        if (draggingInspect) {
            inspectOffX += (int) Math.round(mouseX - lastMx);
            inspectOffY += (int) Math.round(mouseY - lastMy);
            lastMx = mouseX;
            lastMy = mouseY;
            // Move panel chrome with the content (dirty-sync can otherwise leave the frame behind).
            syncOverlays();
            return true;
        }
        if (dragging) {
            cameraX -= (mouseX - lastMx) / zoom;
            cameraY -= (mouseY - lastMy) / zoom;
            lastMx = mouseX;
            lastMy = mouseY;
            return true;
        }
        if (authoring && !selectedId.isEmpty() && button == 0) {
            int gx = gridX(mouseX);
            int gy = gridY(mouseY);
            if (chapter.inBounds(gx, gy)) {
                chapter.tile(selectedId).ifPresent(tile -> chapter = chapter.replaceTile(tile.withPos(new GridPos(gx, gy))));
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (picker.mouseScrolled(scrollY)) {
            return true;
        }
        if (modal == ModalKind.CLAIM_ALL) {
            ModalLayout box = modalLayout();
            int max = Math.max(0, claimAllContentH(claimAllLines()) - box.bodyH());
            if (max > 0) {
                claimAllScroll = Math.max(0, Math.min(max, claimAllScroll - (int) Math.round(scrollY * 18)));
            }
            return true;
        }
        if (logOpen) {
            // The log owns the wheel while it is open: it is a full-panel view with its own overflow.
            int max = logMaxScroll();
            if (max > 0) {
                logScroll = Math.max(0, Math.min(max, logScroll - (int) Math.round(scrollY * 38)));
                fxScrollActivityAt = UiFx.nowMs();
            }
            return true;
        }
        if (overSidebarList(mouseX, mouseY) && sidebarMaxScroll() > 0) {
            sidebarScroll -= (int) Math.round(scrollY * 38);
            clampSidebarScroll();
            rebuildSidebarRows();
            fxScrollActivityAt = UiFx.nowMs();
            return true;
        }
        if (isIntroChapter() && overIntroBody(mouseX, mouseY)) {
            int max = Math.max(0, introBodyContentH - introBodyH());
            if (max > 0) {
                introBodyScroll -= (int) Math.round(scrollY * 18);
                introBodyScroll = Math.max(0, Math.min(introBodyScroll, max));
            }
            return true;
        }
        if (isIntroChapter()) {
            return true;
        }
        if (scrollY > 0) {
            stepZoom(1);
        } else {
            stepZoom(-1);
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // KeyMappings do not consume clicks while a Screen owns input — handle toggle close here.
        if (QuestKeybinds.OPEN_BOOK.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        if (picker.open) {
            return picker.keyTyped((char) 0, keyCode) || super.keyPressed(keyCode, scanCode, modifiers);
        }
        if (modal == ModalKind.CLAIM_ALL && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            dismissModal();
            return true;
        }
        if (editingTitle || editingBody) {
            if (keyCode == GLFW.GLFW_KEY_ENTER) {
                commitTextEdit();
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
                if (editingTitle && !titleBuffer.isEmpty()) {
                    titleBuffer = titleBuffer.substring(0, titleBuffer.length() - 1);
                } else if (editingBody && !bodyBuffer.isEmpty()) {
                    bodyBuffer = bodyBuffer.substring(0, bodyBuffer.length() - 1);
                }
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                editingTitle = false;
                editingBody = false;
                return true;
            }
            return true;
        }
        if (search != null && search.isFocused() && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            blurSearch();
            return true;
        }
        if (authoring && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            if (!linkFrom.isEmpty()) {
                linkFrom = "";
                return true;
            }
        }
        if (authoring && keyCode == GLFW.GLFW_KEY_1) {
            pendingGate = GateOp.AND;
            return true;
        }
        if (authoring && keyCode == GLFW.GLFW_KEY_2) {
            pendingGate = GateOp.OR;
            return true;
        }
        if (authoring && keyCode == GLFW.GLFW_KEY_3) {
            pendingGate = GateOp.XOR;
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER && !searchHits.isEmpty()) {
            searchIndex = (searchIndex + 1) % searchHits.size();
            centerOn(searchHits.get(searchIndex));
            return true;
        }
        if (authoring && keyCode == GLFW.GLFW_KEY_DELETE && !selectedId.isEmpty()) {
            chapter = chapter.removeTile(selectedId);
            selectedId = "";
            expanded = false;
            linkFrom = "";
            saveChapter();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (picker.open) {
            return picker.keyTyped(codePoint, 0);
        }
        if (editingChromeTitle && codePoint >= 32) {
            if (chromeTitleBuffer.length() < 32) {
                chromeTitleBuffer += Character.toString(codePoint);
            }
            return true;
        }
        if (editingTitle && codePoint >= 32) {
            titleBuffer += codePoint;
            return true;
        }
        if (editingBody && codePoint >= 32) {
            bodyBuffer += codePoint;
            return true;
        }
        return super.charTyped(codePoint, modifiers);
    }

    private void togglePin(Tile tile) {
        boolean already = isPinnedTile(tile);
        fxPinFlashAt = UiFx.nowMs();
        fxPinFlashPinned = !already;
        if (already) {
            PINNED_CARD_OFFSET.remove(pinKey(chapter.id().toString(), tile.id()));
            ClientQuestState.setPinLocal(Optional.empty(), Optional.empty());
            QuestNetwork.sendToServer(new PinC2S(chapter.id(), tile.id(), true));
            return;
        }
        // Lock the description card at its current screen position.
        cardX();
        cardY();
        PINNED_CARD_OFFSET.put(pinKey(chapter.id().toString(), tile.id()), new int[]{inspectOffX, inspectOffY});
        selectedId = tile.id();
        expanded = true;
        ClientQuestState.setPinLocal(Optional.of(chapter.id().toString()), Optional.of(tile.id()));
        QuestNetwork.sendToServer(new PinC2S(chapter.id(), tile.id(), false));
    }

    private void clickDone(Tile tile) {
        clickDone(tile, -1);
    }

    /**
     * Submit the open claimable task (or {@code taskIndex} when a specific row was clicked) and claim the
     * tile's rewards. The index used to be hardcoded to 0, so on a multi-task tile every row submitted task
     * 0 — completing the wrong task and consuming its items/XP — and tasks at index >= 1 could never be
     * submitted at all.
     */
    private void clickDone(Tile tile, int taskIndex) {
        String questId = chapter.id() + "/" + tile.id();
        int open = taskIndex;
        if (open < 0) {
            open = -1;
            for (int i = 0; i < tile.tasks().size(); i++) {
                if (needsClaim(tile.tasks().get(i).type())
                        && !ClientQuestState.progress.taskCompleted(questId, Integer.toString(i))) {
                    open = i;
                    break;
                }
            }
        }
        if (open >= 0) {
            QuestNetwork.sendToServer(new SubmitTaskC2S(chapter.id(), tile.id(), open));
        }
        if ("CLAIM".equals(playBar(tile, 0, 0, 200, 80).actionLabel())) {
            armClaimSpark(tile);
        }
        QuestNetwork.sendToServer(new ClaimRewardsC2S(chapter.id(), tile.id()));
    }

    private void armClaimSpark(Tile tile) {
        if (!UiFx.enabled()) {
            return;
        }
        int x = cardX();
        int y = cardY();
        int w = cardW();
        int h = cardH();
        int footY = inspectFootY(tile, x, y, w, h);
        fxClaimSparkAt = UiFx.nowMs();
        fxClaimSparkX = x + w - 20;
        fxClaimSparkY = footY + 8;
    }

    private boolean handleCardClick(Tile tile, int x, int y, int w, int h, double mouseX, double mouseY) {
        if (!authoring) {
            // JEI icon hits use the rectangles painted this frame (bottom-anchored task rows).
            // This must run before CLAIM surface / early return — otherwise "Click: how to make it"
            // tooltips work but the click never reaches openInJei (1.1.164).
            for (TaskIconHit hit : taskIconHits) {
                if (hit.over(mouseX, mouseY) && !hit.stack().isEmpty()) {
                    openInJei(hit.taskIndex());
                    return true;
                }
            }
            PlayBar bar = playBar(tile, x, y, w, h);
            if (bar.action() && over(bar.actionX(), bar.y(), bar.actionW(), 12, mouseX, mouseY)) {
                clickDone(tile);
                return true;
            }
            if (overClaimSurface(tile, x, y, w, h, mouseX, mouseY)) {
                clickDone(tile);
                return true;
            }
            if (bar.choice() && over(bar.choiceAX(), bar.y(), bar.choiceW(), 12, mouseX, mouseY)) {
                QuestNetwork.sendToServer(new ClaimChoiceC2S(chapter.id(), tile.id(), 0));
                return true;
            }
            if (bar.choice() && over(bar.choiceBX(), bar.y(), bar.choiceW(), 12, mouseX, mouseY)) {
                QuestNetwork.sendToServer(new ClaimChoiceC2S(chapter.id(), tile.id(), 1));
                return true;
            }
            if (bar.pin() && over(bar.pinX(), bar.y(), bar.pinW(), 12, mouseX, mouseY)) {
                togglePin(tile);
                return true;
            }
            return false;
        }
        if (authoring && over(x + 10, y + h - 16, 52, 12, mouseX, mouseY)) {
            saveChapter();
            return true;
        }
        if (authoring && over(x + 66, y + h - 16, 52, 12, mouseX, mouseY)) {
            picker.open(ItemPickerOverlay.Mode.ITEM, id -> replaceSelected(current -> current.withIcon(Optional.of(Icon.of(id)))));
            return true;
        }
        if (authoring && over(x + 122, y + h - 16, 52, 12, mouseX, mouseY)) {
            chapter = chapter.removeTile(tile.id());
            selectedId = "";
            expanded = false;
            saveChapter();
            return true;
        }
        if (authoring && over(x + 10, y + 16, 200, 12, mouseX, mouseY)) {
            editingTitle = true;
            editingBody = false;
            titleBuffer = tile.title();
            setFocused(null);
            return true;
        }
        if (authoring && over(x + 10, y + 30, w - 20, 28, mouseX, mouseY)) {
            editingBody = true;
            editingTitle = false;
            bodyBuffer = tile.description();
            setFocused(null);
            return true;
        }
        int taskY = y + 62;
        if (authoring) {
            if (handleQuestlineClick(tile, x, w, taskY, mouseX, mouseY)) {
                return true;
            }
            taskY += measureQuestlineHeight(tile);
        }
        taskY += 10;
        int shown = Math.min(tile.tasks().size(), Math.min(6, drawnTaskRows));
        for (int i = 0; i < shown; i++) {
            int taskIndex = i;
            Task task = tile.tasks().get(taskIndex);
            if (authoring && over(x + w - 36, taskY - 2, 12, 10, mouseX, mouseY)) {
                replaceSelected(current -> replaceTask(current, taskIndex, current.tasks().get(taskIndex).withCount(current.tasks().get(taskIndex).required() + 1)));
                return true;
            }
            if (authoring && over(x + w - 22, taskY - 2, 12, 10, mouseX, mouseY)) {
                List<Task> tasks = new ArrayList<>(tile.tasks());
                tasks.remove(taskIndex);
                replaceSelected(current -> current.withTasks(tasks));
                return true;
            }
            if (authoring && over(x + 10, taskY, 80, 10, mouseX, mouseY)) {
                replaceSelected(current -> replaceTask(current, taskIndex, TaskFactory.next(current.tasks().get(taskIndex))));
                return true;
            }
            if (authoring && over(x + 90, taskY, w - 130, 10, mouseX, mouseY)) {
                editTaskTarget(taskIndex, task);
                return true;
            }
            if (!authoring && tileInteractive(tile) && needsClaim(task.type()) && over(x + w - 70, taskY - 2, 56, 12, mouseX, mouseY)) {
                clickDone(tile, taskIndex);
                return true;
            }
            taskY += 12;
        }
        if (authoring && over(x + 10, taskY, 56, 12, mouseX, mouseY)) {
            List<Task> tasks = new ArrayList<>(tile.tasks());
            tasks.add(TaskFactory.create("obtain"));
            replaceSelected(current -> current.withTasks(tasks));
            return true;
        }
        if (authoring) {
            taskY += 16;
        }
        taskY += 10;
        boolean claimed = ClientQuestState.progress.taskCompleted(chapter.id() + "/" + tile.id(), "choice");
        boolean complete = ClientQuestState.progress.tileCompleted(chapter.id().toString(), tile.id());
        if (tile.rewards().isEmpty()) {
            taskY += 12;
        } else {
            for (int i = 0; i < tile.rewards().size(); i++) {
                int rewardIndex = i;
                Reward reward = tile.rewards().get(rewardIndex);
                if (authoring && over(x + w - 22, taskY - 2, 12, 10, mouseX, mouseY)) {
                    List<Reward> rewards = new ArrayList<>(tile.rewards());
                    rewards.remove(rewardIndex);
                    replaceSelected(current -> current.withRewards(rewards));
                    return true;
                }
                if (authoring && over(x + 10, taskY, 80, 10, mouseX, mouseY)) {
                    replaceSelected(current -> replaceReward(current, rewardIndex, RewardFactory.next(current.rewards().get(rewardIndex))));
                    return true;
                }
                if (authoring && over(x + 90, taskY, w - 120, 10, mouseX, mouseY)) {
                    editRewardTarget(rewardIndex, reward);
                    return true;
                }
                if (!authoring && reward instanceof ChoiceReward && complete && !claimed) {
                    if (over(x + w - 70, taskY - 2, 28, 12, mouseX, mouseY)) {
                        QuestNetwork.sendToServer(new ClaimChoiceC2S(chapter.id(), tile.id(), 0));
                        return true;
                    }
                    if (over(x + w - 38, taskY - 2, 28, 12, mouseX, mouseY)) {
                        QuestNetwork.sendToServer(new ClaimChoiceC2S(chapter.id(), tile.id(), 1));
                        return true;
                    }
                }
                taskY += 14;
            }
        }
        if (authoring && over(x + 10, taskY, 72, 12, mouseX, mouseY)) {
            List<Reward> rewards = new ArrayList<>(tile.rewards());
            rewards.add(RewardFactory.create("item"));
            replaceSelected(current -> current.withRewards(rewards));
            return true;
        }
        tile.target().ifPresent(target -> {
            if (over(x + 10, y + 50, 120, 10, mouseX, mouseY)) {
                ClientQuestState.chapter(target.chapter()).ifPresent(next -> {
                    if (!ClientQuestState.isChapterUnlocked(next) && !authoring) {
                        if (!next.hideUntilUnlocked()) {
                            showLockedChapterCue(next.id());
                        }
                        return;
                    }
                    openChapter(next.id(), target.tile());
                });
            }
        });
        return false;
    }

    private void openChapter(ResourceLocation id) {
        openChapter(id, "");
    }

    private void openChapter(ResourceLocation id, String tileId) {
        ClientQuestState.chapter(id).ifPresent(next -> {
            if (!authoring && !ClientQuestState.isChapterListed(next)) {
                return;
            }
            if (modal == ModalKind.CLAIM_ALL) {
                dismissModal();
            }
            this.chapter = next;
            ClientQuestState.rememberChapter(next.id());
            this.linkFrom = "";
            this.bounce = 0f;
            this.introBodyScroll = 0;
            next.parent().ifPresent(parent -> collapsedChapters.remove(parent.toString()));
            if (!tileId.isEmpty()) {
                this.selectedId = tileId;
                this.expanded = true;
                centerOn(tileId);
            } else {
                focusStartTile();
                // Re-open the pinned description on its chapter (focusStartTile clears selection).
                restorePinnedCardIfNeeded();
            }
            rebuildTileWidgets();
            markWidgetsDirty();
        });
    }

    private boolean handleQuestlineClick(Tile tile, int x, int w, int startY, double mouseX, double mouseY) {
        int y = startY + 10;
        List<Link> inbound = chapter.links().stream().filter(link -> link.to().equals(tile.id())).toList();
        List<Link> outbound = chapter.links().stream().filter(link -> link.from().equals(tile.id())).toList();
        if (inbound.isEmpty() && outbound.isEmpty()) {
            return false;
        }
        for (Link link : inbound) {
            if (over(x + w - 70, y - 2, 32, 10, mouseX, mouseY)) {
                chapter = chapter.setLinkOp(link.from(), link.to(), cycleGate(link.gate().op()));
                saveChapter();
                return true;
            }
            if (over(x + w - 34, y - 2, 12, 10, mouseX, mouseY)) {
                chapter = chapter.removeLink(link.from(), link.to());
                saveChapter();
                return true;
            }
            y += 12;
        }
        for (Link link : outbound) {
            if (over(x + w - 70, y - 2, 32, 10, mouseX, mouseY)) {
                chapter = chapter.setLinkOp(link.from(), link.to(), cycleGate(link.gate().op()));
                saveChapter();
                return true;
            }
            if (over(x + w - 34, y - 2, 12, 10, mouseX, mouseY)) {
                chapter = chapter.removeLink(link.from(), link.to());
                saveChapter();
                return true;
            }
            y += 12;
        }
        return false;
    }

    private int measureQuestlineHeight(Tile tile) {
        List<Link> inbound = chapter.links().stream().filter(link -> link.to().equals(tile.id())).toList();
        List<Link> outbound = chapter.links().stream().filter(link -> link.from().equals(tile.id())).toList();
        if (inbound.isEmpty() && outbound.isEmpty()) {
            return 24;
        }
        return 10 + (inbound.size() + outbound.size()) * 12 + 4;
    }

    private static GateOp cycleGate(GateOp op) {
        return switch (op) {
            case AND -> GateOp.OR;
            case OR -> GateOp.XOR;
            default -> GateOp.AND;
        };
    }

    private void editTaskTarget(int index, Task task) {
        String kind = TaskFactory.targetKind(task.type());
        if ("none".equals(kind)) {
            return;
        }
        if ("here".equals(kind) && minecraft != null && minecraft.player != null) {
            var pos = minecraft.player.blockPosition();
            String dimension = minecraft.player.level().dimension().location().toString();
            replaceSelected(current -> replaceTask(current, index, new LocationTask(pos.getX(), pos.getY(), pos.getZ(), 8, dimension)));
            return;
        }
        picker.open(ItemPickerOverlay.modeFor(kind), id ->
                replaceSelected(current -> replaceTask(current, index, TaskFactory.retarget(current.tasks().get(index), id))));
    }

    private void editRewardTarget(int index, Reward reward) {
        String kind = RewardFactory.targetKind(reward.type());
        if ("none".equals(kind)) {
            return;
        }
        picker.open(ItemPickerOverlay.modeFor(kind), id ->
                replaceSelected(current -> replaceReward(current, index, RewardFactory.retarget(current.rewards().get(index), id))));
    }

    private void replaceSelected(java.util.function.Function<Tile, Tile> update) {
        chapter.tile(selectedId).ifPresent(tile -> {
            chapter = chapter.replaceTile(update.apply(tile));
            saveChapter();
        });
    }

    private static Tile replaceTask(Tile tile, int index, Task next) {
        List<Task> tasks = new ArrayList<>(tile.tasks());
        if (index >= 0 && index < tasks.size()) {
            tasks.set(index, next);
        }
        return tile.withTasks(tasks);
    }

    private static Tile replaceReward(Tile tile, int index, Reward next) {
        List<Reward> rewards = new ArrayList<>(tile.rewards());
        if (index >= 0 && index < rewards.size()) {
            rewards.set(index, next);
        }
        return tile.withRewards(rewards);
    }

    private static boolean needsClaim(String type) {
        return "submit".equals(type) || "checkmark".equals(type) || "xp_levels".equals(type);
    }

    private static String actionLabel(String type) {
        return "checkmark".equals(type) ? "CLAIM" : "SUBMIT";
    }

    /** Base card width before the narrow-GUI shrink. */
    private static final int CARD_W_BASE = 208;

    /**
     * Shrink the whole card when the board gutter is narrower than it, instead of parking a full-width card
     * over the sidebar. Previously the clamp pushed the card left of {@code boardLeft()}, where the sidebar
     * both drew over it and stole the clicks (the sidebar hit-test runs first), so the task icons and labels
     * in that strip were unusable. Scaling keeps every element inside the board and proportional.
     */
    private float cardScale() {
        if (authoring) {
            return 1f;
        }
        int avail = Math.max(1, width - boardLeft() - 16);
        return UiFx.clamp01(Math.min(1f, avail / (float) CARD_W_BASE));
    }

    private int cardW() {
        if (authoring) {
            return 232;
        }
        return Math.max(96, Math.round(CARD_W_BASE * cardScale()));
    }

    /**
     * Body lines that fit between the title and the footer. Accounts for the rows actually drawn and for a
     * tall title, so the last body line can never land on the task block or the action bar.
     */
    private static int bodyLineBudget(int h, int taskRows, int titlePush) {
        int ideal = (h - BODY_RESERVED_PX - Math.max(0, taskRows) * 12) / 10;
        int byTitle = (h - 68 - Math.max(0, titlePush) - Math.max(0, taskRows) * 12) / 10;
        return Math.max(2, Math.min(Math.max(0, ideal), Math.max(0, byTitle)));
    }

    private int cardH() {
        if (authoring) {
            return Math.min(280, Math.max(200, height - 60));
        }
        // Size against the visible inspect tile (pin may keep the card open while selectedId is cleared).
        Tile tile = chapter.tile(inspectTileId()).orElse(null);
        if (tile == null) {
            return 96;
        }
        String title = tile.title().isBlank() ? tile.id() : tile.title();
        int titleLines = wrapLineCount(title.toUpperCase(Locale.ROOT), cardW() - 20);
        int bodyLines = wrapLineCount(inspectBody(tile), cardW() - 20);
        // One 12px row per task, so a multi-item quest gets room for its icons instead of overlapping
        // the play bar.
        int taskRows = Math.min(tile.tasks().size(), MAX_TASK_ROWS);
        int tasksBlock = taskRows == 0 ? 0 : 10 + taskRows * 12;
        int natural = 20 + titleLines * 10 + 4 + bodyLines * 10 + 36 + 18 + tasksBlock;
        int h = Math.min(214, Math.max(88, natural));
        // Scale the height with the width on a narrow GUI so the card stays proportionate and on-screen.
        return Math.max(72, Math.round(h * cardScale()));
    }

    private int wrapLineCount(String text, int max) {
        return Math.max(1, wrapLines(text, max).size());
    }

    private int cardX() {
        int minX = boardLeft() + 8;
        int x = width - cardW() - 16 + inspectOffX;
        // Keep inspect fully on the board (Jerry: was behind sidebar).
        if (x < minX) {
            inspectOffX += (minX - x);
            x = minX;
        }
        // Clamp into [minX, maxX] so the floating description can move freely left-right,
        // not only up-down (prior Math.min(minX, ...) pinned X to the left gutter).
        int maxX = Math.max(minX, width - cardW() - 4);
        return Math.max(minX, Math.min(maxX, x));
    }

    private int cardY() {
        int y = 28 + inspectOffY;
        return Math.max(4, Math.min(height - cardH() - 4, y));
    }

    private int contentWidth() {
        return Math.max(1, width - boardLeft());
    }

    private int contentHeight() {
        return Math.max(1, height - TOP_H);
    }

    private int sidebarWidth() {
        return sidebarCollapsed ? 0 : sidebarW;
    }

    private int boardLeft() {
        return sidebarCollapsed ? TAB_W : sidebarWidth();
    }

    private int introImageH() {
        return Math.max(48, Math.round((height - TOP_H) * 0.55f));
    }

    private int introBodyH() {
        return Math.max(32, height - TOP_H - introImageH());
    }

    private int[] introImagePane() {
        int pad = 8;
        int left = boardLeft() + pad;
        int top = TOP_H + pad;
        int w = Math.max(16, width - left - pad);
        int h = Math.max(16, introImageH() - pad * 2);
        return new int[]{left, top, w, h};
    }

    private int[] introBodyPane() {
        int pad = 8;
        int left = boardLeft() + pad;
        int top = TOP_H + introImageH() + pad;
        int w = Math.max(16, width - left - pad);
        int h = Math.max(16, introBodyH() - pad * 2);
        return new int[]{left, top, w, h};
    }

    private boolean overIntroBody(double mouseX, double mouseY) {
        int[] p = introBodyPane();
        return over(p[0], p[1], p[2], p[3], mouseX, mouseY);
    }

    private String introBodyText() {
        String body = chapter.intro().map(ChapterIntro::body).orElse("");
        String firstDesc = chapter.tiles().isEmpty() ? "" : chapter.tiles().getFirst().description();
        return IntroMarkup.resolveBody(body, chapter.title(), firstDesc);
    }

    private void drawIntro(GuiGraphics graphics) {
        int[] image = introImagePane();
        MockChrome.panel(graphics, image[0], image[1], image[2], image[3], QuestColors.CARD, QuestColors.SIDEBAR_EDGE, 0);
        MockChrome.box(graphics, image[0] + 1, image[1] + 1, image[2] - 2, image[3] - 2, QuestColors.VOID);
        chapter.intro().flatMap(ChapterIntro::image).ifPresent(texture -> drawIntroImage(graphics, texture, image));

        int[] text = introBodyPane();
        MockChrome.panel(graphics, text[0], text[1], text[2], text[3], QuestColors.CARD, QuestColors.SIDEBAR_EDGE, 0);
        int innerX = text[0] + 6;
        int innerY = text[1] + 6;
        int innerW = Math.max(8, text[2] - 12);
        int innerH = Math.max(8, text[3] - 12);
        String body = introBodyText();
        introBodyContentH = IntroMarkup.contentHeight(font, body, innerW, QuestColors.TEXT);
        int maxScroll = Math.max(0, introBodyContentH - innerH);
        introBodyScroll = Math.max(0, Math.min(introBodyScroll, maxScroll));
        // Typewriter reveal on first open of the chapter. Scrollbar measurements use the full body so the
        // thumb does not creep while the text types in.
        String drawnBody = body;
        if (QuestConfig.INTRO_TYPEWRITER.get()) {
            float p = UiFx.progress(fxChapterOpenedAt, TYPEWRITER_MS);
            if (p < 1f) {
                int chars = Math.max(1, Math.round(body.length() * UiFx.easeOut(p)));
                drawnBody = body.substring(0, Math.min(chars, body.length()));
            }
        }
        graphics.enableScissor(innerX, innerY, innerX + innerW, innerY + innerH);
        IntroMarkup.draw(graphics, font, drawnBody, innerX, innerY, innerW, QuestColors.TEXT, introBodyScroll);
        if (UiFx.enabled() && !UiFx.finished(fxChapterOpenedAt, 450L)) {
            float grown = UiFx.easeOut(UiFx.progress(fxChapterOpenedAt, 450L));
            String act = chapter.title().isBlank() ? chapter.id().getPath() : chapter.title();
            int full = Math.round(font.width(act) * 1.5f);
            int lineW = Math.max(1, Math.min(innerW, Math.round(full * grown)));
            MockChrome.box(graphics, innerX, innerY + 16, lineW, 1, QuestColors.EDIT);
        }
        graphics.disableScissor();
        if (maxScroll > 0) {
            int trackX = text[0] + text[2] - 4;
            int thumbH = Math.max(8, innerH * innerH / Math.max(innerH + maxScroll, 1));
            int thumbY = innerY + (int) ((innerH - thumbH) * (introBodyScroll / (double) maxScroll));
            MockChrome.box(graphics, trackX, innerY, 2, innerH, QuestColors.SIDEBAR_EDGE);
            MockChrome.box(graphics, trackX, thumbY, 2, thumbH, QuestColors.SIDEBAR_HEADER);
        }
    }

    private void drawIntroImage(GuiGraphics graphics, ResourceLocation texture, int[] pane) {
        if (minecraft == null || minecraft.getResourceManager().getResource(texture).isEmpty()) {
            return;
        }
        int[] size = introTexSize.computeIfAbsent(texture, this::readTextureSize);
        int paneX = pane[0] + 2;
        int paneY = pane[1] + 2;
        int paneW = pane[2] - 4;
        int paneH = pane[3] - 4;
        if (paneW <= 0 || paneH <= 0) {
            return;
        }
        int texW = Math.max(1, size[0]);
        int texH = Math.max(1, size[1]);
        float texAspect = texW / (float) texH;
        float paneAspect = paneW / (float) paneH;
        int dw;
        int dh;
        if (texAspect > paneAspect) {
            dw = paneW;
            dh = Math.max(1, Math.round(paneW / texAspect));
        } else {
            dh = paneH;
            dw = Math.max(1, Math.round(paneH * texAspect));
        }
        int dx = paneX + (paneW - dw) / 2;
        int dy = paneY + (paneH - dh) / 2;
        float alpha = 1f;
        if (UiFx.enabled() && (QuestConfig.INTRO_TYPEWRITER.get() || QuestConfig.ANIMATIONS.get())) {
            alpha = UiFx.easeOut(UiFx.progress(fxChapterOpenedAt, INTRO_IMAGE_MS));
        }
        graphics.setColor(1f, 1f, 1f, Math.max(0.05f, alpha));
        graphics.blit(texture, dx, dy, 0f, 0f, dw, dh, dw, dh);
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    private int[] readTextureSize(ResourceLocation id) {
        try (var in = minecraft.getResourceManager().open(id); NativeImage image = NativeImage.read(in)) {
            return new int[]{Math.max(1, image.getWidth()), Math.max(1, image.getHeight())};
        } catch (Exception ignored) {
            return new int[]{256, 256};
        }
    }

    private void bindChapterTheme() {
        QuestColors.apply(BookPalette.resolveOrDefault(chapter));
    }

    /**
     * Members of a tag task's tag, in registry-id order so the mosaic does not reshuffle between frames.
     * Empty for anything that is not an {@code item_tag} task, for a tag no pack has bound, or when the item
     * registry is not bootstrapped (unit tests) — in all three cases the row falls back to a single icon,
     * which is the pre-1.1.168 behaviour.
     */
    static List<Item> taskTagItems(Task task) {
        if (task == null || !"item_tag".equals(task.type()) || task.tagId().isEmpty()) {
            return List.of();
        }
        try {
            return BuiltInRegistries.ITEM.getTag(TagKey.create(Registries.ITEM, task.tagId().get()))
                    .map(tag -> tag.stream()
                            .map(Holder::value)
                            .filter(item -> !"minecraft:air".equals(BuiltInRegistries.ITEM.getKey(item).toString()))
                            .sorted(Comparator.comparing(item -> BuiltInRegistries.ITEM.getKey(item).toString()))
                            .toList())
                    .orElseGet(List::of);
        } catch (RuntimeException | LinkageError registriesUnavailable) {
            return List.of();
        }
    }

    /** Cells a mosaic of {@code memberCount} members can show inside {@code stripBudget} pixels, 0 if none fit. */
    static int mosaicIcons(int memberCount, int stripBudget) {
        if (memberCount <= 0 || stripBudget < MOSAIC_CELL) {
            return 0;
        }
        int fit = (stripBudget - MOSAIC_CELL) / MOSAIC_PITCH + 1;
        return Math.min(Math.min(memberCount, MOSAIC_MAX), fit);
    }

    /** Members left over after {@code icons} cells were drawn, i.e. the number on the {@code +N} chip. */
    static int mosaicOverflow(int memberCount, int icons) {
        return Math.max(0, memberCount - Math.max(0, icons));
    }

    /** Width of {@code n} mosaic cells including the gaps between them. */
    static int mosaicStripWidth(int n) {
        return n <= 0 ? 0 : n * MOSAIC_PITCH - MOSAIC_GAP;
    }

    /**
     * Room the mosaic may use in one task row: everything between the icon inset and the label band, once
     * the label's own width is reserved. Tag rows therefore never shrink the label to make room for icons.
     */
    static int labelStripBudget(int cardX, int cardW, int naturalLabelW) {
        int labelRight = cardX + cardW - LABEL_RIGHT_MARGIN;
        int labelStart = labelRight - Math.max(0, naturalLabelW);
        return Math.max(0, labelStart - STRIP_LABEL_GAP - (cardX + STRIP_X));
    }

    /** Screen pixels the {@code +N} chip needs, including the gap that separates it from the strip. */
    private int overflowChipSpan(int overflow) {
        return (font.width("+" + overflow) + 1) / 2 + 2;
    }

    /** A count badge drawn over an icon, kept above the item so the digits stay readable. */
    private void drawCountOver(GuiGraphics graphics, int count, int ix, int iy) {
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 200);
        tinyString(graphics, count + "x", ix, iy, QuestColors.TEXT);
        pose.popPose();
    }

    /**
     * The stack that visually represents a task's target, or empty for tasks with no item to show.
     * Item tasks show the item; block / fluid / observation tasks show the block or fluid as an item,
     * because that is what the player recognises and what JEI can look up.
     */
    static ItemStack taskStack(Task task) {
        if (task == null) {
            return ItemStack.EMPTY;
        }
        Optional<net.minecraft.resources.ResourceLocation> item = task.itemId();
        if (item.isEmpty()) {
            item = task.blockId();
        }
        if (item.isEmpty() && "fluid".equals(task.type())) {
            // Show a fluid as its bucket so there is a concrete item to name and look up in JEI. Lava
            // resolves to air, which must fall through to EMPTY rather than an "empty" stack of air.
            item = task.fluidId().flatMap(fluid -> BuiltInRegistries.FLUID.getOptional(fluid)
                    .map(f -> BuiltInRegistries.ITEM.getKey(f.getBucket()))
                    .filter(id -> !"minecraft:air".equals(id.toString())));
        }
        ItemStack stack = item.flatMap(BuiltInRegistries.ITEM::getOptional)
                .map(i -> new ItemStack(i, Math.max(1, task.required())))
                .orElse(ItemStack.EMPTY);
        return stack.isEmpty() ? ItemStack.EMPTY : stack;
    }

    /**
     * True when the click should open JEI rather than the player's task, i.e. the task wants a concrete
     * item and we can name it. "Get X" opens X's crafting recipes; everything else opens its usages.
     */
    static boolean opensRecipe(Task task) {
        return task != null && ("obtain".equals(task.type()) || "submit".equals(task.type()));
    }

    /** Open a modal and stamp its entrance time so the shimmer sweep starts with the panel. */
    private void openModal(ModalKind kind, String tileId) {
        if (modal != kind) {
            fxModalOpenedAt = UiFx.nowMs();
        }
        modal = kind;
        modalTileId = tileId;
    }

    /**
     * Sparse per-theme ambience on the board only (never behind the sidebar, where text lives). Every
     * particle count is fixed and small, and each particle is a 1:1 blit, so cost is bounded and
     * independent of window size.
     */
    private void drawThemeAmbience(GuiGraphics graphics) {
        if (!UiFx.enabled() || chapter == null) {
            return;
        }
        boolean on = true;
        try {
            on = QuestConfig.AMBIENT_THEME_FX.get();
        } catch (Exception ignored) {
            // Config not ready — leave it on.
        }
        if (!on) {
            return;
        }
        int left = boardLeft();
        int top = TOP_H;
        int w = width - left;
        int h = height - top;
        if (w < 24 || h < 24) {
            return;
        }
        String theme = chapter.theme();
        if (theme == null) {
            return;
        }
        switch (theme) {
            case "ember_forge", "nether_scar" -> drawAmbientEmbers(graphics, left, top, w, h);
            case "ocean_depths" -> drawAmbientCaustics(graphics, left, top, w, h);
            case "midnight_royalty", "end_chorus" -> drawAmbientStars(graphics, left, top, w, h);
            case "frost_peak" -> drawAmbientVignette(graphics, left, top, w, h);
            case "paper_scroll", "desert_oasis" -> drawAmbientMotes(graphics, left, top, w, h);
            case "copper_circuit" -> drawAmbientDrift(graphics, left, top, w, h, QuestColors.EDIT);
            case "verdant_grove" -> drawAmbientDrift(graphics, left, top, w, h, QuestColors.CURRENT);
            default -> {
            }
        }
    }

    /** Warm specks drifting slowly across a parchment page. */
    private void drawAmbientMotes(GuiGraphics graphics, int left, int top, int w, int h) {
        final int count = 20;
        for (int i = 0; i < count; i++) {
            float rx = UiFx.hash01(0x6B43, i);
            float ry = UiFx.hash01(0x2A97, i);
            float speed = 0.4f + UiFx.hash01(0x40D1, i);
            float life = UiFx.phase(Math.round(14000f / speed), i * 613L);
            int px = left + (int) (((rx + life) % 1f) * w);
            int py = top + (int) (ry * h);
            float fade = 1f - Math.abs(life * 2f - 1f);
            MockChrome.box(graphics, px, py, 1, 1, UiFx.withAlpha(QuestColors.MUTED, 0.05f + 0.16f * fade));
        }
    }

    /** Sparse colour-coded specks falling slowly — circuit traces / drifting leaves. */
    private void drawAmbientDrift(GuiGraphics graphics, int left, int top, int w, int h, int base) {
        final int count = 18;
        for (int i = 0; i < count; i++) {
            float rx = UiFx.hash01(0x1F2D, i);
            float speed = 0.3f + UiFx.hash01(0x73C5, i);
            float life = UiFx.phase(Math.round(17000f / speed), i * 811L);
            int px = left + (int) (rx * w) + (int) (Math.sin(life * 6.283f + i * 2f) * 6f);
            int py = top + (int) ((life * h + i * 37) % h);
            float fade = Math.max(0f, 1f - Math.abs(life * 2f - 1f));
            MockChrome.box(graphics, px, py, 1, 1, UiFx.withAlpha(base, 0.04f + 0.14f * fade));
        }
    }

    /** Slow parallax starfield: fixed lattice, each star twinkling on its own offset. */
    private void drawAmbientStars(GuiGraphics graphics, int left, int top, int w, int h) {
        final int count = 34;
        for (int i = 0; i < count; i++) {
            float rx = UiFx.hash01(0x51A2, i);
            float ry = UiFx.hash01(0x77B3, i);
            float depth = UiFx.hash01(0x1C4D, i);
            float driftX = UiFx.phase(26000L, Math.round(depth * 9000f)) * w;
            int px = left + (int) ((rx * w + driftX) % w);
            int py = top + (int) (ry * h);
            float twinkle = UiFx.wave(2400L + Math.round(depth * 2600f), i * 137L);
            MockChrome.box(graphics, px, py, 1, 1, UiFx.withAlpha(QuestColors.TEXT, 0.06f + 0.20f * twinkle));
        }
    }

    /** Warm motes rising from the bottom of the board. */
    private void drawAmbientEmbers(GuiGraphics graphics, int left, int top, int w, int h) {
        final int count = 22;
        for (int i = 0; i < count; i++) {
            float rx = UiFx.hash01(0x3F11, i);
            float speed = 0.5f + UiFx.hash01(0x9B27, i);
            float life = UiFx.phase(Math.round(5200f / speed), i * 421L);
            int px = left + (int) (rx * w) + (int) (Math.sin(life * 6.283f + i) * 4f);
            int py = top + h - 6 - (int) (life * (h - 12));
            float fade = Math.max(0f, 1f - Math.abs(life * 2f - 1f));
            MockChrome.box(graphics, px, py, 1, 1, UiFx.withAlpha(QuestColors.REWARD, 0.10f + 0.28f * fade));
        }
    }

    /** Horizontal caustic bands drifting at different speeds. */
    private void drawAmbientCaustics(GuiGraphics graphics, int left, int top, int w, int h) {
        final int bands = 7;
        for (int i = 0; i < bands; i++) {
            float depth = UiFx.hash01(0x2D6F, i);
            int period = 9000 + Math.round(depth * 7000f);
            float x = UiFx.phase(period, i * 977L) * (w + 120f) - 120f;
            int y = top + 18 + (int) (depth * (h - 36));
            float alpha = 0.05f + 0.10f * UiFx.wave(period, i * 977L);
            MockChrome.box(graphics, left + Math.max(0, (int) x), y, 96, 2, UiFx.withAlpha(QuestColors.NEW, alpha));
        }
    }

    /** Cold edge vignette; static geometry, only alpha breathes so it reads as cold rather than busy. */
    private void drawAmbientVignette(GuiGraphics graphics, int left, int top, int w, int h) {
        int color = UiFx.withAlpha(QuestColors.NEW, 0.05f + 0.05f * UiFx.wave(6000L, 0L));
        MockChrome.box(graphics, left, top, w, 2, color);
        MockChrome.box(graphics, left, top + h - 2, w, 2, color);
        MockChrome.box(graphics, left, top, 2, h, color);
        MockChrome.box(graphics, left + w - 2, top, 2, h, color);
    }

    /** Optional chapter image background — board area only (right of sidebar). */
    private void drawBoardBackgroundImage(GuiGraphics graphics) {
        if (chapter == null || chapter.background().isEmpty()) {
            return;
        }
        ChapterBackground bg = chapter.background().get();
        if (!bg.useImage()) {
            return;
        }
        ResourceLocation texture = bg.image().orElse(null);
        if (texture == null) {
            return;
        }
        int left = boardLeft();
        int top = TOP_H;
        int w = Math.max(0, width - left);
        int h = Math.max(0, height - top);
        if (w <= 0 || h <= 0) {
            return;
        }
        float opacity = bg.opacity();
        graphics.setColor(1f, 1f, 1f, opacity);
        // Tile 64×64 so ImmediatelyFast/Iris keep the blit (no stretch of tiny px).
        final int tile = 64;
        for (int py = top; py < top + h; py += tile) {
            int ph = Math.min(tile, top + h - py);
            for (int px = left; px < left + w; px += tile) {
                int pw = Math.min(tile, left + w - px);
                graphics.blit(texture, px, py, 0f, 0f, pw, ph, tile, tile);
            }
        }
        graphics.setColor(1f, 1f, 1f, 1f);
    }

    private int logX() {
        return width - 8 - LOG_W;
    }

    private int claimAllW() {
        return pillW("CLAIM ALL");
    }

    private int claimAllX() {
        return logX() - 6 - claimAllW();
    }

    private int fitX() {
        int right = claimAllShown() ? claimAllX() : logX();
        return right - 6 - FIT_S;
    }

    private int searchX() {
        // Expanded-sidebar anchor — collapse must not move or grow the search field.
        return Math.max(sidebarW + 8, 72);
    }

    private int searchW() {
        int right = isIntroChapter() ? logX() - 8 : fitX() - 8;
        return Math.max(48, right - searchX());
    }

    // ---- F6 layout helpers (bundle t_5a9d5cde). WIRED: the draw sites call these; every rule has
    // one source, and QuestBookScreenF6LayoutTest pins each constant and relation. Domain: every helper
    // takes (w, h[, inset]) only - screen-fixed chrome, no camera term (camera-invariance rule).
    static final int LOG_CLIP_BOTTOM_INSET = 22;   // 4.5 gui px clearance on the measured h-17.5 plane
    static final int LOG_CLIP_RIGHT_INSET = 21;    // 4.0 gui px clearance on the w-17 inner edge
    static final double LOG_INNER_BOTTOM = 17.5;
    static final int LOG_INNER_RIGHT = 17;
    static final int SIDEBAR_BOTTOM_INSET = 20;    // reserved below the last row: widest pitch 19 + 1
    static final float THUMB_ALPHA_FLOOR = 0.4f;   // phase 2 wire: the scroll thumb keeps this floor

    static int logClipTop(int y) {
        return y + 4;
    }

    static int logClipBottom(int h) {
        return h - LOG_CLIP_BOTTOM_INSET;
    }

    static int logClipRight(int w) {
        return w - LOG_CLIP_RIGHT_INSET;
    }

    static double logInnerBottom(int h) {
        return h - LOG_INNER_BOTTOM;
    }

    static int logViewH(int h, int y) {
        return Math.max(8, logClipBottom(h) - logClipTop(y));
    }

    /** Full-ink rule: a row draws only when its whole ink box is inside BOTH planes - never a partial
     *  row. inkH is font.lineHeight at the call site (one source; 9 at the shipped font = the measured
     *  max incl. descenders). */
    static boolean logRowFits(int ry, int inkH, int clipTop, int clipBottom) {
        return ry >= clipTop && ry + inkH <= clipBottom;
    }

    /** One margin for wrap and clip: called with the CLASS width. 16 is the layout origin (the log
     *  body draws at x = 16) and the row's own inset is subtracted, so the widest row ends exactly at
     *  the clip plane instead of a fixed width that happens to fit. */
    static int logWrapWidth(int w, int rowInset) {
        return logClipRight(w) - 16 - rowInset;
    }

    /** Full-row rule at both edges: a row draws only when it is entirely inside [top, bottom]. */
    static boolean sidebarRowFullyInside(int rowY, int rowH, int top, int bottom) {
        return rowY >= top && rowY + rowH <= bottom;
    }

    static int sidebarMaxScrollFor(int listH, int h) {
        return Math.max(0, listH + SIDEBAR_BOTTOM_INSET - Math.max(1, h - TOP_H - 8));
    }

    /** The scroll thumb never dies: fades from 1.0 down to THUMB_ALPHA_FLOOR and stays there. */
    static float sidebarThumbAlphaFor(float idleFrac) {
        return THUMB_ALPHA_FLOOR + (1f - THUMB_ALPHA_FLOOR) * (1f - UiFx.clamp01((idleFrac - 1f) / 1.5f));
    }

    static boolean moreBelow(int scroll, int scrollMax) {
        return scroll < scrollMax;
    }

    static int tabYFor(int h) {
        return Math.max(28, h / 2 - TAB_H / 2);
    }

    private int tabX() {
        return sidebarCollapsed ? 0 : Math.max(0, sidebarWidth() - TAB_W);
    }

    private int tabY() {
        return Math.max(28, height / 2 - TAB_H / 2);
    }

    private boolean overTab(double mx, double my) {
        return over(tabX(), tabY(), TAB_W, TAB_H, mx, my);
    }

    private boolean overResize(double mx, double my) {
        return mx >= sidebarWidth() - 3 && mx <= sidebarWidth() + 4 && my > 26 && !overTab(mx, my);
    }

    private void toggleSidebar() {
        // Keep tiles on-screen: boardLeft changes with collapse; compensate cameraX so screen()
        // positions (boardLeft + world - camSx) stay put. Jerry: minimizing sidebar must not move the board.
        int oldLeft = boardLeft();
        sidebarCollapsed = !sidebarCollapsed;
        savedSidebarCollapsed = sidebarCollapsed;
        int delta = boardLeft() - oldLeft;
        if (delta != 0 && zoom != 0f) {
            cameraX += delta / (double) zoom;
        }
        layoutSearch();
        rebuildTileWidgets();
    }

    private void setSidebarWidth(int widthPx) {
        sidebarW = Math.max(SIDEBAR_MIN, Math.min(SIDEBAR_MAX, widthPx));
        savedSidebarW = sidebarW;
        sidebarCollapsed = false;
        savedSidebarCollapsed = false;
        layoutSearch();
    }

    private void layoutSearch() {
        if (search == null) {
            return;
        }
        search.setX(searchX());
        search.setY(7);
        search.setWidth(searchW());
        search.visible = !logOpen;
    }

    private void blurSearchIfOutside(double mouseX, double mouseY) {
        if (search == null || !search.isFocused() || search.isMouseOver(mouseX, mouseY)) {
            return;
        }
        blurSearch();
    }

    private void blurSearch() {
        if (search != null) {
            search.setFocused(false);
        }
        if (getFocused() == search) {
            setFocused(null);
        }
    }

    private void drawSidebarTab(GuiGraphics graphics) {
        int x = tabX();
        int y = tabY();
        MockChrome.box(graphics, x, y, TAB_W, TAB_H, QuestColors.SIDEBAR);
        MockChrome.frame(graphics, x, y, TAB_W, TAB_H, QuestColors.SIDEBAR_EDGE);
    }

    private int gridX(double mouseX) {
        return Math.floorDiv((int) Math.floor(mouseX - boardLeft() + camSx()), stridePx());
    }

    private int gridY(double mouseY) {
        return Math.floorDiv((int) Math.floor(mouseY + camSy()), stridePx());
    }

    private void commitTextEdit() {
        chapter.tile(selectedId).ifPresent(tile -> {
            Tile next = tile;
            if (editingTitle) {
                next = next.withTitle(titleBuffer);
            }
            if (editingBody) {
                next = next.withDescription(bodyBuffer);
            }
            chapter = chapter.replaceTile(next);
            saveChapter();
        });
        editingTitle = false;
        editingBody = false;
    }

    private void saveChapter() {
        String json = Chapter.CODEC.encodeStart(JsonOps.INSTANCE, chapter).getOrThrow(RuntimeException::new).toString();
        QuestNetwork.sendToServer(new AuthorSaveC2S(json));
    }

    private void openReport() {
        String base = QuestConfig.GITHUB_ISSUES_URL.get();
        String url = base + (base.contains("?") ? "&" : "?") + "title=Quest+" + chapter.id().getPath() + "+" + selectedId;
        Util.getPlatform().openUri(URI.create(url));
    }

    private void centerOn(String tileId) {
        chapter.tile(tileId).ifPresent(tile -> {
            selectedId = tile.id();
            expanded = true;
            bounce = 0f;
            cameraX = tile.pos().x() * STRIDE + TILE / 2.0 - contentWidth() / (2.0 * zoom);
            cameraY = tile.pos().y() * STRIDE + TILE / 2.0 - (TOP_H + contentHeight() / 2.0) / zoom;
        });
    }

    private int[] screen(int gx, int gy) {
        return new int[]{
                boardLeft() + gx * stridePx() - camSx(),
                gy * stridePx() - camSy()
        };
    }

    private void pixel(GuiGraphics graphics, Component text, int x, int y, int color) {
        graphics.drawString(font, text.getString(), x, y, color, false);
    }

    private int drawWrapped(GuiGraphics graphics, String text, int x, int y, int max, int color) {
        return drawWrapped(graphics, text, x, y, max, color, Integer.MAX_VALUE);
    }

    /**
     * Wrapped text capped at {@code maxLines}; the last kept line is trimmed with an ellipsis so a clipped
     * description reads as deliberately clipped rather than silently ending mid-sentence.
     *
     * <p>The trim reuses {@link #ellipsize}, which is a sidebar helper that returns {@code ""} below its own
     * minimum width. A body is far wider than that floor, but read the result defensively: an empty trim
     * would silently delete the line, so fall back to the untrimmed row.
     */
    private int drawWrapped(GuiGraphics graphics, String text, int x, int y, int max, int color, int maxLines) {
        int cy = y;
        List<String> rows = wrapLines(text, max);
        int limit = Math.min(rows.size(), Math.max(1, maxLines));
        boolean truncated = rows.size() > limit;
        for (int i = 0; i < limit; i++) {
            String row = rows.get(i);
            if (truncated && i == limit - 1) {
                String marked = ellipsize(row + " more", max);
                row = marked.isEmpty() ? row : marked;
            }
            graphics.drawString(font, row, x, cy, color, false);
            cy += 10;
        }
        return cy;
    }

    private List<String> wrapLines(String text, int max) {
        List<String> lines = new ArrayList<>();
        String normalized = text == null ? "" : text.replace("\r\n", "\n").replace('\r', '\n');
        for (String paragraph : normalized.split("\n", -1)) {
            if (paragraph.isEmpty()) {
                lines.add("");
                continue;
            }
            StringBuilder line = new StringBuilder();
            for (String word : paragraph.split(" ")) {
                String next = line.isEmpty() ? word : line + " " + word;
                if (font.width(next) > max && !line.isEmpty()) {
                    lines.add(line.toString());
                    line = new StringBuilder(word);
                } else {
                    line = new StringBuilder(next);
                }
            }
            if (!line.isEmpty()) {
                lines.add(line.toString());
            }
        }
        return lines.isEmpty() ? List.of("") : lines;
    }

    private static void drawButton(GuiGraphics graphics, int x, int y, int w, int h, String label, int color) {
        MockChrome.box(graphics, x, y, w, h, color);
        drawButtonLabel(graphics, x, y, w, h, label);
    }

    private static void drawButtonLabel(GuiGraphics graphics, int x, int y, int w, int h, String label) {
        drawButtonLabel(graphics, x, y, w, h, label, MockChrome.INK);
    }

    private static void drawButtonLabel(GuiGraphics graphics, int x, int y, int w, int h, String label, int ink) {
        int textW = Minecraft.getInstance().font.width(label);
        int tx = x + Math.max(2, (w - textW) / 2);
        graphics.drawString(Minecraft.getInstance().font, label, tx, y + Math.max(1, (h - 8) / 2), ink, false);
    }

    private static void drawOutlinedButton(GuiGraphics graphics, int x, int y, int w, int h, String label, int color) {
        MockChrome.box(graphics, x, y, w, h, QuestColors.CARD);
        MockChrome.frame(graphics, x, y, w, h, color);
        graphics.drawString(Minecraft.getInstance().font, label, x + 4, y + Math.max(1, (h - 8) / 2), color, false);
    }

    static boolean over(int x, int y, int w, int h, double mx, double my) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public static void drawFrame(GuiGraphics graphics, int x, int y, int w, int h, int color) {
        MockChrome.frame(graphics, x, y, w, h, color);
    }

    private static void drawDiamond(GuiGraphics graphics, int cx, int cy, int color) {
        MockChrome.diamond(graphics, cx, cy, color);
    }

    private static void drawExpandGlyph(GuiGraphics graphics, int x, int y, int color) {
        graphics.drawString(net.minecraft.client.Minecraft.getInstance().font, "+", x, y, color | 0xFF000000, false);
    }

    private static void drawLock(GuiGraphics graphics, int x, int y, int color) {
        MockChrome.padlock(graphics, x + 3, y + 8, color);
    }

    private static void drawPlusOrnament(GuiGraphics graphics, int x, int y, int color) {
        MockChrome.plus(graphics, x + 4, y + 4, color);
    }

    private void drainTestClick() {
        // Avoid Files.exists every client tick; Marionette wait(12) still hits within a few polls.
        if (Minecraft.getInstance().level == null
                || Minecraft.getInstance().level.getGameTime() % 5L != 0L) {
            return;
        }
        java.nio.file.Path path = java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "qq-click.json");
        if (!java.nio.file.Files.exists(path)) {
            return;
        }
        try {
            String raw = java.nio.file.Files.readString(path).trim();
            java.nio.file.Files.deleteIfExists(path);
            if (raw.isEmpty()) {
                return;
            }
            com.google.gson.JsonObject json = com.google.gson.JsonParser.parseString(raw).getAsJsonObject();
            if (json.has("search")) {
                if (search != null) {
                    search.setValue(json.get("search").getAsString());
                    search.setFocused(true);
                    setFocused(search);
                }
                return;
            }
            if (json.has("resync") && json.get("resync").getAsBoolean()) {
                // Marionette FAILED path: after /questqueen reload (or fail), force a widget rebuild
                // from the latest ClientQuestState snapshot before screenshot.
                rebuildTileWidgets();
                writeProbeJson();
                return;
            }
            if (json.has("probe") && json.get("probe").getAsBoolean()) {
                writeProbeJson();
                return;
            }
            if (json.has("clearHover") && json.get("clearHover").getAsBoolean()) {
                hoverOverrideX = Integer.MIN_VALUE;
                hoverOverrideY = Integer.MIN_VALUE;
                applyPathHover(-1000, -1000);
                return;
            }
            if (json.has("hoverX") && json.has("hoverY")) {
                hoverOverrideX = json.get("hoverX").getAsInt();
                hoverOverrideY = json.get("hoverY").getAsInt();
                applyPathHover(hoverOverrideX, hoverOverrideY);
                writeProbeJson();
                return;
            }
            if (json.has("hoverPath")) {
                com.google.gson.JsonObject hover = json.getAsJsonObject("hoverPath");
                String from = hover.get("from").getAsString();
                String to = hover.get("to").getAsString();
                for (PathArrow arrow : collectPathArrows()) {
                    if (from.equals(arrow.from()) && to.equals(arrow.to())) {
                        hoverOverrideX = arrow.x();
                        hoverOverrideY = arrow.y();
                        applyPathHover(hoverOverrideX, hoverOverrideY);
                        writeProbeJson();
                        return;
                    }
                }
                return;
            }
            if (json.has("chapter")) {
                ResourceLocation id = ResourceLocation.parse(json.get("chapter").getAsString());
                ClientQuestState.chapter(id).ifPresent(next -> {
                    if (!ClientQuestState.isChapterUnlocked(next) && !authoring) {
                        if (!next.hideUntilUnlocked()) {
                            showLockedChapterCue(next.id());
                        }
                        writeProbeJson();
                        return;
                    }
                    openChapter(next.id());
                });
                return;
            }
            if (json.has("tile")) {
                chapter.tile(json.get("tile").getAsString()).ifPresent(tile -> {
                    int[] s = screen(tile.pos().x(), tile.pos().y());
                    int size = tilePx();
                    clickGui(s[0] + size / 2.0, s[1] + size / 2.0);
                });
                return;
            }
            if (json.has("confirm") && json.get("confirm").getAsBoolean()) {
                ModalLayout box = modalLayout();
                clickGui(box.btnX() + box.btnW() / 2.0, box.btnY() + box.btnH() / 2.0);
                return;
            }
            if (json.has("close") && json.get("close").getAsBoolean()) {
                if (expanded) {
                    clickGui(cardX() + cardW() - 8, cardY() + 6);
                } else {
                    onClose();
                }
                return;
            }
            if (json.has("pin") && json.get("pin").getAsBoolean()) {
                chapter.tile(selectedId).ifPresent(tile -> {
                    PlayBar bar = playBar(tile, cardX(), cardY(), cardW(), cardH());
                    if (bar.pin()) {
                        clickGui(bar.pinX() + 8, bar.y() + 6);
                    }
                });
                return;
            }
            if (json.has("inspectDrag") && json.get("inspectDrag").isJsonObject() && showInspectPanel()) {
                com.google.gson.JsonObject drag = json.getAsJsonObject("inspectDrag");
                int dx = drag.has("dx") ? drag.get("dx").getAsInt() : 0;
                int dy = drag.has("dy") ? drag.get("dy").getAsInt() : 0;
                double x0 = cardX() + Math.min(40, cardW() / 2.0);
                double y0 = cardY() + 6;
                mouseClicked(x0, y0, 0);
                mouseDragged(x0 + dx, y0 + dy, 0, dx, dy);
                mouseReleased(x0 + dx, y0 + dy, 0);
                syncOverlays();
                writeProbeJson();
                return;
            }
            if (json.has("action") && json.get("action").getAsBoolean()) {
                chapter.tile(selectedId).ifPresent(tile -> {
                    PlayBar bar = playBar(tile, cardX(), cardY(), cardW(), cardH());
                    if (bar.action()) {
                        clickGui(bar.actionX() + 20, bar.y() + 6);
                    }
                });
                writeProbeJson();
                return;
            }
            if (json.has("jei")) {
                int idx = json.get("jei").isJsonPrimitive() && json.get("jei").getAsJsonPrimitive().isNumber()
                        ? json.get("jei").getAsInt() : 0;
                if (!taskIconHits.isEmpty()) {
                    TaskIconHit hit = taskIconHits.stream()
                            .filter(h -> h.taskIndex() == idx)
                            .findFirst()
                            .orElse(taskIconHits.getFirst());
                    // Click the strip's own centre: a tag row's hit rect is wider than one 16x16 icon.
                    clickGui(hit.centreX(), hit.centreY());
                } else {
                    openInJei(idx);
                }
                writeProbeJson();
                return;
            }
            if (json.has("claim")) {
                chapter.tile(selectedId).ifPresent(tile -> {
                    PlayBar bar = playBar(tile, cardX(), cardY(), cardW(), cardH());
                    if (bar.choice()) {
                        int which = json.get("claim").getAsInt();
                        clickGui((which == 0 ? bar.choiceAX() : bar.choiceBX()) + 16, bar.y() + 6);
                    }
                });
                return;
            }
            if (json.has("sidebarTitle") || json.has("chromeTitle") || json.has("chrome")) {
                BookChrome cur = resolveChrome();
                if (json.has("sidebarTitle")) {
                    cur = cur.withTitle(json.get("sidebarTitle").getAsString());
                } else if (json.has("chromeTitle")) {
                    cur = cur.withTitle(json.get("chromeTitle").getAsString());
                }
                if (json.has("sidebarTitleColor")) {
                    cur = cur.withColor(json.get("sidebarTitleColor").getAsInt());
                }
                if (json.has("sidebarTitleScale")) {
                    cur = cur.withScale(json.get("sidebarTitleScale").getAsFloat());
                }
                if (json.has("sidebarTitleShadow")) {
                    cur = cur.withShadow(json.get("sidebarTitleShadow").getAsBoolean());
                }
                if (json.has("chrome") && json.get("chrome").isJsonObject()) {
                    var c = json.getAsJsonObject("chrome");
                    if (c.has("sidebarTitle")) cur = cur.withTitle(c.get("sidebarTitle").getAsString());
                    if (c.has("titleColor")) cur = cur.withColor(c.get("titleColor").getAsInt());
                    if (c.has("titleScale")) cur = cur.withScale(c.get("titleScale").getAsFloat());
                    if (c.has("titleShadow")) cur = cur.withShadow(c.get("titleShadow").getAsBoolean());
                }
                applyChrome(cur);
                writeProbeJson();
                return;
            }
            if (json.has("sidebar")) {
                String mode = json.get("sidebar").getAsString();
                if ("toggle".equals(mode)) {
                    toggleSidebar();
                } else if ("collapse".equals(mode)) {
                    if (!sidebarCollapsed) {
                        toggleSidebar();
                    }
                } else if ("expand".equals(mode)) {
                    if (sidebarCollapsed) {
                        toggleSidebar();
                    }
                } else if ("wide".equals(mode)) {
                    setSidebarWidth(220);
                } else if ("narrow".equals(mode)) {
                    setSidebarWidth(SIDEBAR_MIN);
                }
                writeProbeJson();
                return;
            }
            if (json.has("sidebarScroll")) {
                sidebarScroll = json.get("sidebarScroll").getAsInt();
                clampSidebarScroll();
                rebuildSidebarRows();
                writeProbeJson();
                return;
            }
            if (json.has("sidebarScrollBy")) {
                sidebarScroll += json.get("sidebarScrollBy").getAsInt();
                clampSidebarScroll();
                rebuildSidebarRows();
                writeProbeJson();
                return;
            }
            if (json.has("previewZoom")) {
                // Dev-only 0.5 tier (Troi's pre-registered criterion, ledger 4739; Picard's queue addendum
                // 10). Probe-only by construction: no keybind, no options entry and no player command
                // reaches this flag, and while it is off the shipped ladder and floor are what everybody
                // gets. 0.5 is the only accepted value - 0.25 stays refused. Logged loudly so the fit line
                // of any frame shot under it can be read next to the gate state that produced it.
                float want = json.get("previewZoom").getAsFloat();
                previewHalf = want > 0.25f && want <= 0.5f;
                QuestQueen.LOGGER.info("Quest book previewZoom={} halfRung={} floor={}",
                        want, previewHalf, fitFloor());
                return;
            }
            if (json.has("fitTarget")) {
                // Flag only, so both targets can be captured in frames for Troi's comparison. The default
                // stays ANCHOR and this cannot change it by accident: an unknown value leaves it untouched.
                String want = json.get("fitTarget").getAsString();
                if ("count".equalsIgnoreCase(want)) {
                    setFitTarget(FitCamera.Target.COUNT);
                } else if ("anchor".equalsIgnoreCase(want)) {
                    setFitTarget(FitCamera.Target.ANCHOR);
                }
                QuestQueen.LOGGER.info("Quest book fit target={}", fitTarget());
                return;
            }
            if (json.has("fit") && json.get("fit").getAsBoolean()) {
                fitAllContent();
                return;
            }
            if (json.has("cameraX") || json.has("cameraY") || json.has("zoom")) {
                if (json.has("cameraX")) {
                    cameraX = json.get("cameraX").getAsDouble();
                }
                if (json.has("cameraY")) {
                    cameraY = json.get("cameraY").getAsDouble();
                }
                if (json.has("zoom")) {
                    setZoom(json.get("zoom").getAsFloat());
                }
                return;
            }
            if (json.has("log") && json.get("log").getAsBoolean()) {
                clickGui(logX() + LOG_W / 2.0, 13);
                return;
            }
            if (json.has("x") && json.has("y")) {
                clickGui(json.get("x").getAsDouble(), json.get("y").getAsDouble());
            }
        } catch (Exception exception) {
            QuestQueen.LOGGER.warn("qq-click.json failed: {}", exception.toString());
        }
    }

    private void writeProbeJson() {
        com.google.gson.JsonObject out = new com.google.gson.JsonObject();
        out.addProperty("searchFocused", search != null && search.isFocused());
        out.addProperty("searchValue", search == null ? "" : search.getValue());
        BookChrome probeChrome = resolveChrome();
        out.addProperty("sidebarTitle", probeChrome.displayTitle());
        out.addProperty("sidebarTitleColor", probeChrome.titleColor());
        out.addProperty("sidebarTitleScale", probeChrome.titleScale());
        out.addProperty("sidebarTitleShadow", probeChrome.titleShadow());
        out.addProperty("chromeEditorOpen", chromeEditorOpen);
        out.addProperty("selected", selectedId);
        out.addProperty("expanded", expanded);
        out.addProperty("showInspect", showInspectPanel());
        out.addProperty("inspectOffX", inspectOffX);
        out.addProperty("inspectOffY", inspectOffY);
        out.addProperty("cardX", cardX());
        out.addProperty("cardY", cardY());
        out.addProperty("cardW", cardW());
        out.addProperty("cardH", cardH());
        out.addProperty("inspectTile", inspectTileId());
        out.addProperty("overlayX", inspectPanel.getX());
        out.addProperty("overlayY", inspectPanel.getY());
        out.addProperty("overlayW", inspectPanel.getWidth());
        out.addProperty("overlayH", inspectPanel.getHeight());
        out.addProperty("overlayVisible", inspectPanel.visible);
        out.addProperty("chromeMatchesCard", inspectPanel.getX() == cardX() && inspectPanel.getY() == cardY()
                && inspectPanel.getWidth() == cardW() && inspectPanel.getHeight() == cardH());
        out.addProperty("modal", modal.name());
        out.addProperty("modalTile", modalTileId);
        out.addProperty("chapterLockedModal", modal == ModalKind.GOTCHA && (modalTileId == null || modalTileId.isEmpty()));
        out.addProperty("gotchaShowCount", ClientQuestState.gotchaShowCount);
        out.addProperty("initCount", INIT_COUNT);
        String cueKind = ClientQuestState.lastLockedCueKind;
        out.addProperty("lastLockedCueKind", cueKind == null ? "" : cueKind);
        out.addProperty("chapter", chapter.id().toString());
        // Layout diagnostics: the raw inputs behind cardX/cardY/cardW/cardH, so an audit can tell whether a
        // card is off-window because of the window, the sidebar, or the drag offset.
        out.addProperty("guiW", width);
        out.addProperty("guiH", height);
        out.addProperty("sidebarW", sidebarWidth());
        out.addProperty("boardLeftPx", boardLeft());
        out.addProperty("tilePxNow", tilePx());
        // The dev 0.5 preview gate state: a frame can then be bound to the mode that produced it, and a
        // reader can tell a preview capture from a shipped one instead of inferring it from the rung.
        out.addProperty("previewHalf", previewHalf);
        out.addProperty("cardRight", cardX() + cardW());
        out.addProperty("cardBottom", cardY() + cardH());
        out.addProperty("cardScale", cardScale());
        out.addProperty("taskRowsDrawn", drawnTaskRows);
        // Report the same budget the last paint used, so a probe can verify it against the drawn layout.
        out.addProperty("bodyBudget", lastBodyBudget);
        boolean selectedXor = !selectedId.isEmpty()
                && chapter.tile(selectedId).map(t -> ClientQuestState.needsXorChoice(chapter, t)).orElse(false);
        List<String> unresolved = ClientQuestState.unresolvedXorKeys();
        out.addProperty("needsXorSelected", selectedXor);
        out.addProperty("needsXor", !unresolved.isEmpty());
        com.google.gson.JsonArray xorArr = new com.google.gson.JsonArray();
        for (String key : unresolved) {
            xorArr.add(key);
        }
        out.add("unresolvedXor", xorArr);
        out.addProperty("xorCue", modal == ModalKind.NONE && !unresolved.isEmpty());
        out.addProperty("xorDismissed", !selectedId.isEmpty()
                && xorDismissed.contains(ClientQuestState.xorKey(chapter, selectedId)));
        boolean selectedDone = !selectedId.isEmpty()
                && ClientQuestState.progress.tileCompleted(chapter.id().toString(), selectedId);
        out.addProperty("selectedDone", selectedDone);
        com.google.gson.JsonObject visuals = new com.google.gson.JsonObject();
        com.google.gson.JsonArray failedTiles = new com.google.gson.JsonArray();
        for (Tile tile : chapter.tiles()) {
            TileVisual visual = ClientQuestState.visual(chapter, tile, authoring, selectedId);
            visuals.addProperty(tile.id(), visual.name());
            if (visual == TileVisual.FAILED) {
                failedTiles.add(tile.id());
            }
        }
        out.add("visuals", visuals);
        out.add("failedTiles", failedTiles);
        com.google.gson.JsonArray lockedExpand = new com.google.gson.JsonArray();
        com.google.gson.JsonArray bareRewards = new com.google.gson.JsonArray();
        com.google.gson.JsonArray filledRewards = new com.google.gson.JsonArray();
        com.google.gson.JsonArray completedBareRewards = new com.google.gson.JsonArray();
        com.google.gson.JsonArray completedFilledRewards = new com.google.gson.JsonArray();
        com.google.gson.JsonObject rewardFaces = new com.google.gson.JsonObject();
        int pathGates = 0;
        int inboundPorts = 0;
        int blueArrows = 0;
        int redArrows = 0;
        for (Tile tile : chapter.tiles()) {
            TileVisual visual = ClientQuestState.visual(chapter, tile, authoring, selectedId);
            if (visual == TileVisual.LOCKED && showExpandGlyph(visual, headerEdge(tile, visual))) {
                lockedExpand.add(tile.id());
            }
            if (visual != TileVisual.LOCKED && !tile.rewards().isEmpty()) {
                Reward first = tile.rewards().getFirst();
                var icon = RewardIcons.iconId(first);
                if (icon.isPresent() && !rewardFace(first).isEmpty()) {
                    filledRewards.add(tile.id());
                    rewardFaces.addProperty(tile.id(), first.type() + ":" + icon.get().getPath());
                    if (visual == TileVisual.COMPLETED) {
                        completedFilledRewards.add(tile.id());
                    }
                } else {
                    bareRewards.add(tile.id());
                    if (visual == TileVisual.COMPLETED) {
                        completedBareRewards.add(tile.id());
                    }
                }
            }
            int[] s = screen(tile.pos().x(), tile.pos().y());
            int[][] ports = tilePorts(tile, visual, s[0], s[1], tilePx());
            for (int[] port : ports) {
                if (port.length >= 9 && port[8] != 0) {
                    inboundPorts++;
                }
            }
            if (visual != TileVisual.LOCKED && !authoring) {
                for (Link link : chapter.links()) {
                    if (link.from().equals(tile.id()) && chapter.tile(link.to()).filter(to ->
                            tile.pos().cardinalTo(to.pos())
                                    && (authoring || !ClientQuestState.isConcealed(chapter, to))).isPresent()) {
                        pathGates++;
                    }
                }
            }
        }
        com.google.gson.JsonArray pathArrows = new com.google.gson.JsonArray();
        for (PathArrow arrow : collectPathArrows()) {
            if (arrow.color() == QuestColors.PORT_RED || arrow.color() == QuestColors.LOCKED_EDGE) {
                redArrows++;
            } else if (arrow.color() == QuestColors.NEW) {
                blueArrows++;
            }
            com.google.gson.JsonObject row = new com.google.gson.JsonObject();
            row.addProperty("from", arrow.from());
            row.addProperty("to", arrow.to());
            row.addProperty("x", arrow.x());
            row.addProperty("y", arrow.y());
            row.addProperty("locked", arrow.locked());
            row.addProperty("color", (arrow.color() == QuestColors.PORT_RED || arrow.color() == QuestColors.LOCKED_EDGE) ? "red"
                    : arrow.color() == QuestColors.NEW ? "blue" : "other");
            row.addProperty("shape", MockChrome.pathArrowShape(arrow.locked()));
            pathArrows.add(row);
        }
        String hoverLock = "";
        if (hoverOverrideX != Integer.MIN_VALUE) {
            for (PathArrow arrow : collectPathArrows()) {
                if (MockChrome.pathHoverHit(hoverOverrideX, hoverOverrideY, arrow.x(), arrow.y())) {
                    hoverLock = arrow.locked() ? "locked" : "open";
                    break;
                }
            }
        } else {
            for (QuestTileWidget widget : tileWidgets) {
                if (!widget.hoverLock().isEmpty()) {
                    hoverLock = widget.hoverLock();
                    break;
                }
            }
        }
        com.google.gson.JsonArray unlockedChapters = new com.google.gson.JsonArray();
        com.google.gson.JsonArray lockedChapters = new com.google.gson.JsonArray();
        for (Chapter entry : ClientQuestState.pack.chapters()) {
            if (ClientQuestState.isChapterUnlocked(entry)) {
                unlockedChapters.add(entry.id().toString());
            } else {
                lockedChapters.add(entry.id().toString());
            }
        }
        out.addProperty("pathGates", pathGates);
        out.addProperty("inboundPorts", inboundPorts);
        out.addProperty("blueArrows", blueArrows);
        out.addProperty("redArrows", redArrows);
        out.addProperty("arrowPlacement", "path-arrow");
        out.addProperty("sidebarCollapsed", sidebarCollapsed);
        out.addProperty("sidebarScroll", sidebarScroll);
        out.addProperty("sidebarScrollMax", sidebarMaxScroll());
        out.addProperty("sidebarRowCount", sidebarRows.size());
        out.addProperty("searchX", searchX());
        out.addProperty("searchW", searchW());
        out.addProperty("searchWidgetX", search == null ? -1 : search.getX());
        out.addProperty("searchWidgetW", search == null ? -1 : search.getWidth());
        out.addProperty("sidebarTitleVisible", true);
        out.addProperty("boardLeft", boardLeft());
        out.addProperty("dimLeft", modalPanel.dimLeft());
        com.google.gson.JsonArray rewardPlusIds = new com.google.gson.JsonArray();
        com.google.gson.JsonObject rewardCounts = new com.google.gson.JsonObject();
        com.google.gson.JsonObject rewardCaptions = new com.google.gson.JsonObject();
        for (Tile tile : chapter.tiles()) {
            rewardCounts.addProperty(tile.id(), tile.rewards().size());
            if (showsRewardPlus(tile)) {
                rewardPlusIds.add(tile.id());
                rewardCaptions.addProperty(tile.id(), rewardsCaption(tile));
            }
        }
        out.add("rewardPlus", rewardPlusIds);
        out.add("rewardCounts", rewardCounts);
        out.add("rewardCaptions", rewardCaptions);
        out.addProperty("lockedEdge", String.format("%08X", QuestColors.LOCKED_EDGE));
        out.addProperty("failed", String.format("%08X", QuestColors.FAILED));
        out.addProperty("closed", String.format("%08X", QuestColors.CLOSED));
        out.addProperty("modalPink", String.format("%08X", QuestColors.MODAL_PINK));
        if (modal != ModalKind.NONE) {
            ModalLayout box = modalLayout();
            out.addProperty("modalX", box.x());
            out.addProperty("modalY", box.y());
            out.addProperty("modalW", box.w());
            out.addProperty("modalH", box.h());
            out.addProperty("modalCloseX", box.x() + box.w() - 12);
        }
        if (expanded && !selectedId.isEmpty()) {
            chapter.tile(selectedId).ifPresent(tile -> {
                out.addProperty("inspectBody", inspectBody(tile));
                com.google.gson.JsonArray nx = new com.google.gson.JsonArray();
                for (ItemStack face : collectRewardFaces(tile)) {
                    if (face.getCount() > 1) {
                        nx.add(face.getCount() + "x");
                    }
                }
                out.add("inspectNx", nx);
            });
        }
        com.google.gson.JsonObject rewardNx = new com.google.gson.JsonObject();
        for (Tile tile : chapter.tiles()) {
            com.google.gson.JsonArray nx = new com.google.gson.JsonArray();
            for (ItemStack face : collectRewardFaces(tile)) {
                if (!face.isEmpty() && face.getCount() > 1) {
                    nx.add(face.getCount() + "x");
                }
            }
            if (!nx.isEmpty()) {
                rewardNx.add(tile.id(), nx);
            }
        }
        out.add("rewardNx", rewardNx);
        out.addProperty("hoverLock", hoverLock);
        out.addProperty("hoverX", hoverOverrideX);
        out.addProperty("hoverY", hoverOverrideY);
        out.add("pathArrows", pathArrows);
        out.add("lockedExpand", lockedExpand);
        out.add("bareRewards", bareRewards);
        out.add("filledRewards", filledRewards);
        out.add("completedBareRewards", completedBareRewards);
        out.add("completedFilledRewards", completedFilledRewards);
        out.add("rewardFaces", rewardFaces);
        out.add("unlockedChapters", unlockedChapters);
        out.add("lockedChapters", lockedChapters);
        if (!selectedId.isEmpty()) {
            chapter.tile(selectedId).ifPresent(tile -> {
                out.addProperty("visual", ClientQuestState.visual(chapter, tile, authoring, selectedId).name());
                PlayBar bar = playBar(tile, cardX(), cardY(), cardW(), cardH());
                out.addProperty("actionLabel", bar.action() ? bar.actionLabel() : (bar.claimedBadge() ? "CLAIMED" : ""));
                out.addProperty("actionVisible", bar.action());
                out.addProperty("claimedBadge", bar.claimedBadge());
                out.addProperty("choiceVisible", bar.choice());
                out.addProperty("rewardsClaimed", ClientQuestState.progress.taskCompleted(
                        chapter.id() + "/" + tile.id(), "claimed"));
            });
        }
        try {
            java.nio.file.Files.writeString(
                    java.nio.file.Path.of(System.getProperty("java.io.tmpdir"), "qq-probe.json"),
                    out.toString());
        } catch (Exception exception) {
            QuestQueen.LOGGER.warn("qq-probe.json write failed: {}", exception.toString());
        }
    }

    private void clickGui(double x, double y) {
        mouseClicked(x, y, 0);
        mouseReleased(x, y, 0);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private static final class MinecraftFont {
        private static void draw(GuiGraphics graphics, String text, int x, int y, int color) {
            graphics.drawString(net.minecraft.client.Minecraft.getInstance().font, text, x, y, color, false);
        }
    }
}
