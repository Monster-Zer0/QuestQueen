package dev.aof.questqueen.progress;

import dev.aof.questqueen.QuestQueen;
import dev.aof.questqueen.compat.FtbTeamsCompat;
import net.minecraft.server.level.ServerPlayer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Which team's progress a player plays with. Teams come from FTB Teams when it is installed ({@code ftb:<uuid>}:
 * the player's party, else their personal team). Without it every player is their own team ({@code solo:<uuid>}).
 *
 * <p>Quest Queen used to keep its own teams in the {@code team_members} table. {@link #migrate} moves that
 * progress, once per player, to where the player now plays: their FTB personal team, or their solo team.
 */
public final class TeamService {
    public static final String SOLO_PREFIX = "solo:";
    /** Players whose legacy progress has been looked at this server session. */
    private static final Set<UUID> MIGRATED = ConcurrentHashMap.newKeySet();

    private TeamService() {
    }

    /** A new world or server: migration state belongs to the database that was just opened. Game tests reuse it. */
    public static void resetSession() {
        MIGRATED.clear();
    }

    public static String current(ServerPlayer player) {
        return current(player.getUUID());
    }

    public static String current(UUID player) {
        migrate(player);
        return FtbTeamsCompat.currentTeam(player).map(FtbTeamsCompat.FtbTeam::id).orElseGet(() -> soloId(player));
    }

    static String soloId(UUID player) {
        return SOLO_PREFIX + player;
    }

    /** Every player whose progress lives under {@code teamId}, online or not. */
    public static List<UUID> members(String teamId) {
        if (teamId == null) {
            return List.of();
        }
        if (teamId.startsWith(SOLO_PREFIX)) {
            try {
                return List.of(UUID.fromString(teamId.substring(SOLO_PREFIX.length())));
            } catch (IllegalArgumentException exception) {
                return List.of();
            }
        }
        return FtbTeamsCompat.members(teamId).map(set -> (List<UUID>) new ArrayList<>(set)).orElse(List.of());
    }

    /**
     * Moves a player's progress from where Quest Queen used to keep it to where they play now, once. Sources, in
     * order: the legacy {@code team_members} team, then {@code solo:<uuid>}. The first source with progress moves,
     * and only into a target that has none, so nothing is merged or overwritten. A legacy team shared by several
     * players therefore goes to whichever member logs in first.
     *
     * <p>With FTB Teams the target is the personal team even while the player is in a party, the same place FTB
     * Quests keeps a player's own progress. If FTB Teams has not created that team yet, this waits for a later call.
     */
    private static void migrate(UUID player) {
        if (MIGRATED.contains(player)) {
            return;
        }
        String target;
        if (FtbTeamsCompat.present()) {
            Optional<FtbTeamsCompat.FtbTeam> personal = FtbTeamsCompat.personalTeam(player);
            if (personal.isEmpty()) {
                return;
            }
            target = personal.get().id();
        } else {
            target = soloId(player);
        }
        MIGRATED.add(player);
        try {
            List<String> sources = new ArrayList<>();
            // A guest invited the old way sits in solo:<host>; that progress is the host's, never the guest's.
            legacyTeam(player).filter(team -> !team.startsWith(SOLO_PREFIX)).ifPresent(sources::add);
            sources.add(soloId(player));
            for (String source : sources) {
                if (source.equals(target) || !hasProgress(source)) {
                    continue;
                }
                if (hasProgress(target)) {
                    QuestQueen.LOGGER.warn("Quest progress for {} under {} not moved: {} already has progress",
                            player, source, target);
                    break;
                }
                move(source, target);
                QuestQueen.LOGGER.info("Moved quest progress for {} from {} to {}", player, source, target);
                break;
            }
            deleteLegacyMembership(player);
        } catch (SQLException exception) {
            MIGRATED.remove(player);
            throw new IllegalStateException(exception);
        }
    }

    private static Optional<String> legacyTeam(UUID player) throws SQLException {
        try (PreparedStatement statement = ProgressDatabase.get().prepareStatement(
                "SELECT team_id FROM team_members WHERE player_uuid = ?")) {
            statement.setString(1, player.toString());
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? Optional.of(result.getString(1)) : Optional.empty();
            }
        }
    }

    private static boolean hasProgress(String teamId) throws SQLException {
        try (PreparedStatement statement = ProgressDatabase.get().prepareStatement(
                "SELECT 1 FROM progress WHERE team_id = ? LIMIT 1")) {
            statement.setString(1, teamId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }

    private static void move(String source, String target) throws SQLException {
        try (PreparedStatement progress = ProgressDatabase.get().prepareStatement(
                "UPDATE progress SET team_id = ? WHERE team_id = ?");
             PreparedStatement flags = ProgressDatabase.get().prepareStatement(
                     "UPDATE OR IGNORE team_flags SET team_id = ? WHERE team_id = ?");
             PreparedStatement leftover = ProgressDatabase.get().prepareStatement(
                     "DELETE FROM team_flags WHERE team_id = ?")) {
            progress.setString(1, target);
            progress.setString(2, source);
            progress.executeUpdate();
            flags.setString(1, target);
            flags.setString(2, source);
            flags.executeUpdate();
            leftover.setString(1, source);
            leftover.executeUpdate();
        }
    }

    private static void deleteLegacyMembership(UUID player) throws SQLException {
        try (PreparedStatement statement = ProgressDatabase.get().prepareStatement(
                "DELETE FROM team_members WHERE player_uuid = ?")) {
            statement.setString(1, player.toString());
            statement.executeUpdate();
        }
    }

    public static boolean setFlag(String teamId, String flag) {
        try (PreparedStatement statement = ProgressDatabase.get().prepareStatement(
                "INSERT OR IGNORE INTO team_flags(team_id, flag) VALUES(?, ?)")) {
            statement.setString(1, teamId);
            statement.setString(2, flag);
            return statement.executeUpdate() > 0;
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    /**
     * Drop every team flag. Flags back {@code trigger} gates (see {@code ProgressService.extra}),
     * so anything that resets a team's progress must clear them too or the quest stays unlocked.
     */
    public static void clearFlags(String teamId) {
        try (PreparedStatement statement = ProgressDatabase.get().prepareStatement(
                "DELETE FROM team_flags WHERE team_id = ?")) {
            statement.setString(1, teamId);
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static boolean hasFlag(String teamId, String flag) {
        try (PreparedStatement statement = ProgressDatabase.get().prepareStatement(
                "SELECT 1 FROM team_flags WHERE team_id = ? AND flag = ?")) {
            statement.setString(1, teamId);
            statement.setString(2, flag);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }
}
