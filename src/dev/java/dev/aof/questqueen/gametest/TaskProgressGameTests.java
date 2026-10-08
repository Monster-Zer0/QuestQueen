package dev.aof.questqueen.gametest;

import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.Gate;
import dev.aof.questqueen.data.GateCondition;
import dev.aof.questqueen.data.GateOp;
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
import dev.aof.questqueen.compat.FtbTeamsCompat;
import dev.aof.questqueen.compat.Stages;
import dev.aof.questqueen.progress.ProgressDatabase;
import dev.aof.questqueen.progress.ProgressSnapshot;
import dev.aof.questqueen.progress.TeamService;
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

import java.lang.reflect.Method;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Collection;
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
    private static final String PICK3 = "pick3";
    private static final ResourceLocation FORK = ResourceLocation.parse("questqueen:gametest_fork");
    private static final ResourceLocation AFTER_FORK = ResourceLocation.parse("questqueen:gametest_after_fork");
    private static final ResourceLocation STAGES = ResourceLocation.parse("questqueen:gametest_stages");
    private static final ResourceLocation STAGE_CHAPTER = ResourceLocation.parse("questqueen:gametest_stage_chapter");
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
                        Optional.of("questqueen_gametest_never_granted")),
                tile(PICK3, 12, List.of(new CheckmarkTask())).withRewards(List.of(new ChoiceReward(List.of(
                        item("minecraft:diamond", 1), item("minecraft:emerald", 8), item("minecraft:iron_ingot", 16)))))),
                List.of()));
        // hub forks (XOR) into left / right; "before" is NOT-gated on hub; end opens from either branch (OR).
        QuestDefinitions.putChapter(new Chapter(FORK, "GameTest fork", List.of(
                tile("hub", 0, List.of(new CheckmarkTask())),
                tile("left", 1, List.of(new CheckmarkTask())),
                tile("right", 2, List.of(new CheckmarkTask())),
                tile("before", 3, List.of(new CheckmarkTask())),
                tile("end", 4, List.of(new CheckmarkTask()))),
                List.of())
                .upsertLink("hub", "left", GateOp.XOR)
                .upsertLink("hub", "right", GateOp.XOR)
                .upsertLink("hub", "before", GateOp.NOT)
                .upsertLink("left", "end", GateOp.OR)
                .upsertLink("right", "end", GateOp.OR));
        QuestDefinitions.putChapter(new Chapter(AFTER_FORK, "GameTest after fork",
                List.of(tile("z", 0, List.of(new CheckmarkTask()))), List.of())
                .withMeta(Optional.empty(), 0, Optional.empty(),
                        new Gate(GateOp.AND, List.of(new GateCondition("chapter_complete", FORK.toString())))));
        // Stage gating through whichever stage mod is installed (the stage tests below skip without Progression).
        QuestDefinitions.putChapter(new Chapter(STAGES, "GameTest stages", List.of(
                new Tile("gated", new GridPos(0, 0), "gated", "", Optional.empty(), List.of(new CheckmarkTask()),
                        List.of(), List.of(), Optional.empty(), Optional.empty(), Optional.of("qqgt_gate")),
                new Tile("live", new GridPos(1, 0), "live", "", Optional.empty(), List.of(new CheckmarkTask()),
                        List.of(), List.of(), Optional.empty(), Optional.empty(), Optional.of("qqgt_live")),
                tile("grant", 2, List.of(new CheckmarkTask()))
                        .withRewards(List.of(new dev.aof.questqueen.data.reward.StageReward("qqgt_reward")))),
                List.of()));
        QuestDefinitions.putChapter(new Chapter(STAGE_CHAPTER, "GameTest stage chapter",
                List.of(tile("z", 0, List.of(new CheckmarkTask()))), List.of())
                .withMeta(Optional.empty(), 0, Optional.empty(),
                        new Gate(GateOp.AND, List.of(new GateCondition("progression", "qqgt_chapter")))));
    }

    /** Stage tests need a stage mod (Progression or ProgressiveStages) in the run's mods folder; without one they pass trivially (CI has none). */
    private static boolean stageMod() {
        return Stages.present();
    }

    private static String stagesKey(String tile) {
        return ProgressSnapshot.questKey(STAGES, tile);
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aRequiredStageGatesTheQuestUntilProgressionGrantsIt(GameTestHelper helper) {
        if (!stageMod()) {
            helper.succeed();
            return;
        }
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        check(!snapshot(player).revealedTiles().contains(stagesKey("gated")), "gated before the stage");
        check(!TaskHooks.trySubmit(player, STAGES, "gated", 0), "a stage-gated quest must refuse progress");
        check(Stages.grantStage(player, "qqgt_gate"), "Progression refused the grant");
        check(Stages.hasStage(player, "qqgt_gate"), "Progression does not report the granted stage");
        check(snapshot(player).revealedTiles().contains(stagesKey("gated")), "the quest must open once the stage is held");
        check(TaskHooks.trySubmit(player, STAGES, "gated", 0), "the opened quest must accept progress");
        leave(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aStageConditionUnlocksAChapter(GameTestHelper helper) {
        if (!stageMod()) {
            helper.succeed();
            return;
        }
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        check(!snapshot(player).chapterUnlocked(STAGE_CHAPTER.toString()), "unlocked before the stage");
        Stages.grantStage(player, "qqgt_chapter");
        check(snapshot(player).chapterUnlocked(STAGE_CHAPTER.toString()),
                "a \"progression\" unlock condition must open the chapter");
        leave(player);
        helper.succeed();
    }

    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void theStageRewardGrantsAProgressionStage(GameTestHelper helper) {
        if (!stageMod()) {
            helper.succeed();
            return;
        }
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        check(!Stages.hasStage(player, "qqgt_reward"), "held before the claim");
        check(TaskHooks.trySubmit(player, STAGES, "grant", 0), "checkmark was refused");
        check(ProgressService.claimTileRewards(player, STAGES, "grant"), "claim was refused");
        check(Stages.hasStage(player, "qqgt_reward"), "the stage reward must grant the Progression stage");
        check(snapshot(player).ownedStages().contains("qqgt_reward"), "the client snapshot must carry the stage");
        leave(player);
        helper.succeed();
    }

    /**
     * A stage granted outside Quest Queen (a command, another mod) reaches the book without a relog: Progression's
     * StagesChangedEvent resyncs, which drops the cached snapshot. The cache is read WITHOUT invalidating here.
     */
    @GameTest(template = TEMPLATE, batch = BATCH, timeoutTicks = 100)
    public static void aStageGrantedElsewhereResyncsTheSnapshot(GameTestHelper helper) {
        if (!stageMod()) {
            helper.succeed();
            return;
        }
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        check(!snapshot(player).revealedTiles().contains(stagesKey("live")), "open before the stage");
        // Granted straight through Progression's API, the way a command or another mod would: Quest Queen is not
        // told, so only the StagesChangedEvent hook can refresh the cache. (/progression grant <name> needs the
        // server's profile cache, which the game-test server does not have.)
        check(Stages.grantStage(player, "qqgt_live"), "Progression refused the grant");
        helper.succeedWhen(() -> {
            check(ProgressService.snapshot(player).revealedTiles().contains(stagesKey("live")),
                    "the cached snapshot was not refreshed by the stage change");
            leave(player);
        });
    }

    /** A chapter with a fork and a NOT gate is complete once every quest is settled, and its unlock fires. */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aForkedChapterCompletesAndUnlocksTheNext(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        check(!snapshot(player).chapterUnlocked(AFTER_FORK.toString()), "unlocked before anything was done");
        check(TaskHooks.trySubmit(player, FORK, "hub", 0), "hub was refused");
        check(TaskHooks.trySubmit(player, FORK, "left", 0), "left was refused");
        check(!TaskHooks.trySubmit(player, FORK, "right", 0), "the fork's other branch must stay closed");
        check(!snapshot(player).chapterUnlocked(AFTER_FORK.toString()), "unlocked with end still open");
        check(TaskHooks.trySubmit(player, FORK, "end", 0), "end was refused");
        check(snapshot(player).chapterUnlocked(AFTER_FORK.toString()),
                "chapter_complete must count the closed branch and the NOT-shut quest as settled");
        leave(player);
        helper.succeed();
    }

    /** Any option of a choice can be taken, not just the first two the old TAKE A / TAKE B buttons offered. */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void theThirdChoiceOptionCanBeTaken(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        check(TaskHooks.trySubmit(player, CHAPTER, PICK3, 0), "checkmark was refused");
        ProgressService.claimChoice(player, CHAPTER, PICK3, 2);
        check(count(player, Items.IRON_INGOT) == 16, "option 2 must grant 16 iron, got " + count(player, Items.IRON_INGOT));
        check(count(player, Items.DIAMOND) == 0 && count(player, Items.EMERALD) == 0, "only the picked option is granted");
        check(snapshot(player).value(ProgressSnapshot.questKey(CHAPTER, PICK3), "choice") == 2,
                "the pick is recorded so the card and log can show it");
        leave(player);
        helper.succeed();
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

    /** Progress kept under a pre-1.1.208 Quest Queen team moves to wherever the player plays now. */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void legacyTeamProgressMovesToThePlayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        String legacy = "team:" + java.util.UUID.randomUUID();
        sql("INSERT INTO progress(team_id, quest_id, task_id, value, completed) VALUES(?, ?, ?, 5, 0)",
                legacy, ProgressSnapshot.questKey(CHAPTER, GATHER), ProgressSnapshot.taskKey(LOGS));
        sql("INSERT INTO team_members(team_id, player_uuid, role) VALUES(?, ?, 'member')",
                legacy, player.getUUID().toString());
        TeamService.resetSession();
        expectValue(player, GATHER, LOGS, 5);
        check(rows("SELECT 1 FROM progress WHERE team_id = ?", legacy) == 0, "the legacy rows were copied, not moved");
        check(rows("SELECT 1 FROM team_members WHERE player_uuid = ?", player.getUUID().toString()) == 0,
                "the legacy membership must be dropped so the move runs once");
        leave(player);
        helper.succeed();
    }

    /** A guest invited the old way sat in solo:<host>. That progress is the host's and must stay put. */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void aGuestNeverTakesTheHostsSoloProgress(GameTestHelper helper) {
        ServerPlayer guest = helper.makeMockServerPlayerInLevel();
        String hostSolo = "solo:" + java.util.UUID.randomUUID();
        sql("INSERT INTO progress(team_id, quest_id, task_id, value, completed) VALUES(?, ?, ?, 9, 0)",
                hostSolo, ProgressSnapshot.questKey(CHAPTER, GATHER), ProgressSnapshot.taskKey(LOGS));
        sql("INSERT INTO team_members(team_id, player_uuid, role) VALUES(?, ?, 'member')",
                hostSolo, guest.getUUID().toString());
        TeamService.resetSession();
        expectValue(guest, GATHER, LOGS, 0);
        check(rows("SELECT 1 FROM progress WHERE team_id = ?", hostSolo) == 1, "the host's progress moved");
        leave(guest);
        helper.succeed();
    }

    /** Only with FTB Teams loaded: two players in one party store one summed count. */
    @GameTest(template = TEMPLATE, batch = BATCH)
    public static void anFtbPartySharesOneInventoryCount(GameTestHelper helper) {
        if (!FtbTeamsCompat.present()) {
            helper.succeed();
            return;
        }
        ServerPlayer host = helper.makeMockServerPlayerInLevel();
        ServerPlayer guest = helper.makeMockServerPlayerInLevel();
        formParty(host, guest);
        String team = TeamService.current(host);
        check(team.startsWith(FtbTeamsCompat.PREFIX), "host is not on an FTB team: " + team);
        check(team.equals(TeamService.current(guest)), "guest is not on the host's party");
        give(host, Items.OAK_LOG, 10);
        scan(host);
        scan(guest);
        expectValue(host, GATHER, LOGS, 10);
        expectValue(guest, GATHER, LOGS, 10);
        give(guest, Items.OAK_LOG, 6);
        scan(guest);
        expectCompleted(host, GATHER, LOGS);
        leave(guest);
        leave(host);
        helper.succeed();
    }

    /** FTB Teams is not on the compile classpath; drive its party API by reflection. */
    private static void formParty(ServerPlayer host, ServerPlayer guest) {
        try {
            Object api = Class.forName("dev.ftb.mods.ftbteams.api.FTBTeamsAPI").getMethod("api").invoke(null);
            Object manager = api.getClass().getMethod("getManager").invoke(api);
            Object party = manager.getClass().getMethod("createParty", ServerPlayer.class, String.class)
                    .invoke(manager, host, "qq_gametest_" + host.getUUID().toString().substring(0, 8));
            Method invite = party.getClass().getMethod("invite", ServerPlayer.class, Collection.class);
            invite.invoke(party, host, List.of(guest.getGameProfile()));
            party.getClass().getMethod("join", ServerPlayer.class).invoke(party, guest);
        } catch (ReflectiveOperationException exception) {
            throw new GameTestAssertException("could not form an FTB party: " + exception.getCause());
        }
    }

    private static void sql(String statement, String... args) {
        try (PreparedStatement prepared = ProgressDatabase.get().prepareStatement(statement)) {
            for (int i = 0; i < args.length; i++) {
                prepared.setString(i + 1, args[i]);
            }
            prepared.executeUpdate();
        } catch (java.sql.SQLException exception) {
            throw new GameTestAssertException(exception.toString());
        }
    }

    private static int rows(String query, String arg) {
        try (PreparedStatement prepared = ProgressDatabase.get().prepareStatement(query)) {
            prepared.setString(1, arg);
            int count = 0;
            try (ResultSet result = prepared.executeQuery()) {
                while (result.next()) {
                    count++;
                }
            }
            return count;
        } catch (java.sql.SQLException exception) {
            throw new GameTestAssertException(exception.toString());
        }
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
