package dev.aof.questqueen.net;

import dev.aof.questqueen.QuestQueen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record AuthorStageSaveC2S(String json) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AuthorStageSaveC2S> TYPE = new CustomPacketPayload.Type<>(QuestQueen.id("author_stage_save"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuthorStageSaveC2S> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(AuthorSaveC2S.MAX_JSON_BYTES), AuthorStageSaveC2S::json,
            AuthorStageSaveC2S::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
