package dev.aof.questqueen.task;

import dev.aof.questqueen.QuestQueen;
import dev.aof.questqueen.api.QuestNpcApi;
import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.QuestDefinitions;
import dev.aof.questqueen.data.Tile;
import dev.aof.questqueen.data.task.LocationTask;
import dev.aof.questqueen.data.task.Task;
import dev.aof.questqueen.progress.ProgressService;
import dev.aof.questqueen.progress.TeamService;
import dev.aof.questqueen.progress.ProgressSnapshot;
import net.minecraft.advancements.AdvancementHolder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.player.AdvancementEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class TaskHooks {
    private static final int OBSERVATION_INTERVAL = 20;
    private static final int GENERAL_INTERVAL = 20;
    private static final int STRUCTURE_INTERVAL = 40;
    private static final int STRUCTURE_MOVE_THRESHOLD = 16;

    private static volatile int packEpoch;
    private static final Map<UUID, PlayerTaskIndex> INDEX = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockPos> LAST_STRUCTURE_POS = new ConcurrentHashMap<>();
    /** Baseline custom-stat value when a stat task first becomes active, so progress is quest-relative. */
    private static final Map<String, Integer> STAT_BASELINE = new ConcurrentHashMap<>();

    private TaskHooks() {
    }

    /**
     * Call when quest definitions change so per-player indexes rebuild — and check the pack that just
     * became current against the kill-task contract. Every pack change funnels through here
     * ({@code QuestDefinitions.apply}), so this is where a malformed kill task gets its load-time report
     * instead of dying in silence during play.
     */
    public static void invalidateIndexes() {
        packEpoch++;
        INDEX.clear();
        LAST_STRUCTURE_POS.clear();
        STAT_BASELINE.clear();
        reportMalformedKillTasks();
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int tick = player.tickCount;
        if (tick % OBSERVATION_INTERVAL == 0) {
            scanObservation(player);
        }
        if (tick % STRUCTURE_INTERVAL == 0) {
            scanStructures(player);
        }
        if (tick % GENERAL_INTERVAL != 0) {
            return;
        }
        scanInventory(player);
        scanLocation(player);
        scanStats(player);
        scanAdvancement(player);
        scanRaid(player);
        scanWorld(player);
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            LAST_STRUCTURE_POS.remove(player.getUUID());
            scanWorld(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            INDEX.remove(player.getUUID());
            LAST_STRUCTURE_POS.remove(player.getUUID());
            String prefix = player.getUUID() + "|";
            STAT_BASELINE.keySet().removeIf(key -> key.startsWith(prefix));
        }
    }

    @SubscribeEvent
    public static void onPickup(ItemEntityPickupEvent.Post event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            scanInventory(player);
        }
    }

    @SubscribeEvent
    public static void onDeath(LivingDeathEvent event) {
        Entity source = event.getSource().getEntity();
        if (!(source instanceof ServerPlayer)) {
            source = event.getSource().getDirectEntity();
        }
        if (!(source instanceof ServerPlayer player)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        ResourceLocation entityId = BuiltInRegistries.ENTITY_TYPE.getKey(victim.getType());
        boolean inRaid = player.serverLevel().getRaidAt(player.blockPosition()) != null;
        forEachOfTypes(player, List.of("kill", "raid"), (chapter, tile, index, task) -> {
            if ("kill".equals(task.type()) && killMatches(victim, entityId, task)) {
                ProgressService.increment(player, chapter.id(), tile.id(), index, 1);
            }
            if ("raid".equals(task.type()) && inRaid) {
                ProgressService.increment(player, chapter.id(), tile.id(), index, 1);
            }
        });
    }

    @SubscribeEvent
    public static void onAdvancement(AdvancementEvent.AdvancementEarnEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        ResourceLocation id = event.getAdvancement().id();
        // Chapter and tile gates can test advancements, and they are baked into the cached snapshot. Drop it so
        // a gate this advancement opens is seen now, not after the next unrelated progress write.
        // Recipe unlocks are advancements too and arrive in bursts; no gate is written against them.
        if (!id.getPath().startsWith("recipes/")) {
            ProgressService.syncTeam(player.server, TeamService.ensureSolo(player));
        }
        forEachOfTypes(player, List.of("advancement"), (chapter, tile, index, task) -> {
            if (id.equals(task.advancementId().orElse(null))) {
                ProgressService.setCompleted(player, chapter.id(), tile.id(), index);
            }
        });
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onUseBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getLevel().isClientSide() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // The event fires once per hand when the main hand passes; count the click once.
        if (event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        Block block = player.level().getBlockState(event.getPos()).getBlock();
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
        forEachOfTypes(player, List.of("interact_block"), (chapter, tile, index, task) -> {
            if (id.equals(task.blockId().orElse(null))) {
                ProgressService.increment(player, chapter.id(), tile.id(), index, 1);
            }
        });
    }

    @SubscribeEvent
    public static void onUseEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || event.getHand() != InteractionHand.MAIN_HAND) {
            return;
        }
        // One target has one type, so match interact_entity directly instead of walking every task.
        // onUseEntity fires for every right-click.
        ResourceLocation entityType = BuiltInRegistries.ENTITY_TYPE.getKey(event.getTarget().getType());
        forEachOfTypes(player, List.of("interact_entity"), (chapter, tile, index, task) -> {
            if (entityType.equals(task.entityId().orElse(null))) {
                ProgressService.increment(player, chapter.id(), tile.id(), index, 1);
            }
        });
        // npc_dialog / trigger talk credit: named NPCs match by custom name, unnamed by entity type.
        Set<String> talked = new LinkedHashSet<>();
        if (event.getTarget().getCustomName() != null) {
            talked.add(event.getTarget().getCustomName().getString());
        }
        talked.add(entityType.toString());
        for (String npcId : talked) {
            QuestNpcApi.onTalked(player, npcId);
        }
    }

    public static boolean trySubmit(ServerPlayer player, ResourceLocation chapterId, String tileId, int taskIndex) {
        return QuestDefinitions.chapter(chapterId).flatMap(chapter -> chapter.tile(tileId)).map(tile -> {
            if (taskIndex < 0 || taskIndex >= tile.tasks().size()) {
                return false;
            }
            Task task = tile.tasks().get(taskIndex);
            // The tile can still be gated (or already be complete) even though the client asked to
            // submit. Check that up front: setCompleted refuses those, so consuming the cost first
            // burned a player's XP levels or handed over their items for no progress.
            if (!ProgressService.canCompleteTask(player, chapterId, tileId, taskIndex)) {
                return false;
            }
            if ("checkmark".equals(task.type())) {
                return ProgressService.setCompleted(player, chapterId, tileId, taskIndex);
            }
            if ("xp_levels".equals(task.type())) {
                if (player.experienceLevel < task.required()) {
                    return false;
                }
                int spent = task.required();
                player.giveExperienceLevels(-spent);
                if (ProgressService.setCompleted(player, chapterId, tileId, taskIndex)) {
                    return true;
                }
                player.giveExperienceLevels(spent); // write refused after the check — refund
                return false;
            }
            if (!"submit".equals(task.type()) || task.itemId().isEmpty()) {
                return false;
            }
            Item item = BuiltInRegistries.ITEM.getOptional(task.itemId().get()).orElse(null);
            if (item == null) {
                return false;
            }
            int needed = task.required();
            if (countItem(player, item, null) < needed) {
                return false;
            }
            int consumed = 0;
            for (int i = 0; i < player.getInventory().getContainerSize() && consumed < needed; i++) {
                ItemStack stack = player.getInventory().getItem(i);
                if (stack.is(item)) {
                    int take = Math.min(needed - consumed, stack.getCount());
                    stack.shrink(take);
                    consumed += take;
                }
            }
            if (ProgressService.setCompleted(player, chapterId, tileId, taskIndex)) {
                return true;
            }
            if (consumed > 0) {
                // Hand the taken stacks back rather than deleting them (leftovers go to the player).
                player.getInventory().add(new ItemStack(item, consumed));
            }
            return false;
        }).orElse(false);
    }

    private static void scanInventory(ServerPlayer player) {
        forEachOfTypes(player, List.of("obtain", "item_tag"), (chapter, tile, index, task) -> {
            if ("obtain".equals(task.type()) && task.itemId().isPresent()) {
                BuiltInRegistries.ITEM.getOptional(task.itemId().get()).ifPresent(item -> {
                    int have = countItem(player, item, null);
                    if (have >= task.required()) {
                        ProgressService.setCompleted(player, chapter.id(), tile.id(), index);
                    }
                });
            }
            if ("item_tag".equals(task.type()) && task.tagId().isPresent()) {
                TagKey<Item> tag = TagKey.create(Registries.ITEM, task.tagId().get());
                int have = countItem(player, null, tag);
                if (have >= task.required()) {
                    ProgressService.setCompleted(player, chapter.id(), tile.id(), index);
                }
            }
        });
    }

    private static void scanLocation(ServerPlayer player) {
        forEachOfTypes(player, List.of("location"), (chapter, tile, index, task) -> {
            if (task instanceof LocationTask location) {
                if (!location.dimension().isEmpty() && !player.level().dimension().location().toString().equals(location.dimension())) {
                    return;
                }
                double dx = player.getX() - location.x();
                double dy = player.getY() - location.y();
                double dz = player.getZ() - location.z();
                if (dx * dx + dy * dy + dz * dz <= location.radius() * location.radius()) {
                    ProgressService.setCompleted(player, chapter.id(), tile.id(), index);
                }
            }
        });
    }

    private static void scanStructures(ServerPlayer player) {
        BlockPos pos = player.blockPosition();
        BlockPos last = LAST_STRUCTURE_POS.get(player.getUUID());
        if (last != null && last.distManhattan(pos) < STRUCTURE_MOVE_THRESHOLD) {
            return;
        }
        LAST_STRUCTURE_POS.put(player.getUUID(), pos.immutable());
        ServerLevel level = player.serverLevel();
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        forEachOfTypes(player, List.of("visit_structure"), (chapter, tile, index, task) -> {
            task.structureId().ifPresent(id -> {
                ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, id);
                Holder<Structure> holder = registry.getHolder(key).orElse(null);
                if (holder == null) {
                    return;
                }
                if (isInsideStructure(level, holder.value(), pos)) {
                    ProgressService.setCompleted(player, chapter.id(), tile.id(), index);
                }
            });
        });
    }

    /**
     * True when {@code pos} is inside a matching structure start BB or any piece BB.
     * Prefers StructureManager; falls back to already-loaded nearby chunk starts only.
     */
    private static boolean isInsideStructure(ServerLevel level, Structure structure, BlockPos pos) {
        var structures = level.structureManager();
        if (occupiesStructure(level, structures.getStructureWithPieceAt(pos, structure), pos)) {
            return true;
        }
        if (occupiesStructure(level, structures.getStructureAt(pos, structure), pos)) {
            return true;
        }
        ChunkPos origin = new ChunkPos(pos);
        for (int dx = -3; dx <= 3; dx++) {
            for (int dz = -3; dz <= 3; dz++) {
                if (!level.hasChunk(origin.x + dx, origin.z + dz)) {
                    continue;
                }
                ChunkAccess chunk = level.getChunk(origin.x + dx, origin.z + dz);
                StructureStart start = chunk.getStartForStructure(structure);
                if (occupiesStructure(level, start, pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean occupiesStructure(ServerLevel level, StructureStart start, BlockPos pos) {
        if (start == null || !start.isValid()) {
            return false;
        }
        if (start.getBoundingBox().isInside(pos)) {
            return true;
        }
        if (level.structureManager().structureHasPieceAt(pos, start)) {
            return true;
        }
        for (var piece : start.getPieces()) {
            if (piece.getBoundingBox().isInside(pos)) {
                return true;
            }
        }
        return false;
    }

    private static void scanStats(ServerPlayer player) {
        forEachOfTypes(player, List.of("stat"), (chapter, tile, index, task) -> task.statId().ifPresent(id -> {
            int lifetime = readMovementAwareStat(player, id);
            if (lifetime < 0) {
                return;
            }
            String baselineKey = player.getUUID() + "|" + ProgressSnapshot.questKey(chapter.id(), tile.id()) + "|" + index;
            Integer baseline = STAT_BASELINE.get(baselineKey);
            if (baseline == null) {
                // The baseline is memory-only and is dropped on logout and every pack apply. Seed it from the
                // progress already stored for this task, or a relog would reset the task to zero.
                String questKey = ProgressSnapshot.questKey(chapter.id(), tile.id());
                int stored = ProgressService.snapshot(player).value(questKey, ProgressSnapshot.taskKey(index));
                baseline = lifetime - Math.max(0, stored);
                STAT_BASELINE.put(baselineKey, baseline);
            }
            int gained = questRelativeGain(lifetime, baseline);
            ProgressService.setTaskValue(player, chapter.id(), tile.id(), index, gained);
            if (gained >= task.required()) {
                STAT_BASELINE.remove(baselineKey);
            }
        }));
    }

    /**
     * Progress a stat task has made since it became active.
     *
     * <p>Quest-relative rather than lifetime: "walk 1000 cm" means from unlock, so a player with 40 km of
     * lifetime travel is not instantly credited. The baseline is captured the first time the task is seen and
     * a stat can never go backwards, so the result clamps at zero.
     */
    static int questRelativeGain(int lifetime, int baseline) {
        return Math.max(0, lifetime - baseline);
    }

    /**
     * Read a custom stat. {@code walk_one_cm} also includes sprint + crouch: vanilla awards those
     * separately, and nearly all travel is sprinting, so a bare walk counter looks "broken".
     *
     * @return lifetime value, or {@code -1} when the id is not a registered custom stat
     */
    private static int readMovementAwareStat(ServerPlayer player, ResourceLocation id) {
        Optional<ResourceLocation> registered = BuiltInRegistries.CUSTOM_STAT.getOptional(id);
        if (registered.isEmpty()) {
            return -1;
        }
        int value = player.getStats().getValue(Stats.CUSTOM.get(registered.get()));
        if (Stats.WALK_ONE_CM.equals(registered.get())) {
            value += player.getStats().getValue(Stats.CUSTOM.get(Stats.SPRINT_ONE_CM));
            value += player.getStats().getValue(Stats.CUSTOM.get(Stats.CROUCH_ONE_CM));
        }
        return value;
    }

    /**
     * Credit advancement tasks the player has already earned. {@code AdvancementEarnEvent} fires once, and
     * both {@code forEachOfTypes} and {@code PlayerTaskIndex.build} drop locked tiles — so an advancement
     * earned before its tile unlocked used to be stranded forever. Advancements are monotonic, so
     * re-checking is safe and self-heals that case.
     */
    private static void scanAdvancement(ServerPlayer player) {
        forEachOfTypes(player, List.of("advancement"), (chapter, tile, index, task) ->
                task.advancementId().ifPresent(id -> {
                    AdvancementHolder holder = player.server.getAdvancements().get(id);
                    if (holder != null && player.getAdvancements().getOrStartProgress(holder).isDone()) {
                        ProgressService.setCompleted(player, chapter.id(), tile.id(), index);
                    }
                }));
    }

    private static void scanRaid(ServerPlayer player) {
        var raid = player.serverLevel().getRaidAt(player.blockPosition());
        if (raid == null || !raid.isVictory()) {
            return;
        }
        forEachOfTypes(player, List.of("raid"), (chapter, tile, index, task) ->
                ProgressService.setCompleted(player, chapter.id(), tile.id(), index));
    }

    private static void scanWorld(ServerPlayer player) {
        ResourceLocation dimension = player.level().dimension().location();
        ResourceLocation biome = player.level().getBiome(player.blockPosition()).unwrapKey()
                .map(key -> key.location()).orElse(null);
        forEachOfTypes(player, List.of("visit_dimension", "visit_biome", "fluid"), (chapter, tile, index, task) -> {
            if ("visit_dimension".equals(task.type()) && dimension.equals(task.dimensionId().orElse(null))) {
                ProgressService.setCompleted(player, chapter.id(), tile.id(), index);
            }
            if ("visit_biome".equals(task.type()) && biome != null && biome.equals(task.biomeId().orElse(null))) {
                ProgressService.setCompleted(player, chapter.id(), tile.id(), index);
            }
            if ("fluid".equals(task.type()) && task.fluidId().isPresent()) {
                var fluid = BuiltInRegistries.FLUID.getOptional(task.fluidId().get()).orElse(null);
                if (fluid != null && player.level().getFluidState(player.blockPosition()).is(fluid)) {
                    ProgressService.setCompleted(player, chapter.id(), tile.id(), index);
                }
            }
        });
    }

    private static void scanObservation(ServerPlayer player) {
        PlayerTaskIndex index = indexFor(player);
        if (!index.hasType("observation")) {
            return;
        }
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        Vec3 end = eye.add(look.scale(32.0));
        HitResult blockHit = player.level().clip(new ClipContext(
                eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        var box = player.getBoundingBox().expandTowards(look.scale(32.0)).inflate(1.0);
        var entityHit = ProjectileUtil.getEntityHitResult(player, eye, end, box, entity -> true, 32.0 * 32.0);
        forEachOfTypes(player, List.of("observation"), (chapter, tile, taskIndex, task) -> {
            if (blockHit.getType() == HitResult.Type.BLOCK && task.blockId().isPresent()) {
                BlockPos pos = ((BlockHitResult) blockHit).getBlockPos();
                ResourceLocation id = BuiltInRegistries.BLOCK.getKey(player.level().getBlockState(pos).getBlock());
                if (id.equals(task.blockId().get())) {
                    ProgressService.setCompleted(player, chapter.id(), tile.id(), taskIndex);
                }
            }
            if (entityHit != null && task.entityId().isPresent()
                    && BuiltInRegistries.ENTITY_TYPE.getKey(entityHit.getEntity().getType()).equals(task.entityId().get())) {
                ProgressService.setCompleted(player, chapter.id(), tile.id(), taskIndex);
            }
        });
    }

    /**
     * Kill-task matching contract (KILL-2, t_a1272cf2): a kill task names its target with {@code entity}
     * (an exact entity type) and/or {@code tag} (an ENTITY-TYPE tag — the same field name carries an item
     * tag on item types, so a kill task given an item tag matches nothing). Each selector a task carries is
     * independently sufficient: a kill is credited when the victim matches any selector, and no selector is
     * ever silently ignored. A task with NEITHER selector is malformed — it can never match anything. That
     * case used to be invisible: the task rendered, stayed at KILL 0/N forever, and nothing said why.
     * {@link #reportMalformedKillTasks()} now names every such task at pack-apply time, so the defect is
     * loud at load instead of silent in play.
     */
    private static boolean killMatches(LivingEntity victim, ResourceLocation entityId, Task task) {
        if (task.tagId().isPresent()
                && victim.getType().is(TagKey.create(Registries.ENTITY_TYPE, task.tagId().get()))) {
            return true;
        }
        return task.entityId().isPresent() && entityId.equals(task.entityId().get());
    }

    /**
     * One ERROR line per malformed kill task, on every pack apply. The contract: a kill task must carry
     * {@code entity} and/or {@code tag}, and a named {@code entity} must be a registered entity type.
     * Every line starts with the stable prefix "kill-task contract violation" so a pack author (and a
     * test) can grep the load log and the defect cannot come back silently. Tag existence is not checked:
     * the entity-type tag bindings are not reliably readable from the apply path, so an undefined tag is
     * documented rather than rejected — an empty tag matches nothing, same as any other empty tag.
     */
    private static void reportMalformedKillTasks() {
        for (Chapter chapter : QuestDefinitions.chapters()) {
            for (Tile tile : chapter.tiles()) {
                List<Task> tasks = tile.tasks();
                for (int index = 0; index < tasks.size(); index++) {
                    // Copy for the error lambdas below: a loop variable is not effectively final.
                    int taskIndex = index;
                    Task task = tasks.get(index);
                    if (!"kill".equals(task.type())) {
                        continue;
                    }
                    if (task.entityId().isEmpty() && task.tagId().isEmpty()) {
                        QuestQueen.LOGGER.error("kill-task contract violation: {}/{} task[{}] carries neither"
                                + " \"entity\" nor \"tag\" — no kill can ever match it. Give it an exact entity"
                                + " type or an entity-type tag.", chapter.id(), tile.id(), taskIndex);
                        continue;
                    }
                    task.entityId().filter(id -> BuiltInRegistries.ENTITY_TYPE.getOptional(id).isEmpty())
                            .ifPresent(id -> QuestQueen.LOGGER.error("kill-task contract violation: {}/{} task[{}]"
                                    + " names entity {} which is not a registered entity type — no kill can ever"
                                    + " match it.", chapter.id(), tile.id(), taskIndex, id));
                }
            }
        }
    }

    private static int countItem(ServerPlayer player, Item item, TagKey<Item> tag) {
        int total = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (item != null && stack.is(item)) {
                total += stack.getCount();
            } else if (tag != null && stack.is(tag)) {
                total += stack.getCount();
            }
        }
        return total;
    }

    private static void forEachOfTypes(ServerPlayer player, List<String> types, QuadConsumer consumer) {
        PlayerTaskIndex index = indexFor(player);
        ProgressSnapshot snap = ProgressService.snapshot(player);
        // isUnlocked rebuilds the chapter's completed set and may query SQLite for flag gates. A tile with several
        // tasks used to pay that once per task on every scan; evaluate it once per tile per scan instead.
        Map<Tile, Boolean> unlocked = null;
        for (String type : types) {
            List<ActiveTask> tasks = index.byType.get(type);
            if (tasks == null || tasks.isEmpty()) {
                continue;
            }
            for (ActiveTask active : tasks) {
                if (!stillOpen(snap, active.chapter, active.tile, active.taskIndex)) {
                    continue;
                }
                if (unlocked == null) {
                    unlocked = new IdentityHashMap<>();
                }
                if (!unlocked.computeIfAbsent(active.tile,
                        tile -> ProgressService.isUnlocked(player, active.chapter, tile))) {
                    continue;
                }
                consumer.accept(active.chapter, active.tile, active.taskIndex, active.task);
            }
        }
    }

    /**
     * True while this exact task is still worth scanning: neither its tile nor the task index itself is
     * complete. Extracted from {@code forEachOfTypes} so the gate that every one of the nineteen task types
     * passes through is covered by a test rather than only by play.
     */
    static boolean stillOpen(ProgressSnapshot snap, Chapter chapter, Tile tile, int taskIndex) {
        String questKey = ProgressSnapshot.questKey(chapter.id(), tile.id());
        if (snap.completedTiles().contains(questKey)) {
            return false;
        }
        return !snap.completedTasks().contains(questKey + "/" + taskIndex);
    }

    private static PlayerTaskIndex indexFor(ServerPlayer player) {
        ProgressSnapshot snap = ProgressService.snapshot(player);
        PlayerTaskIndex existing = INDEX.get(player.getUUID());
        if (existing != null
                && existing.packEpoch == packEpoch
                && existing.progress == snap) {
            return existing;
        }
        PlayerTaskIndex rebuilt = PlayerTaskIndex.build(snap);
        INDEX.put(player.getUUID(), rebuilt);
        return rebuilt;
    }

    private record ActiveTask(Chapter chapter, Tile tile, int taskIndex, Task task) {
    }

    private static final class PlayerTaskIndex {
        private final int packEpoch;
        private final ProgressSnapshot progress;
        private final Map<String, List<ActiveTask>> byType;

        private PlayerTaskIndex(int packEpoch, ProgressSnapshot progress, Map<String, List<ActiveTask>> byType) {
            this.packEpoch = packEpoch;
            this.progress = progress;
            this.byType = byType;
        }

        private boolean hasType(String type) {
            List<ActiveTask> list = byType.get(type);
            return list != null && !list.isEmpty();
        }

        private static PlayerTaskIndex build(ProgressSnapshot snap) {
            Map<String, List<ActiveTask>> byType = new HashMap<>();
            for (Chapter chapter : QuestDefinitions.chapters()) {
                if (!snap.unlockedChapters().contains(chapter.id().toString())) {
                    continue;
                }
                for (Tile tile : chapter.tiles()) {
                    String questKey = ProgressSnapshot.questKey(chapter.id(), tile.id());
                    if (snap.completedTiles().contains(questKey)) {
                        continue;
                    }
                    for (int i = 0; i < tile.tasks().size(); i++) {
                        String taskKey = questKey + "/" + i;
                        if (snap.completedTasks().contains(taskKey)) {
                            continue;
                        }
                        Task task = tile.tasks().get(i);
                        byType.computeIfAbsent(task.type(), key -> new ArrayList<>())
                                .add(new ActiveTask(chapter, tile, i, task));
                    }
                }
            }
            return new PlayerTaskIndex(TaskHooks.packEpoch, snap, byType);
        }
    }

    @FunctionalInterface
    private interface QuadConsumer {
        void accept(Chapter chapter, Tile tile, int index, Task task);
    }
}
