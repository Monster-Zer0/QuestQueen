package dev.aof.questqueen.data.reward;

import net.minecraft.resources.ResourceLocation;

import java.util.Optional;

/** Board reward face — every grant type maps to a real item icon. No bare "+" . */
public final class RewardIcons {
    private RewardIcons() {
    }

    public static Optional<ResourceLocation> iconId(Reward reward) {
        if (reward == null) {
            return Optional.empty();
        }
        Optional<ResourceLocation> item = reward.itemId();
        if (item.isPresent()) {
            return item;
        }
        if (reward instanceof ChoiceReward choice && !choice.options().isEmpty()) {
            return Optional.of(choice.options().getFirst().item());
        }
        return Optional.ofNullable(switch (reward.type()) {
            case "xp", "xp_levels" -> ResourceLocation.parse("minecraft:experience_bottle");
            case "advancement" -> ResourceLocation.parse("minecraft:knowledge_book");
            case "stage", "progressivestages" -> ResourceLocation.parse("minecraft:amethyst_shard");
            case "toast" -> ResourceLocation.parse("minecraft:paper");
            case "command" -> ResourceLocation.parse("minecraft:command_block");
            case "loot", "loot_table" -> ResourceLocation.parse("minecraft:chest");
            case "choice" -> ResourceLocation.parse("minecraft:chest");
            default -> null;
        });
    }
}
