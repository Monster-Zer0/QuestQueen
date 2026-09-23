package dev.aof.questqueen.data.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.aof.questqueen.compat.ProgressiveStagesCompat;
import net.minecraft.server.level.ServerPlayer;

public record StageReward(String stage) implements Reward {
    public static final MapCodec<StageReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.optionalFieldOf("stage", "").forGetter(StageReward::stage),
            Codec.STRING.optionalFieldOf("id", "").forGetter(reward -> "")
    ).apply(instance, (stage, id) -> new StageReward(!stage.isBlank() ? stage : id)));

    @Override
    public String type() {
        return "stage";
    }

    @Override
    public String describe() {
        return "STAGE: " + stage;
    }

    @Override
    public void grant(ServerPlayer player) {
        ProgressiveStagesCompat.grantStage(player, stage);
    }
}
