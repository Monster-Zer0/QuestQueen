package dev.aof.questqueen.net;

import dev.aof.questqueen.QuestQueen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record AuthorSaveC2S(String json) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AuthorSaveC2S> TYPE = new CustomPacketPayload.Type<>(QuestQueen.id("author_save"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuthorSaveC2S> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, AuthorSaveC2S::json,
            AuthorSaveC2S::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
