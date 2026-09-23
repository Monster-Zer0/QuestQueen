package dev.aof.questqueen;

import net.neoforged.fml.ModList;

/**
 * The one place the mod's own version is read for player-facing text.
 *
 * <p>Command feedback used to hardcode {@code QQ[1.1.129]} — it stayed at 1.1.129 for thirty-plus releases
 * while {@code gradle.properties} moved on to 1.1.167, so every raid/debug line lied about which build a
 * player was running. The version now comes from the loaded mod container, which NeoForge fills from
 * {@code neoforge.mods.toml}, which Gradle expands from {@code mod_version} — one source, no second copy to
 * forget.
 */
public final class QuestVersion {
    /** Shown when there is no mod container to ask (unit tests, tooling, very early startup). */
    public static final String UNKNOWN = "dev";

    private QuestVersion() {
    }

    /** The bare version string, e.g. {@code 1.1.168}. */
    public static String raw() {
        try {
            var container = ModList.get();
            if (container == null) {
                return UNKNOWN;
            }
            return container.getModContainerById(QuestQueen.MODID)
                    .map(mod -> mod.getModInfo().getVersion().toString())
                    .orElse(UNKNOWN);
        } catch (RuntimeException | LinkageError notLoaded) {
            return UNKNOWN;
        }
    }

    /** The feedback prefix, e.g. {@code QQ[1.1.168]}. */
    public static String stamp() {
        return "QQ[" + raw() + "]";
    }

    /** Stamp a message, e.g. {@code QQ[1.1.168] raid-start failed: …}. */
    public static String prefix(String message) {
        return stamp() + " " + message;
    }
}
