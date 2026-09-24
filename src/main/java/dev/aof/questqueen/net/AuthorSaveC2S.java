package dev.aof.questqueen.net;

import dev.aof.questqueen.QuestQueen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record AuthorSaveC2S(String json) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AuthorSaveC2S> TYPE = new CustomPacketPayload.Type<>(QuestQueen.id("author_save"));

    /**
     * STRING_UTF8 caps at 32767 chars and a larger chapter kicked its author mid-save. NeoForge splits payloads
     * over the vanilla packet limit, so a larger explicit bound is safe; EditorBridge refuses bigger bodies.
     */
    public static final int MAX_JSON_BYTES = 1 << 20;

    public static final StreamCodec<RegistryFriendlyByteBuf, AuthorSaveC2S> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(MAX_JSON_BYTES), AuthorSaveC2S::json,
            AuthorSaveC2S::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
