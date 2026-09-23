package dev.aof.questqueen.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

public final class ItemPickerOverlay {
    public enum Mode { ITEM, ENTITY, BLOCK, FLUID, ITEM_TAG, ADVANCEMENT, BIOME, STRUCTURE, DIMENSION, STAT, LOOT }

    private static final List<ResourceLocation> VANILLA_ADVANCEMENTS = List.of(
            ResourceLocation.parse("minecraft:story/root"),
            ResourceLocation.parse("minecraft:story/mine_stone"),
            ResourceLocation.parse("minecraft:adventure/root"),
            ResourceLocation.parse("minecraft:nether/root"),
            ResourceLocation.parse("minecraft:end/root"),
            ResourceLocation.parse("minecraft:husbandry/root")
    );
    private static final List<ResourceLocation> VANILLA_LOOT = List.of(
            ResourceLocation.parse("minecraft:chests/simple_dungeon"),
            ResourceLocation.parse("minecraft:chests/abandoned_mineshaft"),
            ResourceLocation.parse("minecraft:chests/desert_pyramid"),
            ResourceLocation.parse("minecraft:chests/end_city_treasure"),
            ResourceLocation.parse("minecraft:gameplay/cat_morning_gift")
    );

    private final List<ResourceLocation> filtered = new ArrayList<>();
    private String query = "";
    private int scroll;
    public boolean open;
    public Mode mode = Mode.ITEM;
    private Consumer<ResourceLocation> onPick = id -> {
    };

    public void open(Consumer<ResourceLocation> callback) {
        open(Mode.ITEM, callback);
    }

    public void open(Mode mode, Consumer<ResourceLocation> callback) {
        this.mode = mode;
        this.onPick = callback;
        this.open = true;
        this.query = "";
        refresh();
    }

    public void close() {
        this.open = false;
    }

    public void setQuery(String query) {
        this.query = query;
        refresh();
    }

    public String query() {
        return query;
    }

    public static Mode modeFor(String kind) {
        return switch (kind) {
            case "entity" -> Mode.ENTITY;
            case "block" -> Mode.BLOCK;
            case "fluid" -> Mode.FLUID;
            case "item_tag" -> Mode.ITEM_TAG;
            case "advancement" -> Mode.ADVANCEMENT;
            case "biome" -> Mode.BIOME;
            case "structure" -> Mode.STRUCTURE;
            case "dimension" -> Mode.DIMENSION;
            case "stat" -> Mode.STAT;
            case "loot" -> Mode.LOOT;
            default -> Mode.ITEM;
        };
    }

    private void refresh() {
        filtered.clear();
        String needle = query.toLowerCase(Locale.ROOT);
        List<ResourceLocation> source = new ArrayList<>();
        collect(source);
        for (ResourceLocation id : source) {
            String name = displayName(id).toLowerCase(Locale.ROOT);
            if (needle.isEmpty() || id.toString().contains(needle) || id.getPath().contains(needle) || name.contains(needle)) {
                filtered.add(id);
            }
        }
        if (filtered.isEmpty() && query.contains(":")) {
            try {
                filtered.add(ResourceLocation.parse(query.trim()));
            } catch (Exception ignored) {
            }
        }
        scroll = 0;
    }

    private void collect(List<ResourceLocation> into) {
        Minecraft minecraft = Minecraft.getInstance();
        switch (mode) {
            case ITEM -> {
                for (Item item : BuiltInRegistries.ITEM) {
                    if (item != Items.AIR) {
                        into.add(BuiltInRegistries.ITEM.getKey(item));
                    }
                }
            }
            case ENTITY -> into.addAll(BuiltInRegistries.ENTITY_TYPE.keySet());
            case BLOCK -> into.addAll(BuiltInRegistries.BLOCK.keySet());
            case FLUID -> into.addAll(BuiltInRegistries.FLUID.keySet());
            case ITEM_TAG -> BuiltInRegistries.ITEM.getTagNames().forEach(tag -> into.add(tag.location()));
            case STAT -> into.addAll(BuiltInRegistries.CUSTOM_STAT.keySet());
            case ADVANCEMENT -> into.addAll(VANILLA_ADVANCEMENTS);
            case LOOT -> into.addAll(VANILLA_LOOT);
            case BIOME -> {
                if (minecraft.level != null) {
                    minecraft.level.registryAccess().registry(Registries.BIOME)
                            .ifPresent(registry -> into.addAll(registry.keySet()));
                }
            }
            case STRUCTURE -> {
                if (minecraft.level != null) {
                    minecraft.level.registryAccess().registry(Registries.STRUCTURE)
                            .ifPresent(registry -> into.addAll(registry.keySet()));
                }
            }
            case DIMENSION -> {
                if (minecraft.getConnection() != null) {
                    minecraft.getConnection().levels().forEach(key -> into.add(key.location()));
                } else {
                    into.add(ResourceLocation.parse("minecraft:overworld"));
                    into.add(ResourceLocation.parse("minecraft:the_nether"));
                    into.add(ResourceLocation.parse("minecraft:the_end"));
                }
            }
        }
    }

    private String displayName(ResourceLocation id) {
        if (mode == Mode.ITEM) {
            return BuiltInRegistries.ITEM.getOptional(id)
                    .map(item -> new ItemStack(item).getHoverName().getString())
                    .orElse(id.getPath());
        }
        return id.getPath().replace('_', ' ');
    }

    public void render(GuiGraphics graphics, Font font, int x, int y, int mouseX, int mouseY) {
        if (!open) {
            return;
        }
        int w = 240;
        int h = 196;
        UiDraw.begin(graphics);
        UiDraw.fill(graphics, x, y, x + w, y + h, QuestColors.CARD);
        QuestBookScreen.drawFrame(graphics, x, y, w, h, QuestColors.EDIT);
        UiDraw.end();
        graphics.drawString(font, mode + "  " + filtered.size() + " from pack", x + 6, y + 6, QuestColors.EDIT, true);
        graphics.drawString(font, query.isEmpty() ? "type id or display name" : query, x + 6, y + 18, QuestColors.MUTED, true);
        int row = 0;
        for (int i = scroll; i < filtered.size() && row < 9; i++) {
            ResourceLocation id = filtered.get(i);
            int iy = y + 32 + row * 18;
            boolean hover = mouseX >= x + 4 && mouseX < x + w - 4 && mouseY >= iy && mouseY < iy + 16;
            if (hover) {
                UiDraw.fill(graphics, x + 4, iy, x + w - 4, iy + 16, 0x33FFB020);
            }
            if (mode == Mode.ITEM) {
                BuiltInRegistries.ITEM.getOptional(id).ifPresent(item -> graphics.renderItem(new ItemStack(item), x + 6, iy));
            }
            graphics.drawString(font, displayName(id), x + 26, iy + 3, QuestColors.TEXT, false);
            row++;
        }
    }

    public boolean click(int x, int y, int mouseX, int mouseY) {
        if (!open) {
            return false;
        }
        int w = 240;
        int row = 0;
        for (int i = scroll; i < filtered.size() && row < 9; i++) {
            int iy = y + 32 + row * 18;
            if (mouseX >= x + 4 && mouseX < x + w - 4 && mouseY >= iy && mouseY < iy + 16) {
                onPick.accept(filtered.get(i));
                close();
                return true;
            }
            row++;
        }
        if (mouseX < x || mouseY < y || mouseX > x + w || mouseY > y + 196) {
            close();
            return true;
        }
        return true;
    }

    public boolean mouseScrolled(double delta) {
        if (!open) {
            return false;
        }
        scroll = (int) Math.max(0, Math.min(Math.max(0, filtered.size() - 1), scroll - (int) Math.signum(delta)));
        return true;
    }

    public boolean keyTyped(char code, int key) {
        if (!open) {
            return false;
        }
        if (key == 256) {
            close();
            return true;
        }
        if (key == 259 && !query.isEmpty()) {
            setQuery(query.substring(0, query.length() - 1));
            return true;
        }
        if (code >= 32 && code < 127) {
            setQuery(query + code);
            return true;
        }
        return true;
    }
}
