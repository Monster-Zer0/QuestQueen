package dev.aof.questqueen.client;

import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.ChapterTree;
import dev.aof.questqueen.data.GateCondition;
import dev.aof.questqueen.data.GateEvaluator;
import dev.aof.questqueen.data.GateOp;
import dev.aof.questqueen.data.Link;
import dev.aof.questqueen.data.QuestPack;
import dev.aof.questqueen.data.Scroll;
import dev.aof.questqueen.data.StartNodes;
import dev.aof.questqueen.data.Tile;
import dev.aof.questqueen.data.task.Task;
import dev.aof.questqueen.data.task.TaskVerbs;
import dev.aof.questqueen.net.PinC2S;
import dev.aof.questqueen.net.QuestNetwork;
import dev.aof.questqueen.progress.ProgressSnapshot;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public final class ClientQuestState {
    public static QuestPack pack = QuestPack.empty();
    public static ProgressSnapshot progress = ProgressSnapshot.empty();
    /** Full GOTCHA modal at most twice per client session, then fade-lock. */
    public static final int GOTCHA_MODAL_CAP = 2;
    /** Increments only when the GOTCHA modal is shown. Survives book close/reopen. */
    public static int gotchaShowCount;
    /** Last locked cue: {@code modal}, {@code fade}, or empty. */
    public static String lastLockedCueKind = "";

    private static List<ChapterTree.Node> cachedChapterTree = List.of();
    private static QuestPack cachedChapterTreePack;

    private ClientQuestState() {
    }

    /**
     * Combined locked-tile / locked-chapter cue. Increments {@link #gotchaShowCount}
     * only when the full modal still has a slot.
     *
     * @return {@code true} to open GOTCHA, {@code false} to fade-lock
     */
    public static boolean takeGotchaModalSlot() {
        if (gotchaShowCount < GOTCHA_MODAL_CAP) {
            gotchaShowCount++;
            lastLockedCueKind = "modal";
            return true;
        }
        lastLockedCueKind = "fade";
        return false;
    }

    /** Drop everything learned from the server we are leaving. */
    public static void reset() {
        setPack(QuestPack.empty());
        setProgress(ProgressSnapshot.empty());
        // Game times from the world we are leaving mean nothing in the next one: a lower clock there kept
        // tiles flagged NEW for good.
        NEW_SINCE.clear();
    }

    public static void setPack(QuestPack next) {
        pack = next;
        cachedChapterTreePack = null;
        cachedChapterTree = List.of();
    }

    /** Cached parent→children tree; rebuilds only when the pack instance changes. */
    public static List<ChapterTree.Node> chapterTreeRoots() {
        if (cachedChapterTreePack != pack) {
            cachedChapterTree = ChapterTree.roots(pack.chapters());
            cachedChapterTreePack = pack;
        }
        return cachedChapterTree;
    }

    /**
     * An act intro is a parent chapter with no quests of its own. A parent that has quests keeps its board: a
     * child appearing (the Stage Wing loads only with ProgressiveStages) used to wipe the Feature Showcase board.
     */
    public static boolean isIntroChapter(Chapter chapter) {
        return chapter != null && chapter.tiles().isEmpty() && ChapterTree.hasChildren(chapterTreeRoots(), chapter.id());
    }

    public static void setProgress(ProgressSnapshot next) {
        progress = next;
        clearDeadPinIfNeeded();
        QuestBookScreen.onProgressSynced();
    }

    /** Optimistic pin/unpin before the server snapshot arrives. */
    public static void setPinLocal(Optional<String> chapterId, Optional<String> tileId) {
        progress = progress.withPin(chapterId, tileId);
        QuestBookScreen.onPinLocalChanged();
    }

    public static boolean isPinned(Chapter chapter, Tile tile) {
        return progress.pinChapter().orElse("").equals(chapter.id().toString())
                && progress.pinTile().orElse("").equals(tile.id());
    }

    public static boolean hasPin() {
        return progress.pinChapter().isPresent() && progress.pinTile().isPresent();
    }

    /** Drop HUD pin when the pinned tile is already COMPLETED or FAILED. */
    public static void clearDeadPinIfNeeded() {
        if (progress.pinChapter().isEmpty() || progress.pinTile().isEmpty()) {
            return;
        }
        ResourceLocation chapterId;
        try {
            chapterId = ResourceLocation.parse(progress.pinChapter().get());
        } catch (Exception ignored) {
            return;
        }
        Optional<Chapter> chapterOpt = chapter(chapterId);
        if (chapterOpt.isEmpty()) {
            return;
        }
        Chapter ch = chapterOpt.get();
        Optional<Tile> tileOpt = ch.tile(progress.pinTile().get());
        if (tileOpt.isEmpty()) {
            return;
        }
        TileVisual visual = visual(ch, tileOpt.get());
        if (visual == TileVisual.COMPLETED || visual == TileVisual.FAILED) {
            QuestNetwork.sendToServer(new PinC2S(ch.id(), tileOpt.get().id(), true));
        }
    }

    private static ResourceLocation savedChapterId;

    public static Optional<Chapter> rememberedChapter() {
        if (savedChapterId == null) {
            return Optional.empty();
        }
        return chapter(savedChapterId).filter(ClientQuestState::isChapterAvailable);
    }

    public static void rememberChapter(ResourceLocation id) {
        if (id != null) {
            savedChapterId = id;
        }
    }

    /** Outcome of a network-driven chapter selection. One distinct value per log line — no silent returns. */
    public enum ChapterSelect {
        /** No chapter requested: the ordinary open, which must land on the remembered chapter. */
        NO_ID,
        /** The id was not a valid ResourceLocation. */
        MALFORMED,
        /** Valid id, but no such chapter is loaded on this client. */
        UNKNOWN,
        /** Chapter exists but this client does not consider it available. Never selected. */
        LOCKED,
        /** Already the displayed chapter. Accepted; nothing to rebuild. */
        ALREADY_CURRENT,
        /** Accepted: the displayed chapter changes. */
        SWITCHED
    }

    /**
     * Pure decision table for {@link #selectChapter(String)}, split out so it can be unit-tested without a
     * live pack. SWITCHED / ALREADY_CURRENT are returned only for a chapter this client considers available,
     * so the unlock gate is never bypassed, and a null Chapter is never handed to the predicate (Worf D-5).
     */
    public static ChapterSelect decideChapter(String chapterId, boolean idParsed, boolean chapterFound,
                                              boolean available, boolean alreadyCurrent) {
        if (chapterId == null || chapterId.isEmpty()) {
            return ChapterSelect.NO_ID;
        }
        if (!idParsed) {
            return ChapterSelect.MALFORMED;
        }
        if (!chapterFound) {
            return ChapterSelect.UNKNOWN;
        }
        if (!available) {
            return ChapterSelect.LOCKED;
        }
        return alreadyCurrent ? ChapterSelect.ALREADY_CURRENT : ChapterSelect.SWITCHED;
    }

    /**
     * Resolve an explicitly-requested chapter id and remember it if — and only if — it is accepted.
     * Uses {@code tryParse}, not {@code parse}: a malformed id must be a no-op, never a throw.
     */
    public static ChapterSelect selectChapter(String chapterId) {
        ResourceLocation id = (chapterId == null || chapterId.isEmpty())
                ? null
                : ResourceLocation.tryParse(chapterId);
        Optional<Chapter> found = id == null ? Optional.empty() : chapter(id);
        boolean available = found.filter(ClientQuestState::isChapterAvailable).isPresent();
        boolean alreadyCurrent = found
                .map(target -> rememberedChapter().map(shown -> shown.id().equals(target.id())).orElse(false))
                .orElse(false);
        ChapterSelect outcome = decideChapter(chapterId, id != null, found.isPresent(), available, alreadyCurrent);
        if (outcome == ChapterSelect.SWITCHED || outcome == ChapterSelect.ALREADY_CURRENT) {
            rememberChapter(id);
        }
        return outcome;
    }

    public static Optional<Chapter> chapter(ResourceLocation id) {
        return pack.chapters().stream().filter(chapter -> chapter.id().equals(id)).findFirst();
    }

    public static Chapter firstChapter() {
        if (pack.chapters().isEmpty()) {
            return Chapter.blank(ResourceLocation.parse("questqueen:blank"));
        }
        for (ChapterTree.Node node : ChapterTree.roots(pack.chapters())) {
            if (isChapterUnlocked(node.chapter())) {
                return node.chapter();
            }
        }
        for (Chapter chapter : ChapterTree.flatten(ChapterTree.roots(pack.chapters()))) {
            if (isChapterUnlocked(chapter)) {
                return chapter;
            }
        }
        for (Chapter chapter : ChapterTree.flatten(ChapterTree.roots(pack.chapters()))) {
            if (isChapterListed(chapter)) {
                return chapter;
            }
        }
        return pack.chapters().getFirst();
    }

    public static boolean isChapterUnlocked(Chapter chapter) {
        if (!progress.unlockedChapters().isEmpty()) {
            return progress.chapterUnlocked(chapter.id().toString());
        }
        // Fallback if an older snapshot omitted the set: treat empty unlock as open.
        return chapter.unlock().conditions().isEmpty() && chapter.parent().isEmpty();
    }

    /** Sidebar / log: hidden chapters stay off the list until unlocked. */
    public static boolean isChapterListed(Chapter chapter) {
        return isChapterUnlocked(chapter) || !chapter.hideUntilUnlocked();
    }

    public static List<Scroll> ownedScrolls() {
        List<Scroll> owned = new ArrayList<>();
        for (ResourceLocation id : progress.scrolls()) {
            pack.scrolls().stream().filter(scroll -> scroll.id().equals(id)).findFirst().ifPresent(owned::add);
        }
        return owned;
    }

    public static TileVisual visual(Chapter chapter, Tile tile) {
        if (progress.tileCompleted(chapter.id().toString(), tile.id())) {
            return TileVisual.COMPLETED;
        }
        // FAILED before XOR-closed so a failed sibling still shows FAILED chrome.
        if (isFailed(chapter, tile)) {
            return TileVisual.FAILED;
        }
        if (isXorClosed(chapter, tile)) {
            return TileVisual.CLOSED;
        }
        // Prefer gate/start logic over the revealed sync set so the book stays usable if progress is empty.
        boolean open = isUnlocked(chapter, tile) || progress.revealed(chapter.id().toString(), tile.id());
        if (!open) {
            return TileVisual.LOCKED;
        }
        if (isCurrent(chapter, tile)) {
            return TileVisual.CURRENT;
        }
        return TileVisual.NEW;
    }

    public static boolean isChapterAvailable(Chapter chapter) {
        return isChapterUnlocked(chapter);
    }

    public static boolean isUnlocked(Chapter chapter, Tile tile) {
        if (!isChapterAvailable(chapter)) {
            return false;
        }
        if (progress.revealed(chapter.id().toString(), tile.id())) {
            return true;
        }
        if (tile.requiredStage().isPresent() && !progress.hasStage(tile.requiredStage().get())) {
            return false;
        }
        Set<String> completed = tileIds(chapter, progress.completedTiles());
        return GateEvaluator.unlocked(chapter, tile.id(), completed, ClientQuestState::extraMet)
                || StartNodes.isStart(chapter, tile.id());
    }

    public static boolean isCurrent(Chapter chapter, Tile tile) {
        if (progress.tileCompleted(chapter.id().toString(), tile.id())) {
            return false;
        }
        if (progress.pinChapter().orElse("").equals(chapter.id().toString())
                && progress.pinTile().orElse("").equals(tile.id())) {
            return true;
        }
        if (!isUnlocked(chapter, tile)) {
            return false;
        }
        for (int i = 0; i < tile.tasks().size(); i++) {
            if (progress.value(chapter.id() + "/" + tile.id(), Integer.toString(i)) > 0
                    && !progress.taskCompleted(chapter.id() + "/" + tile.id(), Integer.toString(i))) {
                return true;
            }
        }
        return StartNodes.isStart(chapter, tile.id());
    }

    public static boolean isFailed(Chapter chapter, Tile tile) {
        return progress.taskCompleted(chapter.id() + "/" + tile.id(), "failed");
    }

    /** Sibling lost an XOR fork after another branch was completed. Shared rule — see GateEvaluator. */
    public static boolean isXorClosed(Chapter chapter, Tile tile) {
        if (progress.tileCompleted(chapter.id().toString(), tile.id())) {
            return false;
        }
        return GateEvaluator.xorSiblingCompleted(chapter, tile.id(), tileIds(chapter, progress.completedTiles()));
    }

    public static boolean hasXorBadge(Chapter chapter, Tile tile) {
        return chapter.links().stream().anyMatch(link ->
                (link.from().equals(tile.id()) || link.to().equals(tile.id())) && link.gate().op() == GateOp.XOR);
    }

    public static List<Link> incompleteParents(Chapter chapter, Tile tile) {
        List<Link> incomplete = new ArrayList<>();
        for (Link link : chapter.links()) {
            if (link.to().equals(tile.id()) && !progress.tileCompleted(chapter.id().toString(), link.from())) {
                incomplete.add(link);
            }
        }
        return incomplete;
    }

    public static boolean needsXorChoice(Chapter chapter, Tile tile) {
        if (!progress.tileCompleted(chapter.id().toString(), tile.id())) {
            return false;
        }
        List<Link> outs = xorOuts(chapter, tile);
        if (outs.size() < 2) {
            return false;
        }
        return outs.stream().noneMatch(link -> progress.tileCompleted(chapter.id().toString(), link.to()));
    }

    /** Completed XOR parents in this chapter that still have no chosen child. */
    public static List<String> unresolvedXorParents(Chapter chapter) {
        List<String> ids = new ArrayList<>();
        for (Tile tile : chapter.tiles()) {
            if (needsXorChoice(chapter, tile)) {
                ids.add(tile.id());
            }
        }
        return ids;
    }

    /** Pack-wide fork debt: chapterId/tileId for every unresolved XOR parent. */
    public static List<String> unresolvedXorKeys() {
        List<String> keys = new ArrayList<>();
        for (Chapter chapter : pack.chapters()) {
            for (String tileId : unresolvedXorParents(chapter)) {
                keys.add(chapter.id() + "/" + tileId);
            }
        }
        return keys;
    }

    public static boolean hasUnresolvedXor() {
        return !unresolvedXorKeys().isEmpty();
    }

    /** Open XOR child waiting for a path pick — not a commit, just the choose-path cue. */
    public static boolean isOpenXorBranch(Chapter chapter, Tile tile) {
        if (progress.tileCompleted(chapter.id().toString(), tile.id()) || isFailed(chapter, tile)) {
            return false;
        }
        for (Link link : chapter.links()) {
            if (!link.to().equals(tile.id()) || link.gate().op() != GateOp.XOR) {
                continue;
            }
            if (chapter.tile(link.from()).map(parent -> needsXorChoice(chapter, parent)).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    public static String xorKey(Chapter chapter, String tileId) {
        return chapter.id() + "/" + tileId;
    }

    private static List<Link> xorOuts(Chapter chapter, Tile tile) {
        return chapter.links().stream()
                .filter(link -> link.from().equals(tile.id()) && link.gate().op() == GateOp.XOR)
                .toList();
    }

    public static String taskVerb(Tile tile) {
        if (tile.tasks().isEmpty()) {
            return tile.title().isBlank() ? tile.id() : tile.title();
        }
        return taskVerb(tile.tasks().getFirst(), tile);
    }

    /**
     * Verb for one specific task; {@code tile} is only consulted for the empty-task fallback.
     *
     * <p>The verbs now live in {@link TaskVerbs} as {@code questqueen.task.<type>} translation keys. They used
     * to be an inline switch here whose {@code default} branch uppercased the raw type id, which is why
     * {@code item_tag} and {@code xp_levels} rendered as {@code ITEM_TAG} / {@code XP_LEVELS} and why no pack
     * could correct them.
     */
    public static String taskVerb(Task task, Tile tile) {
        if (task == null) {
            return tile.title().isBlank() ? tile.id() : tile.title();
        }
        return TaskVerbs.verb(task);
    }

    /**
     * Progress label for a single task row. Index 0 keeps the tile-level wording so the CLAIMED state
     * still shows; later rows get their own verb and count.
     */
    public static String taskProgressLabel(Chapter chapter, Tile tile, int taskIndex) {
        if (taskIndex <= 0) {
            return taskProgressLabel(chapter, tile);
        }
        if (taskIndex >= tile.tasks().size()) {
            return "";
        }
        Task task = tile.tasks().get(taskIndex);
        int need = Math.max(1, task.required());
        int value = progress.value(chapter.id() + "/" + tile.id(), Integer.toString(taskIndex));
        String verb = taskVerb(task, tile);
        if (need <= 1) {
            return verb;
        }
        return verb + " " + value + "/" + need;
    }

    /**
     * The count column of a task row on the quest card: {@code DONE} once the task (or its tile) is finished,
     * {@code value/need} while a counted task is open, and blank for an open one-shot task (nothing to count).
     */
    public static String taskCount(Chapter chapter, Tile tile, int taskIndex) {
        if (taskIndex < 0 || taskIndex >= tile.tasks().size()) {
            return "";
        }
        String questId = chapter.id() + "/" + tile.id();
        if (progress.tileCompleted(chapter.id().toString(), tile.id())
                || progress.taskCompleted(questId, Integer.toString(taskIndex))) {
            return "DONE";
        }
        int need = Math.max(1, tile.tasks().get(taskIndex).required());
        if (need <= 1) {
            return "";
        }
        return liveValue(chapter, tile, taskIndex) + "/" + need;
    }

    /** Local player's XP level, for the count of an xp_levels task before it is submitted. Tests replace it. */
    static java.util.function.IntSupplier playerLevel = () -> {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        return player == null ? 0 : player.experienceLevel;
    };

    /**
     * Progress value to show for an open task, capped at its need. An xp_levels task only records progress when
     * it is submitted (the levels are spent then), so before that its count is the player's current level —
     * otherwise a player holding the levels read 0/2 next to a SUBMIT that would work.
     */
    static int liveValue(Chapter chapter, Tile tile, int taskIndex) {
        Task task = tile.tasks().get(taskIndex);
        int need = Math.max(1, task.required());
        int value = progress.value(chapter.id() + "/" + tile.id(), Integer.toString(taskIndex));
        if ("xp_levels".equals(task.type())) {
            value = Math.max(value, playerLevel.getAsInt());
        }
        return Math.max(0, Math.min(need, value));
    }

    /**
     * How far through its tasks a quest is, 0..1, for the board tile's progress bar. Each task weighs its own
     * {@code required} count, so a 16-log task outweighs a one-shot. A finished task (or tile) counts in full.
     */
    public static float taskFraction(Chapter chapter, Tile tile) {
        if (tile.tasks().isEmpty()) {
            return progress.tileCompleted(chapter.id().toString(), tile.id()) ? 1f : 0f;
        }
        String questId = chapter.id() + "/" + tile.id();
        boolean tileDone = progress.tileCompleted(chapter.id().toString(), tile.id());
        long have = 0;
        long need = 0;
        for (int i = 0; i < tile.tasks().size(); i++) {
            int required = Math.max(1, tile.tasks().get(i).required());
            need += required;
            if (tileDone || progress.taskCompleted(questId, Integer.toString(i))) {
                have += required;
            } else {
                have += liveValue(chapter, tile, i);
            }
        }
        return need <= 0 ? 0f : have / (float) need;
    }

    /** One task's progress, 0..1: {@code value/need}, full once the task or its tile is done. */
    public static float taskFraction(Chapter chapter, Tile tile, int taskIndex) {
        if (taskIndex < 0 || taskIndex >= tile.tasks().size()) {
            return 0f;
        }
        String questId = chapter.id() + "/" + tile.id();
        if (progress.tileCompleted(chapter.id().toString(), tile.id())
                || progress.taskCompleted(questId, Integer.toString(taskIndex))) {
            return 1f;
        }
        int need = Math.max(1, tile.tasks().get(taskIndex).required());
        return liveValue(chapter, tile, taskIndex) / (float) need;
    }

    /**
     * Count shown on a board tile: the one counted task's {@code value/need}, or tasks done over tasks for a
     * quest with several. Blank when there is nothing to count (a single one-shot task).
     */
    public static String tileCountText(Chapter chapter, Tile tile) {
        int tasks = tile.tasks().size();
        if (tasks == 0) {
            return "";
        }
        if (tasks == 1) {
            String count = taskCount(chapter, tile, 0);
            return "DONE".equals(count) ? "" : count;
        }
        String questId = chapter.id() + "/" + tile.id();
        int done = 0;
        for (int i = 0; i < tasks; i++) {
            if (progress.taskCompleted(questId, Integer.toString(i))) {
                done++;
            }
        }
        return done + "/" + tasks;
    }

    /**
     * Finished with rewards still waiting: CLAIM, or TAKE A / TAKE B on a choice quest. The same test the card's
     * footer makes, so a tile shows the claim cue exactly when its card shows the button.
     */
    public static boolean claimable(Chapter chapter, Tile tile) {
        if (!progress.tileCompleted(chapter.id().toString(), tile.id())) {
            return false;
        }
        if (tile.rewards().isEmpty() && tile.scrolls().isEmpty()) {
            return false;
        }
        String questId = chapter.id() + "/" + tile.id();
        boolean hasChoice = tile.rewards().stream()
                .anyMatch(reward -> reward instanceof dev.aof.questqueen.data.reward.ChoiceReward);
        if (hasChoice && !progress.taskCompleted(questId, "choice")) {
            return true;
        }
        return !hasChoice && !progress.taskCompleted(questId, "claimed");
    }

    /**
     * Sidebar numbers for one chapter.
     *
     * @param done      completed quests
     * @param total     quests the player can see (hidden-until quests stay out until revealed)
     * @param claimable completed quests with rewards still to claim
     */
    public record ChapterStats(int done, int total, int claimable) {
        public static final ChapterStats EMPTY = new ChapterStats(0, 0, 0);

        public float fraction() {
            return total <= 0 ? 0f : done / (float) total;
        }

        public boolean complete() {
            return total > 0 && done >= total;
        }
    }

    private static final java.util.Map<ResourceLocation, ChapterStats> STATS = new java.util.HashMap<>();
    private static ProgressSnapshot statsProgress;
    private static QuestPack statsPack;

    /** Cached per progress snapshot and pack: the sidebar asks for every row every frame. */
    public static ChapterStats chapterStats(Chapter chapter) {
        if (chapter == null) {
            return ChapterStats.EMPTY;
        }
        if (statsProgress != progress || statsPack != pack) {
            STATS.clear();
            statsProgress = progress;
            statsPack = pack;
        }
        return STATS.computeIfAbsent(chapter.id(), id -> computeStats(chapter));
    }

    private static ChapterStats computeStats(Chapter chapter) {
        int done = 0;
        int total = 0;
        int claimable = 0;
        Set<String> completed = tileIds(chapter, progress.completedTiles());
        for (Tile tile : chapter.tiles()) {
            // A closed fork branch or a NOT gate shut for good can never be done, so it is not part of the total:
            // counting it left a finished chapter at 5/7 forever.
            if (isConcealed(chapter, tile) || GateEvaluator.closed(chapter, tile.id(), completed)) {
                continue;
            }
            total++;
            if (progress.tileCompleted(chapter.id().toString(), tile.id())) {
                done++;
            }
            if (claimable(chapter, tile)) {
                claimable++;
            }
        }
        return new ChapterStats(done, total, claimable);
    }

    /** Book-wide numbers over the chapters the sidebar lists. */
    public static ChapterStats bookStats() {
        int done = 0;
        int total = 0;
        int claimable = 0;
        for (Chapter chapter : pack.chapters()) {
            if (!isChapterListed(chapter) || isIntroChapter(chapter)) {
                continue;
            }
            ChapterStats stats = chapterStats(chapter);
            done += stats.done();
            total += stats.total();
            claimable += stats.claimable();
        }
        return new ChapterStats(done, total, claimable);
    }

    public static boolean rewardsClaimed(Chapter chapter, Tile tile) {
        String questId = chapter.id() + "/" + tile.id();
        if (progress.taskCompleted(questId, "claimed")) {
            return true;
        }
        if (!tile.rewards().isEmpty()) {
            return false;
        }
        return "checkmark".equals(tile.tasks().isEmpty() ? "" : tile.tasks().getFirst().type())
                && (progress.taskCompleted(questId, "0") || progress.tileCompleted(chapter.id().toString(), tile.id()));
    }

    public static String taskProgressLabel(Chapter chapter, Tile tile) {
        if (tile.tasks().isEmpty()) {
            return taskVerb(tile);
        }
        Task task = tile.tasks().getFirst();
        int value = progress.value(chapter.id() + "/" + tile.id(), "0");
        int need = Math.max(1, task.required());
        String verb = taskVerb(tile);
        if ("CLAIM".equals(verb) && rewardsClaimed(chapter, tile)) {
            verb = "CLAIMED";
        }
        if (need <= 1 && ("checkmark".equals(task.type()) || "advancement".equals(task.type())
                || "visit_dimension".equals(task.type()) || "visit_biome".equals(task.type())
                || "visit_structure".equals(task.type()) || "location".equals(task.type())
                || "observation".equals(task.type()))) {
            return verb;
        }
        return verb + " " + value + "/" + need;
    }

    public static Set<String> pathHighlight(Chapter chapter) {
        Set<String> path = new HashSet<>();
        if (progress.pinChapter().isEmpty() || !progress.pinChapter().orElse("").equals(chapter.id().toString())) {
            return path;
        }
        String target = progress.pinTile().orElse("");
        if (target.isEmpty()) {
            return path;
        }
        ArrayDeque<String> queue = new ArrayDeque<>();
        queue.add(target);
        path.add(target);
        while (!queue.isEmpty()) {
            String node = queue.removeFirst();
            for (Link link : chapter.links()) {
                if (link.to().equals(node) && path.add(link.from())) {
                    queue.add(link.from());
                }
            }
        }
        return path;
    }

    public static int remainingOnPath(Chapter chapter) {
        int remaining = 0;
        // Once per call: this used to rebuild the whole path for every tile in the chapter.
        Set<String> path = pathHighlight(chapter);
        for (Tile tile : chapter.tiles()) {
            if (!path.contains(tile.id())) {
                continue;
            }
            for (int i = 0; i < tile.tasks().size(); i++) {
                if (!progress.taskCompleted(chapter.id() + "/" + tile.id(), Integer.toString(i))) {
                    remaining++;
                }
            }
        }
        return remaining;
    }

    public static boolean isConcealed(Chapter chapter, Tile tile) {
        if (progress.tileCompleted(chapter.id().toString(), tile.id())
                || progress.revealed(chapter.id().toString(), tile.id())) {
            return false;
        }
        return tile.hiddenUntil()
                .map(hidden -> !extraMet(new GateCondition(hidden.type(), hidden.id())))
                .orElse(false);
    }

    private static boolean extraMet(GateCondition condition) {
        return switch (condition.type()) {
            case "stage", "progressivestages" -> progress.hasStage(condition.id());
            case "quest_complete" -> progress.completedTiles().contains(condition.id());
            // The client cannot evaluate advancement / scoreboard / team_flag / chapter_complete / trigger
            // gates — those need live server state. Report them UNMET rather than satisfied: a permissive
            // default here drew the card, its descriptions and its buttons for quests the server still
            // locked. Legitimately open tiles still light up through the server's `revealed` set, which
            // `isUnlocked` consults first.
            default -> false;
        };
    }

    private static Set<String> tileIds(Chapter chapter, Set<String> completed) {
        Set<String> ids = new HashSet<>();
        String prefix = chapter.id() + "/";
        for (String key : completed) {
            if (key.startsWith(prefix)) {
                ids.add(key.substring(prefix.length()));
            }
        }
        return ids;
    }

    /** Tracks gameTime when a tile first appeared as NEW this session (client). */
    private static final java.util.Map<String, Long> NEW_SINCE = new java.util.concurrent.ConcurrentHashMap<>();

    public static boolean isNewStale(Chapter chapter, Tile tile, long ticks) {
        if (chapter == null || tile == null) {
            return false;
        }
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.level == null) {
            return false;
        }
        String key = chapter.id() + "/" + tile.id();
        long now = mc.level.getGameTime();
        Long since = NEW_SINCE.putIfAbsent(key, now);
        if (since == null) {
            since = now;
        }
        return (now - since) >= ticks;
    }
}
