package dev.aof.questqueen.compat;

import dev.aof.questqueen.QuestQueen;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The one door to stages. Everything in Quest Queen that reads or grants a stage ({@code required_stage}, "stage"
 * conditions on links, chapter unlocks and {@code hidden_until}, the stage reward) goes through here, and this picks
 * the installed stage mod: StageLock first, then ProgressiveStages, else none (every stage check answers false).
 */
public final class Stages {
    public enum Backend { STAGELOCK, PROGRESSIVE_STAGES, NONE }

    /** Stage id rule StageLock enforces (and a safe one for ProgressiveStages): lowercase, 1-64 of [a-z0-9_.:/-]. */
    private static final Pattern VALID = Pattern.compile("^[a-z0-9_.:/-]{1,64}$");

    private static Backend backend;

    private Stages() {
    }

    /** Chosen once, lazily, so code that merely mentions stages (and unit tests, with no mod list) never fails. */
    public static synchronized Backend backend() {
        if (backend == null) {
            backend = pick();
        }
        return backend;
    }

    private static Backend pick() {
        try {
            ModList mods = ModList.get();
            if (mods == null) {
                return Backend.NONE;
            }
            if (mods.isLoaded(StageLockCompat.MOD_ID)) {
                return Backend.STAGELOCK;
            }
            if (mods.isLoaded(ProgressiveStagesCompat.MOD_ID)) {
                return Backend.PROGRESSIVE_STAGES;
            }
        } catch (Throwable ignored) {
        }
        return Backend.NONE;
    }

    public static boolean present() {
        return backend() != Backend.NONE;
    }

    public static void register() {
        Backend chosen = backend();
        QuestQueen.LOGGER.info("Quest stages: {}", switch (chosen) {
            case STAGELOCK -> "StageLock";
            case PROGRESSIVE_STAGES -> "ProgressiveStages";
            case NONE -> "no stage mod installed";
        });
        switch (chosen) {
            case STAGELOCK -> StageLockCompat.register();
            case PROGRESSIVE_STAGES -> ProgressiveStagesCompat.register();
            case NONE -> {
            }
        }
    }

    public static boolean hasStage(ServerPlayer player, String id) {
        return switch (backend()) {
            case STAGELOCK -> StageLockCompat.hasStage(player, id);
            case PROGRESSIVE_STAGES -> ProgressiveStagesCompat.hasStage(player, id);
            case NONE -> false;
        };
    }

    public static boolean grantStage(ServerPlayer player, String id) {
        return switch (backend()) {
            case STAGELOCK -> StageLockCompat.grantStage(player, id);
            case PROGRESSIVE_STAGES -> ProgressiveStagesCompat.grantStage(player, id);
            case NONE -> false;
        };
    }

    /** The player's stages, sent to the client so the book can decide stage-gated quests the way the server does. */
    public static Set<String> ownedStages(ServerPlayer player) {
        return switch (backend()) {
            case STAGELOCK -> StageLockCompat.ownedStages(player);
            case PROGRESSIVE_STAGES -> ProgressiveStagesCompat.ownedStages(player);
            case NONE -> Set.of();
        };
    }

    /** Does a synced stage list contain {@code id}? Each stage mod has its own id rules. */
    public static boolean matches(Set<String> owned, String id) {
        return switch (backend()) {
            case STAGELOCK -> StageLockCompat.matches(owned, id);
            case PROGRESSIVE_STAGES -> ProgressiveStagesCompat.matches(owned, id);
            case NONE -> false;
        };
    }

    /** Stages the pack defines (id → display name), for the editor's dropdown. Only StageLock lists them. */
    public static Map<String, String> definedStages() {
        return backend() == Backend.STAGELOCK ? StageLockCompat.definedStages() : Map.of();
    }

    /** A stage id StageLock accepts after lowercasing (it lowercases ids itself). */
    public static boolean validId(String id) {
        return id != null && VALID.matcher(id.trim().toLowerCase(java.util.Locale.ROOT)).matches();
    }

    /** Condition and reward type names that mean "a stage". "progression" is StageLock's old name (packs from 1.1.213). */
    public static boolean isStageType(String type) {
        return "stage".equals(type) || "stagelock".equals(type) || "progression".equals(type) || "progressivestages".equals(type);
    }

    /** Test hook: pin the backend (unit tests run without a mod list). */
    static synchronized void force(Backend next) {
        backend = next;
    }
}
