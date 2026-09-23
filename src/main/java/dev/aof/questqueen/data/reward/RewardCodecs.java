package dev.aof.questqueen.data.reward;

import com.mojang.serialization.MapCodec;

import java.util.Locale;
import java.util.Map;

/**
 * The reward-type → codec registry.
 *
 * <p>Deliberately not declared on {@link Reward}: holding it there made {@code Reward.<clinit>} resolve
 * every reward codec while each of those codecs resolves {@code Reward::type} — a class-initialization
 * cycle that left entries null and poisoned the whole reward/task/chapter graph (every codec test failed
 * with {@code Could not initialize class dev.aof.questqueen.data.reward.Reward}). The same fix applied to
 * {@code TaskCodecs}.
 */
final class RewardCodecs {
    static final Map<String, MapCodec<? extends Reward>> CODECS = Map.ofEntries(
            Map.entry("item", ItemReward.CODEC),
            Map.entry("xp", XpReward.CODEC),
            Map.entry("xp_levels", XpLevelReward.CODEC),
            Map.entry("command", CommandReward.CODEC),
            Map.entry("loot", LootTableReward.CODEC),
            Map.entry("toast", ToastReward.CODEC),
            Map.entry("advancement", AdvancementReward.CODEC),
            Map.entry("choice", ChoiceReward.CODEC),
            Map.entry("stage", StageReward.CODEC),
            Map.entry("progressivestages", StageReward.CODEC)
    );

    private RewardCodecs() {
    }

    static MapCodec<? extends Reward> forType(String type) {
        MapCodec<? extends Reward> codec = CODECS.get(type.toLowerCase(Locale.ROOT));
        if (codec == null) {
            throw new IllegalArgumentException("Unknown quest reward type: " + type);
        }
        return codec;
    }
}
