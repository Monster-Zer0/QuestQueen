package dev.aof.questqueen.net;

import dev.aof.questqueen.QuestQueen;
import net.minecraft.server.MinecraftServer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Runs {@code /reload} shortly after a stage is saved or deleted, so StageLock reads the new files without the
 * author typing it. Debounced: saving several stages in a row reloads once. Running the command (rather than
 * {@code reloadResources} directly) also discovers a {@code questqueen_authored} pack created by this save.
 */
public final class StageReload {
    private static final int DELAY_TICKS = 20;
    private static volatile int dueTick = -1;

    private StageReload() {
    }

    static void request(MinecraftServer server) {
        dueTick = server.getTickCount() + DELAY_TICKS;
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        int due = dueTick;
        MinecraftServer server = event.getServer();
        if (due < 0 || server.getTickCount() < due) {
            return;
        }
        dueTick = -1;
        QuestQueen.LOGGER.info("Quest Queen: reloading data packs for edited stages");
        server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), "reload");
    }
}
