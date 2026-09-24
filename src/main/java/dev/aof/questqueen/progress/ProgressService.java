package dev.aof.questqueen.progress;

import dev.aof.questqueen.QuestQueen;
import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.ChapterTree;
import dev.aof.questqueen.data.GateCondition;
import dev.aof.questqueen.data.GateEvaluator;
import dev.aof.questqueen.data.HiddenUntil;
import dev.aof.questqueen.data.QuestDefinitions;
import dev.aof.questqueen.data.StartNodes;
import dev.aof.questqueen.data.Tile;
import dev.aof.questqueen.data.reward.ChoiceReward;
import dev.aof.questqueen.data.reward.Reward;
import dev.aof.questqueen.data.reward.StageReward;
import dev.aof.questqueen.data.task.Task;
import dev.aof.questqueen.net.QuestNetwork;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ProgressService {
    /** Coalesce team progress S2C within this many server ticks after a mutation. */
    private static final int SYNC_DEBOUNCE_TICKS = 5;

    private static final Map<UUID, ProgressSnapshot> SNAPSHOT_CACHE = new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> SUSPEND_SYNC = ThreadLocal.withInitial(() -> false);
    /** teamId → server tick when a debounced sync should flush. */
    private static final Map<String, Integer> PENDING_TEAM_SYNC = new ConcurrentHashMap<>();
    private static int serverTick;
    /** Tiles whose stage grants were skipped by the last {@link #grantAll} call. */
    private static volatile int lastBulkStagesSkipped;

    private ProgressService() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            TeamService.ensureSolo(player);
            invalidatePlayer(player.getUUID());
            syncNow(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            invalidatePlayer(player.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        serverTick++;
        if (PENDING_TEAM_SYNC.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        Iterator<Map.Entry<String, Integer>> it = PENDING_TEAM_SYNC.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Integer> entry = it.next();
            if (serverTick >= entry.getValue()) {
                it.remove();
                flushTeamSync(server, entry.getKey());
            }
        }
    }

    public static boolean canAuthor(ServerPlayer player) {
        return EditorSessions.isEnabled(player);
    }

    /** Drop all cached snapshots (datapack reload / pack mutate). */
    public static void invalidateAll() {
        SNAPSHOT_CACHE.clear();
    }

    public static void invalidatePlayer(UUID playerId) {
        SNAPSHOT_CACHE.remove(playerId);
    }

    public static void invalidateTeam(MinecraftServer server, String teamId) {
        for (UUID member : TeamService.members(teamId)) {
            SNAPSHOT_CACHE.remove(member);
        }
    }

    public static ProgressSnapshot snapshot(ServerPlayer player) {
        return SNAPSHOT_CACHE.computeIfAbsent(player.getUUID(), id -> buildSnapshot(player));
    }

    private static ProgressSnapshot buildSnapshot(ServerPlayer player) {
        String teamId = TeamService.ensureSolo(player);
        Map<String, Integer> values = new HashMap<>();
        Set<String> completedTasks = new HashSet<>();
        Set<String> completedTiles = new HashSet<>();
        try (PreparedStatement statement = ProgressDatabase.get().prepareStatement(
                "SELECT quest_id, task_id, value, completed FROM progress WHERE team_id = ?")) {
            statement.setString(1, teamId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    String questId = result.getString("quest_id");
                    String taskId = result.getString("task_id");
                    String key = questId + "/" + taskId;
                    values.put(key, result.getInt("value"));
                    if (result.getInt("completed") != 0) {
                        completedTasks.add(key);
                    }
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }

        for (Chapter chapter : QuestDefinitions.chapters()) {
            for (Tile tile : chapter.tiles()) {
                if (tileComplete(chapter, tile, completedTasks)) {
                    completedTiles.add(ProgressSnapshot.questKey(chapter.id(), tile.id()));
                }
            }
        }

        Set<String> unlockedChapters = computeUnlockedChapters(player, completedTiles);
        Set<String> revealed = computeRevealed(player, completedTiles, unlockedChapters);
        List<ResourceLocation> scrolls = ownedScrolls(player.getUUID());
        Optional<String> pinChapter = Optional.empty();
        Optional<String> pinTile = Optional.empty();
        try (PreparedStatement statement = ProgressDatabase.get().prepareStatement(
                "SELECT chapter_id, tile_id FROM pins WHERE player_uuid = ?")) {
            statement.setString(1, player.getUUID().toString());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    pinChapter = Optional.of(result.getString(1));
                    pinTile = Optional.of(result.getString(2));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
        return new ProgressSnapshot(teamId, canAuthor(player), values, completedTasks, completedTiles, revealed,
                unlockedChapters, dev.aof.questqueen.compat.ProgressiveStagesCompat.ownedStages(player),
                scrolls, pinChapter, pinTile);
    }

    /** Immediate sync (login / pin / reset). Invalidates cache then sends. */
    public static void sync(ServerPlayer player) {
        if (Boolean.TRUE.equals(SUSPEND_SYNC.get())) {
            return;
        }
        syncNow(player);
    }

    private static void syncNow(ServerPlayer player) {
        invalidatePlayer(player.getUUID());
        QuestNetwork.sendProgress(player, snapshot(player));
    }

    public static void syncTeam(MinecraftServer server, String teamId) {
        invalidateTeam(server, teamId);
        PENDING_TEAM_SYNC.put(teamId, serverTick + SYNC_DEBOUNCE_TICKS);
    }

    /** Flush any pending team syncs immediately (tests / end of critical path). */
    public static void flushPendingSyncs(MinecraftServer server) {
        if (PENDING_TEAM_SYNC.isEmpty()) {
            return;
        }
        List<String> teams = new ArrayList<>(PENDING_TEAM_SYNC.keySet());
        PENDING_TEAM_SYNC.clear();
        for (String teamId : teams) {
            flushTeamSync(server, teamId);
        }
    }

    private static void flushTeamSync(MinecraftServer server, String teamId) {
        for (UUID member : TeamService.members(teamId)) {
            ServerPlayer player = server.getPlayerList().getPlayer(member);
            if (player != null) {
                syncNow(player);
            }
        }
    }

    public static int increment(ServerPlayer player, ResourceLocation chapterId, String tileId, int taskIndex, int amount) {
        Optional<Chapter> chapter = QuestDefinitions.chapter(chapterId);
        if (chapter.isEmpty()) {
            return 0;
        }
        Optional<Tile> tile = chapter.get().tile(tileId);
        if (tile.isEmpty() || taskIndex < 0 || taskIndex >= tile.get().tasks().size()) {
            return 0;
        }
        if (!isUnlocked(player, chapter.get(), tile.get())) {
            return 0;
        }
        Task task = tile.get().tasks().get(taskIndex);
        String teamId = TeamService.ensureSolo(player);
        String questId = ProgressSnapshot.questKey(chapterId, tileId);
        String taskId = ProgressSnapshot.taskKey(taskIndex);
        int current = readValue(teamId, questId, taskId);
        if (isCompleted(teamId, questId, taskId)) {
            return current;
        }
        int next = Math.min(task.required(), current + amount);
        boolean done = next >= task.required();
        writeProgress(teamId, questId, taskId, next, done);
        noteMutation(player, teamId);
        if (done) {
            maybeCompleteTile(player, chapter.get(), tile.get());
        }
        syncTeam(player.server, teamId);
        return next;
    }

    /**
     * Set a task's absolute progress value (e.g. from a vanilla custom-stat scan). Completes the task when
     * {@code value >= required}. No-ops when the value is unchanged so a 20-tick poll does not spam syncs.
     */
    public static int setTaskValue(ServerPlayer player, ResourceLocation chapterId, String tileId, int taskIndex, int value) {
        Optional<Chapter> chapter = QuestDefinitions.chapter(chapterId);
        if (chapter.isEmpty()) {
            return 0;
        }
        Optional<Tile> tile = chapter.get().tile(tileId);
        if (tile.isEmpty() || taskIndex < 0 || taskIndex >= tile.get().tasks().size()) {
            return 0;
        }
        if (!isUnlocked(player, chapter.get(), tile.get())) {
            return 0;
        }
        Task task = tile.get().tasks().get(taskIndex);
        String teamId = TeamService.ensureSolo(player);
        String questId = ProgressSnapshot.questKey(chapterId, tileId);
        String taskId = ProgressSnapshot.taskKey(taskIndex);
        if (isCompleted(teamId, questId, taskId)) {
            return readValue(teamId, questId, taskId);
        }
        int next = Math.max(0, Math.min(task.required(), value));
        int current = readValue(teamId, questId, taskId);
        boolean done = next >= task.required();
        if (next == current && !done) {
            return current;
        }
        writeProgress(teamId, questId, taskId, next, done);
        noteMutation(player, teamId);
        if (done) {
            maybeCompleteTile(player, chapter.get(), tile.get());
        }
        syncTeam(player.server, teamId);
        return next;
    }

    public static boolean setCompleted(ServerPlayer player, ResourceLocation chapterId, String tileId, int taskIndex) {
        Optional<Chapter> chapter = QuestDefinitions.chapter(chapterId);
        if (chapter.isEmpty()) {
            return false;
        }
        Optional<Tile> tile = chapter.get().tile(tileId);
        if (tile.isEmpty() || taskIndex < 0 || taskIndex >= tile.get().tasks().size()) {
            return false;
        }
        if (!isUnlocked(player, chapter.get(), tile.get())) {
            return false;
        }
        String teamId = TeamService.ensureSolo(player);
        String questId = ProgressSnapshot.questKey(chapterId, tileId);
        String taskId = ProgressSnapshot.taskKey(taskIndex);
        if (isCompleted(teamId, questId, taskId)) {
            return false;
        }
        Task task = tile.get().tasks().get(taskIndex);
        writeProgress(teamId, questId, taskId, task.required(), true);
        noteMutation(player, teamId);
        maybeCompleteTile(player, chapter.get(), tile.get());
        syncTeam(player.server, teamId);
        return true;
    }

    /** True when a submit-style task could still be completed right now (exists, unlocked, not done). */
    public static boolean canCompleteTask(ServerPlayer player, ResourceLocation chapterId, String tileId, int taskIndex) {
        Optional<Chapter> chapter = QuestDefinitions.chapter(chapterId);
        if (chapter.isEmpty()) {
            return false;
        }
        Optional<Tile> tile = chapter.get().tile(tileId);
        if (tile.isEmpty() || taskIndex < 0 || taskIndex >= tile.get().tasks().size()) {
            return false;
        }
        if (!isUnlocked(player, chapter.get(), tile.get())) {
            return false;
        }
        return !isCompleted(TeamService.ensureSolo(player), ProgressSnapshot.questKey(chapterId, tileId),
                ProgressSnapshot.taskKey(taskIndex));
    }

    /**
     * Explicit CLAIM for a completed tile's non-choice rewards (and scrolls). Choice tiles must use
     * {@link #claimChoice} / TAKE A·B — this path refuses them so option 0 is never silently taken.
     */
    public static boolean claimTileRewards(ServerPlayer player, ResourceLocation chapterId, String tileId) {
        Optional<Chapter> chapter = QuestDefinitions.chapter(chapterId);
        if (chapter.isEmpty()) {
            return false;
        }
        Optional<Tile> tile = chapter.get().tile(tileId);
        if (tile.isEmpty() || !isUnlocked(player, chapter.get(), tile.get())) {
            return false;
        }
        if (hasChoiceReward(tile.get())) {
            return false;
        }
        String teamId = TeamService.ensureSolo(player);
        String questId = ProgressSnapshot.questKey(chapterId, tileId);
        if (!isCompleted(teamId, questId, "tile") || isCompleted(teamId, questId, "claimed")) {
            return false;
        }
        grantTileRewards(player, tile.get(), true);
        writeProgress(teamId, questId, "claimed", 1, true);
        noteMutation(player, teamId);
        syncTeam(player.server, teamId);
        return true;
    }

    /**
     * Claim every ready non-choice reward in one chapter. The eligible set is recomputed here; a client
     * list is never trusted. One sync and one chat line, not one of each per quest.
     */
    public static int claimChapterRewards(ServerPlayer player, ResourceLocation chapterId) {
        Optional<Chapter> chapter = QuestDefinitions.chapter(chapterId);
        if (chapter.isEmpty() || !isChapterUnlocked(player, chapter.get())) {
            return 0;
        }
        Chapter open = chapter.get();
        ProgressSnapshot snap = snapshot(player);
        List<Tile> tiles = ClaimAll.grantable(open, snap, tile -> isUnlocked(player, open, tile));
        if (tiles.isEmpty()) {
            return 0;
        }
        String teamId = TeamService.ensureSolo(player);
        int granted = 0;
        SUSPEND_SYNC.set(true);
        try {
            for (Tile tile : tiles) {
                String questId = ProgressSnapshot.questKey(chapterId, tile.id());
                if (!isCompleted(teamId, questId, "tile") || isCompleted(teamId, questId, "claimed")
                        || hasChoiceReward(tile)) {
                    continue;
                }
                for (Reward reward : tile.rewards()) {
                    reward.grant(player);
                }
                for (ResourceLocation scroll : tile.scrolls()) {
                    grantScroll(player, scroll, false);
                }
                writeProgress(teamId, questId, "claimed", 1, true);
                granted++;
            }
        } finally {
            SUSPEND_SYNC.set(false);
        }
        if (granted == 0) {
            return 0;
        }
        noteMutation(player, teamId);
        syncTeam(player.server, teamId);
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4F, 1.2F);
        String title = open.title().isBlank() ? open.id().toString() : open.title();
        player.sendSystemMessage(Component.translatable("questqueen.rewards_claimed_chapter", granted, title));
        return granted;
    }

    /** Clear the claimed flag so CLAIM / TAKE A·B can run again (ops repairing a missed grant). */
    public static boolean unclaimRewards(ServerPlayer player, ResourceLocation chapterId, String tileId) {
        Optional<Chapter> chapter = QuestDefinitions.chapter(chapterId);
        if (chapter.isEmpty() || chapter.get().tile(tileId).isEmpty()) {
            return false;
        }
        String teamId = TeamService.ensureSolo(player);
        String questId = ProgressSnapshot.questKey(chapterId, tileId);
        deleteProgress(teamId, questId, "claimed");
        deleteProgress(teamId, questId, "choice");
        noteMutation(player, teamId);
        syncTeam(player.server, teamId);
        return true;
    }

    /**
     * Force-complete every loaded tile, ignoring locks. Used by {@code /questqueen grant-all}.
     * Returns how many tiles were newly completed.
     */
    public static int grantAll(ServerPlayer player) {
        String teamId = TeamService.ensureSolo(player);
        int granted = 0;
        var connection = ProgressDatabase.get();
        try {
            connection.setAutoCommit(false);
            for (Chapter chapter : QuestDefinitions.chapters()) {
                for (Tile tile : chapter.tiles()) {
                    if (forceCompleteTile(teamId, chapter, tile)) {
                        granted++;
                    }
                }
            }
            connection.commit();
        } catch (Exception exception) {
            try {
                connection.rollback();
            } catch (Exception ignored) {
            }
            throw new IllegalStateException("grant-all failed", exception);
        } finally {
            restoreAutoCommit(connection);
        }
        // Chapter unlocks in this pack are stage gates (camp_established, …). Progress-only
        // grant-all left those chapters locked even though every tile was COMPLETED.
        SUSPEND_SYNC.set(true);
        int stagesSkipped = 0;
        try {
            for (Chapter chapter : QuestDefinitions.chapters()) {
                for (Tile tile : chapter.tiles()) {
                    // A throwing stage bridge must not abort the sweep after progress is committed.
                    try {
                        grantStageRewards(player, tile);
                    } catch (Exception exception) {
                        stagesSkipped++;
                        QuestQueen.LOGGER.warn("grant-all: stage rewards skipped for {}/{}",
                                chapter.id(), tile.id(), exception);
                    }
                }
            }
        } finally {
            SUSPEND_SYNC.set(false);
        }
        lastBulkStagesSkipped = stagesSkipped;
        noteMutation(player, teamId);
        syncTeam(player.server, teamId);
        return granted;
    }

    /** Tiles whose stage rewards were skipped by the most recent {@link #grantAll}. */
    public static int lastBulkStagesSkipped() {
        return lastBulkStagesSkipped;
    }

    /** Drop out of manual transaction mode without letting a driver quirk mask the real failure. */
    private static void restoreAutoCommit(java.sql.Connection connection) {
        try {
            if (!connection.getAutoCommit()) {
                connection.setAutoCommit(true);
            }
        } catch (Exception exception) {
            QuestQueen.LOGGER.error("Quest database left in manual-commit mode", exception);
        }
    }

    private static boolean forceCompleteTile(String teamId, Chapter chapter, Tile tile) {
        String questId = ProgressSnapshot.questKey(chapter.id(), tile.id());
        deleteProgress(teamId, questId, "failed");
        if (isCompleted(teamId, questId, "tile")) {
            return false;
        }
        for (int i = 0; i < tile.tasks().size(); i++) {
            String taskId = ProgressSnapshot.taskKey(i);
            if (!isCompleted(teamId, questId, taskId)) {
                writeProgress(teamId, questId, taskId, Math.max(1, tile.tasks().get(i).required()), true);
            }
        }
        writeProgress(teamId, questId, "tile", 1, true);
        return true;
    }

    /** Silent Progressive Stages grants so chapter `unlock` gates can open after grant-all. */
    private static void grantStageRewards(ServerPlayer player, Tile tile) {
        for (Reward reward : tile.rewards()) {
            grantStageReward(player, reward);
        }
    }

    private static void grantStageReward(ServerPlayer player, Reward reward) {
        if (reward instanceof StageReward stage) {
            stage.grant(player);
            return;
        }
        if (reward instanceof ChoiceReward choice) {
            for (Reward option : choice.options()) {
                grantStageReward(player, option);
            }
        }
    }

    private static void grantTileRewards(ServerPlayer player, Tile tile, boolean announce) {
        for (Reward reward : tile.rewards()) {
            if (!announce && reward.noisy()) {
                continue;
            }
            reward.grant(player);
        }
        for (ResourceLocation scroll : tile.scrolls()) {
            grantScroll(player, scroll, false);
        }
        if (announce) {
            // Short XP ding instead of the advancement challenge fanfare.
            player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4F, 1.2F);
            player.sendSystemMessage(Component.translatable("questqueen.rewards_claimed", tile.title()));
        }
    }

    /**
     * Marks a tile FAILED and keeps it incomplete so FAILED chrome can surface
     * (including XOR-closed siblings). Always syncs the client snapshot.
     */
    public static boolean markFailed(ServerPlayer player, ResourceLocation chapterId, String tileId) {
        Optional<Chapter> chapter = QuestDefinitions.chapter(chapterId);
        if (chapter.isEmpty()) {
            return false;
        }
        Optional<Tile> tile = chapter.get().tile(tileId);
        if (tile.isEmpty()) {
            return false;
        }
        String teamId = TeamService.ensureSolo(player);
        String questId = ProgressSnapshot.questKey(chapterId, tileId);
        deleteProgress(teamId, questId, "tile");
        for (int i = 0; i < tile.get().tasks().size(); i++) {
            int value = Math.max(1, readValue(teamId, questId, ProgressSnapshot.taskKey(i)));
            writeProgress(teamId, questId, ProgressSnapshot.taskKey(i), value, false);
        }
        writeProgress(teamId, questId, "failed", 1, true);
        noteMutation(player, teamId);
        syncTeam(player.server, teamId);
        return true;
    }

    public static void pin(ServerPlayer player, ResourceLocation chapter, String tile) {
        if (!canPin(player, chapter, tile)) {
            return;
        }
        try (PreparedStatement statement = ProgressDatabase.get().prepareStatement(
                "INSERT OR REPLACE INTO pins(player_uuid, chapter_id, tile_id) VALUES(?, ?, ?)")) {
            statement.setString(1, player.getUUID().toString());
            statement.setString(2, chapter.toString());
            statement.setString(3, tile);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
        syncNow(player);
    }

    /** Chapter and tile must exist; refuse COMPLETED / FAILED (same as client {@code clearDeadPinIfNeeded}). */
    public static boolean canPin(ServerPlayer player, ResourceLocation chapterId, String tileId) {
        Optional<Chapter> chapter = QuestDefinitions.chapter(chapterId);
        if (chapter.isEmpty()) {
            return false;
        }
        Optional<Tile> tile = chapter.get().tile(tileId);
        if (tile.isEmpty()) {
            return false;
        }
        ProgressSnapshot snap = snapshot(player);
        if (snap.tileCompleted(chapterId.toString(), tileId)) {
            return false;
        }
        return !snap.taskCompleted(ProgressSnapshot.questKey(chapterId, tileId), "failed");
    }

    /** Chapter and tile must exist and currently be unlocked — no claim through a still-locked gate. */
    public static boolean canClaimRewards(ServerPlayer player, ResourceLocation chapterId, String tileId) {
        Optional<Chapter> chapter = QuestDefinitions.chapter(chapterId);
        if (chapter.isEmpty()) {
            return false;
        }
        Optional<Tile> tile = chapter.get().tile(tileId);
        return tile.isPresent() && isUnlocked(player, chapter.get(), tile.get());
    }

    public static void unpin(ServerPlayer player) {
        try (PreparedStatement statement = ProgressDatabase.get().prepareStatement(
                "DELETE FROM pins WHERE player_uuid = ?")) {
            statement.setString(1, player.getUUID().toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
        syncNow(player);
    }

    public static void grantScroll(ServerPlayer player, ResourceLocation scrollId) {
        grantScroll(player, scrollId, true);
    }

    private static void grantScroll(ServerPlayer player, ResourceLocation scrollId, boolean syncAfter) {
        try (PreparedStatement statement = ProgressDatabase.get().prepareStatement(
                "INSERT OR IGNORE INTO scrolls(player_uuid, scroll_id) VALUES(?, ?)")) {
            statement.setString(1, player.getUUID().toString());
            statement.setString(2, scrollId.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
        invalidatePlayer(player.getUUID());
        if (syncAfter) {
            syncNow(player);
        }
    }

    public static void claimChoice(ServerPlayer player, ResourceLocation chapterId, String tileId, int option) {
        Optional<Chapter> chapter = QuestDefinitions.chapter(chapterId);
        if (chapter.isEmpty()) {
            return;
        }
        Optional<Tile> tile = chapter.get().tile(tileId);
        if (tile.isEmpty() || !snapshot(player).tileCompleted(chapterId.toString(), tileId)) {
            return;
        }
        String teamId = TeamService.ensureSolo(player);
        String questId = ProgressSnapshot.questKey(chapterId, tileId);
        if (isCompleted(teamId, questId, "choice")) {
            return;
        }
        for (dev.aof.questqueen.data.reward.Reward reward : tile.get().rewards()) {
            if (reward instanceof dev.aof.questqueen.data.reward.ChoiceReward choice) {
                // Reject a client-supplied option outside the pack's own list. Without this the
                // out-of-range index is still persisted as the "choice" value and the one-time
                // claim is burned with nothing granted.
                if (option < 0 || option >= choice.options().size()) {
                    return;
                }
                // Sibling non-choice rewards (loot / xp / item / …) grant with the pick — one settle.
                for (dev.aof.questqueen.data.reward.Reward other : tile.get().rewards()) {
                    if (!(other instanceof ChoiceReward)) {
                        other.grant(player);
                    }
                }
                for (ResourceLocation scroll : tile.get().scrolls()) {
                    grantScroll(player, scroll, false);
                }
                choice.grantOption(player, option);
                writeProgress(teamId, questId, "choice", option, true);
                writeProgress(teamId, questId, "claimed", 1, true);
                noteMutation(player, teamId);
                syncTeam(player.server, teamId);
                player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.4F, 1.2F);
                return;
            }
        }
    }

    public static void reset(ServerPlayer player) {
        String teamId = TeamService.ensureSolo(player);
        try (PreparedStatement progress = ProgressDatabase.get().prepareStatement("DELETE FROM progress WHERE team_id = ?");
             PreparedStatement scrolls = ProgressDatabase.get().prepareStatement("DELETE FROM scrolls WHERE player_uuid = ?");
             PreparedStatement pins = ProgressDatabase.get().prepareStatement("DELETE FROM pins WHERE player_uuid = ?")) {
            progress.setString(1, teamId);
            progress.executeUpdate();
            scrolls.setString(1, player.getUUID().toString());
            scrolls.executeUpdate();
            pins.setString(1, player.getUUID().toString());
            pins.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
        invalidateTeam(player.server, teamId);
        // team_flags back `trigger` gates; leaving them behind made reset a no-op for those quests.
        TeamService.clearFlags(teamId);
        PENDING_TEAM_SYNC.remove(teamId);
        syncNow(player);
    }

    public static boolean isUnlocked(ServerPlayer player, Chapter chapter, Tile tile) {
        ProgressSnapshot snap = snapshot(player);
        if (!snap.unlockedChapters().contains(chapter.id().toString())) {
            return false;
        }
        Set<String> completedTiles = snap.completedTiles();
        Set<String> completedForChapter = chapterCompletedTiles(chapter, completedTiles);
        if (tile.requiredStage().isPresent()
                && !dev.aof.questqueen.compat.ProgressiveStagesCompat.hasStage(player, tile.requiredStage().get())) {
            return false;
        }
        if (!GateEvaluator.unlocked(chapter, tile.id(), completedForChapter, condition -> extra(player, condition, completedTiles))) {
            return false;
        }
        return tile.hiddenUntil().map(hidden -> hiddenMet(player, hidden, completedTiles)).orElse(true);
    }

    public static boolean isChapterUnlocked(ServerPlayer player, Chapter chapter) {
        return snapshot(player).unlockedChapters().contains(chapter.id().toString());
    }

    public static boolean extra(ServerPlayer player, GateCondition condition) {
        return extra(player, condition, snapshot(player).completedTiles());
    }

    private static Set<String> chapterCompletedTiles(Chapter chapter, Set<String> completedTiles) {
        Set<String> completedForChapter = new HashSet<>();
        String prefix = chapter.id() + "/";
        for (String key : completedTiles) {
            if (key.startsWith(prefix)) {
                completedForChapter.add(key.substring(prefix.length()));
            }
        }
        return completedForChapter;
    }

    private static boolean extra(ServerPlayer player, GateCondition condition, Set<String> completedTiles) {
        return switch (condition.type()) {
            case "advancement" -> {
                AdvancementHolder holder = player.server.getAdvancements().get(ResourceLocation.parse(condition.id()));
                yield holder != null && player.getAdvancements().getOrStartProgress(holder).isDone();
            }
            case "scoreboard" -> {
                Objective objective = player.getScoreboard().getObjective(condition.id());
                yield objective != null && player.getScoreboard().getOrCreatePlayerScore(ScoreHolder.forNameOnly(player.getScoreboardName()), objective).get() > 0;
            }
            case "team_flag" -> TeamService.hasFlag(TeamService.ensureSolo(player), condition.id());
            case "quest_complete" -> completedTiles.contains(condition.id());
            case "chapter_complete" -> ChapterCompletion.fullyComplete(
                    condition.id(), QuestDefinitions.chapters(), completedTiles);
            case "trigger" -> completedTiles.contains(condition.id())
                    || TeamService.hasFlag(TeamService.ensureSolo(player), "trigger:" + condition.id());
            case "stage", "progressivestages" ->
                    dev.aof.questqueen.compat.ProgressiveStagesCompat.hasStage(player, condition.id());
            default -> false;
        };
    }

    private static boolean hiddenMet(ServerPlayer player, HiddenUntil hidden, Set<String> completedTiles) {
        return extra(player, new GateCondition(hidden.type(), hidden.id()), completedTiles);
    }

    private static void maybeCompleteTile(ServerPlayer player, Chapter chapter, Tile tile) {
        String teamId = TeamService.ensureSolo(player);
        // DB was just written; drop cache so tileComplete sees fresh completedTasks.
        invalidatePlayer(player.getUUID());
        Set<String> completedTasks = snapshot(player).completedTasks();
        if (!tileComplete(chapter, tile, completedTasks)) {
            return;
        }
        writeProgress(teamId, ProgressSnapshot.questKey(chapter.id(), tile.id()), "tile", 1, true);
        // Rewards wait for an explicit CLAIM / TAKE A·B in the book — never auto-grant here.
        // (Auto-grant made loot feel "missing" because COMPLETED showed reward icons with no button.)
        noteMutation(player, teamId);
        player.playNotifySound(SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.35F, 1.35F);
        player.sendSystemMessage(Component.translatable("questqueen.complete", tile.title()));
        // Caller (increment/setCompleted) already schedules syncTeam — one flush covers the tile flag.
    }

    /** True when this tile offers a pick-one reward, i.e. completion does not settle it. */
    public static boolean hasChoiceReward(Tile tile) {
        for (Reward reward : tile.rewards()) {
            if (reward instanceof ChoiceReward) {
                return true;
            }
        }
        return false;
    }

    private static void noteMutation(ServerPlayer player, String teamId) {
        invalidateTeam(player.server, teamId);
    }

    private static boolean tileComplete(Chapter chapter, Tile tile, Set<String> completedTasks) {
        if (tile.tasks().isEmpty()) {
            return completedTasks.contains(ProgressSnapshot.questKey(chapter.id(), tile.id()) + "/tile");
        }
        for (int i = 0; i < tile.tasks().size(); i++) {
            if (!completedTasks.contains(ProgressSnapshot.questKey(chapter.id(), tile.id()) + "/" + i)) {
                return false;
            }
        }
        return true;
    }

    private static Set<String> computeUnlockedChapters(ServerPlayer player, Set<String> completedTiles) {
        List<Chapter> chapters = List.copyOf(QuestDefinitions.chapters());
        Set<String> unlocked = new HashSet<>();
        boolean changed = true;
        while (changed) {
            changed = false;
            for (Chapter chapter : chapters) {
                String id = chapter.id().toString();
                if (unlocked.contains(id)) {
                    continue;
                }
                if (ChapterTree.isUnlocked(
                        chapter,
                        chapters,
                        condition -> extra(player, condition, completedTiles),
                        unlocked::contains
                )) {
                    unlocked.add(id);
                    changed = true;
                }
            }
        }
        return unlocked;
    }

    private static Set<String> computeRevealed(ServerPlayer player, Set<String> completedTiles, Set<String> unlockedChapters) {
        Set<String> revealed = new HashSet<>();
        for (Chapter chapter : QuestDefinitions.chapters()) {
            if (!unlockedChapters.contains(chapter.id().toString())) {
                continue;
            }
            Set<String> completedForChapter = chapterCompletedTiles(chapter, completedTiles);
            Set<String> starts = StartNodes.of(chapter);
            for (Tile tile : chapter.tiles()) {
                boolean unlocked = GateEvaluator.unlocked(chapter, tile.id(), completedForChapter, condition -> extra(player, condition, completedTiles));
                boolean hidden = tile.hiddenUntil().isPresent() && !hiddenMet(player, tile.hiddenUntil().get(), completedTiles);
                boolean stageOk = tile.requiredStage().isEmpty()
                        || dev.aof.questqueen.compat.ProgressiveStagesCompat.hasStage(player, tile.requiredStage().get());
                if ((unlocked || starts.contains(tile.id())) && !hidden && stageOk) {
                    revealed.add(ProgressSnapshot.questKey(chapter.id(), tile.id()));
                }
            }
        }
        return revealed;
    }

    private static List<ResourceLocation> ownedScrolls(UUID player) {
        List<ResourceLocation> scrolls = new ArrayList<>();
        try (PreparedStatement statement = ProgressDatabase.get().prepareStatement(
                "SELECT scroll_id FROM scrolls WHERE player_uuid = ?")) {
            statement.setString(1, player.toString());
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    scrolls.add(ResourceLocation.parse(result.getString(1)));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
        return scrolls;
    }

    private static int readValue(String teamId, String questId, String taskId) {
        try (PreparedStatement statement = ProgressDatabase.get().prepareStatement(
                "SELECT value FROM progress WHERE team_id = ? AND quest_id = ? AND task_id = ?")) {
            statement.setString(1, teamId);
            statement.setString(2, questId);
            statement.setString(3, taskId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static boolean isCompleted(String teamId, String questId, String taskId) {
        try (PreparedStatement statement = ProgressDatabase.get().prepareStatement(
                "SELECT completed FROM progress WHERE team_id = ? AND quest_id = ? AND task_id = ?")) {
            statement.setString(1, teamId);
            statement.setString(2, questId);
            statement.setString(3, taskId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() && result.getInt(1) != 0;
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void deleteProgress(String teamId, String questId, String taskId) {
        try (PreparedStatement statement = ProgressDatabase.get().prepareStatement(
                "DELETE FROM progress WHERE team_id = ? AND quest_id = ? AND task_id = ?")) {
            statement.setString(1, teamId);
            statement.setString(2, questId);
            statement.setString(3, taskId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private static void writeProgress(String teamId, String questId, String taskId, int value, boolean completed) {
        try (PreparedStatement statement = ProgressDatabase.get().prepareStatement(
                "INSERT INTO progress(team_id, quest_id, task_id, value, completed) VALUES(?, ?, ?, ?, ?) " +
                        "ON CONFLICT(team_id, quest_id, task_id) DO UPDATE SET value = excluded.value, completed = excluded.completed")) {
            statement.setString(1, teamId);
            statement.setString(2, questId);
            statement.setString(3, taskId);
            statement.setInt(4, value);
            statement.setInt(5, completed ? 1 : 0);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
