package dev.aof.questqueen.progress;

import dev.aof.questqueen.QuestQueen;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

public final class ProgressDatabase {
    private static Connection connection;

    private ProgressDatabase() {
    }

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        open(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        close();
    }

    public static synchronized Connection get() {
        if (connection == null) {
            throw new IllegalStateException("Quest Queen database is not open");
        }
        return connection;
    }

    public static synchronized void open(MinecraftServer server) {
        close();
        try {
            Class.forName("org.sqlite.JDBC");
            Path folder = server.getWorldPath(LevelResource.ROOT).resolve("questqueen");
            Files.createDirectories(folder);
            Path db = folder.resolve("progress.db");
            connection = DriverManager.getConnection("jdbc:sqlite:" + db.toAbsolutePath());
            try (Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS teams (
                          team_id TEXT PRIMARY KEY,
                          name TEXT NOT NULL
                        )""");
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS team_members (
                          team_id TEXT NOT NULL,
                          player_uuid TEXT NOT NULL UNIQUE,
                          role TEXT NOT NULL,
                          PRIMARY KEY (team_id, player_uuid)
                        )""");
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS team_invites (
                          team_id TEXT NOT NULL,
                          player_uuid TEXT NOT NULL,
                          PRIMARY KEY (team_id, player_uuid)
                        )""");
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS progress (
                          team_id TEXT NOT NULL,
                          quest_id TEXT NOT NULL,
                          task_id TEXT NOT NULL,
                          value INTEGER NOT NULL DEFAULT 0,
                          completed INTEGER NOT NULL DEFAULT 0,
                          PRIMARY KEY (team_id, quest_id, task_id)
                        )""");
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS scrolls (
                          player_uuid TEXT NOT NULL,
                          scroll_id TEXT NOT NULL,
                          PRIMARY KEY (player_uuid, scroll_id)
                        )""");
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS pins (
                          player_uuid TEXT PRIMARY KEY,
                          chapter_id TEXT NOT NULL,
                          tile_id TEXT NOT NULL
                        )""");
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS team_flags (
                          team_id TEXT NOT NULL,
                          flag TEXT NOT NULL,
                          PRIMARY KEY (team_id, flag)
                        )""");
            }
            QuestQueen.LOGGER.info("Opened quest progress database at {}", db.toAbsolutePath());
        } catch (Exception exception) {
            throw new IllegalStateException("Failed to open Quest Queen SQLite database", exception);
        }
    }

    public static synchronized void close() {
        if (connection != null) {
            try {
                connection.close();
            } catch (SQLException exception) {
                QuestQueen.LOGGER.error("Failed to close quest database", exception);
            }
            connection = null;
        }
    }
}
