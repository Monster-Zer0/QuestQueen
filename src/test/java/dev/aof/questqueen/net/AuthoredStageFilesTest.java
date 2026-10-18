package dev.aof.questqueen.net;

import com.google.gson.JsonParser;
import dev.aof.questqueen.data.stage.AuthoredStage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** An editor-made stage writes its three files inside the authored pack, and deleting it removes exactly those. */
class AuthoredStageFilesTest {
    @TempDir
    Path temp;

    private Path root() {
        return temp.resolve("datapacks").resolve("questqueen_authored");
    }

    private static AuthoredStage stage(String id, List<AuthoredStage.Lock> locks) {
        return new AuthoredStage(id, "Iron Age", "", "", "player", true, List.of(), List.of(), locks);
    }

    private static final AuthoredStage.Lock DIAMONDS =
            new AuthoredStage.Lock("item", List.of("minecraft:diamond"), Map.of(), "");

    @Test
    void writesAllThreeFilesUnderTheNamespace() throws Exception {
        AuthoredStageFiles.Files3 files = AuthoredStageFiles.write(root(), "mypack", stage("iron_age", List.of(DIAMONDS)));
        assertEquals(root().resolve("data/mypack/questqueen/stages/iron_age.json"), files.authored());
        assertEquals(root().resolve("data/mypack/stagelock/stages/iron_age.json"), files.definition());
        assertEquals(root().resolve("data/mypack/stagelock/restrictions/questqueen/iron_age.json"), files.restrictions());
        for (Path file : files.all()) {
            assertTrue(Files.exists(file), file.toString());
        }
        assertTrue(Files.exists(root().resolve("pack.mcmeta")));
        assertEquals("iron_age", JsonParser.parseString(Files.readString(files.definition()))
                .getAsJsonObject().get("id").getAsString());
    }

    @Test
    void removingEveryLockRemovesTheRuleFile() throws Exception {
        AuthoredStageFiles.Files3 files = AuthoredStageFiles.write(root(), "mypack", stage("iron_age", List.of(DIAMONDS)));
        AuthoredStageFiles.write(root(), "mypack", stage("iron_age", List.of()));
        assertFalse(Files.exists(files.restrictions()));
        assertTrue(Files.exists(files.definition()));
    }

    @Test
    void namespacedIdBecomesAFolder() throws Exception {
        AuthoredStageFiles.Files3 files = AuthoredStageFiles.write(root(), "questqueen", stage("mypack:nether", List.of()));
        assertEquals(root().resolve("data/questqueen/stagelock/stages/mypack/nether.json"), files.definition());
    }

    @Test
    void deleteRemovesTheFilesAndReportsWhetherTheyWereThere() throws Exception {
        AuthoredStageFiles.Files3 files = AuthoredStageFiles.write(root(), "mypack", stage("iron_age", List.of(DIAMONDS)));
        assertTrue(AuthoredStageFiles.delete(root(), "mypack", "iron_age"));
        for (Path file : files.all()) {
            assertFalse(Files.exists(file), file.toString());
        }
        assertFalse(AuthoredStageFiles.delete(root(), "mypack", "iron_age"));
    }

    @Test
    void unsafeIdsAndNamespacesAreRefusedOrContained() throws Exception {
        assertThrows(IOException.class, () -> AuthoredStageFiles.files(root(), "mypack", "../../ops"));
        // A namespace that walks falls back to the mod's own: the write still lands inside the pack.
        AuthoredStageFiles.Files3 files = AuthoredStageFiles.files(root(), "../..", "iron_age");
        assertTrue(files.definition().startsWith(root().resolve("data/questqueen")));
    }
}
