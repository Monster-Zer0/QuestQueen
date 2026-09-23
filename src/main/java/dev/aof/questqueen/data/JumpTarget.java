package dev.aof.questqueen.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

public record JumpTarget(ResourceLocation chapter, String tile) {
    public static final Codec<JumpTarget> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ResourceLocation.CODEC.fieldOf("chapter").forGetter(JumpTarget::chapter),
            Codec.STRING.fieldOf("tile").forGetter(JumpTarget::tile)
    ).apply(instance, JumpTarget::new));
}
