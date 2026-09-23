package dev.aof.questqueen.progress;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class EditorSessions {
    private static final Set<UUID> ENABLED = ConcurrentHashMap.newKeySet();

    private EditorSessions() {
    }

    public static boolean isEnabled(ServerPlayer player) {
        return ENABLED.contains(player.getUUID());
    }

    public static boolean enable(ServerPlayer player) {
        return ENABLED.add(player.getUUID());
    }

    public static boolean disable(UUID player) {
        return ENABLED.remove(player);
    }

    public static boolean toggle(ServerPlayer player) {
        if (isEnabled(player)) {
            disable(player.getUUID());
            return false;
        }
        enable(player);
        return true;
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            disable(player.getUUID());
        }
    }
}
