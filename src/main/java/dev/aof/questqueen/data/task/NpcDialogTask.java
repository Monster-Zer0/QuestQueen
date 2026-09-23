package dev.aof.questqueen.data.task;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.Optional;

public record NpcDialogTask(String npc) implements Task {
    public static final MapCodec<NpcDialogTask> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("npc").forGetter(NpcDialogTask::npc)
    ).apply(instance, NpcDialogTask::new));

    @Override
    public String type() {
        return "npc_dialog";
    }

    @Override
    public Optional<String> npcId() {
        return Optional.of(npc);
    }

    @Override
    public Task withCount(int newCount) {
        return this;
    }
}
