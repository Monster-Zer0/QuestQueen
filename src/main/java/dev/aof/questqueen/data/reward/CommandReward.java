package dev.aof.questqueen.data.reward;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

import java.util.Optional;

/**
 * Runs a command as the reward. Players never see the command: the book shows {@code icon}, {@code name} and
 * {@code description} when the pack sets them, the item a {@code give} hands out when it does not, and a plain
 * "Bonus reward" for anything else.
 *
 * <p>{@code icon} is an item written as in {@code /give} ({@code id} or {@code id[components]}), {@code glyph:<id>},
 * or a resource-pack picture ({@code ns:textures/….png}).
 */
public record CommandReward(String command, String icon, String name, String description) implements Reward {
    public static final MapCodec<CommandReward> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.STRING.fieldOf("command").forGetter(CommandReward::command),
            Codec.STRING.optionalFieldOf("icon", "").forGetter(CommandReward::icon),
            Codec.STRING.optionalFieldOf("name", "").forGetter(CommandReward::name),
            Codec.STRING.optionalFieldOf("description", "").forGetter(CommandReward::description)
    ).apply(instance, CommandReward::new));

    public static final String FALLBACK_NAME = "Bonus reward";

    public CommandReward {
        command = command == null ? "" : command;
        icon = icon == null ? "" : icon.trim();
        name = name == null ? "" : name;
        description = description == null ? "" : description;
    }

    public CommandReward(String command) {
        this(command, "", "", "");
    }

    @Override
    public String type() {
        return "command";
    }

    /** The item a {@code give} command hands out, if this is one. */
    public Optional<GiveCommand> give() {
        return GiveCommand.parse(command);
    }

    /** {@code glyph:<id>} icon, lower-cased; empty when the icon is not a glyph. */
    public Optional<String> iconGlyph() {
        return icon.regionMatches(true, 0, "glyph:", 0, 6) && icon.length() > 6
                ? Optional.of(icon.substring(6).trim().toLowerCase(java.util.Locale.ROOT))
                : Optional.empty();
    }

    /** A resource-pack picture icon ({@code ns:textures/….png}); empty otherwise. */
    public Optional<ResourceLocation> iconTexture() {
        if (iconGlyph().isPresent() || !icon.endsWith(".png")) {
            return Optional.empty();
        }
        ResourceLocation id = ResourceLocation.tryParse(icon);
        return id != null && id.getPath().startsWith("textures/") ? Optional.of(id) : Optional.empty();
    }

    /** An item icon ({@code id} or {@code id[components]}); empty when the icon is a glyph, a picture or unset. */
    public Optional<String> iconItemSpec() {
        return icon.isEmpty() || iconGlyph().isPresent() || iconTexture().isPresent() ? Optional.empty() : Optional.of(icon);
    }

    @Override
    public int count() {
        return give().map(GiveCommand::count).orElse(1);
    }

    /** The player-facing name. Never the command. */
    @Override
    public String describe() {
        if (!name.isBlank()) {
            return name.trim();
        }
        return give().map(GiveCommand::plainLabel).orElse(FALLBACK_NAME);
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
