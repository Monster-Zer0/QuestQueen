package dev.aof.questqueen.net;

import dev.aof.questqueen.QuestQueen;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * What each advancement used by a quest task is, for the task row's hover: its title and description, or for a
 * tracking advancement with no display (never sent to clients by vanilla) a line saying what it asks for.
 */
public record AdvancementInfoS2C(List<Entry> entries) implements CustomPacketPayload {
    public static final CustomPacketPayload.Type<AdvancementInfoS2C> TYPE = new CustomPacketPayload.Type<>(QuestQueen.id("advancement_info"));

    public record Entry(ResourceLocation id, Component title, List<Component> lines, ItemStack icon) {
        public static final StreamCodec<RegistryFriendlyByteBuf, Entry> STREAM_CODEC = StreamCodec.composite(
                ResourceLocation.STREAM_CODEC, Entry::id,
                ComponentSerialization.STREAM_CODEC, Entry::title,
                ComponentSerialization.STREAM_CODEC.apply(ByteBufCodecs.list()), Entry::lines,
                ItemStack.OPTIONAL_STREAM_CODEC, Entry::icon,
                Entry::new
        );
    }

    public static final StreamCodec<RegistryFriendlyByteBuf, AdvancementInfoS2C> STREAM_CODEC = StreamCodec.composite(
            Entry.STREAM_CODEC.apply(ByteBufCodecs.list()), AdvancementInfoS2C::entries,
            AdvancementInfoS2C::new
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
