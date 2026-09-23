package dev.aof.questqueen.data.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerPlayer;

public record XpReward(int amount) implements Reward {
    public static final MapCodec<XpReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.INT.optionalFieldOf("amount", 5).forGetter(XpReward::amount)
    ).apply(instance, XpReward::new));

    @Override
    public String type() {
        return "xp";
    }

    @Override
    public int count() {
        return amount;
    }

    @Override
    public String describe() {
        return amount + " XP";
    }

    @Override
    public void grant(ServerPlayer player) {
        player.giveExperiencePoints(amount);
    }
}
