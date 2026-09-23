package dev.aof.questqueen.data.task;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Player-facing verb for every task type, as a translation key.
 *
 * <p>Where this lives and why: the task row renders {@code <VERB> <current>/<required>}. The verb used to
 * come from a hardcoded switch inside {@code ClientQuestState} that ended in
 * {@code default -> task.type().toUpperCase(Locale.ROOT)} — so the eight types missing from the switch
 * leaked their raw id into the book ({@code ITEM_TAG 0/16}, {@code XP_LEVELS 0/5}). A pack could not fix
 * it: {@code ItemTagTask} is a record of {@code (type, tag, count, required)} with no text field, and
 * {@code assets/questqueen/lang/en_us.json} had no task-verb keys at all.
 *
 * <p>Now every type resolves through {@code questqueen.task.<type>} with the previous English string as the
 * translatable's own fallback, so a pack or a translator can correct or localise a verb without a mod
 * release, and an unmapped future type degrades to a humanised name rather than an identifier.
 */
public final class TaskVerbs {
    /** Prefix for every verb key, e.g. {@code questqueen.task.item_tag}. */
    public static final String KEY_PREFIX = "questqueen.task.";

    /**
     * The canonical types in {@link TaskFactory#TYPES} order, mapped to the English string the mod ships in
     * {@code en_us.json}. This map is the hardcoded fallback handed to the translatable, so the book reads
     * correctly even with no language file loaded (a dedicated server, a unit test, an early client).
     */
    static final Map<String, String> VERBS = Map.ofEntries(
            Map.entry("obtain", "FIND"),
            Map.entry("submit", "GIVE"),
            Map.entry("item_tag", "COLLECT"),
            Map.entry("kill", "KILL"),
            Map.entry("raid", "DEFEND"),
            Map.entry("advancement", "EARN"),
            Map.entry("location", "EXPLORE"),
            Map.entry("visit_structure", "EXPLORE"),
            Map.entry("visit_dimension", "EXPLORE"),
            Map.entry("visit_biome", "EXPLORE"),
            Map.entry("observation", "EXPLORE"),
            Map.entry("checkmark", "CLAIM"),
            Map.entry("stat", "STAT"),
            Map.entry("interact_block", "USE"),
            Map.entry("interact_entity", "USE"),
            Map.entry("fluid", "COLLECT"),
            Map.entry("xp_levels", "REACH"),
            Map.entry("npc_dialog", "TALK"),
            Map.entry("trigger", "DO")
    );

    /** Custom-stat sub-verbs, keyed by the fragment {@link #statVerb} matched. */
    static final Map<String, String> STAT_VERBS = Map.of(
            "walk", "WALK",
            "fly", "FLY",
            "swim", "SWIM",
            "sleep", "SLEEP"
    );

    /**
     * Fragments that select a stat sub-verb, in match order. Vanilla awards sprinting and crouching to
     * separate stats from {@code walk_one_cm}, so "walk" also covers sprint/crouch travel.
     */
    private static final List<Map.Entry<String, List<String>>> STAT_MATCHES = List.of(
            Map.entry("walk", List.of("walk", "sprint", "crouch")),
            Map.entry("fly", List.of("fly", "aviate")),
            Map.entry("swim", List.of("swim", "water_one")),
            Map.entry("sleep", List.of("sleep"))
    );

    private TaskVerbs() {
    }

    /** The verb shown for a whole task. */
    public static String verb(Task task) {
        if (task == null) {
            return "";
        }
        String statFragment = statFragment(task).orElse(null);
        if (statFragment != null) {
            return translated(KEY_PREFIX + "stat." + statFragment, STAT_VERBS.get(statFragment));
        }
        return verb(task.type());
    }

    /** The verb shown for a raw type id, with aliases resolved and unknown types humanised. */
    public static String verb(String type) {
        String canonical = canonical(type);
        return translated(KEY_PREFIX + canonical, VERBS.getOrDefault(canonical, humanize(canonical)));
    }

    /**
     * The translation key a pack overrides for this type. Unknown types still get a key, so a pack can name
     * one that the mod does not know yet.
     */
    public static String keyFor(String type) {
        String canonical = canonical(type);
        if ("stat".equals(canonical)) {
            return KEY_PREFIX + "stat";
        }
        return KEY_PREFIX + canonical;
    }

    /** English string the mod ships for a type; never a raw identifier. */
    public static String defaultVerb(String type) {
        String canonical = canonical(type);
        return VERBS.getOrDefault(canonical, humanize(canonical));
    }

    /** Aliased spellings ({@code item}, {@code kill_entity}, …) fold onto their canonical type. */
    public static String canonical(String type) {
        if (type == null || type.isBlank()) {
            return "checkmark";
        }
        String key = type.toLowerCase(Locale.ROOT);
        return TaskCodecs.ALIASES.getOrDefault(key, key);
    }

    /**
     * Last-resort name for a type with no mapping anywhere: {@code item_tag} -> {@code Item Tag}. This is
     * the "softened fallback" — an unmapped type degrades to readable words, never to {@code ITEM_TAG}.
     */
    public static String humanize(String type) {
        if (type == null || type.isBlank()) {
            return "";
        }
        String body = type.toLowerCase(Locale.ROOT);
        // A namespaced id carries no meaning for a task type, and its namespace would read as a leaked
        // identifier fragment: questqueen:thing -> "Thing", not "Questqueen Thing".
        int colon = body.indexOf(':');
        if (colon >= 0 && colon < body.length() - 1) {
            body = body.substring(colon + 1);
        }
        String[] words = body.split("[_:./-]+");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) {
                continue;
            }
            if (!out.isEmpty()) {
                out.append(' ');
            }
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.isEmpty() ? type : out.toString();
    }

    /**
     * Which stat sub-verb this task wants, if it is a custom-stat task. {@code walk_one_cm} reads WALK,
     * {@code aviate_one_cm} reads FLY, and anything else falls through to plain STAT.
     */
    static Optional<String> statFragment(Task task) {
        if (!"stat".equals(canonical(task.type()))) {
            return Optional.empty();
        }
        String path = task.statId().map(ResourceLocation::getPath).orElse("").toLowerCase(Locale.ROOT);
        for (Map.Entry<String, List<String>> match : STAT_MATCHES) {
            for (String needle : match.getValue()) {
                if (path.contains(needle)) {
                    return Optional.of(match.getKey());
                }
            }
        }
        return Optional.empty();
    }

    /**
     * Resolve a key against the current language, falling back to the exact English string and formatting it
     * with {@code args} when the language system is unavailable. Never throws: the language system is absent
     * on a dedicated server and in unit tests, and a missing translation must not take the book down.
     */
    public static String translate(String key, String fallback, Object... args) {
        try {
            String value = Component.translatableWithFallback(key, fallback, args).getString();
            if (value != null && !value.isBlank()) {
                return value;
            }
        } catch (RuntimeException | LinkageError absentLanguageSystem) {
            // fall through to the English string below
        }
        return args.length == 0 ? fallback : String.format(fallback, args);
    }

    private static String translated(String key, String fallback) {
        return translate(key, fallback);
    }
}
