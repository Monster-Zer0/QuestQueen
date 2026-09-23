package dev.aof.questqueen.data.reward;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.Optional;

public record ChoiceReward(List<ItemReward> options) implements Reward {
    public static final MapCodec<ChoiceReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            ItemReward.CODEC.codec().listOf().fieldOf("options").forGetter(ChoiceReward::options)
    ).apply(instance, ChoiceReward::new));

    public ChoiceReward {
        options = List.copyOf(options);
        // A choice with nothing to choose would burn the one-time `choice` flag while granting nothing,
        // so refuse it at parse time rather than accepting an unsatisfiable reward.
        if (options.isEmpty()) {
            throw new IllegalArgumentException("choice reward needs at least one option");
        }
    }

    @Override
    public String type() {
        return "choice";
    }

    @Override
    public Optional<ResourceLocation> itemId() {
        return options.isEmpty() ? Optional.empty() : Optional.of(options.getFirst().item());
    }

    @Override
    public String describe() {
        return "choose 1 of " + options.size();
    }

    @Override
    public void grant(ServerPlayer player) {
        // Claimed from the book via ClaimChoiceC2S.
    }

    /** Whether {@code index} names one of this reward's options. */
    public boolean validChoiceIndex(int index) {
        return index >= 0 && index < options.size();
    }

    public void grantOption(ServerPlayer player, int index) {
        if (validChoiceIndex(index)) {
            options.get(index).grant(player);
        }
    }
}
