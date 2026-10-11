package dev.aof.questqueen.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Readable lines for pack advancements that have no title or description (Skylore's "logged" ones). */
class AdvancementSummaryTest {
    private static final AdvancementSummary.Names NAMES = new AdvancementSummary.Names() {
        public Component item(ResourceLocation id) { return Component.literal(AdvancementSummary.humanize(id)); }
        public Component block(ResourceLocation id) { return Component.literal(AdvancementSummary.humanize(id)); }
        public Component biome(ResourceLocation id) { return Component.literal(AdvancementSummary.humanize(id)); }
        public Component structure(ResourceLocation id) { return Component.literal(AdvancementSummary.humanize(id)); }
        public Component dimension(ResourceLocation id) { return Component.literal(AdvancementSummary.humanize(id)); }
    };

    private static String line(String json) {
        JsonObject criterion = JsonParser.parseString(json).getAsJsonObject();
        return AdvancementSummary.criterion(criterion, NAMES).getString();
    }

    @Test
    void inventoryItemsAndTags() {
        assertEquals("Have any Logs", line("{\"trigger\":\"minecraft:inventory_changed\",\"conditions\":{\"items\":[{\"items\":\"#minecraft:logs\"}]}}"));
        assertEquals("Have Pain Pills", line("{\"trigger\":\"minecraft:inventory_changed\",\"conditions\":{\"items\":[{\"items\":\"marbledsfirstaid:pain_pills\"}]}}"));
        assertEquals("Have 64 Glow Berries", line("{\"trigger\":\"minecraft:inventory_changed\",\"conditions\":{\"items\":[{\"items\":\"minecraft:glow_berries\",\"count\":{\"min\":64}}]}}"));
        assertEquals("Have Iron Ingot or Gold Ingot, Stick", line("{\"trigger\":\"minecraft:inventory_changed\",\"conditions\":{\"items\":[{\"items\":[\"minecraft:iron_ingot\",\"minecraft:gold_ingot\"]},{\"items\":\"minecraft:stick\"}]}}"));
    }

    @Test
    void placeEatVisitAndScripted() {
        assertEquals("Place Hospital Bed", line("{\"trigger\":\"minecraft:placed_block\",\"conditions\":{\"location\":[{\"condition\":\"minecraft:location_check\",\"predicate\":{\"block\":{\"blocks\":\"hospitals:hospital_bed\"}}}]}}"));
        assertEquals("Eat or drink Beef Stew", line("{\"trigger\":\"minecraft:consume_item\",\"conditions\":{\"item\":{\"items\":\"farmersdelight:beef_stew\"}}}"));
        assertEquals("Go to Crashed Main Ship", line("{\"trigger\":\"minecraft:location\",\"conditions\":{\"player\":[{\"condition\":\"minecraft:entity_properties\",\"entity\":\"this\",\"predicate\":{\"location\":{\"structures\":\"skylore_structures:crashed_main_ship\"}}}]}}"));
        assertEquals("Unlocked by an event in the story", line("{\"trigger\":\"minecraft:impossible\"}"));
        assertEquals("Trigger: Bred Animals", line("{\"trigger\":\"minecraft:bred_animals\",\"conditions\":{}}"));
    }

    @Test
    void severalCriteria() {
        List<Component> one = AdvancementSummary.criteria(List.of(Component.literal("Have A")), List.of(List.of("a")));
        assertEquals(1, one.size());
        List<Component> any = AdvancementSummary.criteria(List.of(Component.literal("Have A"), Component.literal("Have B")),
                List.of(List.of("a", "b")));
        assertEquals("Any of these:", any.getFirst().getString());
        assertEquals("• Have B", any.get(2).getString());
        List<Component> all = AdvancementSummary.criteria(List.of(Component.literal("Have A"), Component.literal("Have B")),
                List.of(List.of("a"), List.of("b")));
        assertEquals("All of these:", all.getFirst().getString());
    }

    @Test
    void humanizesIds() {
        assertEquals("Iron Ingots", AdvancementSummary.humanize(ResourceLocation.parse("c:ingots/iron")));
        assertEquals("Crashed Main Ship", AdvancementSummary.humanize(ResourceLocation.parse("skylore_structures:crashed_main_ship")));
    }
}
