package dev.aof.questqueen.data.stage;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.List;
import java.util.Optional;

/**
 * Turns an {@link AuthoredStage} into the two files StageLock reads: a stage definition and a restriction file.
 * Pure (no game, no files), so the shapes are pinned by unit tests against StageLock's own examples.
 *
 * <p>Every StageLock action defaults to "locked", so a toggle the author switched off is written as {@code false}
 * and the rest are left to that default. A lock group that ends up locking nothing writes no rule.
 */
public final class StageLockFiles {
    private StageLockFiles() {
    }

    /**
     * The file path (without {@code .json}) a stage id is stored under: {@code iron_age} → {@code iron_age},
     * {@code mypack:nether} → {@code mypack/nether}. Empty when the id would walk out of its folder.
     */
    public static Optional<String> path(String id) {
        if (id == null || id.isEmpty()) {
            return Optional.empty();
        }
        String path = id.replace(':', '/');
        for (String segment : path.split("/", -1)) {
            if (segment.isEmpty() || ".".equals(segment) || "..".equals(segment)) {
                return Optional.empty();
            }
        }
        return Optional.of(path);
    }

    /** {@code data/<ns>/stagelock/stages/<path>.json}. The id is always explicit: no implied namespace. */
    public static JsonObject definition(AuthoredStage stage) {
        JsonObject root = new JsonObject();
        root.addProperty("id", stage.id());
        if (!stage.name().isBlank()) {
            root.addProperty("display_name", stage.name());
        }
        if (!stage.description().isBlank()) {
            root.addProperty("description", stage.description());
        }
        if (!stage.icon().isBlank()) {
            root.addProperty("icon", stage.icon());
        }
        if (!stage.requires().isEmpty()) {
            root.add("requires", strings(stage.requires()));
        }
        if (!stage.advancements().isEmpty() || !"player".equals(stage.scope())) {
            JsonObject unlock = new JsonObject();
            if (!stage.advancements().isEmpty()) {
                unlock.add("advancements", strings(stage.advancements()));
            }
            unlock.addProperty("scope", stage.scope());
            root.add("unlock", unlock);
        }
        JsonObject onGrant = new JsonObject();
        onGrant.addProperty("message", stage.message());
        root.add("on_grant", onGrant);
        return root;
    }

    /** {@code data/<ns>/stagelock/restrictions/questqueen/<path>.json}: {@code {"rules": [...]}}. */
    public static JsonObject restrictions(AuthoredStage stage) {
        JsonArray rules = new JsonArray();
        for (AuthoredStage.Lock lock : stage.locks()) {
            if (lock.targets().isEmpty()) {
                continue;
            }
            switch (lock.kind()) {
                case "item" -> item(stage.id(), lock, rules);
                case "block" -> block(stage.id(), lock, rules);
                case "mod" -> mod(stage.id(), lock, rules);
                case "dimension" -> dimension(stage.id(), lock, rules);
                case "entity" -> entity(stage.id(), lock, rules);
                default -> {
                }
            }
        }
        JsonObject root = new JsonObject();
        root.add("rules", rules);
        return root;
    }

    /** Toggles: use (use/place/attack/mine/equip), pickup (and keep holding), craft, hide (in recipe viewers), hide_name. */
    private static void item(String stage, AuthoredStage.Lock lock, JsonArray rules) {
        boolean use = lock.flag("use", true);
        boolean pickup = lock.flag("pickup", true);
        boolean craft = lock.flag("craft", true);
        boolean hide = lock.flag("hide", true);
        if (!use && !pickup && !craft && !hide) {
            return;
        }
        JsonObject rule = rule("stagelock:item", stage);
        rule.add("items", strings(lock.targets()));
        JsonObject actions = new JsonObject();
        actions.addProperty("pickup", pickup);
        actions.addProperty("use", use);
        actions.addProperty("place", use);
        actions.addProperty("attack", use);
        actions.addProperty("mine", use);
        actions.addProperty("equip", use);
        // An item the player may not pick up drops out of their hands too; otherwise they keep holding it.
        actions.addProperty("hold", pickup ? "drop" : "allow");
        actions.addProperty("craft", craft);
        actions.addProperty("viewer", hide ? "hide" : "lock");
        rule.add("actions", actions);
        rule.addProperty("hide_name", lock.flag("hide_name", true));
        message(lock, rule);
        rules.add(rule);
    }

    /** Toggles: break, place, use (right-click), disguise (looks and drops like stone). */
    private static void block(String stage, AuthoredStage.Lock lock, JsonArray rules) {
        boolean breakBlock = lock.flag("break", true);
        boolean place = lock.flag("place", true);
        boolean use = lock.flag("use", true);
        boolean disguise = lock.flag("disguise", false);
        if (!breakBlock && !place && !use && !disguise) {
            return;
        }
        JsonObject rule = rule("stagelock:block", stage);
        rule.add("blocks", strings(lock.targets()));
        JsonObject actions = new JsonObject();
        actions.addProperty("break", breakBlock);
        actions.addProperty("interact", use);
        actions.addProperty("place", place);
        rule.add("actions", actions);
        if (disguise) {
            rule.addProperty("disguise_as", "minecraft:stone");
        }
        message(lock, rule);
        rules.add(rule);
    }

    /** A whole mod: every item it adds (all actions) and every recipe it adds. Toggle: hide (in recipe viewers). */
    private static void mod(String stage, AuthoredStage.Lock lock, JsonArray rules) {
        JsonArray mods = new JsonArray();
        for (String target : lock.targets()) {
            String mod = target.startsWith("@") ? target.substring(1) : target;
            if (!mod.isEmpty() && !mod.contains(":") && !mod.startsWith("#")) {
                mods.add("@" + mod);
            }
        }
        if (mods.isEmpty()) {
            return;
        }
        String viewer = lock.flag("hide", true) ? "hide" : "lock";
        JsonObject items = rule("stagelock:item", stage);
        items.add("items", mods.deepCopy());
        JsonObject actions = new JsonObject();
        actions.addProperty("viewer", viewer);
        items.add("actions", actions);
        message(lock, items);
        rules.add(items);
        JsonObject recipes = rule("stagelock:recipe", stage);
        recipes.add("recipes", mods);
        recipes.addProperty("viewer", viewer);
        rules.add(recipes);
    }

    private static void dimension(String stage, AuthoredStage.Lock lock, JsonArray rules) {
        JsonObject rule = rule("stagelock:dimension", stage);
        rule.add("dimensions", strings(lock.targets()));
        message(lock, rule);
        rules.add(rule);
    }

    /** Toggles: attack, interact, spawn (only spawns near a player who has the stage). */
    private static void entity(String stage, AuthoredStage.Lock lock, JsonArray rules) {
        boolean attack = lock.flag("attack", true);
        boolean interact = lock.flag("interact", true);
        boolean spawn = lock.flag("spawn", false);
        if (!attack && !interact && !spawn) {
            return;
        }
        JsonObject rule = rule("stagelock:entity", stage);
        rule.add("entities", strings(lock.targets()));
        JsonObject actions = new JsonObject();
        actions.addProperty("attack", attack);
        actions.addProperty("interact", interact);
        if (spawn) {
            JsonObject range = new JsonObject();
            range.addProperty("range", 128);
            actions.add("spawn", range);
        }
        rule.add("actions", actions);
        message(lock, rule);
        rules.add(rule);
    }

    private static JsonObject rule(String type, String stage) {
        JsonObject rule = new JsonObject();
        rule.addProperty("type", type);
        rule.addProperty("requires", stage);
        return rule;
    }

    private static void message(AuthoredStage.Lock lock, JsonObject rule) {
        if (!lock.message().isBlank()) {
            rule.addProperty("message", lock.message());
        }
    }

    private static JsonArray strings(List<String> values) {
        JsonArray array = new JsonArray();
        values.forEach(array::add);
        return array;
    }
}
