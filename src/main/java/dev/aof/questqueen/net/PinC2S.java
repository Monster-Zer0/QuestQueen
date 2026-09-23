package dev.aof.questqueen.net;

import dev.aof.questqueen.QuestQueen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

public record PinC2S(ResourceLocation chapter, String tile, boolean clear) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<PinC2S> TYPE = new CustomPacketPayload.Type<>(QuestQueen.id("pin"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PinC2S> STREAM_CODEC = StreamCodec.composite(
            ResourceLocation.STREAM_CODEC, PinC2S::chapter,
            ByteBufCodecs.STRING_UTF8, PinC2S::tile,
            ByteBufCodecs.BOOL, PinC2S::clear,
            PinC2S::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
