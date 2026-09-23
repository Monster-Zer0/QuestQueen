package dev.aof.questqueen.net;

import dev.aof.questqueen.QuestQueen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Open the quest book, optionally on a named chapter and/or focused on a tile.
 *
 * <p>{@code chapter} is an EXPLICIT field, never derived by splitting {@code tileId} (Worf D-3). It is a
 * ResourceLocation string; {@code ""} means "keep whatever chapter the client is already showing".
 *
 * <p>Wire format changed by adding {@code chapter}, so the payload registrar version was bumped 1 -> 2.
 * A stale client is then refused at negotiation instead of mis-parsing the trailing field.
 */
public record OpenBookS2C(String tileId, boolean expanded, String chapter) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<OpenBookS2C> TYPE = new CustomPacketPayload.Type<>(QuestQueen.id("open_book"));

    public static final StreamCodec<RegistryFriendlyByteBuf, OpenBookS2C> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.STRING_UTF8, OpenBookS2C::tileId,
            ByteBufCodecs.BOOL, OpenBookS2C::expanded,
            ByteBufCodecs.STRING_UTF8, OpenBookS2C::chapter,
            OpenBookS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
