package dev.aof.questqueen.net;

import dev.aof.questqueen.QuestQueen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The server's answer to an editor request the browser waits on (a chapter delete): ok, or why not. */
public record AuthorReplyS2C(String id, boolean ok, String message) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AuthorReplyS2C> TYPE = new CustomPacketPayload.Type<>(QuestQueen.id("author_reply"));

    public static final StreamCodec<RegistryFriendlyByteBuf, AuthorReplyS2C> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.stringUtf8(512), AuthorReplyS2C::id,
            ByteBufCodecs.BOOL, AuthorReplyS2C::ok,
            ByteBufCodecs.stringUtf8(1024), AuthorReplyS2C::message,
            AuthorReplyS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
