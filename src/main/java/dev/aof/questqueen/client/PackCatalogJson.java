package dev.aof.questqueen.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.storage.loot.BuiltInLootTables;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

public final class PackCatalogJson {
    private PackCatalogJson() {
    }

    public static String create() {
        Minecraft minecraft = Minecraft.getInstance();
        JsonObject root = new JsonObject();
        JsonArray items = new JsonArray();
        for (Item item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) {
                continue;
            }
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            items.add(entry(id.toString(), new ItemStack(item).getHoverName().getString()));
        }
        root.add("items", items);
        root.add("entities", ids(BuiltInRegistries.ENTITY_TYPE.keySet()));
        root.add("blocks", ids(BuiltInRegistries.BLOCK.keySet()));
        root.add("fluids", ids(BuiltInRegistries.FLUID.keySet()));
        JsonArray tags = new JsonArray();
        BuiltInRegistries.ITEM.getTagNames().forEach(tag -> tags.add(entry(tag.location().toString(), tag.location().toString())));
        root.add("tags", tags);
        root.add("stats", ids(BuiltInRegistries.CUSTOM_STAT.keySet()));
        // Stages the pack defines (StageLock syncs its definitions to the client); the editor offers them by name.
        JsonArray stages = new JsonArray();
        dev.aof.questqueen.compat.Stages.definedStages().forEach((id, name) -> stages.add(entry(id, name)));
        root.add("stages", stages);
        root.addProperty("stageLock", dev.aof.questqueen.compat.Stages.backend() == dev.aof.questqueen.compat.Stages.Backend.STAGELOCK);
        // Installed mods, for "lock a whole mod" in the stage editor.
        JsonArray mods = new JsonArray();
        net.neoforged.fml.ModList.get().getMods().forEach(mod -> mods.add(entry(mod.getModId(), mod.getDisplayName())));
        root.add("mods", mods);

        JsonArray biomes = new JsonArray();
        JsonArray structures = new JsonArray();
        JsonArray dimensions = new JsonArray();
        RegistryAccess access = registryAccess(minecraft);
        if (access != null) {
            copyKeys(access, Registries.BIOME, biomes);
            copyKeys(access, Registries.STRUCTURE, structures);
        }
        if (minecraft.getConnection() != null) {
            minecraft.getConnection().levels().forEach(key ->
                    dimensions.add(entry(key.location().toString(), key.location().getPath().replace('_', ' '))));
        } else {
            dimensions.add(entry("minecraft:overworld", "overworld"));
            dimensions.add(entry("minecraft:the_nether", "the_nether"));
            dimensions.add(entry("minecraft:the_end", "the_end"));
        }
        root.add("biomes", biomes);
        root.add("structures", structures);
        root.add("dimensions", dimensions);

        root.add("advancements", advancements(minecraft));
        root.add("loot", lootTables(minecraft));
        JsonArray entityTags = new JsonArray();
        BuiltInRegistries.ENTITY_TYPE.getTagNames().forEach(tag -> entityTags.add(entry(tag.location().toString(), tag.location().toString())));
        root.add("entityTags", entityTags);
        root.add("objectives", objectives(minecraft));
        JsonArray scrolls = new JsonArray();
        ClientQuestState.pack.scrolls().forEach(scroll -> scrolls.add(entry(scroll.id().toString(), scroll.title())));
        root.add("scrolls", scrolls);
        root.add("textures", textures(minecraft));
        return root.toString();
    }

    /** Every advancement the game knows (the whole set in single player, what the server sent otherwise), titled. */
    private static JsonArray advancements(Minecraft minecraft) {
        JsonArray array = new JsonArray();
        Collection<AdvancementHolder> holders = new ArrayList<>();
        MinecraftServer server = minecraft.getSingleplayerServer();
        if (server != null) {
            holders.addAll(server.getAdvancements().getAllAdvancements());
        } else if (minecraft.getConnection() != null) {
            minecraft.getConnection().getAdvancements().getTree().nodes().forEach(node -> holders.add(node.holder()));
        }
        holders.stream()
                // One hidden "you can now craft X" advancement per recipe: thousands of rows nobody gates on.
                .filter(holder -> !holder.id().getPath().startsWith("recipes/"))
                .sorted(Comparator.comparing(holder -> holder.id().toString()))
                .forEach(holder -> array.add(entry(holder.id().toString(), holder.value().display()
                        .map(display -> display.getTitle().getString())
                        .filter(title -> !title.isBlank())
                        .orElse(holder.id().toString()))));
        if (array.isEmpty()) {
            for (String id : new String[]{
                    "minecraft:story/root", "minecraft:story/mine_stone", "minecraft:adventure/root",
                    "minecraft:nether/root", "minecraft:end/root", "minecraft:husbandry/root"
            }) {
                array.add(entry(id, id));
            }
        }
        return array;
    }

    /** Every loot table the pack loads in single player; vanilla's built-in tables otherwise. */
    private static JsonArray lootTables(Minecraft minecraft) {
        MinecraftServer server = minecraft.getSingleplayerServer();
        List<ResourceLocation> ids = new ArrayList<>();
        if (server != null) {
            ids.addAll(server.reloadableRegistries().getKeys(Registries.LOOT_TABLE));
        } else {
            BuiltInLootTables.all().forEach(key -> ids.add(key.location()));
        }
        ids.sort(Comparator.comparing(ResourceLocation::toString));
        JsonArray array = new JsonArray();
        ids.forEach(id -> array.add(entry(id.toString(), id.toString())));
        return array;
    }

    /** Scoreboard objectives, by name (what a scoreboard gate names) and display name. */
    private static JsonArray objectives(Minecraft minecraft) {
        JsonArray array = new JsonArray();
        MinecraftServer server = minecraft.getSingleplayerServer();
        Scoreboard scoreboard = server != null ? server.getScoreboard() : minecraft.level != null ? minecraft.level.getScoreboard() : null;
        if (scoreboard != null) {
            scoreboard.getObjectives().stream()
                    .sorted(Comparator.comparing(Objective::getName))
                    .forEach(objective -> array.add(entry(objective.getName(), objective.getDisplayName().getString())));
        }
        return array;
    }

    /** Every PNG under textures/ in the loaded resource packs, as ids the book's pictures take. */
    private static JsonArray textures(Minecraft minecraft) {
        JsonArray array = new JsonArray();
        minecraft.getResourceManager().listResources("textures", id -> id.getPath().endsWith(".png")).keySet().stream()
                .map(ResourceLocation::toString)
                .sorted()
                .forEach(array::add);
        return array;
    }

    private static RegistryAccess registryAccess(Minecraft minecraft) {
        if (minecraft.level != null) {
            return minecraft.level.registryAccess();
        }
        if (minecraft.getConnection() != null) {
            return minecraft.getConnection().registryAccess();
        }
        return null;
    }

    private static <T> void copyKeys(RegistryAccess access, ResourceKey<? extends Registry<? extends T>> key, JsonArray into) {
        Optional<? extends Registry<? extends T>> registry = access.registry(key);
        registry.ifPresent(found -> found.keySet().forEach(id -> into.add(entry(id.toString(), id.getPath()))));
    }

    private static JsonArray ids(Iterable<ResourceLocation> keys) {
        JsonArray array = new JsonArray();
        for (ResourceLocation id : keys) {
            array.add(entry(id.toString(), id.getPath().replace('_', ' ')));
        }
        return array;
    }

    private static JsonObject entry(String id, String name) {
        JsonObject object = new JsonObject();
        object.addProperty("id", id);
        object.addProperty("name", name);
        return object;
    }
}
