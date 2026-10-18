package dev.aof.questqueen.data.stage;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The StageLock files an editor-made stage turns into, pinned against the shapes in StageLock's own examples. */
class StageLockFilesTest {
    private static AuthoredStage parse(String json) {
        return AuthoredStage.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow(IllegalStateException::new);
    }

    private static boolean parses(String json) {
        return AuthoredStage.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).result().isPresent();
    }

    private static JsonArray rules(String json) {
        return StageLockFiles.restrictions(parse(json)).getAsJsonArray("rules");
    }

    @Test
    void definitionNamesTheIdExplicitly() {
        JsonObject def = StageLockFiles.definition(parse("""
                {"id":"Iron_Age","name":"Iron Age","description":"Smelt iron.","icon":"minecraft:iron_ingot",
                 "requires":["stone_age"],"advancements":["minecraft:story/smelt_iron"]}"""));
        assertEquals("iron_age", def.get("id").getAsString(), "ids are lowercased like StageLock does");
        assertEquals("Iron Age", def.get("display_name").getAsString());
        assertEquals("minecraft:iron_ingot", def.get("icon").getAsString());
        assertEquals("stone_age", def.getAsJsonArray("requires").get(0).getAsString());
        assertEquals("minecraft:story/smelt_iron", def.getAsJsonObject("unlock").getAsJsonArray("advancements").get(0).getAsString());
        assertTrue(def.getAsJsonObject("on_grant").get("message").getAsBoolean(), "the unlock message is on by default");
    }

    @Test
    void plainStageWritesNoUnlockBlock() {
        JsonObject def = StageLockFiles.definition(parse("{\"id\":\"plain\"}"));
        assertFalse(def.has("unlock"));
        assertFalse(def.has("display_name"));
    }

    @Test
    void teamScopeIsWrittenEvenWithoutAdvancements() {
        JsonObject def = StageLockFiles.definition(parse("{\"id\":\"nether\",\"scope\":\"team\"}"));
        assertEquals("team", def.getAsJsonObject("unlock").get("scope").getAsString());
    }

    @Test
    void itemLockWithDefaultsLocksEverything() {
        JsonArray rules = rules("{\"id\":\"iron_age\",\"locks\":[{\"kind\":\"item\",\"targets\":[\"minecraft:diamond_pickaxe\",\"#c:gems/diamond\"]}]}");
        assertEquals(1, rules.size());
        JsonObject rule = rules.get(0).getAsJsonObject();
        assertEquals("stagelock:item", rule.get("type").getAsString());
        assertEquals("iron_age", rule.get("requires").getAsString());
        assertEquals(2, rule.getAsJsonArray("items").size());
        JsonObject actions = rule.getAsJsonObject("actions");
        for (String key : List.of("pickup", "use", "place", "attack", "mine", "equip", "craft")) {
            assertTrue(actions.get(key).getAsBoolean(), key);
        }
        assertEquals("drop", actions.get("hold").getAsString());
        assertEquals("hide", actions.get("viewer").getAsString());
    }

    @Test
    void itemTogglesSwitchedOffAreWrittenFalse() {
        JsonObject actions = rules("""
                {"id":"s","locks":[{"kind":"item","targets":["minecraft:diamond"],
                 "flags":{"pickup":false,"hide":false,"hide_name":false}}]}""")
                .get(0).getAsJsonObject().getAsJsonObject("actions");
        assertFalse(actions.get("pickup").getAsBoolean());
        assertEquals("allow", actions.get("hold").getAsString(), "items you may hold are not dropped");
        assertEquals("lock", actions.get("viewer").getAsString(), "shown with a padlock instead of hidden");
    }

    @Test
    void lockThatLocksNothingWritesNoRule() {
        assertEquals(0, rules("""
                {"id":"s","locks":[{"kind":"item","targets":["minecraft:diamond"],
                 "flags":{"use":false,"pickup":false,"craft":false,"hide":false}},
                 {"kind":"block","targets":[]}]}""").size());
    }

    @Test
    void blockLockMapsUseToInteractAndDisguisesAsStone() {
        JsonObject rule = rules("""
                {"id":"s","locks":[{"kind":"block","targets":["#c:ores/diamond"],"flags":{"place":false,"disguise":true}}]}""")
                .get(0).getAsJsonObject();
        assertEquals("stagelock:block", rule.get("type").getAsString());
        JsonObject actions = rule.getAsJsonObject("actions");
        assertTrue(actions.get("break").getAsBoolean());
        assertTrue(actions.get("interact").getAsBoolean());
        assertFalse(actions.get("place").getAsBoolean());
        assertEquals("minecraft:stone", rule.get("disguise_as").getAsString());
    }

    @Test
    void modLockWritesItemAndRecipeRules() {
        JsonArray rules = rules("{\"id\":\"s\",\"locks\":[{\"kind\":\"mod\",\"targets\":[\"create\",\"@ae2\"]}]}");
        assertEquals(2, rules.size());
        JsonObject items = rules.get(0).getAsJsonObject();
        assertEquals("stagelock:item", items.get("type").getAsString());
        assertEquals("@create", items.getAsJsonArray("items").get(0).getAsString());
        assertEquals("@ae2", items.getAsJsonArray("items").get(1).getAsString());
        JsonObject recipes = rules.get(1).getAsJsonObject();
        assertEquals("stagelock:recipe", recipes.get("type").getAsString());
        assertEquals("@create", recipes.getAsJsonArray("recipes").get(0).getAsString());
        assertEquals("hide", recipes.get("viewer").getAsString());
    }

    @Test
    void dimensionLockCarriesItsMessage() {
        JsonObject rule = rules("""
                {"id":"nether","locks":[{"kind":"dimension","targets":["minecraft:the_nether"],"message":"The portal refuses you."}]}""")
                .get(0).getAsJsonObject();
        assertEquals("stagelock:dimension", rule.get("type").getAsString());
        assertEquals("minecraft:the_nether", rule.getAsJsonArray("dimensions").get(0).getAsString());
        assertEquals("The portal refuses you.", rule.get("message").getAsString());
    }

    @Test
    void entityLockSpawnRangeOnlyWhenAsked() {
        JsonObject plain = rules("{\"id\":\"s\",\"locks\":[{\"kind\":\"entity\",\"targets\":[\"minecraft:witch\"]}]}")
                .get(0).getAsJsonObject().getAsJsonObject("actions");
        assertFalse(plain.has("spawn"));
        JsonObject spawn = rules("{\"id\":\"s\",\"locks\":[{\"kind\":\"entity\",\"targets\":[\"minecraft:witch\"],\"flags\":{\"spawn\":true}}]}")
                .get(0).getAsJsonObject().getAsJsonObject("actions");
        assertEquals(128, spawn.getAsJsonObject("spawn").get("range").getAsInt());
    }

    @Test
    void codecRefusesWhatStageLockWouldNot() {
        assertFalse(parses("{\"id\":\"iron age\"}"), "spaces");
        assertFalse(parses("{\"id\":\"s\",\"scope\":\"world\"}"), "unknown scope");
        assertFalse(parses("{\"id\":\"s\",\"locks\":[{\"kind\":\"recipe_book\",\"targets\":[]}]}"), "unknown kind");
        assertFalse(parses("{\"id\":\"s\",\"locks\":[{\"kind\":\"item\",\"targets\":[\"Not An Id\"]}]}"), "bad target");
        assertFalse(parses("{\"id\":\"s\",\"requires\":[\"s\"]}"), "requires itself");
        assertFalse(parses("{\"id\":\"s\",\"icon\":\"<script>\"}"), "bad icon");
    }

    @Test
    void roundTripsThroughTheCodec() {
        AuthoredStage stage = new AuthoredStage("iron_age", "Iron Age", "", "minecraft:iron_ingot", "player", true,
                List.of(), List.of(), List.of(new AuthoredStage.Lock("item", List.of("minecraft:diamond"), Map.of("pickup", false), "")));
        AuthoredStage back = AuthoredStage.CODEC.parse(JsonOps.INSTANCE,
                AuthoredStage.CODEC.encodeStart(JsonOps.INSTANCE, stage).getOrThrow()).getOrThrow();
        assertEquals(stage, back);
    }

    @Test
    void idPathsNeverWalkUp() {
        assertEquals(Optional.of("iron_age"), StageLockFiles.path("iron_age"));
        assertEquals(Optional.of("mypack/nether/deep"), StageLockFiles.path("mypack:nether/deep"));
        assertEquals(Optional.of("a..b"), StageLockFiles.path("a..b"), "dots inside a name are fine");
        assertTrue(StageLockFiles.path("../../ops").isEmpty());
        assertTrue(StageLockFiles.path("mypack:..").isEmpty());
        assertTrue(StageLockFiles.path("/abs").isEmpty());
        assertTrue(StageLockFiles.path("a//b").isEmpty());
        assertTrue(StageLockFiles.path("").isEmpty());
    }
}
