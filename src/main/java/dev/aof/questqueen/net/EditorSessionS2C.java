package dev.aof.questqueen.net;

import dev.aof.questqueen.QuestQueen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

public record EditorSessionS2C(boolean enabled) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<EditorSessionS2C> TYPE = new CustomPacketPayload.Type<>(QuestQueen.id("editor_session"));

    public static final StreamCodec<RegistryFriendlyByteBuf, EditorSessionS2C> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, EditorSessionS2C::enabled,
            EditorSessionS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
