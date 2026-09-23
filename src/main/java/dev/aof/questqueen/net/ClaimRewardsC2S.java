package dev.aof.questqueen.net;

import dev.aof.questqueen.QuestQueen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClaimRewardsC2S(ResourceLocation chapter, String tile) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClaimRewardsC2S> TYPE = new CustomPacketPayload.Type<>(QuestQueen.id("claim_rewards"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimRewardsC2S> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ClaimRewardsC2S::chapter,
            ByteBufCodecs.STRING_UTF8, ClaimRewardsC2S::tile,
            ClaimRewardsC2S::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
