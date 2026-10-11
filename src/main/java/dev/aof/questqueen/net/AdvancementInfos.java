package dev.aof.questqueen.net;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.aof.questqueen.QuestQueen;
import dev.aof.questqueen.data.AdvancementSummary;
import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.QuestPack;
import dev.aof.questqueen.data.Tile;
import dev.aof.questqueen.data.task.Task;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.advancements.Criterion;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryOps;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Builds {@link AdvancementInfoS2C} on the server from the loaded advancements. */
public final class AdvancementInfos {
    private static final AdvancementSummary.Names NAMES = new AdvancementSummary.Names() {
        @Override
        public Component item(ResourceLocation id) {
            return BuiltInRegistries.ITEM.getOptional(id).map(item -> (Component) Component.translatable(item.getDescriptionId()))
                    .orElseGet(() -> Component.literal(AdvancementSummary.humanize(id)));
        }

        @Override
        public Component block(ResourceLocation id) {
            return BuiltInRegistries.BLOCK.getOptional(id).map(block -> (Component) Component.translatable(block.getDescriptionId()))
                    .orElseGet(() -> Component.literal(AdvancementSummary.humanize(id)));
        }

        @Override
        public Component biome(ResourceLocation id) {
            return Component.translatableWithFallback("biome." + id.getNamespace() + "." + id.getPath(),
                    AdvancementSummary.humanize(id));
        }

        @Override
        public Component structure(ResourceLocation id) {
            return Component.literal(AdvancementSummary.humanize(id));
        }

        @Override
        public Component dimension(ResourceLocation id) {
            return Component.literal(AdvancementSummary.humanize(id));
        }
    };

    private AdvancementInfos() {
    }

    /** Every advancement id a quest task in the pack points at. */
    public static Set<ResourceLocation> referenced(QuestPack pack) {
        Set<ResourceLocation> ids = new LinkedHashSet<>();
        for (Chapter chapter : pack.chapters()) {
            for (Tile tile : chapter.tiles()) {
                for (Task task : tile.tasks()) {
                    task.advancementId().ifPresent(ids::add);
                }
            }
        }
        return ids;
    }

    public static AdvancementInfoS2C build(MinecraftServer server, Set<ResourceLocation> ids) {
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        List<AdvancementInfoS2C.Entry> entries = new ArrayList<>();
        for (ResourceLocation id : ids) {
            AdvancementHolder holder = server.getAdvancements().get(id);
            if (holder == null) {
                entries.add(new AdvancementInfoS2C.Entry(id, Component.literal(AdvancementSummary.humanize(id)),
                        List.of(Component.translatableWithFallback("questqueen.adv.missing",
                                "No advancement with this id is loaded")), ItemStack.EMPTY));
                continue;
            }
            var display = holder.value().display();
            if (display.isPresent()) {
                entries.add(new AdvancementInfoS2C.Entry(id, display.get().getTitle(),
                        List.of(display.get().getDescription()), display.get().getIcon().copy()));
                continue;
            }
            List<Component> lines = new ArrayList<>();
            // In the order the advancement lists its requirements (the criteria map has no stable order).
            Set<String> order = new LinkedHashSet<>();
            holder.value().requirements().requirements().forEach(order::addAll);
            order.addAll(new java.util.TreeSet<>(holder.value().criteria().keySet()));
            for (String name : order) {
                Criterion<?> criterion = holder.value().criteria().get(name);
                if (criterion == null) {
                    continue;
                }
                try {
                    JsonElement json = Criterion.CODEC.encodeStart(ops, criterion).getOrThrow();
                    lines.add(AdvancementSummary.criterion(json.getAsJsonObject(), NAMES));
                } catch (RuntimeException unreadable) {
                    QuestQueen.LOGGER.debug("Cannot describe criterion {} of {}: {}", name, id, unreadable.toString());
                }
            }
            List<Component> described = lines.isEmpty() ? List.of(Component.literal(AdvancementSummary.humanize(id)))
                    : AdvancementSummary.criteria(lines, holder.value().requirements().requirements());
            entries.add(new AdvancementInfoS2C.Entry(id, described.getFirst(),
                    List.copyOf(described.subList(1, described.size())), ItemStack.EMPTY));
        }
        return new AdvancementInfoS2C(entries);
    }
}
