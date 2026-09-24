package dev.aof.questqueen.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.entity.raid.Raids;
import net.minecraft.tags.PoiTypeTags;
import net.minecraft.world.entity.ai.village.poi.PoiManager;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.Difficulty;
import net.minecraft.core.BlockPos;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import dev.aof.questqueen.QuestVersion;
import dev.aof.questqueen.api.QuestNpcApi;
import dev.aof.questqueen.data.QuestDefinitions;
import dev.aof.questqueen.net.QuestNetwork;
import dev.aof.questqueen.progress.EditorSessions;
import dev.aof.questqueen.progress.ProgressService;
import dev.aof.questqueen.progress.TeamService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

public final class QuestQueenCommands {
    /**
     * Version stamp for command feedback. Read from the loaded mod rather than written down here, so a
     * build can never ship feedback that names a build it is not.
     */
    private static final String QQ = QuestVersion.stamp();
    private static final SuggestionProvider<CommandSourceStack> ITEMS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(BuiltInRegistries.ITEM.keySet(), builder);
    private static final SuggestionProvider<CommandSourceStack> CHAPTERS = (context, builder) ->
            SharedSuggestionProvider.suggestResource(QuestDefinitions.chapters().stream().map(chapter -> chapter.id()), builder);

    private QuestQueenCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    /** The whole command tree; split from the event so tests can build it and read its permission gates. */
    static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("questqueen")
                .then(Commands.literal("team")
                        .then(Commands.literal("create")
                                .then(Commands.argument("name", StringArgumentType.string())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            String id = TeamService.create(player, StringArgumentType.getString(ctx, "name"));
                                            ProgressService.sync(player);
                                            ctx.getSource().sendSuccess(() -> Component.literal("Created team " + id), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("invite")
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> {
                                            ServerPlayer player = ctx.getSource().getPlayerOrException();
                                            ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                            String team = TeamService.ensureSolo(player);
                                            TeamService.invite(team, target.getUUID());
                                            target.sendSystemMessage(Component.literal("Quest team invite from " + player.getGameProfile().getName() + ". Use /questqueen team accept"));
                                            ctx.getSource().sendSuccess(() -> Component.literal("Invited " + target.getGameProfile().getName()), true);
                                            return 1;
                                        })))
                        .then(Commands.literal("accept")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    return TeamService.accept(player).map(teamId -> {
                                        // The team just gained a member; existing members still carry a
                                        // snapshot built from the old roster, so sync the whole team.
                                        ProgressService.invalidatePlayer(player.getUUID());
                                        ProgressService.syncTeam(player.server, teamId);
                                        ctx.getSource().sendSuccess(() -> Component.literal("Joined " + teamId), true);
                                        return 1;
                                    }).orElseGet(() -> {
                                        ctx.getSource().sendFailure(Component.literal("No pending invite"));
                                        return 0;
                                    });
                                }))
                        .then(Commands.literal("leave")
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    TeamService.leave(player, true);
                                    ProgressService.sync(player);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Left team"), true);
                                    return 1;
                                })))
                // trigger/dialog complete quests and set team flags, so they are for command blocks, functions and
                // NPC mods (all level 2), not for a survival player typing them into chat.
                .then(Commands.literal("trigger")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("id", StringArgumentType.string())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String id = StringArgumentType.getString(ctx, "id");
                                    QuestNpcApi.fireTrigger(player, id);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Triggered " + id), true);
                                    return 1;
                                })))
                .then(Commands.literal("dialog")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("npc", StringArgumentType.string())
                                .executes(ctx -> {
                                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                                    String npc = StringArgumentType.getString(ctx, "npc");
                                    QuestNpcApi.onTalked(player, npc);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Dialog " + npc), true);
                                    return 1;
                                })))
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> {
                            for (ServerPlayer player : ctx.getSource().getServer().getPlayerList().getPlayers()) {
                                QuestNetwork.sendDefinitions(player);
                                ProgressService.sync(player);
                            }
                            ctx.getSource().sendSuccess(() -> Component.literal("Resynced Quest Queen definitions"), true);
                            return 1;
                        }))
                .then(Commands.literal("grant")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("chapter", ResourceLocationArgument.id())
                                        .suggests(CHAPTERS)
                                        .then(Commands.argument("tile", StringArgumentType.string())
                                                .executes(ctx -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    ResourceLocation chapter = ResourceLocationArgument.getId(ctx, "chapter");
                                                    String tile = StringArgumentType.getString(ctx, "tile");
                                                    QuestDefinitions.chapter(chapter).flatMap(found -> found.tile(tile)).ifPresent(foundTile -> {
                                                        for (int i = 0; i < foundTile.tasks().size(); i++) {
                                                            ProgressService.setCompleted(target, chapter, tile, i);
                                                        }
                                                    });
                                                    ctx.getSource().sendSuccess(() -> Component.literal("Granted " + tile), true);
                                                    return 1;
                                                })))))
                .then(Commands.literal("grant-all")
                        .executes(ctx -> grantAllSelf(ctx.getSource()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> grantAllTarget(
                                        ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("unclaim")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("chapter", ResourceLocationArgument.id())
                                        .suggests(CHAPTERS)
                                        .then(Commands.argument("tile", StringArgumentType.string())
                                                .executes(ctx -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    ResourceLocation chapter = ResourceLocationArgument.getId(ctx, "chapter");
                                                    String tile = StringArgumentType.getString(ctx, "tile");
                                                    if (ProgressService.unclaimRewards(target, chapter, tile)) {
                                                        ctx.getSource().sendSuccess(
                                                                () -> Component.literal("Unclaimed " + tile + " — open the book and CLAIM"),
                                                                true);
                                                        return 1;
                                                    }
                                                    ctx.getSource().sendFailure(Component.literal("Unknown chapter/tile"));
                                                    return 0;
                                                })))))
                .then(Commands.literal("fail")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .then(Commands.argument("chapter", ResourceLocationArgument.id())
                                        .suggests(CHAPTERS)
                                        .then(Commands.argument("tile", StringArgumentType.string())
                                                .executes(ctx -> {
                                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                                    ResourceLocation chapter = ResourceLocationArgument.getId(ctx, "chapter");
                                                    String tile = StringArgumentType.getString(ctx, "tile");
                                                    boolean ok = ProgressService.markFailed(target, chapter, tile);
                                                    if (ok) {
                                                        ctx.getSource().sendSuccess(
                                                                () -> Component.literal("Failed " + tile + " (synced)"), true);
                                                        return 1;
                                                    }
                                                    ctx.getSource().sendFailure(Component.literal("Unknown chapter/tile"));
                                                    return 0;
                                                })))))
                .then(Commands.literal("status")
                        .executes(ctx -> showStatus(ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .requires(source -> source.hasPermission(2))
                                .executes(ctx -> showStatus(EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("reset")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> {
                                    ServerPlayer target = EntityArgument.getPlayer(ctx, "player");
                                    ProgressService.reset(target);
                                    ctx.getSource().sendSuccess(() -> Component.literal("Reset quests for " + target.getGameProfile().getName()), true);
                                    return 1;
                                })))
                .then(Commands.literal("editor")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> toggleEditor(ctx.getSource().getPlayerOrException()))
                        .then(Commands.literal("on").executes(ctx -> setEditor(ctx.getSource().getPlayerOrException(), true)))
                        .then(Commands.literal("off").executes(ctx -> setEditor(ctx.getSource().getPlayerOrException(), false))))
                .then(Commands.literal("book")
                        .executes(ctx -> {
                            QuestNetwork.sendOpenBook(ctx.getSource().getPlayerOrException(), "", false, "");
                            return 1;
                        })
                        .then(Commands.literal("chapter")
                                .then(Commands.argument("chapter", ResourceLocationArgument.id())
                                        .executes(ctx -> {
                                            QuestNetwork.sendOpenBook(
                                                    ctx.getSource().getPlayerOrException(),
                                                    "",
                                                    false,
                                                    ResourceLocationArgument.getId(ctx, "chapter").toString());
                                            return 1;
                                        })))
                        .then(Commands.argument("tile", StringArgumentType.string())
                                .executes(ctx -> {
                                    QuestNetwork.sendOpenBook(
                                            ctx.getSource().getPlayerOrException(),
                                            StringArgumentType.getString(ctx, "tile"),
                                            true,
                                            "");
                                    return 1;
                                })
                                .then(Commands.literal("chapter")
                                        .then(Commands.argument("chapter", ResourceLocationArgument.id())
                                                .executes(ctx -> {
                                                    QuestNetwork.sendOpenBook(
                                                            ctx.getSource().getPlayerOrException(),
                                                            StringArgumentType.getString(ctx, "tile"),
                                                            true,
                                                            ResourceLocationArgument.getId(ctx, "chapter").toString());
                                                    return 1;
                                                })))))
                .then(Commands.literal("raid-start")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> startRaid(ctx.getSource().getPlayerOrException(), 1))
                        .then(Commands.argument("omen", IntegerArgumentType.integer(1, 5))
                                .executes(ctx -> startRaid(
                                        ctx.getSource().getPlayerOrException(),
                                        IntegerArgumentType.getInteger(ctx, "omen")))))
                .then(Commands.literal("raid-check")
                        .executes(ctx -> checkRaid(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("raid-stop")
                        .requires(source -> source.hasPermission(2))
                        .executes(ctx -> stopRaid(ctx.getSource().getPlayerOrException())))
                .then(Commands.literal("item")
                        .then(Commands.argument("id", ResourceLocationArgument.id())
                                .suggests(ITEMS)
                                .executes(ctx -> {
                                    ctx.getSource().sendSuccess(() -> Component.literal(ResourceLocationArgument.getId(ctx, "id").toString()), false);
                                    return 1;
                                }))));
    }

    
    private static int startRaid(ServerPlayer player, int omenLevel) {
        if (player.serverLevel().getDifficulty() == Difficulty.PEACEFUL) {
            player.sendSystemMessage(Component.literal(QQ + " raid-start failed: difficulty is PEACEFUL"));
            return 0;
        }
        if (player.serverLevel().getGameRules().getBoolean(GameRules.RULE_DISABLE_RAIDS)) {
            player.sendSystemMessage(Component.literal(QQ + " raid-start failed: doDisableRaids gamerule is true"));
            return 0;
        }
        if (!player.level().dimensionType().hasRaids()) {
            player.sendSystemMessage(Component.literal(QQ + " raid-start failed: this dimension has no raids (use overworld)"));
            return 0;
        }
        BlockPos pos = player.blockPosition();
        // Without occupied village POIs, vanilla raid ticks stop / wander off — kill-credit then fails
        // (qq-229: 768 spawn false-success; village BB 1741,173,1779 PASS).
        long villagePois = player.serverLevel().getPoiManager()
                .getInRange(holder -> holder.is(PoiTypeTags.VILLAGE), pos, 64, PoiManager.Occupancy.IS_OCCUPIED)
                .count();
        if (villagePois <= 0) {
            player.sendSystemMessage(Component.literal(
                    QQ + " raid-start failed: no occupied village POI within 64. Stand in a village "
                            + "(e.g. /execute in minecraft:overworld run tp @s 1741 173 1779) "
                            + "or /place structure minecraft:village_plains then seat villagers, then retry."));
            return 0;
        }
        if (player.serverLevel().isRaided(pos)) {
            player.sendSystemMessage(Component.literal(QQ + " raid-start: raid already active here — kill any mob while standing in it"));
            return 1;
        }
        Raids raids = player.serverLevel().getRaids();
        Raid raid = raids.createOrExtendRaid(player, pos);
        if (raid == null) {
            player.sendSystemMessage(Component.literal(QQ + " raid-start failed: could not create raid"));
            return 0;
        }
        raid.setRaidOmenLevel(omenLevel);
        raids.setDirty();
        if (player.serverLevel().getRaidAt(pos) == null) {
            player.sendSystemMessage(Component.literal(
                    QQ + " raid-start failed: raid created but getRaidAt is null at feet — move into village center and retry"));
            return 0;
        }
        player.sendSystemMessage(Component.literal(
                QQ + " raid-start ok omen=" + omenLevel
                        + " villagePois=" + villagePois
                        + " center=" + raid.getCenter().toShortString()
                        + " — kill any mob while in the raid to credit type_raid (waves=1)"));
        return 1;
    }

    private static int checkRaid(ServerPlayer player) {
        Raid raid = player.serverLevel().getRaidAt(player.blockPosition());
        if (raid == null) {
            player.sendSystemMessage(Component.literal(QQ + " raid-check: no raid at your position"));
            return 0;
        }
        player.sendSystemMessage(Component.literal(
                QQ + " raid-check: active=" + raid.isActive()
                        + " started=" + raid.isStarted()
                        + " victory=" + raid.isVictory()
                        + " groups=" + raid.getGroupsSpawned()
                        + " raiders=" + raid.getTotalRaidersAlive()
                        + " omen=" + raid.getRaidOmenLevel()
                        + " center=" + raid.getCenter().toShortString()));
        return 1;
    }

    private static int stopRaid(ServerPlayer player) {
        Raid raid = player.serverLevel().getRaidAt(player.blockPosition());
        if (raid == null) {
            player.sendSystemMessage(Component.literal(QQ + " raid-stop: no raid here"));
            return 0;
        }
        raid.stop();
        player.sendSystemMessage(Component.literal(QQ + " raid-stop: stopped"));
        return 1;
    }
    private static int grantAllSelf(CommandSourceStack source) {
        ServerPlayer player;
        try {
            player = source.getPlayerOrException();
        } catch (Exception exception) {
            source.sendFailure(Component.literal("grant-all needs a player"));
            return 0;
        }
        if (!source.hasPermission(2) && !player.isCreative()) {
            source.sendFailure(Component.literal("grant-all requires creative mode or permission 2"));
            return 0;
        }
        return grantAllTarget(source, player);
    }

    private static int grantAllTarget(CommandSourceStack source, ServerPlayer target) {
        int granted = ProgressService.grantAll(target);
        int skipped = ProgressService.lastBulkStagesSkipped();
        source.sendSuccess(() -> Component.literal(
                "Granted " + granted + " quests for " + target.getGameProfile().getName()
                        + " (progress only — no rewards)"
                        + (skipped > 0 ? "; " + skipped + " tile(s) skipped stage rewards (see log)" : "")), true);
        return 1;
    }

    private static int showStatus(ServerPlayer player) {
        var snapshot = ProgressService.snapshot(player);
        String completed = snapshot.completedTiles().isEmpty()
                ? "(none)"
                : String.join(", ", snapshot.completedTiles().stream().sorted().toList());
        String pin = snapshot.pinChapter().isPresent() && snapshot.pinTile().isPresent()
                ? snapshot.pinChapter().get() + "/" + snapshot.pinTile().get()
                : "(none)";
        String unlocked = snapshot.unlockedChapters().isEmpty()
                ? "(none)"
                : String.join(", ", snapshot.unlockedChapters().stream().sorted().toList());
        player.sendSystemMessage(Component.literal("QQ status completed=[" + completed + "]"));
        player.sendSystemMessage(Component.literal("QQ status pin=" + pin));
        player.sendSystemMessage(Component.literal("QQ status chapters=[" + unlocked + "]"));
        return 1;
    }

    private static int toggleEditor(ServerPlayer player) {
        return setEditor(player, !EditorSessions.isEnabled(player));
    }

    private static int setEditor(ServerPlayer player, boolean enable) {
        if (enable) {
            EditorSessions.enable(player);
        } else {
            EditorSessions.disable(player.getUUID());
        }
        ProgressService.sync(player);
        QuestNetwork.sendEditorSession(player, enable);
        player.sendSystemMessage(Component.translatable(enable ? "questqueen.editor.on" : "questqueen.editor.off"));
        return 1;
    }
}
