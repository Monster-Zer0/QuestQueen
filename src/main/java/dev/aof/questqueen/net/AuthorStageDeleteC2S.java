package dev.aof.questqueen.net;

import dev.aof.questqueen.QuestQueen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record AuthorStageDeleteC2S(String id) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AuthorStageDeleteC2S> TYPE = new CustomPacketPayload.Type<>(QuestQueen.id("author_stage_delete"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuthorStageDeleteC2S> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(AuthorSaveC2S.MAX_JSON_BYTES), AuthorStageDeleteC2S::id,
            AuthorStageDeleteC2S::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
