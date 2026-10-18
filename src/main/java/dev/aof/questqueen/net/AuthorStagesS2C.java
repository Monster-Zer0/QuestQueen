package dev.aof.questqueen.net;

import dev.aof.questqueen.QuestQueen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record AuthorStagesS2C(String json) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AuthorStagesS2C> TYPE = new CustomPacketPayload.Type<>(QuestQueen.id("author_stages"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuthorStagesS2C> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(AuthorSaveC2S.MAX_JSON_BYTES), AuthorStagesS2C::json,
            AuthorStagesS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
