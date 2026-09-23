package dev.aof.questqueen.data.task;

import com.mojang.serialization.MapCodec;

import java.util.Locale;
import java.util.Map;

/**
 * The task-type → codec registry and the authoring aliases.
 *
 * <p>Deliberately not declared on {@link Task}. Holding these constants in the interface made
 * {@code Task.<clinit>} resolve all nineteen task codecs, while each of those codecs resolves
 * {@code Task::type} — a class-initialization cycle that left {@code CODECS} entries null and poisoned the
 * whole task/reward class graph (every codec test failed with {@code Could not initialize class
 * dev.aof.questqueen.data.task.Task}).
 */
final class TaskCodecs {
    static final Map<String, String> ALIASES = Map.of(
            "item", "obtain",
            "kill_entity", "kill",
            "dimension", "visit_dimension",
            "biome", "visit_biome",
            "structure", "visit_structure"
    );

    static final Map<String, MapCodec<? extends Task>> CODECS = Map.ofEntries(
            Map.entry("obtain", ObtainTask.CODEC),
            Map.entry("item_tag", ItemTagTask.CODEC),
            Map.entry("submit", SubmitTask.CODEC),
            Map.entry("kill", KillTask.CODEC),
            Map.entry("raid", RaidTask.CODEC),
            Map.entry("advancement", AdvancementTask.CODEC),
            Map.entry("location", LocationTask.CODEC),
            Map.entry("visit_structure", VisitStructureTask.CODEC),
            Map.entry("visit_dimension", DimensionTask.CODEC),
            Map.entry("visit_biome", BiomeTask.CODEC),
            Map.entry("observation", ObservationTask.CODEC),
            Map.entry("checkmark", CheckmarkTask.CODEC),
            Map.entry("stat", StatTask.CODEC),
            Map.entry("interact_block", InteractBlockTask.CODEC),
            Map.entry("interact_entity", InteractEntityTask.CODEC),
            Map.entry("fluid", FluidTask.CODEC),
            Map.entry("xp_levels", XpLevelTask.CODEC),
            Map.entry("npc_dialog", NpcDialogTask.CODEC),
            Map.entry("trigger", TriggerTask.CODEC)
    );

    private TaskCodecs() {
    }

    static MapCodec<? extends Task> forType(String type) {
        String key = type.toLowerCase(Locale.ROOT);
        MapCodec<? extends Task> codec = CODECS.get(ALIASES.getOrDefault(key, key));
        if (codec == null) {
            throw new IllegalArgumentException("Unknown quest task type: " + type);
        }
        return codec;
    }
}
