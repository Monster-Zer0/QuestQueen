package dev.aof.questqueen.api;

import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.QuestDefinitions;
import dev.aof.questqueen.data.Tile;
import dev.aof.questqueen.data.task.Task;
import dev.aof.questqueen.progress.ProgressService;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public final class QuestNpcApi {
    private QuestNpcApi() {
    }

    public static void onTalked(ServerPlayer player, String npcId) {
        for (Chapter chapter : QuestDefinitions.chapters()) {
            for (Tile tile : chapter.tiles()) {
                ListWalker.forEachTask(chapter, tile, (index, task) -> {
                    if ("npc_dialog".equals(task.type()) && npcId.equals(task.npcId().orElse(""))) {
                        ProgressService.setCompleted(player, chapter.id(), tile.id(), index);
                    }
                });
            }
        }
    }

    public static void grantScroll(ServerPlayer player, ResourceLocation scrollId) {
        ProgressService.grantScroll(player, scrollId);
    }

    public static void completeTrigger(ServerPlayer player, String triggerId) {
        fireTrigger(player, triggerId);
    }

    public static void fireTrigger(ServerPlayer player, String triggerId) {
        for (Chapter chapter : QuestDefinitions.chapters()) {
            for (Tile tile : chapter.tiles()) {
                ListWalker.forEachTask(chapter, tile, (index, task) -> {
                    if ("trigger".equals(task.type()) && triggerId.equals(task.triggerId().orElse(""))) {
                        ProgressService.setCompleted(player, chapter.id(), tile.id(), index);
                    }
                });
            }
        }
        ProgressService.extra(player, new dev.aof.questqueen.data.GateCondition("trigger", triggerId));
        String teamId = dev.aof.questqueen.progress.TeamService.ensureSolo(player);
        dev.aof.questqueen.progress.TeamService.setFlag(teamId, "trigger:" + triggerId);
        // The flag is team-wide, so every member's cached gates are now stale, not only the firing player's.
        ProgressService.syncTeam(player.server, teamId);
    }

    @FunctionalInterface
    private interface TaskConsumer {
        void accept(int index, Task task);
    }

    private static final class ListWalker {
        private static void forEachTask(Chapter chapter, Tile tile, TaskConsumer consumer) {
            for (int i = 0; i < tile.tasks().size(); i++) {
                consumer.accept(i, tile.tasks().get(i));
            }
        }
    }
}
