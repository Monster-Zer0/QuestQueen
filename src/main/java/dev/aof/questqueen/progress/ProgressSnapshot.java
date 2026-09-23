package dev.aof.questqueen.progress;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public record ProgressSnapshot(
        String teamId,
        boolean canAuthor,
        Map<String, Integer> values,
        Set<String> completedTasks,
        Set<String> completedTiles,
        Set<String> revealedTiles,
        Set<String> unlockedChapters,
        Set<String> ownedStages,
        List<ResourceLocation> scrolls,
        Optional<String> pinChapter,
        Optional<String> pinTile
) {
    public static final Codec<ProgressSnapshot> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Codec.STRING.fieldOf("teamId").forGetter(ProgressSnapshot::teamId),
            Codec.BOOL.fieldOf("canAuthor").forGetter(ProgressSnapshot::canAuthor),
            Codec.unboundedMap(Codec.STRING, Codec.INT).fieldOf("values").forGetter(ProgressSnapshot::values),
            Codec.STRING.listOf().xmap(Set::copyOf, List::copyOf).fieldOf("completedTasks").forGetter(ProgressSnapshot::completedTasks),
            Codec.STRING.listOf().xmap(Set::copyOf, List::copyOf).fieldOf("completedTiles").forGetter(ProgressSnapshot::completedTiles),
            Codec.STRING.listOf().xmap(Set::copyOf, List::copyOf).fieldOf("revealedTiles").forGetter(ProgressSnapshot::revealedTiles),
            Codec.STRING.listOf().optionalFieldOf("unlockedChapters", List.of()).forGetter(s -> List.copyOf(s.unlockedChapters())),
            Codec.STRING.listOf().optionalFieldOf("ownedStages", List.of()).forGetter(s -> List.copyOf(s.ownedStages())),
            ResourceLocation.CODEC.listOf().fieldOf("scrolls").forGetter(ProgressSnapshot::scrolls),
            Codec.STRING.optionalFieldOf("pinChapter").forGetter(ProgressSnapshot::pinChapter),
            Codec.STRING.optionalFieldOf("pinTile").forGetter(ProgressSnapshot::pinTile)
    ).apply(instance, ProgressSnapshot::fromCodec));

    private static ProgressSnapshot fromCodec(
            String teamId,
            boolean canAuthor,
            Map<String, Integer> values,
            Set<String> completedTasks,
            Set<String> completedTiles,
            Set<String> revealedTiles,
            List<String> unlockedChapters,
            List<String> ownedStages,
            List<ResourceLocation> scrolls,
            Optional<String> pinChapter,
            Optional<String> pinTile
    ) {
        return new ProgressSnapshot(teamId, canAuthor, values, completedTasks, completedTiles, revealedTiles,
                Set.copyOf(unlockedChapters), Set.copyOf(ownedStages), scrolls, pinChapter, pinTile);
    }

    public ProgressSnapshot {
        values = Map.copyOf(values);
        completedTasks = Set.copyOf(completedTasks);
        completedTiles = Set.copyOf(completedTiles);
        revealedTiles = Set.copyOf(revealedTiles);
        unlockedChapters = Set.copyOf(unlockedChapters);
        ownedStages = Set.copyOf(ownedStages);
        scrolls = List.copyOf(scrolls);
    }

    public static ProgressSnapshot empty() {
        return new ProgressSnapshot("", false, Map.of(), Set.of(), Set.of(), Set.of(), Set.of(), Set.of(), List.of(), Optional.empty(), Optional.empty());
    }

    public int value(String questId, String taskId) {
        return values.getOrDefault(questId + "/" + taskId, 0);
    }

    public boolean taskCompleted(String questId, String taskId) {
        return completedTasks.contains(questId + "/" + taskId);
    }

    public boolean tileCompleted(String chapterId, String tileId) {
        return completedTiles.contains(chapterId + "/" + tileId);
    }

    public boolean revealed(String chapterId, String tileId) {
        return revealedTiles.contains(chapterId + "/" + tileId);
    }

    public boolean chapterUnlocked(String chapterId) {
        return unlockedChapters.contains(chapterId);
    }

    public boolean hasStage(String id) {
        return dev.aof.questqueen.compat.ProgressiveStagesCompat.matches(ownedStages, id);
    }

    public static String questKey(ResourceLocation chapter, String tile) {
        return chapter + "/" + tile;
    }

    public static String taskKey(int index) {
        return Integer.toString(index);
    }

    public ProgressSnapshot withMaps(Map<String, Integer> newValues, Set<String> newCompletedTasks, Set<String> newCompletedTiles) {
        return new ProgressSnapshot(teamId, canAuthor, new HashMap<>(newValues), new HashSet<>(newCompletedTasks),
                new HashSet<>(newCompletedTiles), revealedTiles, unlockedChapters, ownedStages, scrolls, pinChapter, pinTile);
    }

    public ProgressSnapshot withPin(Optional<String> newPinChapter, Optional<String> newPinTile) {
        return new ProgressSnapshot(teamId, canAuthor, values, completedTasks, completedTiles, revealedTiles,
                unlockedChapters, ownedStages, scrolls, newPinChapter, newPinTile);
    }
}
