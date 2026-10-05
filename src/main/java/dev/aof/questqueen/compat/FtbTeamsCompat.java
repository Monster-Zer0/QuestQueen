package dev.aof.questqueen.compat;

import dev.aof.questqueen.QuestQueen;
import net.neoforged.fml.ModList;

import java.lang.reflect.Method;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Optional FTB Teams bridge. Safe when the mod is absent: every lookup answers empty and Quest Queen treats each
 * player as their own team. Reflection rather than a compile dependency, like {@link ProgressiveStagesCompat}, so
 * the build pulls in neither FTB Teams nor its FTB Library / Architectury stack.
 *
 * <p>Team ids are {@code ftb:<team uuid>}. A player always has a personal FTB team and may also be in a party;
 * {@link #currentTeam} answers the party when there is one, {@link #personalTeam} always the personal team.
 */
public final class FtbTeamsCompat {
    public static final String MOD_ID = "ftbteams";
    public static final String PREFIX = "ftb:";
    private static final String API = "dev.ftb.mods.ftbteams.api.FTBTeamsAPI";

    private static Boolean present;
    private static boolean broken;
    private static Method api;
    private static Method isManagerLoaded;
    private static Method getManager;
    private static Method teamForPlayer;
    private static Method personalTeamForPlayer;
    private static Method teamById;
    private static Method teamId;
    private static Method members;

    /** A resolved FTB team: Quest Queen's id for it and the member uuids. */
    public record FtbTeam(String id, Set<UUID> members) {
    }

    private FtbTeamsCompat() {
    }

    public static boolean present() {
        if (present == null) {
            ModList mods = ModList.get();
            present = mods != null && mods.isLoaded(MOD_ID);
        }
        return present && !broken;
    }

    /** The team whose progress the player plays with now: their party if they are in one, else their own. */
    public static Optional<FtbTeam> currentTeam(UUID player) {
        return lookup(() -> teamForPlayer, player);
    }

    /** The player's personal team, even while they are in a party. */
    public static Optional<FtbTeam> personalTeam(UUID player) {
        return lookup(() -> personalTeamForPlayer, player);
    }

    /** Members of a team by Quest Queen id ({@code ftb:<uuid>}); empty when it is not a live FTB team. */
    public static Optional<Set<UUID>> members(String questTeamId) {
        if (questTeamId == null || !questTeamId.startsWith(PREFIX)) {
            return Optional.empty();
        }
        UUID id;
        try {
            id = UUID.fromString(questTeamId.substring(PREFIX.length()));
        } catch (IllegalArgumentException exception) {
            return Optional.empty();
        }
        return lookup(() -> teamById, id).map(FtbTeam::members);
    }

    private static Optional<FtbTeam> lookup(MethodRef finder, UUID key) {
        if (!present() || !bind()) {
            return Optional.empty();
        }
        try {
            Object instance = api.invoke(null);
            if (!Boolean.TRUE.equals(isManagerLoaded.invoke(instance))) {
                return Optional.empty();
            }
            Object manager = getManager.invoke(instance);
            Optional<?> team = (Optional<?>) finder.get().invoke(manager, key);
            if (team.isEmpty()) {
                return Optional.empty();
            }
            Object resolved = team.get();
            @SuppressWarnings("unchecked")
            Set<UUID> memberIds = Set.copyOf((Set<UUID>) members.invoke(resolved));
            return Optional.of(new FtbTeam(PREFIX + teamId.invoke(resolved), memberIds));
        } catch (Throwable exception) {
            disable(exception);
            return Optional.empty();
        }
    }

    private static synchronized boolean bind() {
        if (members != null) {
            return true;
        }
        try {
            Class<?> apiClass = Class.forName(API);
            Class<?> apiInterface = Class.forName(API + "$API");
            Class<?> managerClass = Class.forName("dev.ftb.mods.ftbteams.api.TeamManager");
            Class<?> teamClass = Class.forName("dev.ftb.mods.ftbteams.api.Team");
            api = apiClass.getMethod("api");
            isManagerLoaded = apiInterface.getMethod("isManagerLoaded");
            getManager = apiInterface.getMethod("getManager");
            teamForPlayer = managerClass.getMethod("getTeamForPlayerID", UUID.class);
            personalTeamForPlayer = managerClass.getMethod("getPlayerTeamForPlayerID", UUID.class);
            teamById = managerClass.getMethod("getTeamByID", UUID.class);
            // getId, not getTeamId: on a personal team getTeamId answers the party it is in.
            teamId = teamClass.getMethod("getId");
            members = teamClass.getMethod("getMembers");
            QuestQueen.LOGGER.info("FTB Teams compat enabled: quest progress follows FTB teams");
            return true;
        } catch (Throwable exception) {
            members = null;
            disable(exception);
            return false;
        }
    }

    /** An FTB Teams API change must not take quest progress down with it: fall back to solo teams. */
    private static void disable(Throwable exception) {
        if (!broken) {
            broken = true;
            QuestQueen.LOGGER.error("FTB Teams compat failed; quest progress falls back to one team per player",
                    exception);
        }
    }

    @FunctionalInterface
    private interface MethodRef {
        Method get();
    }
}
