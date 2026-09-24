package dev.aof.questqueen.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.aof.questqueen.QuestConfig;
import dev.aof.questqueen.QuestQueen;
import dev.aof.questqueen.compat.ProgressiveStagesCompat;
import dev.aof.questqueen.net.QuestNetwork;
import dev.aof.questqueen.progress.ProgressService;
import dev.aof.questqueen.task.TaskHooks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.AddReloadListenerEvent;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public final class QuestDefinitions {
    private static QuestPack pack = QuestPack.empty();
    /** Namespace of the {@code book.json} currently in force. Editor chrome saves write back here. */
    private static String chromeNamespace = QuestQueen.MODID;

    private QuestDefinitions() {
    }

    @SubscribeEvent
    public static void onAddReloadListener(AddReloadListenerEvent event) {
        event.addListener(new QuestReloadListener());
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() != null) {
            QuestNetwork.sendDefinitions(event.getPlayer());
        } else {
            MinecraftServer server = event.getPlayerList().getServer();
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                QuestNetwork.sendDefinitions(player);
            }
        }
    }

    public static void apply(QuestPack next) {
        apply(next, true);
    }

    /**
     * @param sendDefinitions false on the datapack reload path: {@link #onDatapackSync} sends the new pack to
     *                        every player right after, and sending here too made each client download it twice
     */
    public static void apply(QuestPack next, boolean sendDefinitions) {
        pack = next;
        ProgressService.invalidateAll();
        TaskHooks.invalidateIndexes();
        QuestQueen.LOGGER.info("Loaded {} quest chapters, {} scrolls, sidebarTitle={}", next.chapters().size(), next.scrolls().size(), next.chrome().sidebarTitle());
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                if (sendDefinitions) {
                    QuestNetwork.sendDefinitions(player);
                }
                ProgressService.sync(player);
            }
        }
    }

    public static void putChapter(Chapter chapter) {
        Map<ResourceLocation, Chapter> chapters = new LinkedHashMap<>();
        for (Chapter existing : pack.chapters()) {
            chapters.put(existing.id(), existing);
        }
        chapters.put(chapter.id(), chapter);
        apply(new QuestPack(List.copyOf(chapters.values()), pack.scrolls(), pack.chrome()));
    }

    public static QuestPack pack() {
        return pack;
    }

    public static Collection<Chapter> chapters() {
        return pack.chapters();
    }

    public static Optional<Chapter> chapter(ResourceLocation id) {
        return pack.chapters().stream().filter(chapter -> chapter.id().equals(id)).findFirst();
    }

    public static Optional<Chapter> chapter(String id) {
        return chapter(ResourceLocation.parse(id));
    }

    public static Optional<Scroll> scroll(ResourceLocation id) {
        return pack.scrolls().stream().filter(scroll -> scroll.id().equals(id)).findFirst();
    }

    public static String chromeNamespace() {
        return chromeNamespace == null || chromeNamespace.isBlank() ? QuestQueen.MODID : chromeNamespace;
    }

    public static QuestPack loadFrom(ResourceManager manager) {
        List<Chapter> loaded = new ArrayList<>();
        List<Scroll> scrolls = new ArrayList<>();
        List<DemoChapters.ChromeFile> chromeFiles = new ArrayList<>();
        Map<String, Integer> packRank = packRanks(manager);
        for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources("questqueen/chapters", path -> path.getPath().endsWith(".json")).entrySet()) {
            parse(entry, Chapter.CODEC).ifPresent(loaded::add);
        }
        for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources("questqueen/scrolls", path -> path.getPath().endsWith(".json")).entrySet()) {
            parse(entry, Scroll.CODEC).ifPresent(scrolls::add);
        }
        for (Map.Entry<ResourceLocation, Resource> entry : manager.listResources("questqueen", path -> path.getPath().endsWith("/book.json") || path.getPath().equals("questqueen/book.json")).entrySet()) {
            Optional<BookChrome> parsed = parse(entry, BookChrome.CODEC);
            parsed.ifPresent(chrome -> {
                String packId = entry.getValue().sourcePackId();
                chromeFiles.add(new DemoChapters.ChromeFile(
                        entry.getKey(), chrome, packId, packRank.getOrDefault(packId, 0)));
            });
        }
        DemoChapters.Mode mode = DemoChapters.Mode.parse(safeDemoMode());
        boolean includeDev = safeIncludeDev();
        DemoChapters.FilterResult filtered = DemoChapters.filter(
                loaded, mode, includeDev, ProgressiveStagesCompat.present());
        boolean hasCustom = filtered.customCount() > 0;
        boolean keepPlayer = DemoChapters.keepPlayerDemos(mode, hasCustom);
        List<Scroll> keptScrolls = DemoChapters.filterScrolls(scrolls, keepPlayer, includeDev);
        BookChrome chrome = DemoChapters.pickChrome(chromeFiles, hasCustom);
        chromeNamespace = DemoChapters.pickChromeNamespace(chromeFiles, hasCustom);
        QuestQueen.LOGGER.info("Loaded {} quest chapters, {} scrolls, sidebarTitle={} namespace={} demo={} custom={} dropped={}",
                filtered.chapters().size(), keptScrolls.size(), chrome.sidebarTitle(), chromeNamespace,
                mode.name().toLowerCase(), filtered.customCount(), filtered.dropped());
        return new QuestPack(filtered.chapters(), keptScrolls, chrome);
    }

    /** Later packs in {@link ResourceManager#listPacks()} outrank earlier ones. */
    private static Map<String, Integer> packRanks(ResourceManager manager) {
        Map<String, Integer> ranks = new HashMap<>();
        int index = 0;
        for (PackResources pack : manager.listPacks().toList()) {
            ranks.put(pack.packId(), index++);
        }
        return ranks;
    }

    private static String safeDemoMode() {
        try {
            return QuestConfig.DEMO_CHAPTERS.get();
        } catch (Exception ignored) {
            return "auto";
        }
    }

    private static boolean safeIncludeDev() {
        try {
            return QuestConfig.INCLUDE_DEV_CHAPTERS.get();
        } catch (Exception ignored) {
            return false;
        }
    }

    public static void putChrome(BookChrome chrome) {
        apply(pack.withChrome(chrome));
    }

    private static <T> Optional<T> parse(Map.Entry<ResourceLocation, Resource> entry, com.mojang.serialization.Codec<T> codec) {
        try (InputStreamReader reader = new InputStreamReader(entry.getValue().open(), StandardCharsets.UTF_8)) {
            JsonElement json = JsonParser.parseReader(reader);
            return Optional.of(codec.parse(JsonOps.INSTANCE, json).getOrThrow(RuntimeException::new));
        } catch (Exception exception) {
            QuestQueen.LOGGER.error("Failed to parse quest file {}", entry.getKey(), exception);
            return Optional.empty();
        }
    }
}
