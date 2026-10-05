package dev.aof.questqueen.gametest;

import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.GridPos;
import dev.aof.questqueen.data.QuestDefinitions;
import dev.aof.questqueen.data.Tile;
import dev.aof.questqueen.data.reward.ChoiceReward;
import dev.aof.questqueen.data.reward.ItemReward;
import dev.aof.questqueen.data.reward.Reward;
import dev.aof.questqueen.data.task.CheckmarkTask;
import dev.aof.questqueen.data.task.ItemTagTask;
import dev.aof.questqueen.data.task.KillTask;
import dev.aof.questqueen.data.task.ObtainTask;
import dev.aof.questqueen.data.task.SubmitTask;
import dev.aof.questqueen.data.task.Task;
import dev.aof.questqueen.progress.ProgressService;
import dev.aof.questqueen.progress.ProgressSnapshot;
import dev.aof.questqueen.task.TaskHooks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.BeforeBatch;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Task progress on a real server: a mock {@link ServerPlayer}, the real event handlers, and SQLite.
 *
 * <p>Each test gets a fresh mock player, so a fresh solo team, so no test sees another's progress. Scans are
 * driven through {@link PlayerTickEvent.Post} at a tick the 20-tick poll accepts, and pickups through
 * {@link ItemEntity#playerTouch}, which is the path that fires the pickup event in play.
 */
@GameTestHolder("questqueen")
@PrefixGameTestTemplate(false)
public final class TaskProgressGameTests {
    private static final String BATCH = "questqueen_tasks";
    private static final String TEMPLATE = "empty";
    private static final ResourceLocation CHAPTER = ResourceLocation.parse("questqueen:gametest_tasks");
    private static final String GATHER = "gather";
    private static final String TRIBUTE = "tribute";
    private static final String HUNT = "hunt";
    private static final String PAYOUT = "payout";
    private static final String PICK = "pick";
    /** required_stage with no Progressive Stages installed: completable only by grant-all, never unlocked. */
    private static final String SEALED = "sealed";
    private static final int LOGS = 0;
    private static final int COBBLE = 1;
    private static final int DIRT = 2;

    private TaskProgressGameTests() {
    }

    /** Before any mock player exists, so installing the chapter syncs nobody mid-test. */
    @BeforeBatch(batch = BATCH)
    public static void installChapter(ServerLevel level) {
        QuestDefinitions.putChapter(new Chapter(CHAPTER, "GameTest tasks", List.of(
                tile(GATHER, 0, List.of(
                        new ItemTagTask(ResourceLocation.parse("minecraft:logs"), 16),
                        new ObtainTask(ResourceLocation.parse("minecraft:cobblestone"), 32),
                        new ObtainTask(ResourceLocation.parse("minecraft:dirt"), 16))),
                tile(TRIBUTE, 2, List.of(new SubmitTask(ResourceLocation.parse("minecraft:apple"), 1))),
                tile(HUNT, 4, List.of(new KillTask(
                        Optional.of(ResourceLocation.parse("minecraft:chicken")), Optional.empty(), 3))),
                tile(PAYOUT, 6, List.of(new CheckmarkTask())).withRewards(List.of(item("minecraft:diamond", 2))),
                tile(PICK, 8, List.of(new CheckmarkTask())).withRewards(List.of(choice())),
                new Tile(SEALED, new GridPos(10, 0), SEALED, "", Optional.empty(), List.of(new CheckmarkTask()),
                        List.of(choice()), List.of(), Optional.empty(), Optional.empty(),
                        Optional.of("questqueen_gametest_never_granted"))),
                List.of()));
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void sevenLogsStoreSevenAndSixteenComplete(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        give(player, Items.OAK_LOG, 7);
        scan(player);
        expectValue(player, GATHER, LOGS, 7);
        expectOpen(player, GATHER, LOGS);
        give(player, Items.BIRCH_LOG, 9);
        scan(player);
        expectValue(player, GATHER, LOGS, 16);
        expectCompleted(player, GATHER, LOGS);
        leave(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void eachTaskOfATileTracksItsOwnCount(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        give(player, Items.OAK_LOG, 7);
        give(player, Items.COBBLESTONE, 23);
        give(player, Items.DIRT, 27);
        scan(player);
        expectValue(player, GATHER, LOGS, 7);
        expectValue(player, GATHER, COBBLE, 23);
        expectValue(player, GATHER, DIRT, 16);
        expectOpen(player, GATHER, LOGS);
        expectOpen(player, GATHER, COBBLE);
        expectCompleted(player, GATHER, DIRT);

        take(player, Items.OAK_LOG, 4);
        take(player, Items.DIRT, 27);
        scan(player);
        expectValue(player, GATHER, LOGS, 3);
        expectCompleted(player, GATHER, DIRT);
        leave(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aPickupCountsBeforeTheNextPoll(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.tickCount = 7;
        ItemEntity drop = new ItemEntity(helper.getLevel(), player.getX(), player.getY(), player.getZ(),
                new ItemStack(Items.OAK_LOG, 5));
        drop.setNoPickUpDelay();
        helper.getLevel().addFreshEntity(drop);
        drop.playerTouch(player);
        check(drop.isRemoved(), "the mock player did not pick the logs up");
        expectValue(player, GATHER, LOGS, 5);
        leave(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void submitWaitsForTheHandInAndConsumes(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        give(player, Items.APPLE, 2);
        scan(player);
        expectOpen(player, TRIBUTE, 0);
        check(count(player, Items.APPLE) == 2, "a scan must not take submit items");
        check(TaskHooks.trySubmit(player, CHAPTER, TRIBUTE, 0), "hand-in was refused");
        expectCompleted(player, TRIBUTE, 0);
        check(count(player, Items.APPLE) == 1, "hand-in must consume exactly one apple, left "
                + count(player, Items.APPLE));
        leave(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void oneKillCountsOnce(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Chicken chicken = helper.spawn(EntityType.CHICKEN, new BlockPos(0, 1, 0));
        chicken.hurt(helper.getLevel().damageSources().playerAttack(player), 1000f);
        check(chicken.isDeadOrDying(), "the chicken survived");
        expectValue(player, HUNT, 0, 1);
        leave(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void partialProgressSurvivesLeaving(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        give(player, Items.OAK_LOG, 7);
        scan(player);
        leave(player);
        expectValue(player, GATHER, LOGS, 7);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void claimGrantsOnceAndRecordsTheClaim(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        check(TaskHooks.trySubmit(player, CHAPTER, PAYOUT, 0), "checkmark was refused");
        check(ProgressService.claimTileRewards(player, CHAPTER, PAYOUT), "first claim was refused");
        check(count(player, Items.DIAMOND) == 2, "claim must grant 2 diamonds, got " + count(player, Items.DIAMOND));
        check(snapshot(player).taskCompleted(ProgressSnapshot.questKey(CHAPTER, PAYOUT), "claimed"),
                "the claim must be recorded");
        check(!ProgressService.claimTileRewards(player, CHAPTER, PAYOUT), "second claim must be refused");
        check(count(player, Items.DIAMOND) == 2, "second claim granted again: " + count(player, Items.DIAMOND));
        leave(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aChoiceIsPickedOnce(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        check(TaskHooks.trySubmit(player, CHAPTER, PICK, 0), "checkmark was refused");
        ProgressService.claimChoice(player, CHAPTER, PICK, 1);
        ProgressService.claimChoice(player, CHAPTER, PICK, 0);
        check(count(player, Items.EMERALD) == 1, "option 1 must grant one emerald, got " + count(player, Items.EMERALD));
        check(count(player, Items.DIAMOND) == 0, "a second pick must grant nothing");
        leave(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aLockedChoiceCannotBePicked(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        ProgressService.grantAll(player);
        check(snapshot(player).tileCompleted(CHAPTER.toString(), SEALED), "grant-all should complete the sealed tile");
        ProgressService.claimChoice(player, CHAPTER, SEALED, 0);
        check(count(player, Items.DIAMOND) == 0, "a pick through a locked tile granted " + count(player, Items.DIAMOND));
        check(!snapshot(player).taskCompleted(ProgressSnapshot.questKey(CHAPTER, SEALED), "claimed"),
                "a refused pick must not burn the claim");
        leave(player);
        helper.succeed();
    }

    private static ItemReward item(String id, int count) {
        return new ItemReward(ResourceLocation.parse(id), count);
    }

    private static Reward choice() {
        return new ChoiceReward(List.of(item("minecraft:diamond", 1), item("minecraft:emerald", 1)));
    }

    private static Tile tile(String id, int x, List<Task> tasks) {
        return Tile.blank(id, x, 0).withPos(new GridPos(x, 0)).withTitle(id).withTasks(tasks);
    }

    /** One general poll: {@code tickCount} lands on the 20-tick interval the scan accepts. */
    private static void scan(ServerPlayer player) {
        player.tickCount = 20;
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(player));
    }

    /** Logout drops every in-memory cache, so the next read comes from the database. */
    private static void leave(ServerPlayer player) {
        player.server.getPlayerList().remove(player);
    }

    private static void give(ServerPlayer player, Item item, int count) {
        player.getInventory().add(new ItemStack(item, count));
    }

    private static void take(ServerPlayer player, Item item, int count) {
        int left = count;
        for (int i = 0; i < player.getInventory().getContainerSize() && left > 0; i++) {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item)) {
                int taken = Math.min(left, stack.getCount());
                stack.shrink(taken);
                left -= taken;
            }
        }
    }

    private static int count(ServerPlayer player, Item item) {
        return player.getInventory().countItem(item);
    }

    private static void expectValue(ServerPlayer player, String tile, int task, int expected) {
        int actual = snapshot(player).value(ProgressSnapshot.questKey(CHAPTER, tile), ProgressSnapshot.taskKey(task));
        check(actual == expected, tile + " task " + task + ": expected " + expected + ", stored " + actual);
    }

    private static void expectCompleted(ServerPlayer player, String tile, int task) {
        check(snapshot(player).taskCompleted(ProgressSnapshot.questKey(CHAPTER, tile), ProgressSnapshot.taskKey(task)),
                tile + " task " + task + " should be complete");
    }

    private static void expectOpen(ServerPlayer player, String tile, int task) {
        check(!snapshot(player).taskCompleted(ProgressSnapshot.questKey(CHAPTER, tile), ProgressSnapshot.taskKey(task)),
                tile + " task " + task + " should still be open");
    }

    private static ProgressSnapshot snapshot(ServerPlayer player) {
        ProgressService.invalidatePlayer(player.getUUID());
        return ProgressService.snapshot(player);
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new GameTestAssertException(message);
        }
    }
}
