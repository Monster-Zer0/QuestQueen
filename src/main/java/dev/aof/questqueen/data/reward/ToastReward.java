package dev.aof.questqueen.data.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public record ToastReward(String message) implements Reward {
    public static final MapCodec<ToastReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("message").forGetter(ToastReward::message)
    ).apply(instance, ToastReward::new));

    @Override
    public String type() {
        return "toast";
    }

    @Override
    public String describe() {
        return message;
    }

    @Override
    public boolean noisy() {
        return true;
    }

    @Override
    public void grant(ServerPlayer player) {
        player.displayClientMessage(Component.literal(message), true);
    }
}
