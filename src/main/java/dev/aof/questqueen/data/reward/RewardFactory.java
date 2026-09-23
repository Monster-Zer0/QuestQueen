package dev.aof.questqueen.data.reward;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

public final class RewardFactory {
    public static final List<String> TYPES = List.of(
            "item", "xp", "xp_levels", "command", "loot", "toast", "advancement", "choice", "stage"
    );

    private RewardFactory() {
    }

    public static Reward create(String type) {
        return switch (type) {
            case "item" -> new ItemReward(ResourceLocation.parse("minecraft:diamond"), 1);
            case "xp" -> new XpReward(25);
            case "xp_levels" -> new XpLevelReward(1);
            case "command" -> new CommandReward("say Quest complete");
            case "loot" -> new LootTableReward(ResourceLocation.parse("minecraft:chests/simple_dungeon"));
            case "toast" -> new ToastReward("Quest complete");
            case "advancement" -> new AdvancementReward(ResourceLocation.parse("minecraft:story/root"));
            case "choice" -> new ChoiceReward(List.of(
                    new ItemReward(ResourceLocation.parse("minecraft:diamond"), 1),
                    new ItemReward(ResourceLocation.parse("minecraft:emerald"), 8)
            ));
            case "stage", "progressivestages" -> new StageReward("example");
            default -> new XpReward(5);
        };
    }

    public static Reward next(Reward current) {
        int index = TYPES.indexOf(current.type());
        return create(TYPES.get((Math.max(index, 0) + 1) % TYPES.size()));
    }

    public static Reward retarget(Reward reward, ResourceLocation id) {
        return switch (reward) {
            case ItemReward item -> new ItemReward(id, item.count());
            case LootTableReward ignored -> new LootTableReward(id);
            case AdvancementReward ignored -> new AdvancementReward(id);
            case ChoiceReward choice -> {
                List<ItemReward> options = new ArrayList<>(choice.options());
                if (options.isEmpty()) {
                    options.add(new ItemReward(id, 1));
                } else {
                    // Retarget the PICKED option explicitly; the old code retargeted a bare `Task` id
                    // (never an ItemReward) and then discarded the list, so the pick silently did nothing.
                    options.set(0, (ItemReward) retarget(options.getFirst(), id));
                }
                yield new ChoiceReward(options);
            }
            default -> reward;
        };
    }

    public static Reward withCount(Reward reward, int count) {
        int next = Math.max(1, count);
        return switch (reward) {
            case ItemReward item -> new ItemReward(item.item(), next);
            case XpReward ignored -> new XpReward(next);
            case XpLevelReward ignored -> new XpLevelReward(next);
            default -> reward;
        };
    }

    public static String targetKind(String type) {
        return switch (type) {
            case "item", "choice" -> "item";
            case "loot" -> "loot";
            case "advancement" -> "advancement";
            default -> "none";
        };
    }
}
