package dev.aof.questqueen.data.reward;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

public record AdvancementReward(ResourceLocation advancement) implements Reward {
    public static final MapCodec<AdvancementReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("advancement").forGetter(AdvancementReward::advancement)
    ).apply(instance, AdvancementReward::new));

    @Override
    public String type() {
        return "advancement";
    }

    @Override
    public String describe() {
        return advancement.getPath();
    }

    @Override
    public boolean noisy() {
        return true;
    }

    @Override
    public void grant(ServerPlayer player) {
        AdvancementHolder holder = player.server.getAdvancements().get(advancement);
        if (holder == null) {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(holder);
        for (String criterion : progress.getRemainingCriteria()) {
            player.getAdvancements().award(holder, criterion);
        }
    }
}
