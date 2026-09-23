package dev.aof.questqueen.net;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.aof.questqueen.QuestQueen;
import dev.aof.questqueen.progress.ProgressSnapshot;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.nio.charset.StandardCharsets;

public record ProgressS2C(String json) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<ProgressS2C> TYPE = new CustomPacketPayload.Type<>(QuestQueen.id("progress"));

    /** BYTE_ARRAY, not STRING_UTF8 — grant-all snapshots exceed the 32767-char string cap and kick the player. */
    public static final StreamCodec<RegistryFriendlyByteBuf, ProgressS2C> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BYTE_ARRAY,
            payload -> payload.json().getBytes(StandardCharsets.UTF_8),
            bytes -> new ProgressS2C(new String(bytes, StandardCharsets.UTF_8))
    );

    public static ProgressS2C of(ProgressSnapshot snapshot) {
        String json = ProgressSnapshot.CODEC.encodeStart(JsonOps.INSTANCE, snapshot)
                .getOrThrow(RuntimeException::new)
                .toString();
        return new ProgressS2C(json);
    }

    public ProgressSnapshot decode() {
        return ProgressSnapshot.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                .getOrThrow(RuntimeException::new);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
