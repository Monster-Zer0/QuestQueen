package dev.aof.questqueen.compat;

import dev.aof.questqueen.QuestQueen;
import dev.aof.questqueen.progress.ProgressService;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Optional bridge to StageLock (mod id {@code stagelock}), the stage mod. Reflection rather than a compile
 * dependency, like {@link FtbTeamsCompat}, so the jar works with or without it. Any failure disables the bridge
 * with one WARN and Quest Queen carries on as if no stage mod were installed.
 *
 * <p>StageLock stage ids are exact: lowercase, 1-64 chars of {@code [a-z0-9_.:/-]}, no implied namespace (a
 * definition at {@code data/mypack/stagelock/stages/iron_age.json} with no "id" is {@code mypack:iron_age}).
 */
public final class StageLockCompat {
    public static final String MOD_ID = "stagelock";
    private static final String API = "dev.stagelock.api.StageLockAPI";
    private static final String STAGE_SET = "dev.stagelock.api.StageSet";
    private static final String CHANGED_EVENT = "dev.stagelock.api.event.StagesChangedEvent";
    private static final String DEFINITIONS = "dev.stagelock.core.definition.StageDefinitions";

    private static boolean broken;
    private static Method has;
    private static Method getStages;
    private static Method asSet;
    private static Method grant;

    private StageLockCompat() {
    }

    /** Resolve the API once. False (and logged once) when StageLock's API is not what this bridge expects. */
    private static synchronized boolean ready() {
        if (broken) {
            return false;
        }
        if (has != null) {
            return true;
        }
        try {
            Class<?> api = Class.forName(API);
            has = api.getMethod("has", Player.class, String.class);
            getStages = api.getMethod("getStages", Player.class);
            asSet = Class.forName(STAGE_SET).getMethod("asSet");
            grant = api.getMethod("grant", ServerPlayer.class, String[].class);
            return true;
        } catch (Throwable exception) {
            fail("API lookup", exception);
            return false;
        }
    }

    private static void fail(String what, Throwable exception) {
        if (!broken) {
            broken = true;
            QuestQueen.LOGGER.warn("StageLock is installed but its {} failed ({}); stage features are off", what,
                    exception.toString());
        }
    }

    /** StageLock lowercases ids; a pack may have written {@code Iron_Age}. */
    static String normalize(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    public static void register() {
        if (!ready()) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Event> event = (Class<? extends Event>) Class.forName(CHANGED_EVENT);
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, event, StageLockCompat::onStagesChanged);
            QuestQueen.LOGGER.info("StageLock stage compat enabled");
        } catch (Throwable exception) {
            fail("stage-change hook", exception);
        }
    }

    public static boolean hasStage(ServerPlayer player, String id) {
        if (player == null || normalize(id).isEmpty() || !ready()) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(has.invoke(null, player, normalize(id)));
        } catch (Throwable exception) {
            fail("stage check", exception);
            return false;
        }
    }

    public static boolean grantStage(ServerPlayer player, String id) {
        if (player == null || normalize(id).isEmpty() || !ready()) {
            return false;
        }
        try {
            grant.invoke(null, player, new String[]{normalize(id)});
            return true;
        } catch (Throwable exception) {
            QuestQueen.LOGGER.warn("StageLock grant failed for {}: {}", id, exception.toString());
            return false;
        }
    }

    /** The player's effective stages (player, team, global and contributed), as StageLock reports them. */
    public static Set<String> ownedStages(Player player) {
        Set<String> owned = new LinkedHashSet<>();
        if (player == null || !ready()) {
            return owned;
        }
        try {
            Object set = asSet.invoke(getStages.invoke(null, player));
            if (set instanceof Iterable<?> stages) {
                for (Object stage : stages) {
                    owned.add(String.valueOf(stage));
                }
            }
        } catch (Throwable exception) {
            fail("stage list", exception);
        }
        return owned;
    }

    /** Exact match after lowercasing: StageLock has no implied namespace to strip or add. */
    public static boolean matches(Set<String> owned, String id) {
        return owned != null && !normalize(id).isEmpty() && owned.contains(normalize(id));
    }

    /**
     * Stages the pack defines, id → display name, from the client's synced copy. That store is StageLock's
     * internal class (the public API has no listing), so a change there only empties the editor's dropdown.
     */
    public static Map<String, String> definedStages() {
        return definitions("client");
    }

    /** Ids of the stages the server's datapacks define; empty when none are defined (or the store is unreadable). */
    public static Set<String> serverDefinedStages() {
        return definitions("server").keySet();
    }

    private static Map<String, String> definitions(String side) {
        Map<String, String> out = new LinkedHashMap<>();
        try {
            Class<?> store = Class.forName(DEFINITIONS);
            Object copy = store.getMethod(side).invoke(null);
            Object all = store.getMethod("all").invoke(copy);
            if (all instanceof Map<?, ?> map) {
                for (Map.Entry<?, ?> entry : map.entrySet()) {
                    String id = String.valueOf(entry.getKey());
                    out.put(id, displayName(entry.getValue()).orElse(id));
                }
            }
        } catch (Throwable ignored) {
            // No definitions available: the editor still lets authors type stage ids.
        }
        return out;
    }

    private static Optional<String> displayName(Object definition) {
        try {
            Object name = definition.getClass().getMethod("displayName").invoke(definition);
            if (name instanceof Optional<?> optional && optional.isPresent() && optional.get() instanceof Component component) {
                return Optional.of(component.getString());
            }
        } catch (Throwable ignored) {
        }
        return Optional.empty();
    }

    private static void onStagesChanged(Event event) {
        try {
            Object player = event.getClass().getMethod("getEntity").invoke(event);
            if (player instanceof ServerPlayer serverPlayer) {
                // Fires on both sides once per player per tick; only the server re-sends quest progress.
                ProgressService.onServerThread(serverPlayer, () -> ProgressService.sync(serverPlayer));
            }
        } catch (Throwable ignored) {
        }
    }
}
