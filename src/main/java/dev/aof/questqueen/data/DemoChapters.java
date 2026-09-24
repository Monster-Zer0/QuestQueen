package dev.aof.questqueen.data;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/** Built-in jar chapters vs pack-authored chapters. */
public final class DemoChapters {
    public static final Set<String> PLAYER_IDS = Set.of(
            "questqueen:showcase",
            "questqueen:showcase_stage"
    );
    public static final Set<String> DEV_IDS = Set.of(
            "questqueen:blank",
            "questqueen:starter",
            "questqueen:nether",
            "questqueen:test",
            "questqueen:test_bonus"
    );
    public static final String STAGE_WING = "questqueen:showcase_stage";
    /** NeoForge pack id of this mod's own resources. Other packs must outrank it to own the book title. */
    public static final String JAR_PACK = "mod/questqueen";
    public static final Set<String> PLAYER_SCROLLS = Set.of("questqueen:showcase_brief");
    public static final Set<String> DEV_SCROLLS = Set.of(
            "questqueen:chest_note",
            "questqueen:zombie_note",
            "questqueen:test_note"
    );

    public enum Mode {
        AUTO, ALWAYS, NEVER;

        public static Mode parse(String raw) {
            if (raw == null || raw.isBlank()) {
                return AUTO;
            }
            return switch (raw.trim().toLowerCase(Locale.ROOT)) {
                case "always" -> ALWAYS;
                case "never" -> NEVER;
                default -> AUTO;
            };
        }
    }

    public record ChromeFile(ResourceLocation id, BookChrome chrome, String packId, int packRank) {
        /** Tests and callers that only know the resource id. A non-questqueen namespace outranks the jar. */
        public ChromeFile(ResourceLocation id, BookChrome chrome) {
            this(id, chrome,
                    id != null && "questqueen".equals(id.getNamespace()) ? JAR_PACK : "file/pack",
                    id != null && "questqueen".equals(id.getNamespace()) ? 0 : 1);
        }
    }

    public record FilterResult(List<Chapter> chapters, int customCount, int dropped) {
    }

    private DemoChapters() {
    }

    public static boolean isPlayerDemo(String id) {
        return PLAYER_IDS.contains(id);
    }

    public static boolean isDevDemo(String id) {
        return DEV_IDS.contains(id);
    }

    public static boolean isBuiltin(String id) {
        return isPlayerDemo(id) || isDevDemo(id);
    }

    public static boolean isCustom(Chapter chapter) {
        return chapter != null && !isBuiltin(chapter.id().toString());
    }

    public static boolean hasCustom(List<Chapter> chapters) {
        return chapters.stream().anyMatch(DemoChapters::isCustom);
    }

    public static FilterResult filter(
            List<Chapter> loaded,
            Mode mode,
            boolean includeDevChapters,
            boolean progressiveStagesPresent
    ) {
        boolean custom = hasCustom(loaded);
        boolean keepPlayer = keepPlayerDemos(mode, custom);
        List<Chapter> kept = new ArrayList<>();
        for (Chapter chapter : loaded) {
            String id = chapter.id().toString();
            if (isCustom(chapter)) {
                kept.add(chapter);
                continue;
            }
            if (isDevDemo(id)) {
                if (includeDevChapters) {
                    kept.add(chapter);
                }
                continue;
            }
            if (isPlayerDemo(id) && keepPlayer) {
                if (STAGE_WING.equals(id) && !progressiveStagesPresent) {
                    continue;
                }
                kept.add(chapter);
            }
        }
        return new FilterResult(List.copyOf(kept), (int) loaded.stream().filter(DemoChapters::isCustom).count(),
                loaded.size() - kept.size());
    }

    /** True when the built-in showcase chapter (and its brief) should stay in the loaded pack. */
    public static boolean keepPlayerDemos(Mode mode, boolean custom) {
        return switch (mode) {
            case ALWAYS -> true;
            case NEVER -> false;
            case AUTO -> !custom;
        };
    }

    public static boolean isJarPack(String packId) {
        return JAR_PACK.equals(packId);
    }

    /**
     * Showcase brief stays only while the showcase chapter does. Dev notes stay only while the harness
     * chapters do. Every other scroll is pack content.
     */
    public static List<Scroll> filterScrolls(List<Scroll> loaded, boolean keepPlayer, boolean includeDevChapters) {
        if (loaded == null || loaded.isEmpty()) {
            return List.of();
        }
        List<Scroll> kept = new ArrayList<>();
        for (Scroll scroll : loaded) {
            if (scroll == null || scroll.id() == null) {
                continue;
            }
            String id = scroll.id().toString();
            if (PLAYER_SCROLLS.contains(id)) {
                if (keepPlayer) {
                    kept.add(scroll);
                }
                continue;
            }
            if (DEV_SCROLLS.contains(id)) {
                if (includeDevChapters) {
                    kept.add(scroll);
                }
                continue;
            }
            kept.add(scroll);
        }
        return List.copyOf(kept);
    }

    /**
     * When a pack has its own chapters, the book title comes from the highest-priority {@code book.json}
     * that is not this mod's jar. Equal ranks keep the later file. With no pack chrome, the jar title wins.
     */
    public static Optional<ChromeFile> pickChromeFile(List<ChromeFile> files, boolean hasCustom) {
        if (files == null || files.isEmpty()) {
            return Optional.empty();
        }
        if (hasCustom) {
            ChromeFile best = null;
            for (ChromeFile file : files) {
                if (file == null || isJarPack(file.packId())) {
                    continue;
                }
                if (best == null || file.packRank() >= best.packRank()) {
                    best = file;
                }
            }
            if (best != null) {
                return Optional.of(best);
            }
        }
        ChromeFile jarNamespace = null;
        for (ChromeFile file : files) {
            if (file != null && file.id() != null && "questqueen".equals(file.id().getNamespace())) {
                if (jarNamespace == null || file.packRank() >= jarNamespace.packRank()) {
                    jarNamespace = file;
                }
            }
        }
        if (jarNamespace != null) {
            return Optional.of(jarNamespace);
        }
        ChromeFile best = null;
        for (ChromeFile file : files) {
            if (file == null) {
                continue;
            }
            if (best == null || file.packRank() >= best.packRank()) {
                best = file;
            }
        }
        return Optional.ofNullable(best);
    }

    public static BookChrome pickChrome(List<ChromeFile> files, boolean hasCustom) {
        return pickChromeFile(files, hasCustom).map(ChromeFile::chrome).orElse(BookChrome.DEFAULT);
    }

    /** Namespace the chosen {@code book.json} lives in. Editor saves write back to this namespace. */
    public static String pickChromeNamespace(List<ChromeFile> files, boolean hasCustom) {
        return pickChromeFile(files, hasCustom)
                .map(file -> file.id() == null ? "questqueen" : file.id().getNamespace())
                .orElse("questqueen");
    }

    public static Set<String> idsOf(List<Chapter> chapters) {
        Set<String> ids = new LinkedHashSet<>();
        for (Chapter chapter : chapters) {
            ids.add(chapter.id().toString());
        }
        return ids;
    }
}
