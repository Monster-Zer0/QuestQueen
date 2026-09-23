package dev.aof.questqueen.compat;

import dev.aof.questqueen.QuestQueen;
import dev.aof.questqueen.progress.ProgressService;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Optional ProgressiveStages 3.x bridge. Safe when the mod is absent. */
public final class ProgressiveStagesCompat {
    public static final String MOD_ID = "progressivestages";
    private static final String API = "com.enviouse.progressivestages.common.api.ProgressiveStagesAPI";
    private static final String STAGE_ID = "com.enviouse.progressivestages.common.api.StageId";
    private static final String STAGE_CAUSE = "com.enviouse.progressivestages.common.api.StageCause";
    private static final String STAGE_EVENT = "com.enviouse.progressivestages.common.api.StageChangeEvent";

    private static final boolean PRESENT = ModList.get().isLoaded(MOD_ID);

    private ProgressiveStagesCompat() {
    }

    public static boolean present() {
        return PRESENT;
    }

    public static void register() {
        if (!PRESENT) {
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            Class<? extends Event> event = (Class<? extends Event>) Class.forName(STAGE_EVENT);
            NeoForge.EVENT_BUS.addListener(EventPriority.NORMAL, false, event, ProgressiveStagesCompat::onStageEvent);
            QuestQueen.LOGGER.info("ProgressiveStages compat enabled");
        } catch (Throwable exception) {
            QuestQueen.LOGGER.warn("ProgressiveStages present but event hook failed: {}", exception.toString());
        }
    }

    public static boolean hasStage(ServerPlayer player, String id) {
        if (!PRESENT || player == null || id == null || id.isBlank()) {
            return false;
        }
        try {
            Class<?> api = Class.forName(API);
            try {
                Method has = api.getMethod("hasStage", ServerPlayer.class, String.class);
                return Boolean.TRUE.equals(has.invoke(null, player, id));
            } catch (NoSuchMethodException ignored) {
                Class<?> stageIdClass = Class.forName(STAGE_ID);
                Object stageId = parseStageId(stageIdClass, id);
                Method has = api.getMethod("hasStage", ServerPlayer.class, stageIdClass);
                return Boolean.TRUE.equals(has.invoke(null, player, stageId));
            }
        } catch (Throwable exception) {
            return false;
        }
    }

    public static boolean grantStage(ServerPlayer player, String id) {
        if (!PRESENT || player == null || id == null || id.isBlank()) {
            return false;
        }
        try {
            Class<?> api = Class.forName(API);
            Class<?> stageIdClass = Class.forName(STAGE_ID);
            Class<?> causeClass = Class.forName(STAGE_CAUSE);
            Object stageId = parseStageId(stageIdClass, id);
            Object cause = stageCause(causeClass);
            Method grant = api.getMethod("grantStage", ServerPlayer.class, stageIdClass, causeClass);
            return Boolean.TRUE.equals(grant.invoke(null, player, stageId, cause));
        } catch (Throwable exception) {
            QuestQueen.LOGGER.warn("ProgressiveStages grant failed for {}: {}", id, exception.toString());
            return false;
        }
    }

    public static Set<String> ownedStages(ServerPlayer player) {
        Set<String> owned = new LinkedHashSet<>();
        if (!PRESENT || player == null) {
            return owned;
        }
        try {
            Class<?> api = Class.forName(API);
            Method get = api.getMethod("getStages", ServerPlayer.class);
            Object raw = get.invoke(null, player);
            if (raw instanceof Iterable<?> list) {
                for (Object stage : list) {
                    addStageToken(owned, stage);
                }
            }
        } catch (Throwable ignored) {
        }
        return owned;
    }

    private static void addStageToken(Set<String> owned, Object stage) {
        if (stage == null) {
            return;
        }
        String token = stage.toString();
        for (String method : List.of("id", "value", "getId", "asString")) {
            try {
                Object raw = stage.getClass().getMethod(method).invoke(stage);
                if (raw != null && !raw.toString().isBlank()) {
                    token = raw.toString();
                    break;
                }
            } catch (Throwable ignored) {
            }
        }
        owned.add(token);
        int colon = token.indexOf(':');
        if (colon >= 0 && colon < token.length() - 1) {
            owned.add(token.substring(colon + 1));
        }
    }

    public static boolean matches(Set<String> owned, String id) {
        if (id == null || id.isBlank() || owned == null || owned.isEmpty()) {
            return false;
        }
        if (owned.contains(id)) {
            return true;
        }
        if (!id.contains(":")) {
            return owned.contains("progressivestages:" + id);
        }
        int slash = id.indexOf(':');
        return owned.contains(id.substring(slash + 1));
    }

    private static Object parseStageId(Class<?> stageIdClass, String id) throws Exception {
        for (String method : List.of("parse", "of", "fromString", "from")) {
            try {
                return stageIdClass.getMethod(method, String.class).invoke(null, id);
            } catch (NoSuchMethodException ignored) {
            }
        }
        throw new NoSuchMethodException("StageId.parse/of/fromString/from");
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Object stageCause(Class<?> causeClass) {
        for (String name : List.of("QUEST_REWARD", "COMMAND", "API", "UNKNOWN")) {
            try {
                return Enum.valueOf((Class) causeClass, name);
            } catch (IllegalArgumentException ignored) {
            }
        }
        Object[] constants = causeClass.getEnumConstants();
        if (constants != null && constants.length > 0) {
            return constants[0];
        }
        throw new IllegalStateException("No StageCause constants");
    }

    private static void onStageEvent(Event event) {
        try {
            Object player;
            try {
                player = event.getClass().getMethod("getPlayer").invoke(event);
            } catch (NoSuchMethodException ignored) {
                player = event.getClass().getMethod("player").invoke(event);
            }
            if (player instanceof ServerPlayer serverPlayer) {
                ProgressService.sync(serverPlayer);
            }
        } catch (Throwable ignored) {
        }
    }
}
