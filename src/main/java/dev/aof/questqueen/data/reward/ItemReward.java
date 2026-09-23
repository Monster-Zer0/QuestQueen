package dev.aof.questqueen.data.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.Optional;

public record ItemReward(ResourceLocation item, int count) implements Reward {
    public static final MapCodec<ItemReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("item").forGetter(ItemReward::item),
            Codec.INT.optionalFieldOf("count", 1).forGetter(ItemReward::count)
    ).apply(instance, ItemReward::new));

    @Override
    public String type() {
        return "item";
    }

    @Override
    public Optional<ResourceLocation> itemId() {
        return Optional.of(item);
    }

    @Override
    public String describe() {
        return count + "x " + item.getPath().replace('_', ' ');
    }

    @Override
    public void grant(ServerPlayer player) {
        BuiltInRegistries.ITEM.getOptional(item).ifPresent(resolved -> {
            ItemStack stack = new ItemStack(resolved, count);
            if (!player.addItem(stack)) {
                player.drop(stack, false);
            }
        });
    }
}
