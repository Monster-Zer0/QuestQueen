package dev.aof.questqueen.net;

import dev.aof.questqueen.QuestQueen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record SubmitTaskC2S(ResourceLocation chapter, String tile, int taskIndex) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<SubmitTaskC2S> TYPE = new CustomPacketPayload.Type<>(QuestQueen.id("submit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, SubmitTaskC2S> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, SubmitTaskC2S::chapter,
            ByteBufCodecs.STRING_UTF8, SubmitTaskC2S::tile,
            ByteBufCodecs.VAR_INT, SubmitTaskC2S::taskIndex,
            SubmitTaskC2S::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
