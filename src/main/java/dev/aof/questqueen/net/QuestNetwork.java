package dev.aof.questqueen.net;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.aof.questqueen.QuestQueen;
import dev.aof.questqueen.data.BookChrome;
import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.QuestDefinitions;
import dev.aof.questqueen.data.QuestPack;
import dev.aof.questqueen.progress.ProgressService;
import dev.aof.questqueen.progress.ProgressSnapshot;
import dev.aof.questqueen.task.TaskHooks;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.storage.LevelResource;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.handling.IPayloadContext;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class QuestNetwork {
    public static final int CHUNK = 900_000;
    private static final Map<String, List<byte[]>> CLIENT_CHUNKS = new ConcurrentHashMap<>();

    private QuestNetwork() {
    }

    public static void register(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("2");
        registrar.playToClient(DefinitionChunkS2C.TYPE, DefinitionChunkS2C.STREAM_CODEC, QuestNetwork::handleDefinition);
        registrar.playToClient(ProgressS2C.TYPE, ProgressS2C.STREAM_CODEC, QuestNetwork::handleProgress);
        registrar.playToClient(EditorSessionS2C.TYPE, EditorSessionS2C.STREAM_CODEC, QuestNetwork::handleEditorSession);
        registrar.playToClient(OpenBookS2C.TYPE, OpenBookS2C.STREAM_CODEC, QuestNetwork::handleOpenBook);
        registrar.playToServer(SubmitTaskC2S.TYPE, SubmitTaskC2S.STREAM_CODEC, QuestNetwork::handleSubmit);
        registrar.playToServer(PinC2S.TYPE, PinC2S.STREAM_CODEC, QuestNetwork::handlePin);
        registrar.playToServer(AuthorSaveC2S.TYPE, AuthorSaveC2S.STREAM_CODEC, QuestNetwork::handleAuthorSave);
        registrar.playToServer(AuthorChromeC2S.TYPE, AuthorChromeC2S.STREAM_CODEC, QuestNetwork::handleAuthorChrome);
        registrar.playToServer(ClaimChoiceC2S.TYPE, ClaimChoiceC2S.STREAM_CODEC, QuestNetwork::handleClaim);
        registrar.playToServer(ClaimRewardsC2S.TYPE, ClaimRewardsC2S.STREAM_CODEC, QuestNetwork::handleClaimRewards);
        registrar.playToServer(ClaimAllC2S.TYPE, ClaimAllC2S.STREAM_CODEC, QuestNetwork::handleClaimAll);
    }

    public static void sendDefinitions(ServerPlayer player) {
        String json = QuestPack.CODEC.encodeStart(JsonOps.INSTANCE, QuestDefinitions.pack())
                .getOrThrow(RuntimeException::new)
                .toString();
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        int total = Math.max(1, (bytes.length + CHUNK - 1) / CHUNK);
        int session = player.getId() ^ json.hashCode();
        for (int i = 0; i < total; i++) {
            int start = i * CHUNK;
            int end = Math.min(bytes.length, start + CHUNK);
            byte[] slice = new byte[end - start];
            System.arraycopy(bytes, start, slice, 0, slice.length);
            PacketDistributor.sendToPlayer(player, new DefinitionChunkS2C(session, i, total, slice));
        }
    }

    public static void sendProgress(ServerPlayer player, ProgressSnapshot snapshot) {
        PacketDistributor.sendToPlayer(player, ProgressS2C.of(snapshot));
    }

    public static void sendEditorSession(ServerPlayer player, boolean enabled) {
        PacketDistributor.sendToPlayer(player, new EditorSessionS2C(enabled));
    }

    /** Legacy shape: no chapter change. Kept so existing callers keep working. */
    public static void sendOpenBook(ServerPlayer player, String tileId, boolean expanded) {
        sendOpenBook(player, tileId, expanded, "");
    }

    /**
     * @param chapter explicit ResourceLocation string, or "" to keep the client's current chapter.
     *                Never derived from {@code tileId} (Worf D-3): the pack's 34 chapters reuse tile ids
     *                (18 of them collide across chapters), so a derived chapter is ambiguous.
     */
    public static void sendOpenBook(ServerPlayer player, String tileId, boolean expanded, String chapter) {
        PacketDistributor.sendToPlayer(player, new OpenBookS2C(
                tileId == null ? "" : tileId,
                expanded,
                chapter == null ? "" : chapter));
    }

    /** A definition transfer cut off by a disconnect must not be merged with the next server's chunks. */
    public static void clearClientChunks() {
        CLIENT_CHUNKS.clear();
    }

    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    private static void handleDefinition(DefinitionChunkS2C payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            String key = Integer.toString(payload.session());
            List<byte[]> parts = CLIENT_CHUNKS.computeIfAbsent(key, ignored -> new ArrayList<>());
            while (parts.size() <= payload.index()) {
                parts.add(null);
            }
            parts.set(payload.index(), payload.data());
            if (parts.stream().filter(part -> part != null).count() == payload.total()) {
                int size = parts.stream().mapToInt(part -> part.length).sum();
                byte[] all = new byte[size];
                int offset = 0;
                for (byte[] part : parts) {
                    System.arraycopy(part, 0, all, offset, part.length);
                    offset += part.length;
                }
                CLIENT_CHUNKS.remove(key);
                String json = new String(all, StandardCharsets.UTF_8);
                QuestPack pack = QuestPack.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json))
                        .getOrThrow(RuntimeException::new);
                ClientSync.applyDefinitions(pack);
            }
        });
    }

    private static void handleProgress(ProgressS2C payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientSync.applyProgress(payload.decode()));
    }

    private static void handleEditorSession(EditorSessionS2C payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientSync.applyEditor(payload.enabled()));
    }

    private static void handleOpenBook(OpenBookS2C payload, IPayloadContext context) {
        context.enqueueWork(() -> ClientSync.applyOpenBook(payload.tileId(), payload.expanded(), payload.chapter()));
    }

    private static void handleSubmit(SubmitTaskC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                // Task completion only — rewards wait for ClaimRewardsC2S / ClaimChoiceC2S so the book
                // can show CLAIM or TAKE A·B. Do not late-claim here.
                TaskHooks.trySubmit(player, payload.chapter(), payload.tile(), payload.taskIndex());
            }
        });
    }

    private static void handlePin(PinC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                if (payload.clear()) {
                    ProgressService.unpin(player);
                } else if (ProgressService.canPin(player, payload.chapter(), payload.tile())) {
                    ProgressService.pin(player, payload.chapter(), payload.tile());
                }
            }
        });
    }

    private static void handleClaim(ClaimChoiceC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ProgressService.claimChoice(player, payload.chapter(), payload.tile(), payload.option());
            }
        });
    }

    private static void handleClaimRewards(ClaimRewardsC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && ProgressService.canClaimRewards(player, payload.chapter(), payload.tile())) {
                ProgressService.claimTileRewards(player, payload.chapter(), payload.tile());
            }
        });
    }

    private static void handleClaimAll(ClaimAllC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player) {
                ProgressService.claimChapterRewards(player, payload.chapter());
            }
        });
    }

    private static void handleAuthorSave(AuthorSaveC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !ProgressService.canAuthor(player)) {
                return;
            }
            try {
                Chapter chapter = Chapter.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(payload.json()))
                        .getOrThrow(RuntimeException::new);
                writeAuthored(player, chapter);
                QuestDefinitions.putChapter(chapter);
                player.sendSystemMessage(Component.translatable("questqueen.authored", chapter.id().toString()));
            } catch (Exception exception) {
                QuestQueen.LOGGER.error("Failed to apply authored chapter", exception);
                player.sendSystemMessage(Component.literal(
                        "Quest Queen: chapter save failed — " + exception.getMessage()));
            }
        });
    }

    private static void handleAuthorChrome(AuthorChromeC2S payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player) || !ProgressService.canAuthor(player)) {
                return;
            }
            BookChrome chrome = payload.chrome();
            try {
                writeAuthoredChrome(player.server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("questqueen_authored"), chrome);
            } catch (Exception exception) {
                // The live edit still applies; only the copy that survives /reload failed.
                QuestQueen.LOGGER.error("Failed to write authored book chrome", exception);
            }
            QuestDefinitions.putChrome(chrome);
        });
    }

    /**
     * Writes the book chrome as {@code data/questqueen/questqueen/book.json} in the authored pack. World
     * datapacks sit above mod resources, so this file replaces the jar's own book.json on the next reload.
     */
    static Path writeAuthoredChrome(Path root, BookChrome chrome) throws Exception {
        Path file = root.resolve("data").resolve(QuestQueen.MODID).resolve("questqueen").resolve("book.json");
        String json = BookChrome.CODEC.encodeStart(JsonOps.INSTANCE, chrome).getOrThrow(RuntimeException::new).toString();
        return writeAuthoredJson(root, file, "book chrome", json);
    }

    private static void writeAuthored(ServerPlayer player, Chapter chapter) throws Exception {
        writeAuthoredChapter(player.server.getWorldPath(LevelResource.DATAPACK_DIR).resolve("questqueen_authored"), chapter);
    }

    /**
     * Writes an authored chapter into {@code root} — the {@code questqueen_authored} datapack folder —
     * and returns the file it landed in. No {@code ServerPlayer} is needed here, so the confinement
     * below can be attacked from a unit test rather than argued about.
     *
     * <p>Ordering is the W-2 fix: the id is resolved and refused BEFORE anything is created, and every
     * level this call creates is recorded, so a refused or failed save removes its own traces.
     * Containment is checked HERE, in the state the path will actually be written in (the W-1 fix):
     * the parent exists by then, so {@code toRealPath()} really resolves a link sitting between the
     * root and the file — {@code getCanonicalFile()} did not (its junction handling even differs
     * between JDK 21 and 25; {@code toRealPath()} resolves junctions on both, measured). The check
     * and the write are not atomic — a filesystem change inside that window is latent and requires
     * local access; it is stated, not closed.
     */
    static Path writeAuthoredChapter(Path root, Chapter chapter) throws Exception {
        Path file = authoredChapterFile(root, chapter.id());
        String json = Chapter.CODEC.encodeStart(JsonOps.INSTANCE, chapter)
                .getOrThrow(RuntimeException::new)
                .toString();
        return writeAuthoredJson(root, file, "authored chapter '" + chapter.id() + "'", json);
    }

    /** The containment-checked, rolled-back write shared by chapters and the book chrome. */
    private static Path writeAuthoredJson(Path root, Path file, String what, String json) throws Exception {
        List<Path> created = new ArrayList<>();
        Path wroteMcmeta = null;
        boolean fileExisted = false;
        try {
            createChain(file.getParent(), created);
            Path realRoot = root.toRealPath();
            Path realParent = file.getParent().toRealPath();
            if (!realParent.startsWith(realRoot)) {
                throw new IOException("Refusing " + what + ": " + realParent
                        + " resolves outside " + realRoot + " — a link sits between the pack root and the file");
            }
            if (Files.isSymbolicLink(file)) {
                throw new IOException("Refusing " + what + ": " + file + " is a symbolic link");
            }
            Path mcmeta = root.resolve("pack.mcmeta");
            if (!Files.exists(mcmeta)) {
                Files.writeString(mcmeta, """
                        {
                          "pack": {
                            "pack_format": 48,
                            "description": "Quest Queen authored chapters"
                          }
                        }
                        """);
                wroteMcmeta = mcmeta;
            }
            fileExisted = Files.exists(file);
            Files.writeString(file, json);
            return file;
        } catch (Exception failure) {
            rollback(created, wroteMcmeta, fileExisted ? null : file);
            throw failure;
        }
    }

    /**
     * Creates every missing level on the way down to {@code target}, recording each level THIS call
     * creates (shallowest first). Levels that already exist are skipped — including links: a link is
     * skipped, its target is walked through, and the containment check afterwards is what refuses
     * the write when the walk left the pack root.
     */
    private static void createChain(Path target, List<Path> created) throws IOException {
        List<Path> missing = new ArrayList<>();
        for (Path cursor = target; cursor != null && !Files.exists(cursor); cursor = cursor.getParent()) {
            missing.add(cursor);
        }
        for (int i = missing.size() - 1; i >= 0; i--) {
            Files.createDirectory(missing.get(i));
            created.add(missing.get(i));
        }
    }

    /**
     * Removes what a failed or refused save created, deepest first, and only that: a chapter file
     * that already existed is never deleted (its old bytes are beyond restoring anyway — writeString
     * truncates, and this change deliberately adds no atomic move), and a pre-existing level that
     * has acquired foreign content is left where it is rather than emptied to look clean.
     */
    private static void rollback(List<Path> created, Path wroteMcmeta, Path fileToRemove) {
        if (fileToRemove != null) {
            try {
                Files.deleteIfExists(fileToRemove);
            } catch (IOException ignored) {
                // best effort — the save is already failing on its own account
            }
        }
        if (wroteMcmeta != null) {
            try {
                Files.deleteIfExists(wroteMcmeta);
            } catch (IOException ignored) {
                // best effort
            }
        }
        for (int i = created.size() - 1; i >= 0; i--) {
            try {
                Files.deleteIfExists(created.get(i));
            } catch (IOException ignored) {
                // best effort — a level that has acquired foreign content stays
            }
        }
    }

    /**
     * Resolves the file an authored chapter id writes to, confined to {@code <root>/data}.
     *
     * <p>The id is not a trustworthy path fragment: Minecraft's own {@code ResourceLocation} admits
     * {@code '.'} and {@code '/'} ({@code isValidPath} allows both), so
     * {@code questqueen:../../../../../../ops} parses as a well-formed id and would otherwise write
     * outside the datapack — any {@code .json} the server process can reach, plus the directories on
     * the way. This method refuses any {@code ".."} segment and requires the normalized path to stay
     * under {@code data}; on those two checks alone it is deliberately pure — it touches nothing.
     *
     * <p>It does NOT check canonical containment, and cannot: it runs before the target exists, and a
     * canonical answer for a nonexistent path is the lexical path — the inversion that let a junction
     * between the base and the file pass the old check even though it "re-checked in canonical form"
     * (W-1; the junction is neither resolved by {@code getCanonicalFile()} on the shipped JDK 21, nor
     * caught by {@code isSymbolicLink}). Canonical containment is enforced by
     * {@link #writeAuthoredChapter} on the resolved parent, in the state the file will be written in.
     */
    static Path authoredChapterFile(Path root, ResourceLocation id) throws IOException {
        Path base = root.resolve("data").normalize();
        Path file = base.resolve(id.getNamespace())
                .resolve("questqueen")
                .resolve("chapters")
                .resolve(id.getPath() + ".json")
                .normalize();
        boolean contained = file.startsWith(base);
        if (!contained || hasDotDotSegment(id.getNamespace()) || hasDotDotSegment(id.getPath())) {
            throw new IOException("Refusing authored chapter '" + id + "': it must stay under " + base);
        }
        return file;
    }

    /** True when a ResourceLocation fragment carries a {@code ".."} segment — the id must not walk up. */
    private static boolean hasDotDotSegment(String fragment) {
        for (String segment : fragment.split("/", -1)) {
            if ("..".equals(segment)) {
                return true;
            }
        }
        return false;
    }
}
