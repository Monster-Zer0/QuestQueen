package dev.aof.questqueen.data;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
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

    public record ChromeFile(ResourceLocation id, BookChrome chrome) {
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
        boolean keepPlayer = switch (mode) {
            case ALWAYS -> true;
            case NEVER -> false;
            case AUTO -> !custom;
        };
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

    public static BookChrome pickChrome(List<ChromeFile> files, boolean hasCustom) {
        if (files == null || files.isEmpty()) {
            return BookChrome.DEFAULT;
        }
        if (hasCustom) {
            for (ChromeFile file : files) {
                if (file.id() != null && !"questqueen".equals(file.id().getNamespace())) {
                    return file.chrome();
                }
            }
        }
        for (ChromeFile file : files) {
            if (file.id() != null && "questqueen".equals(file.id().getNamespace())) {
                return file.chrome();
            }
        }
        return files.getFirst().chrome();
    }

    public static Set<String> idsOf(List<Chapter> chapters) {
        Set<String> ids = new LinkedHashSet<>();
        for (Chapter chapter : chapters) {
            ids.add(chapter.id().toString());
        }
        return ids;
    }
}
