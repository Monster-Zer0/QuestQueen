package dev.aof.questqueen.data.task;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * First coverage of the {@link Task} subclasses and of the task → verb mapping.
 *
 * <p>{@code docs/LOGIC-AUDIT.md} records that no test touched any Task subclass; only the codecs were
 * exercised, and only through the reward/count fixtures. This walks {@link TaskFactory#TYPES} — the
 * authoritative type list — and pins three things per type: the subclass reports its own canonical id, it
 * survives a codec round trip, and it has a player-facing verb that is neither empty nor the raw identifier.
 *
 * <p>The verb half is the regression guard for the defect where eight types rendered their raw id in the
 * book ({@code ITEM_TAG 0/16}, {@code XP_LEVELS 0/5}) because {@code taskVerb}'s switch ended in
 * {@code default -> task.type().toUpperCase(Locale.ROOT)}.
 */
class TaskTypeCoverageTest {
    /** The verbs the pack proposed, adopted as shipped. */
    private static final Map<String, String> EXPECTED = Map.ofEntries(
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

    @Test
    void everyFactoryTypeBuildsASubclassThatReportsItsCannedId() {
        for (String type : TaskFactory.TYPES) {
            Task task = TaskFactory.create(type);
            assertNotNull(task, type + " has no factory case");
            assertEquals(type, task.type(), type + " built a " + task.getClass().getSimpleName()
                    + " that reports a different type");
        }
    }

    @Test
    void everyTaskSubclassSurvivesACodecRoundTrip() {
        for (String type : TaskFactory.TYPES) {
            Task original = TaskFactory.create(type).withCount(7);
            int expectedCount = original.required();
            JsonElement encoded = Task.CODEC.encodeStart(JsonOps.INSTANCE, original)
                    .getOrThrow(err -> new AssertionError(type + " failed to encode: " + err));
            // The dispatch codec must write the type it dispatches on, or a pack could not round-trip.
            assertTrue(encoded.getAsJsonObject().has("type"), type + " encoded without a type field");
            assertEquals(type, encoded.getAsJsonObject().get("type").getAsString());
            Task parsed = Task.CODEC.parse(JsonOps.INSTANCE, encoded)
                    .getOrThrow(err -> new AssertionError(type + " failed to re-parse: " + err));
            assertEquals(type, parsed.type());
            assertEquals(original.getClass(), parsed.getClass(), type + " changed class across the codec");
            assertEquals(expectedCount, parsed.required(), type + " lost its count across the codec");
        }
    }

    @Test
    void authoringAliasesResolveToTheirCanonicalType() {
        // The aliases live on the codec dispatch (TaskCodecs.forType), which is what a pack's JSON goes
        // through — not on TaskFactory.create, which only cycles the canonical list.
        for (Map.Entry<String, String> alias : Map.of(
                "item", "obtain",
                "kill_entity", "kill",
                "dimension", "visit_dimension",
                "biome", "visit_biome",
                "structure", "visit_structure").entrySet()) {
            assertEquals(alias.getValue(), TaskVerbs.canonical(alias.getKey()),
                    alias.getKey() + " should fold onto " + alias.getValue());
            var encoded = JsonParser.parseString("{\"type\":\"" + alias.getKey()
                    + "\"" + aliasPayload(alias.getValue()) + "}");
            Task parsed = Task.CODEC.parse(JsonOps.INSTANCE, encoded)
                    .getOrThrow(err -> new AssertionError(alias.getKey() + " did not parse: " + err));
            assertEquals(alias.getValue(), parsed.type(), alias.getKey() + " parsed to the wrong type");
        }
    }

    /** The minimum extra fields each aliased task needs beyond its type. */
    private static String aliasPayload(String canonicalType) {
        return switch (canonicalType) {
            case "obtain", "visit_biome", "visit_dimension", "visit_structure" -> ",\""
                    + switch (canonicalType) {
                        case "obtain" -> "item";
                        case "visit_biome" -> "biome";
                        case "visit_dimension" -> "dimension";
                        default -> "structure";
                    } + "\":\"minecraft:plains\"";
            case "kill" -> ",\"entity\":\"minecraft:zombie\"";
            default -> "";
        };
    }

    @Test
    void everyTypeHasAVerbAndNoTypeLeaksItsRawId() {
        for (String type : TaskFactory.TYPES) {
            String verb = TaskVerbs.verb(TaskFactory.create(type));
            assertFalse(verb.isBlank(), type + " has no verb at all");
            // No identifier fragment survives: item_tag -> COLLECT, never ITEM_TAG or "Item Tag".
            assertFalse(verb.contains("_"), type + " leaked an identifier fragment: " + verb);
            // A multi-word id would read as an identifier if it fell through to the old default branch.
            if (type.contains("_")) {
                assertNotEquals(type.toUpperCase(java.util.Locale.ROOT), verb,
                        type + " still renders its raw identifier");
            }
        }
    }

    @Test
    void theVerbsThePackAskedForAreTheOnesShipped() {
        for (Map.Entry<String, String> expected : EXPECTED.entrySet()) {
            String type = expected.getKey();
            if ("stat".equals(type)) {
                // A stat task's verb comes from its stat id, so the table entry is the bare fallback and the
                // fixture (walk_one_cm) reads WALK. Both are asserted in statTasksPickTheirSubVerbFromTheStatId.
                assertEquals("STAT", TaskVerbs.defaultVerb(type));
                continue;
            }
            assertEquals(expected.getValue(), TaskVerbs.verb(TaskFactory.create(type)),
                    type + " no longer reads as " + expected.getValue());
            assertEquals(expected.getValue(), TaskVerbs.defaultVerb(type));
        }
    }

    @Test
    void theEightFormerlyUnmappedTypesAreTheRegressionThePackReported() {
        // These are exactly the types whose switch cases were missing, so they hit the upper-casing default.
        List<String> formerlyUnmapped = List.of("item_tag", "xp_levels", "advancement", "interact_block",
                "interact_entity", "fluid", "npc_dialog", "trigger");
        for (String type : formerlyUnmapped) {
            assertTrue(TaskVerbs.VERBS.containsKey(type), type + " is unlabelled again");
        }
        // The two the pack measured on screen.
        assertEquals("COLLECT", TaskVerbs.verb("item_tag"));
        assertEquals("REACH", TaskVerbs.verb("xp_levels"));
    }

    @Test
    void verbTableCoversTheFactoryTypeListExactly() {
        assertEquals(new HashSet<>(TaskFactory.TYPES), new HashSet<>(TaskVerbs.VERBS.keySet()),
                "a new task type was added without a verb, or a verb outlived its type");
    }

    @Test
    void statTasksPickTheirSubVerbFromTheStatId() {
        assertEquals("WALK", TaskVerbs.verb(new StatTask(ResourceLocation.parse("minecraft:walk_one_cm"), 1000)));
        assertEquals("WALK", TaskVerbs.verb(new StatTask(ResourceLocation.parse("minecraft:sprint_one_cm"), 1000)));
        assertEquals("FLY", TaskVerbs.verb(new StatTask(ResourceLocation.parse("minecraft:aviate_one_cm"), 100)));
        assertEquals("SWIM", TaskVerbs.verb(new StatTask(ResourceLocation.parse("minecraft:swim_one_cm"), 100)));
        assertEquals("SLEEP", TaskVerbs.verb(new StatTask(ResourceLocation.parse("minecraft:sleep_in_bed"), 1)));
        assertEquals("STAT", TaskVerbs.verb(new StatTask(ResourceLocation.parse("minecraft:deaths"), 1)));
        // A non-stat task must never borrow a stat sub-verb.
        assertEquals("FIND", TaskVerbs.verb(new ObtainTask(ResourceLocation.parse("minecraft:dirt"), 1)));
    }

    @Test
    void unmappedTypesDegradeToReadableWordsNotIdentifiers() {
        assertEquals("Item Tag", TaskVerbs.humanize("item_tag"));
        assertEquals("Some Future Type", TaskVerbs.humanize("some_future_type"));
        assertEquals("Some Future Type", TaskVerbs.verb("some_future_type"),
                "an unknown type must not leak its id");
        assertEquals("Thing", TaskVerbs.verb("questqueen:thing"));
    }

    @Test
    void everyTypeHasAPackOverridableKey() {
        Set<String> keys = new HashSet<>();
        for (String type : TaskFactory.TYPES) {
            String key = TaskVerbs.keyFor(type);
            assertTrue(key.startsWith(TaskVerbs.KEY_PREFIX), key + " is outside the questqueen.task namespace");
            assertTrue(keys.add(key), "two types share the translation key " + key);
        }
        assertEquals("questqueen.task.item_tag", TaskVerbs.keyFor("item_tag"));
        assertEquals("questqueen.task.obtain", TaskVerbs.keyFor("item"));
        assertEquals("questqueen.task.stat", TaskVerbs.keyFor("stat"));
    }

    @Test
    void languageKeysParseAndCarryEveryVerb() throws Exception {
        // The verbs are shipped as en_us.json values; if that file drifts from the Java fallback a pack
        // override and the built-in default would disagree. Parse the real resource and compare.
        try (var in = TaskTypeCoverageTest.class.getResourceAsStream("/assets/questqueen/lang/en_us.json")) {
            assertNotNull(in, "en_us.json is not on the test classpath");
            var json = JsonParser.parseReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8))
                    .getAsJsonObject();
            for (Map.Entry<String, String> entry : EXPECTED.entrySet()) {
                String key = TaskVerbs.keyFor(entry.getKey());
                assertTrue(json.has(key), key + " is missing from en_us.json");
                assertEquals(entry.getValue(), json.get(key).getAsString(),
                        key + " disagrees with the built-in fallback");
            }
            // The stat sub-verbs are looked up separately from the plain "stat" key.
            for (String fragment : TaskVerbs.STAT_VERBS.keySet()) {
                String key = TaskVerbs.KEY_PREFIX + "stat." + fragment;
                assertTrue(json.has(key), key + " is missing from en_us.json");
            }
        }
    }
}
