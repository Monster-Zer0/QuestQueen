package dev.aof.questqueen.net;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.aof.questqueen.data.BookChrome;
import dev.aof.questqueen.data.Chapter;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * QQ-1 — an authored chapter save must not follow the chapter id out of its datapack folder,
 * and a save that is refused must leave the case tree exactly as it was.
 *
 * <p>Original round (t_9cda1938): {@code QuestNetwork.writeAuthored} resolved
 * {@code id.getNamespace()} and {@code id.getPath()} straight into a filesystem path.
 * Minecraft's own {@code ResourceLocation} admits {@code '.'} and {@code '/'}, so
 * {@code questqueen:../../../../../../ops} parses as a well-formed id — the codec is not a path
 * guard — and an operator could overwrite any {@code .json} the server process can reach.
 *
 * <p>Successor round (t_c690e90e), from Worf's non-author attack: the first fix checked
 * containment with {@code File.getCanonicalFile()} BEFORE the target existed, and a canonical
 * answer for a nonexistent path is the lexical path — so a junction between the base and the
 * file passed the check exactly where it had to fail (W-1), and a refused save still left
 * {@code <root>} plus {@code pack.mcmeta} behind (W-2). These failures are why the check now
 * runs on the resolved parent at write time, and why every refused save below is measured by a
 * BEFORE/AFTER INVENTORY of the whole case tree rather than by targeted assertions: a targeted
 * assertion cannot see the entry nobody thought of.
 *
 * <p>The link cases are created with {@code mklink /J}. If junctions cannot be created the
 * suite FAILS LOUDLY — never skips — because a security test that can skip is a control whose
 * regression is invisible in a clean-looking tally (Worf R3).
 */
class QuestNetworkAuthoredSaveTest {

    @TempDir
    Path temp;

    /** The authored-datapack root, as {@code writeAuthored} computes it under a world folder. */
    private Path root() {
        return temp.resolve("datapacks").resolve("questqueen_authored");
    }

    private static ResourceLocation id(String raw) {
        ResourceLocation parsed = ResourceLocation.tryParse(raw);
        assertNotNull(parsed, "ResourceLocation rejected '" + raw + "' — re-derive the QQ-1 premise");
        return parsed;
    }

    private static String encoded(Chapter chapter) {
        return Chapter.CODEC.encodeStart(JsonOps.INSTANCE, chapter)
                .getOrThrow(RuntimeException::new).toString();
    }

    /**
     * Creates a directory junction, or FAILS. Skipping would hide the W-1 regression this file
     * exists to pin: on a host without junction support the suite must be red, not quietly green.
     */
    private static void createJunction(Path link, Path target) throws Exception {
        Process process = new ProcessBuilder("cmd", "/c", "mklink", "/J", link.toString(), target.toString())
                .redirectErrorStream(true)
                .start();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        int rc = process.waitFor();
        if (rc != 0 || !Files.exists(link, LinkOption.NOFOLLOW_LINKS)) {
            fail("mklink /J could not create " + link + " -> " + target + " (rc=" + rc + ", output='" + output
                    + "'). This suite does NOT skip the junction cases: without them the W-1 link"
                    + " regression is undetectable and a green tally would be lying (Worf R3).");
        }
    }

    /**
     * Every entry under {@code base}, relative, INCLUDING {@code base} itself. The net-zero
     * measure: refused saves are compared by this, not by guessing which paths to assert absent.
     */
    private static Set<String> inventory(Path base) throws IOException {
        if (!Files.exists(base)) {
            return Set.of();
        }
        try (var walk = Files.walk(base)) {
            return walk.map(path -> base.relativize(path).toString()).collect(Collectors.toSet());
        }
    }

    @Test
    void dotDotIsAWellFormedResourceLocation() {
        assertNotNull(ResourceLocation.tryParse("questqueen:../../../../../../ops"),
                "premise: '.' and '/' are legal path characters, so a '..' chapter id parses");
        assertNotNull(ResourceLocation.tryParse("..:chapter"),
                "premise: '.' is a legal namespace character");
        assertNotNull(ResourceLocation.tryParse("questqueen:.."),
                "premise: '..' alone is a legal path");
    }

    @Test
    void pathTraversalIsRefusedAndWritesNothing() throws Exception {
        Path root = root();
        try {
            QuestNetwork.writeAuthoredChapter(root, Chapter.blank(id("questqueen:../../../../../../ops")));
            fail("a traversing chapter id was written instead of refused");
        } catch (IOException refused) {
            // required: refusal, not a write
        }
        assertFalse(Files.exists(temp.resolve("ops.json")),
                "the refused write must not create the escaped file");
        assertFalse(Files.exists(root.resolve("ops.json")),
                "the refused write must not create the escaped file under the authored root");
    }

    @Test
    void namespaceTraversalIsRefused() {
        Path root = root();
        assertThrows(IOException.class,
                () -> QuestNetwork.writeAuthoredChapter(root, Chapter.blank(id("..:chapter"))));
        assertFalse(Files.exists(root.resolve("questqueen")),
                "no directory may be created outside <root>/data by a refused save");
    }

    @Test
    void bareDotDotIdIsRefused() {
        assertThrows(IOException.class,
                () -> QuestNetwork.writeAuthoredChapter(root(), Chapter.blank(id("questqueen:.."))));
    }

    @Test
    void anyDotDotSegmentIsRefusedEvenWhenItNormalizesBackInside() {
        // 'a/../b' would normalize to a confined path; it is still refused, because confinement must
        // not rest on normalize() semantics for an id that carries a '..' at all.
        assertThrows(IOException.class,
                () -> QuestNetwork.writeAuthoredChapter(root(), Chapter.blank(id("questqueen:a/../b"))));
    }

    @Test
    void aLegitimateSaveStillLandsWhereItAlwaysDid() throws Exception {
        Path root = root();
        Chapter legitimate = Chapter.blank(id("questqueen:my_chapter"));
        Path written = QuestNetwork.writeAuthoredChapter(root, legitimate);
        // The pre-fix formula, verbatim: the fix must not move a legitimate chapter file.
        Path expected = root.resolve("data").resolve("questqueen").resolve("questqueen")
                .resolve("chapters").resolve("my_chapter.json");
        assertEquals(expected, written, "a legitimate save must go to the same file as before the fix");
        assertTrue(Files.exists(expected));
        String onDisk = Files.readString(expected);
        String encoded = Chapter.CODEC.encodeStart(JsonOps.INSTANCE, legitimate)
                .getOrThrow(RuntimeException::new).toString();
        assertEquals(encoded, onDisk, "a legitimate save must be byte-for-byte what the codec produced");
        Chapter roundTripped = Chapter.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(onDisk))
                .getOrThrow(RuntimeException::new);
        assertEquals(legitimate.id(), roundTripped.id());
        assertEquals(legitimate.tiles().size(), roundTripped.tiles().size());
        assertTrue(Files.exists(root.resolve("pack.mcmeta")),
                "pack.mcmeta is still written for the datapack");
    }

    @Test
    void aSubdirectoryResourcePathIsStillAllowed() throws Exception {
        // '/' inside a path is legal and must keep working; only walking UP is refused.
        Path root = root();
        Path written = QuestNetwork.writeAuthoredChapter(root, Chapter.blank(id("questqueen:act_i/prologue")));
        assertEquals(root.resolve("data").resolve("questqueen").resolve("questqueen")
                .resolve("chapters").resolve("act_i").resolve("prologue.json"), written);
        assertTrue(Files.exists(written));
    }

    // ---- successor round (t_c690e90e): W-1 junctions, W-2 no-trace refusals, W-3 class ----

    @Test
    void aRefusedIdLeavesTheCaseTreeUntouched() throws Exception {
        Set<String> before = inventory(temp);
        assertThrows(IOException.class,
                () -> QuestNetwork.writeAuthoredChapter(root(), Chapter.blank(id("questqueen:../../../../../../ops"))));
        assertEquals(before, inventory(temp),
                "W-2: a refused id must not create so much as the case root or pack.mcmeta — measured by inventory");
    }

    @Test
    void aJunctionAtTheDataBaseIsRefusedAndLeavesNoTrace() throws Exception {
        // Worf §6.1: a junction AT <root>/data. Refused before this round; the fix must keep it refused.
        Path caseRoot = root();
        Files.createDirectories(caseRoot);
        Path outside = temp.resolve("outside");
        Files.createDirectories(outside);
        Files.writeString(outside.resolve("canary.txt"), "canary");
        createJunction(caseRoot.resolve("data"), outside);
        Set<String> before = inventory(temp);
        assertThrows(IOException.class,
                () -> QuestNetwork.writeAuthoredChapter(caseRoot, Chapter.blank(id("questqueen:chapter"))),
                "the data position must stay refused");
        assertEquals(before, inventory(temp), "nothing may be added through the junction");
        assertTrue(Files.exists(outside.resolve("canary.txt")), "the canary must survive untouched");
    }

    @Test
    void aJunctionAtTheNamespaceLevelIsRefusedAndLeavesNoTrace() throws Exception {
        Path caseRoot = root();
        Files.createDirectories(caseRoot.resolve("data"));
        Path outside = temp.resolve("outside");
        Files.createDirectories(outside);
        Files.writeString(outside.resolve("canary.txt"), "canary");
        createJunction(caseRoot.resolve("data").resolve("questqueen"), outside);
        Set<String> before = inventory(temp);
        assertThrows(IOException.class,
                () -> QuestNetwork.writeAuthoredChapter(caseRoot, Chapter.blank(id("questqueen:chapter"))));
        assertEquals(before, inventory(temp), "nothing may be added through the junction");
        assertTrue(Files.exists(outside.resolve("canary.txt")), "the canary must survive untouched");
    }

    @Test
    void aJunctionInsideTheNamespaceIsRefusedAndLeavesNoTrace() throws Exception {
        // Worf's original W-1 position: <root>/data/questqueen/questqueen -> OUTSIDE.
        Path caseRoot = root();
        Files.createDirectories(caseRoot.resolve("data").resolve("questqueen"));
        Path outside = temp.resolve("outside");
        Files.createDirectories(outside);
        Files.writeString(outside.resolve("canary.txt"), "canary");
        createJunction(caseRoot.resolve("data").resolve("questqueen").resolve("questqueen"), outside);
        Set<String> before = inventory(temp);
        assertThrows(IOException.class,
                () -> QuestNetwork.writeAuthoredChapter(caseRoot, Chapter.blank(id("questqueen:chapter"))));
        assertEquals(before, inventory(temp), "nothing may be added through the junction");
        assertTrue(Files.exists(outside.resolve("canary.txt")), "the canary must survive untouched");
    }

    @Test
    void aJunctionAtTheChaptersDirectoryIsRefusedAndLeavesNoTrace() throws Exception {
        Path caseRoot = root();
        Files.createDirectories(caseRoot.resolve("data").resolve("questqueen").resolve("questqueen"));
        Path outside = temp.resolve("outside");
        Files.createDirectories(outside);
        Files.writeString(outside.resolve("canary.txt"), "canary");
        createJunction(caseRoot.resolve("data").resolve("questqueen").resolve("questqueen").resolve("chapters"), outside);
        Set<String> before = inventory(temp);
        assertThrows(IOException.class,
                () -> QuestNetwork.writeAuthoredChapter(caseRoot, Chapter.blank(id("questqueen:chapter"))));
        assertEquals(before, inventory(temp), "nothing may be added through the junction");
        assertTrue(Files.exists(outside.resolve("canary.txt")), "the canary must survive untouched");
    }

    @Test
    void aJunctionAtTheRootItselfIsAllowedAndLandsAtTheResolvedTarget() throws Exception {
        // Worf's condition on design item 4: the root-link allowance is a POSITIVE CONTROL, not
        // just a permission — a legitimate save must land at the RESOLVED target, byte-identical,
        // and a second, DIFFERENT chapter must land too (the old code's behaviour here was
        // inconsistent between its first and later saves; that is what this exercises).
        Path realRoot = temp.resolve("realroot");
        Files.createDirectories(realRoot);
        Files.createDirectories(temp.resolve("datapacks"));
        Path caseRoot = root();
        createJunction(caseRoot, realRoot);

        Chapter first = Chapter.blank(id("questqueen:first_chapter"));
        Path writtenFirst = QuestNetwork.writeAuthoredChapter(caseRoot, first);
        Path expectedFirst = realRoot.resolve("data").resolve("questqueen").resolve("questqueen")
                .resolve("chapters").resolve("first_chapter.json");
        assertTrue(Files.exists(expectedFirst), "with a link at root, the save must land at the RESOLVED target");
        assertEquals(encoded(first), Files.readString(expectedFirst),
                "the chapter at the resolved target must be byte-identical to the codec output");
        assertEquals(caseRoot.resolve("data").resolve("questqueen").resolve("questqueen")
                .resolve("chapters").resolve("first_chapter.json"), writtenFirst,
                "the returned path keeps the caller-visible shape");

        Chapter second = Chapter.blank(id("questqueen:second_chapter"));
        Path writtenSecond = QuestNetwork.writeAuthoredChapter(caseRoot, second);
        Path expectedSecond = realRoot.resolve("data").resolve("questqueen").resolve("questqueen")
                .resolve("chapters").resolve("second_chapter.json");
        assertTrue(Files.exists(expectedSecond), "a second new chapter must also land");
        assertEquals(encoded(second), Files.readString(expectedSecond));
        assertEquals(caseRoot.resolve("data").resolve("questqueen").resolve("questqueen")
                .resolve("chapters").resolve("second_chapter.json"), writtenSecond,
                "the returned path keeps the caller-visible shape");
    }

    @Test
    void anOverlongSingleSegmentIdLeavesNoTrace() throws Exception {
        ResourceLocation overlong = ResourceLocation.tryParse("questqueen:" + "x".repeat(271));
        assertNotNull(overlong, "premise: a 271-char single-segment id still parses (Worf §4)");
        Set<String> before = inventory(temp);
        assertThrows(IOException.class,
                () -> QuestNetwork.writeAuthoredChapter(root(), Chapter.blank(overlong)),
                "the filesystem must refuse a 276-char filename, not silently truncate");
        assertEquals(before, inventory(temp),
                "W-3 class: a save the filesystem refuses must not leave a half-built tree");
    }

    @Test
    void anObstacleInsideTheCaseTreeNeverLeavesMoreThanBefore() throws Exception {
        // Residual (b), sharpened: a pre-existing obstacle (here a FILE named 'chapters') blocks the
        // write. The old code left root+pack.mcmeta behind on this path; the fix must not leave MORE.
        Path caseRoot = root();
        Path chaptersFile = caseRoot.resolve("data").resolve("questqueen").resolve("questqueen").resolve("chapters");
        Files.createDirectories(chaptersFile.getParent());
        Files.writeString(chaptersFile, "obstacle");
        Set<String> before = inventory(temp);
        assertThrows(IOException.class,
                () -> QuestNetwork.writeAuthoredChapter(caseRoot, Chapter.blank(id("questqueen:blocked_chapter"))));
        Set<String> added = new HashSet<>(inventory(temp));
        added.removeAll(before);
        assertFalse(added.contains("datapacks/questqueen_authored/pack.mcmeta"),
                "the refusal must not leave pack.mcmeta behind (W-2)");
        assertFalse(Files.exists(chaptersFile.resolve("blocked_chapter.json")),
                "no chapter may land behind the obstacle");
        assertTrue(added.isEmpty(),
                "no new entry may remain when the write failed — measured by inventory, added=" + added);
        assertTrue(Files.exists(chaptersFile) && "obstacle".equals(Files.readString(chaptersFile)),
                "the pre-existing obstacle must never be deleted or altered");
    }

    @Test
    void bookChromeLandsWhereTheReloadListenerReadsIt() throws Exception {
        BookChrome chrome = new BookChrome("Saga", 0xFF123456, 1.5f, true, false);
        Path file = QuestNetwork.writeAuthoredChrome(root(), chrome);
        assertEquals(root().resolve("data").resolve("questqueen").resolve("questqueen").resolve("book.json"), file);
        assertTrue(Files.exists(root().resolve("pack.mcmeta")), "the authored pack needs its mcmeta to load");
        BookChrome read = BookChrome.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(Files.readString(file)))
                .getOrThrow(RuntimeException::new);
        assertEquals(chrome, read);
    }

    @Test
    void craftedChromePacketIsClampedIntoRange() {
        BookChrome chrome = new AuthorChromeC2S("", 0, Float.NaN, false, true).chrome();
        assertEquals(BookChrome.DEFAULT.sidebarTitle(), chrome.sidebarTitle(), "a blank title falls back");
        assertEquals(BookChrome.DEFAULT.titleScale(), chrome.titleScale(), "NaN must not survive the clamp");
        assertEquals(2.0f, new AuthorChromeC2S("x", 0, 99f, false, true).chrome().titleScale());
        assertEquals(AuthorChromeC2S.MAX_TITLE,
                AuthorChromeC2S.of(BookChrome.DEFAULT.withTitle("y".repeat(500))).sidebarTitle().length());
    }
}
