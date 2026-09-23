package dev.aof.questqueen.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.aof.questqueen.data.reward.Reward;
import dev.aof.questqueen.data.task.Task;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

public record Tile(
        String id,
        GridPos pos,
        String title,
        String description,
        Optional<Icon> icon,
        List<Task> tasks,
        List<Reward> rewards,
        List<ResourceLocation> scrolls,
        Optional<JumpTarget> target,
        Optional<HiddenUntil> hiddenUntil,
        Optional<String> requiredStage
) {
    public static final Codec<Tile> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("id").forGetter(Tile::id),
            GridPos.CODEC.fieldOf("pos").forGetter(Tile::pos),
            Codec.STRING.optionalFieldOf("title", "").forGetter(Tile::title),
            Codec.STRING.optionalFieldOf("description", "").forGetter(Tile::description),
            Icon.CODEC.optionalFieldOf("icon").forGetter(Tile::icon),
            Task.CODEC.listOf().optionalFieldOf("tasks", List.of()).forGetter(Tile::tasks),
            Reward.CODEC.listOf().optionalFieldOf("rewards", List.of()).forGetter(Tile::rewards),
            ResourceLocation.CODEC.listOf().optionalFieldOf("scrolls", List.of()).forGetter(Tile::scrolls),
            JumpTarget.CODEC.optionalFieldOf("target").forGetter(Tile::target),
            HiddenUntil.CODEC.optionalFieldOf("hidden_until").forGetter(Tile::hiddenUntil),
            Codec.STRING.optionalFieldOf("required_stage").forGetter(Tile::requiredStage)
    ).apply(instance, Tile::new));

    public Tile {
        tasks = List.copyOf(tasks);
        rewards = List.copyOf(rewards);
        scrolls = List.copyOf(scrolls);
    }

    public Tile withPos(GridPos newPos) {
        return new Tile(id, newPos, title, description, icon, tasks, rewards, scrolls, target, hiddenUntil, requiredStage);
    }

    public Tile withTitle(String newTitle) {
        return new Tile(id, pos, newTitle, description, icon, tasks, rewards, scrolls, target, hiddenUntil, requiredStage);
    }

    public Tile withDescription(String newDescription) {
        return new Tile(id, pos, title, newDescription, icon, tasks, rewards, scrolls, target, hiddenUntil, requiredStage);
    }

    public Tile withIcon(Optional<Icon> newIcon) {
        return new Tile(id, pos, title, description, newIcon, tasks, rewards, scrolls, target, hiddenUntil, requiredStage);
    }

    public Tile withTasks(List<Task> newTasks) {
        return new Tile(id, pos, title, description, icon, newTasks, rewards, scrolls, target, hiddenUntil, requiredStage);
    }

    public Tile withRewards(List<Reward> newRewards) {
        return new Tile(id, pos, title, description, icon, tasks, newRewards, scrolls, target, hiddenUntil, requiredStage);
    }

    public static Tile blank(String id, int x, int y) {
        return new Tile(id, new GridPos(x, y), "New tile", "Describe this quest.", Optional.empty(),
                List.of(), List.of(), List.of(), Optional.empty(), Optional.empty(), Optional.empty());
    }
}
