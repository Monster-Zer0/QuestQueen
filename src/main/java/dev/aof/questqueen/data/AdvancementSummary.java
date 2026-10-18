package dev.aof.questqueen.data;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Says in words what an advancement asks for, from its criteria as JSON. Pack advancements that only exist to
 * track something (no title, no description, never sent to the client) get a readable line such as
 * "Have any Logs" instead of nothing.
 *
 * <p>Pure: names come from {@link Names}, so callers choose real item names or plain text.
 */
public final class AdvancementSummary {
    /** How ids become words. */
    public interface Names {
        Component item(ResourceLocation id);

        Component block(ResourceLocation id);

        Component biome(ResourceLocation id);

        Component structure(ResourceLocation id);

        Component dimension(ResourceLocation id);

        /** A tag of items, blocks, biomes or structures, e.g. "any Logs". */
        default Component tag(ResourceLocation tag) {
            return text("questqueen.adv.any_tag", "any %s", Component.literal(humanize(tag)));
        }
    }

    private enum Kind { ITEM, BLOCK, BIOME, STRUCTURE, DIMENSION }

    private AdvancementSummary() {
    }

    /** One line for a criterion: {@code {"trigger": "...", "conditions": {...}}}. */
    public static Component criterion(JsonObject criterion, Names names) {
        String trigger = string(criterion, "trigger");
        JsonObject conditions = criterion.has("conditions") && criterion.get("conditions").isJsonObject()
                ? criterion.getAsJsonObject("conditions") : new JsonObject();
        Component described = switch (trigger) {
            case "minecraft:inventory_changed" -> join(itemPredicates(conditions.get("items"), names), ", ")
                    .map(what -> text("questqueen.adv.have", "Have %s", what)).orElse(null);
            case "minecraft:consume_item" -> join(itemPredicates(conditions.get("item"), names), ", ")
                    .map(what -> text("questqueen.adv.consume", "Eat or drink %s", what)).orElse(null);
            case "minecraft:placed_block" -> join(placedBlocks(conditions.get("location"), names), " or ")
                    .map(what -> text("questqueen.adv.place", "Place %s", what)).orElse(null);
            case "minecraft:location", "minecraft:tick" -> join(places(conditions.get("player"), names), " or ")
                    .map(where -> text("questqueen.adv.visit", "Go to %s", where)).orElse(null);
            case "minecraft:impossible" -> text("questqueen.adv.scripted", "Unlocked by an event in the story");
            default -> null;
        };
        if (described != null) {
            return described;
        }
        ResourceLocation id = ResourceLocation.tryParse(trigger);
        return text("questqueen.adv.trigger", "Trigger: %s",
                Component.literal(id == null ? trigger : humanize(id)));
    }

    /**
     * Title and lines for an advancement with no display: one criterion is the title itself; several become
     * "Any of these:" / "All of these:" over a line each.
     */
    public static List<Component> criteria(List<Component> lines, List<List<String>> requirements) {
        List<Component> out = new ArrayList<>();
        if (lines.size() == 1) {
            out.add(lines.getFirst());
            return out;
        }
        boolean any = requirements.size() == 1 && requirements.getFirst().size() > 1;
        out.add(any ? text("questqueen.adv.any_of", "Any of these:") : text("questqueen.adv.all_of", "All of these:"));
        for (Component line : lines) {
            out.add(Component.literal("• ").append(line));
        }
        return out;
    }

    private static List<Component> itemPredicates(JsonElement element, Names names) {
        List<Component> out = new ArrayList<>();
        for (JsonElement predicate : list(element)) {
            if (!predicate.isJsonObject()) {
                continue;
            }
            JsonObject p = predicate.getAsJsonObject();
            join(ids(p.get("items"), Kind.ITEM, names), " or ").ifPresent(what -> {
                int count = minCount(p.get("count"));
                out.add(count > 1 ? Component.literal(count + " ").append(what) : what);
            });
        }
        return out;
    }

    private static List<Component> placedBlocks(JsonElement element, Names names) {
        List<Component> out = new ArrayList<>();
        for (JsonElement condition : list(element)) {
            if (!condition.isJsonObject()) {
                continue;
            }
            JsonObject c = condition.getAsJsonObject();
            if (c.has("block")) {
                out.addAll(ids(c.get("block"), Kind.BLOCK, names));
            }
            JsonObject predicate = object(c, "predicate");
            JsonObject block = predicate == null ? null : object(predicate, "block");
            if (block != null) {
                out.addAll(ids(block.get("blocks"), Kind.BLOCK, names));
            }
        }
        return out;
    }

    private static List<Component> places(JsonElement element, Names names) {
        List<Component> out = new ArrayList<>();
        for (JsonElement condition : list(element)) {
            if (!condition.isJsonObject()) {
                continue;
            }
            JsonObject predicate = object(condition.getAsJsonObject(), "predicate");
            JsonObject location = predicate == null ? null : object(predicate, "location");
            if (location == null) {
                continue;
            }
            out.addAll(ids(first(location, "structures", "structure"), Kind.STRUCTURE, names));
            out.addAll(ids(first(location, "biomes", "biome"), Kind.BIOME, names));
            out.addAll(ids(location.get("dimension"), Kind.DIMENSION, names));
        }
        return out;
    }

    /** {@code "id"}, {@code "#tag"} or a list of ids, as names. */
    private static List<Component> ids(JsonElement element, Kind kind, Names names) {
        List<Component> out = new ArrayList<>();
        for (JsonElement entry : list(element)) {
            if (!entry.isJsonPrimitive()) {
                continue;
            }
            String raw = entry.getAsString();
            boolean tag = raw.startsWith("#");
            ResourceLocation id = ResourceLocation.tryParse(tag ? raw.substring(1) : raw);
            if (id == null) {
                continue;
            }
            out.add(tag ? names.tag(id) : switch (kind) {
                case ITEM -> names.item(id);
                case BLOCK -> names.block(id);
                case BIOME -> names.biome(id);
                case STRUCTURE -> names.structure(id);
                case DIMENSION -> names.dimension(id);
            });
        }
        return out;
    }

    private static int minCount(JsonElement count) {
        if (count == null) {
            return 1;
        }
        if (count.isJsonPrimitive() && count.getAsJsonPrimitive().isNumber()) {
            return count.getAsInt();
        }
        if (count.isJsonObject() && count.getAsJsonObject().has("min")) {
            return count.getAsJsonObject().get("min").getAsInt();
        }
        return 1;
    }

    private static java.util.Optional<Component> join(List<Component> parts, String separator) {
        if (parts.isEmpty()) {
            return java.util.Optional.empty();
        }
        MutableComponent out = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                out.append(separator.equals(" or ") ? text("questqueen.adv.or", " or ") : Component.literal(separator));
            }
            out.append(parts.get(i));
        }
        return java.util.Optional.of(out);
    }

    private static List<JsonElement> list(JsonElement element) {
        List<JsonElement> out = new ArrayList<>();
        if (element == null || element.isJsonNull()) {
            return out;
        }
        if (element.isJsonArray()) {
            for (JsonElement e : (JsonArray) element) {
                out.add(e);
            }
        } else {
            out.add(element);
        }
        return out;
    }

    private static JsonElement first(JsonObject object, String... keys) {
        for (String key : keys) {
            if (object.has(key)) {
                return object.get(key);
            }
        }
        return null;
    }

    private static JsonObject object(JsonObject parent, String key) {
        return parent.has(key) && parent.get(key).isJsonObject() ? parent.getAsJsonObject(key) : null;
    }

    private static String string(JsonObject object, String key) {
        return object.has(key) && object.get(key).isJsonPrimitive() ? object.get(key).getAsString() : "";
    }

    static MutableComponent text(String key, String fallback, Object... args) {
        return Component.translatableWithFallback(key, fallback, args);
    }

    /** {@code skylore_structures:crashed_main_ship} → "Crashed Main Ship"; {@code c:ingots/iron} → "Iron Ingots". */
    public static String humanize(ResourceLocation id) {
        String[] parts = id.getPath().split("/");
        StringBuilder out = new StringBuilder();
        for (int i = parts.length - 1; i >= 0; i--) {
            for (String word : parts[i].split("_")) {
                if (word.isEmpty()) {
                    continue;
                }
                if (!out.isEmpty()) {
                    out.append(' ');
                }
                out.append(word.substring(0, 1).toUpperCase(Locale.ROOT)).append(word.substring(1));
            }
        }
        return out.toString();
    }
}
