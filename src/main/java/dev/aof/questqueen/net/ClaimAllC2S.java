package dev.aof.questqueen.net;

import dev.aof.questqueen.QuestQueen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Claim every ready non-choice reward in one chapter. The server recomputes the tiles. */
public record ClaimAllC2S(ResourceLocation chapter) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClaimAllC2S> TYPE = new CustomPacketPayload.Type<>(QuestQueen.id("claim_all"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimAllC2S> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ClaimAllC2S::chapter,
            ClaimAllC2S::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
