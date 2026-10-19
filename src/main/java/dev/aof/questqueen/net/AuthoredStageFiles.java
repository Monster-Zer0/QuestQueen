package dev.aof.questqueen.net;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.aof.questqueen.data.stage.AuthoredStage;
import dev.aof.questqueen.data.stage.StageLockFiles;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * Where an authored stage lands in the {@code questqueen_authored} datapack, and the write and delete of its three
 * files. Writes go through {@link QuestNetwork#writeAuthoredJson}, the same containment-checked, rolled-back write
 * chapters use; the id is turned into a path by {@link StageLockFiles#path}, which refuses anything that walks up.
 */
final class AuthoredStageFiles {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** The editor's own copy, StageLock's definition, and StageLock's rules, in that order. */
    record Files3(Path authored, Path definition, Path restrictions) {
        List<Path> all() {
            return List.of(authored, definition, restrictions);
        }
    }

    private AuthoredStageFiles() {
    }

    static Files3 files(Path root, String namespace, String id) throws IOException {
        String ns = QuestNetwork.safeNamespace(namespace);
        String path = StageLockFiles.path(id)
                .orElseThrow(() -> new IOException("Refusing stage '" + id + "': it is not a safe file name"));
        Path data = root.resolve("data").normalize();
        Path base = data.resolve(ns);
        Files3 files = new Files3(
                base.resolve("questqueen").resolve("stages").resolve(path + ".json").normalize(),
                base.resolve("stagelock").resolve("stages").resolve(path + ".json").normalize(),
                base.resolve("stagelock").resolve("restrictions").resolve("questqueen").resolve(path + ".json").normalize());
        for (Path file : files.all()) {
            if (!file.startsWith(data)) {
                throw new IOException("Refusing stage '" + id + "': it must stay under " + data);
            }
        }
        return files;
    }

    static Files3 write(Path root, String namespace, AuthoredStage stage) throws Exception {
        Files3 files = files(root, namespace, stage.id());
        JsonElement authored = AuthoredStage.CODEC.encodeStart(JsonOps.INSTANCE, stage).getOrThrow(RuntimeException::new);
        QuestNetwork.writeAuthoredJson(root, files.authored(), "stage '" + stage.id() + "'", GSON.toJson(authored));
        QuestNetwork.writeAuthoredJson(root, files.definition(), "stage definition '" + stage.id() + "'",
                GSON.toJson(StageLockFiles.definition(stage)));
        JsonObject rules = StageLockFiles.restrictions(stage);
        if (rules.getAsJsonArray("rules").isEmpty()) {
            deleteContained(root, files.restrictions());
        } else {
            QuestNetwork.writeAuthoredJson(root, files.restrictions(), "stage rules '" + stage.id() + "'", GSON.toJson(rules));
        }
        return files;
    }

    /** True when the editor's copy of the stage was there to delete. */
    static boolean delete(Path root, String namespace, String id) throws IOException {
        Files3 files = files(root, namespace, id);
        boolean existed = Files.exists(files.authored());
        for (Path file : files.all()) {
            deleteContained(root, file);
        }
        return existed;
    }

    /** Deletes a file only when it really sits inside the pack and is not a link. */
    private static void deleteContained(Path root, Path file) throws IOException {
        if (!Files.exists(file) && !Files.isSymbolicLink(file)) {
            return;
        }
        if (Files.isSymbolicLink(file)) {
            throw new IOException("Refusing to delete " + file + ": it is a symbolic link");
        }
        Path realRoot = root.toRealPath();
        if (!file.getParent().toRealPath().startsWith(realRoot)) {
            throw new IOException("Refusing to delete " + file + ": it resolves outside " + realRoot);
        }
        Files.delete(file);
    }
}
