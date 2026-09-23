package dev.aof.questqueen.net;

import dev.aof.questqueen.QuestQueen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record DefinitionChunkS2C(int session, int index, int total, byte[] data) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<DefinitionChunkS2C> TYPE = new CustomPacketPayload.Type<>(QuestQueen.id("definition_chunk"));

    public static final StreamCodec<RegistryFriendlyByteBuf, DefinitionChunkS2C> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, DefinitionChunkS2C::session,
            ByteBufCodecs.VAR_INT, DefinitionChunkS2C::index,
            ByteBufCodecs.VAR_INT, DefinitionChunkS2C::total,
            ByteBufCodecs.BYTE_ARRAY, DefinitionChunkS2C::data,
            DefinitionChunkS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
