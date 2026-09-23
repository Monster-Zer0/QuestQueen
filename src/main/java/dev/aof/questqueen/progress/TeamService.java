package dev.aof.questqueen.progress;

import net.minecraft.server.level.ServerPlayer;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public final class TeamService {
    private TeamService() {
    }

    public static String ensureSolo(ServerPlayer player) {
        UUID uuid = player.getUUID();
        Optional<String> existing = teamOf(uuid);
        if (existing.isPresent()) {
            return existing.get();
        }
        String teamId = "solo:" + uuid;
        try {
            try (PreparedStatement insertTeam = ProgressDatabase.get().prepareStatement(
                    "INSERT OR IGNORE INTO teams(team_id, name) VALUES(?, ?)")) {
                insertTeam.setString(1, teamId);
                insertTeam.setString(2, player.getGameProfile().getName());
                insertTeam.executeUpdate();
            }
            try (PreparedStatement insertMember = ProgressDatabase.get().prepareStatement(
                    "INSERT OR REPLACE INTO team_members(team_id, player_uuid, role) VALUES(?, ?, 'leader')")) {
                insertMember.setString(1, teamId);
                insertMember.setString(2, uuid.toString());
                insertMember.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
        return teamId;
    }

    public static Optional<String> teamOf(UUID player) {
        try (PreparedStatement statement = ProgressDatabase.get().prepareStatement(
                "SELECT team_id FROM team_members WHERE player_uuid = ?")) {
            statement.setString(1, player.toString());
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    return Optional.of(result.getString(1));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
        return Optional.empty();
    }

    public static String create(ServerPlayer player, String name) {
        leave(player, false);
        String teamId = "team:" + UUID.randomUUID();
        try {
            try (PreparedStatement insertTeam = ProgressDatabase.get().prepareStatement(
                    "INSERT INTO teams(team_id, name) VALUES(?, ?)")) {
                insertTeam.setString(1, teamId);
                insertTeam.setString(2, name);
                insertTeam.executeUpdate();
            }
            try (PreparedStatement insertMember = ProgressDatabase.get().prepareStatement(
                    "INSERT INTO team_members(team_id, player_uuid, role) VALUES(?, ?, 'leader')")) {
                insertMember.setString(1, teamId);
                insertMember.setString(2, player.getUUID().toString());
                insertMember.executeUpdate();
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
        return teamId;
    }

    public static void invite(String teamId, UUID player) {
        try (PreparedStatement statement = ProgressDatabase.get().prepareStatement(
                "INSERT OR REPLACE INTO team_invites(team_id, player_uuid) VALUES(?, ?)")) {
            statement.setString(1, teamId);
            statement.setString(2, player.toString());
            statement.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static Optional<String> accept(ServerPlayer player) {
        UUID uuid = player.getUUID();
        try (PreparedStatement select = ProgressDatabase.get().prepareStatement(
                "SELECT team_id FROM team_invites WHERE player_uuid = ? LIMIT 1")) {
            select.setString(1, uuid.toString());
            try (ResultSet result = select.executeQuery()) {
                if (!result.next()) {
                    return Optional.empty();
                }
                String teamId = result.getString(1);
                leave(player, false);
                try (PreparedStatement insert = ProgressDatabase.get().prepareStatement(
                        "INSERT INTO team_members(team_id, player_uuid, role) VALUES(?, ?, 'member')")) {
                    insert.setString(1, teamId);
                    insert.setString(2, uuid.toString());
                    insert.executeUpdate();
                }
                try (PreparedStatement delete = ProgressDatabase.get().prepareStatement(
                        "DELETE FROM team_invites WHERE player_uuid = ?")) {
                    delete.setString(1, uuid.toString());
                    delete.executeUpdate();
                }
                return Optional.of(teamId);
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
    }

    public static void leave(ServerPlayer player, boolean restoreSolo) {
        UUID uuid = player.getUUID();
        try (PreparedStatement delete = ProgressDatabase.get().prepareStatement(
                "DELETE FROM team_members WHERE player_uuid = ?")) {
            delete.setString(1, uuid.toString());
            delete.executeUpdate();
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
        if (restoreSolo) {
            ensureSolo(player);
        }
    }

    public static List<UUID> members(String teamId) {
        List<UUID> members = new ArrayList<>();
        try (PreparedStatement statement = ProgressDatabase.get().prepareStatement(
                "SELECT player_uuid FROM team_members WHERE team_id = ?")) {
            statement.setString(1, teamId);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    members.add(UUID.fromString(result.getString(1)));
                }
            }
        } catch (SQLException exception) {
            throw new IllegalStateException(exception);
        }
        return members;
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
