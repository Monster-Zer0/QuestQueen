package dev.aof.questqueen.data.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

public record CommandReward(String command) implements Reward {
    public static final MapCodec<CommandReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("command").forGetter(CommandReward::command)
    ).apply(instance, CommandReward::new));

    @Override
    public String type() {
        return "command";
    }

    @Override
    public String describe() {
        return "/" + command;
    }

    @Override
    public boolean noisy() {
        return true;
    }

    @Override
    public void grant(ServerPlayer player) {
        CommandSourceStack source = player.createCommandSourceStack().withPermission(2).withSuppressedOutput();
        String parsed = command.replace("@p", player.getGameProfile().getName()).replace("@s", player.getGameProfile().getName());
        player.server.getCommands().performPrefixedCommand(source, parsed);
    }
}
