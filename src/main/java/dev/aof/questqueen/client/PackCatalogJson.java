package dev.aof.questqueen.client;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.core.Registry;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

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
                    dimensions.add(entry(key.location().toString(), key.location().getPath())));
        } else {
            dimensions.add(entry("minecraft:overworld", "overworld"));
            dimensions.add(entry("minecraft:the_nether", "the_nether"));
            dimensions.add(entry("minecraft:the_end", "the_end"));
        }
        root.add("biomes", biomes);
        root.add("structures", structures);
        root.add("dimensions", dimensions);

        JsonArray advancements = new JsonArray();
        for (String id : new String[]{
                "minecraft:story/root", "minecraft:story/mine_stone", "minecraft:adventure/root",
                "minecraft:nether/root", "minecraft:end/root", "minecraft:husbandry/root"
        }) {
            advancements.add(entry(id, id));
        }
        root.add("advancements", advancements);
        JsonArray loot = new JsonArray();
        for (String id : new String[]{
                "minecraft:chests/simple_dungeon", "minecraft:chests/abandoned_mineshaft",
                "minecraft:chests/desert_pyramid", "minecraft:chests/end_city_treasure"
        }) {
            loot.add(entry(id, id));
        }
        root.add("loot", loot);
        return root.toString();
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
