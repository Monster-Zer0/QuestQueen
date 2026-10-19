package dev.aof.questqueen.data.stage;

import com.google.gson.JsonArray;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.aof.questqueen.QuestQueen;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimplePreparableReloadListener;
import net.minecraft.util.profiling.ProfilerFiller;

import java.io.Reader;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * The stages the web editor made, read from {@code data/<ns>/questqueen/stages/**.json} in every datapack.
 * Server side only; the editor gets them through {@code AuthorStagesS2C}. A stage here is one the editor may
 * change or delete; any other StageLock stage belongs to the pack and is shown read-only.
 */
public final class AuthoredStages {
    public static final String FOLDER = "questqueen/stages";

    /** A stage and the namespace its files live under, so a re-save overwrites them instead of copying. */
    public record Entry(AuthoredStage stage, String namespace) {
    }

    private static volatile Map<String, Entry> current = Map.of();

    private AuthoredStages() {
    }

    public static Collection<Entry> all() {
        return current.values();
    }

    public static Optional<Entry> get(String id) {
        return Optional.ofNullable(current.get(id));
    }

    /** A save is live before the reload that follows it, so the editor sees it at once. */
    public static synchronized void put(Entry entry) {
        Map<String, Entry> next = new LinkedHashMap<>(current);
        next.put(entry.stage().id(), entry);
        current = Map.copyOf(next);
    }

    public static synchronized void remove(String id) {
        Map<String, Entry> next = new LinkedHashMap<>(current);
        next.remove(id);
        current = Map.copyOf(next);
    }

    /** Every authored stage as a JSON array, for the editor. */
    public static String json() {
        JsonArray array = new JsonArray();
        for (Entry entry : current.values()) {
            AuthoredStage.CODEC.encodeStart(JsonOps.INSTANCE, entry.stage()).result().ifPresent(array::add);
        }
        return array.toString();
    }

    static Map<String, Entry> load(ResourceManager manager) {
        Map<String, Entry> loaded = new LinkedHashMap<>();
        for (Map.Entry<ResourceLocation, Resource> file
                : manager.listResources(FOLDER, path -> path.getPath().endsWith(".json")).entrySet()) {
            try (Reader reader = file.getValue().openAsReader()) {
                AuthoredStage stage = AuthoredStage.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseReader(reader))
                        .getOrThrow(RuntimeException::new);
                loaded.put(stage.id(), new Entry(stage, file.getKey().getNamespace()));
            } catch (Exception exception) {
                QuestQueen.LOGGER.warn("Skipping authored stage {}: {}", file.getKey(), exception.getMessage());
            }
        }
        return loaded;
    }

    public static final class ReloadListener extends SimplePreparableReloadListener<Map<String, Entry>> {
        @Override
        protected Map<String, Entry> prepare(ResourceManager manager, ProfilerFiller profiler) {
            return load(manager);
        }

        @Override
        protected void apply(Map<String, Entry> loaded, ResourceManager manager, ProfilerFiller profiler) {
            current = Map.copyOf(loaded);
        }
    }
}
