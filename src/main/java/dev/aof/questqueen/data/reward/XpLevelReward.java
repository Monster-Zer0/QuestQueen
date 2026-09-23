package dev.aof.questqueen.data.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

public record XpLevelReward(int levels) implements Reward {
    public static final MapCodec<XpLevelReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("levels", 1).forGetter(XpLevelReward::levels)
    ).apply(instance, XpLevelReward::new));

    @Override
    public String type() {
        return "xp_levels";
    }

    @Override
    public int count() {
        return levels;
    }

    @Override
    public String describe() {
        return levels + " levels";
    }

    @Override
    public void grant(ServerPlayer player) {
        player.giveExperienceLevels(levels);
    }
}
