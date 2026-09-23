package dev.aof.questqueen.net;

import dev.aof.questqueen.QuestQueen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record ClaimChoiceC2S(ResourceLocation chapter, String tile, int option) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ClaimChoiceC2S> TYPE = new CustomPacketPayload.Type<>(QuestQueen.id("claim_choice"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ClaimChoiceC2S> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, ClaimChoiceC2S::chapter,
            ByteBufCodecs.STRING_UTF8, ClaimChoiceC2S::tile,
            ByteBufCodecs.VAR_INT, ClaimChoiceC2S::option,
            ClaimChoiceC2S::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
