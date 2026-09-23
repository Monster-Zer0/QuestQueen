package dev.aof.questqueen.data.task;

import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

public final class TaskFactory {
    public static final List<String> TYPES = List.of(
            "obtain", "submit", "item_tag", "kill", "raid", "advancement",
            "location", "visit_structure", "visit_dimension", "visit_biome",
            "observation", "checkmark", "stat", "interact_block", "interact_entity",
            "fluid", "xp_levels", "npc_dialog", "trigger"
    );

    private TaskFactory() {
    }

    public static Task create(String type) {
        ResourceLocation stone = ResourceLocation.parse("minecraft:stone");
        return switch (type) {
            case "obtain", "item" -> new ObtainTask(ResourceLocation.parse("minecraft:chest"), 1);
            case "submit" -> new SubmitTask(ResourceLocation.parse("minecraft:rotten_flesh"), 1);
            case "item_tag" -> new ItemTagTask(ResourceLocation.parse("minecraft:planks"), 1);
            case "kill", "kill_entity" -> new KillTask(Optional.of(ResourceLocation.parse("minecraft:zombie")), Optional.empty(), 1);
            case "raid" -> new RaidTask(1);
            case "advancement" -> new AdvancementTask(ResourceLocation.parse("minecraft:story/root"));
            case "location" -> new LocationTask(0, 64, 0, 8, "");
            case "visit_structure" -> new VisitStructureTask(ResourceLocation.parse("minecraft:village_plains"));
            case "visit_dimension", "dimension" -> new DimensionTask(ResourceLocation.parse("minecraft:the_nether"));
            case "visit_biome", "biome" -> new BiomeTask(ResourceLocation.parse("minecraft:plains"));
            case "observation" -> new ObservationTask(Optional.of(stone), Optional.empty());
            case "checkmark" -> new CheckmarkTask();
            case "stat" -> new StatTask(ResourceLocation.parse("minecraft:walk_one_cm"), 1000);
            case "interact_block" -> new InteractBlockTask(ResourceLocation.parse("minecraft:crafting_table"), 1);
            case "interact_entity" -> new InteractEntityTask(ResourceLocation.parse("minecraft:villager"), 1);
            case "fluid" -> new FluidTask(ResourceLocation.parse("minecraft:lava"), 1);
            case "xp_levels" -> new XpLevelTask(5);
            case "npc_dialog" -> new NpcDialogTask("guide");
            case "trigger" -> new TriggerTask("questqueen:custom");
            default -> new CheckmarkTask();
        };
    }

    public static Task next(Task current) {
        int index = TYPES.indexOf(current.type());
        String type = TYPES.get((Math.max(index, 0) + 1) % TYPES.size());
        Task next = create(type);
        // withCount returns `this` for the binary types, so identity tells us whether the new type honours a
        // count at all. The old code passed the count into those no-ops and it silently reverted.
        return next.withCount(Math.max(1, current.required()));
    }

    public static Task retarget(Task task, ResourceLocation id) {
        int count = Math.max(1, task.required());
        return switch (task.type()) {
            case "obtain" -> new ObtainTask(id, count);
            case "submit" -> new SubmitTask(id, count);
            case "item_tag" -> new ItemTagTask(id, count);
            case "kill" -> new KillTask(Optional.of(id), Optional.empty(), count);
            case "advancement" -> new AdvancementTask(id);
            case "visit_structure" -> new VisitStructureTask(id);
            case "visit_dimension" -> new DimensionTask(id);
            case "visit_biome" -> new BiomeTask(id);
            case "observation" -> new ObservationTask(Optional.of(id), task.entityId());
            case "stat" -> new StatTask(id, count);
            case "interact_block" -> new InteractBlockTask(id, count);
            case "interact_entity" -> new InteractEntityTask(id, count);
            case "fluid" -> new FluidTask(id, count);
            default -> task;
        };
    }

    public static String targetKind(String type) {
        return switch (type) {
            case "kill", "interact_entity" -> "entity";
            case "interact_block", "observation" -> "block";
            case "fluid" -> "fluid";
            case "advancement" -> "advancement";
            case "visit_biome" -> "biome";
            case "visit_structure" -> "structure";
            case "visit_dimension" -> "dimension";
            case "item_tag" -> "item_tag";
            case "stat" -> "stat";
            case "location" -> "here";
            case "checkmark", "raid", "xp_levels", "npc_dialog", "trigger" -> "none";
            default -> "item";
        };
    }
}
