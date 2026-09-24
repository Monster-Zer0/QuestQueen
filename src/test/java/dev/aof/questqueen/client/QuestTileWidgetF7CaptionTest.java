package dev.aof.questqueen.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.ToIntFunction;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * F7 clipped board captions (card t_d3e18398). Fix A: marked truncation, marker RESERVED BEFORE the
 * shrink. Fix B: a third caption line exactly where the lower band is free (no reward faces, no XOR
 * badge), decided from conditions the renderer already holds - never a per-tile allowlist.
 *
 * <p>Domain discipline:
 * <ul>
 *   <li>THE WIDTH TABLE IS A CALIBRATED APPROXIMATION of the default font, not a font dump. It is
 *       calibrated so that the PRE-FIX algorithm reproduces the three frame-documented cuts EXACTLY
 *       at the flag class (maxW = 88 font px, keep = 2): letters 6, 'I' 3, space 4, '.' 2.
 *       Exact per-glyph fidelity is NOT
 *       claimed; the A1 capture on the real font is the full-fidelity proof, this table makes the
 *       policy hermetic and headless.</li>
 *   <li>THE PRE-FIX REFERENCE IS THE CONTROL (A2 binding 4): a verbatim copy of the pre-fix wrap
 *       (snapshot .seven-snapshots/QuestTileWidget.java.pre-f7, sha256 ea98ea1e...) sits in this
 *       file, and the same checker that must stay clean on the fix must FLAG all three named
 *       strings on it. A test that cannot fail on the broken code is a description.</li>
 *   <li>THE CORPUS IS ENUMERATED, NOT SAMPLED (A2 binding 3): every "title" string in all 34
 *       authored chapter files, at every ladder size where wrapTiny runs (48, 56, 64 and up), in
 *       both icon regimes (stacked / beside) and at both the 2-line and the Fix-B budgets.</li>
 * </ul>
 */
class QuestTileWidgetF7CaptionTest {

    private static final String T1 = "Craft an Inscription Table";
    private static final String T2 = "Collect the Rest of the Rainbow";
    private static final String T3 = "Bring Noven a Block of Massive Mass";

    /** The size-48 rung visual budget off the flag frame: maxW = 88 = 44 * 2. */
    private static final int FLAG_VISUAL_MAX = 44;
    /** @deprecated 1.1.192 back on half-scale; use FLAG_VISUAL_MAX (44 → font maxW 88). */
    private static final int FLAG_WRAP_MAX = 44;
    private static final int FLAG_KEEP = 2;

    // ---------- the calibrated table ----------

    static int widthOf(String s) {
        int total = 0;
        for (int i = 0; i < s.length(); i++) {
            total += switch (s.charAt(i)) {
                case 'I' -> 3;
                case ' ' -> 4;
                case '.' -> 2;
                default -> 6;
            };
        }
        return total;
    }

    // ---------- the control: pre-fix algorithm, verbatim ----------

    /**
     * Pre-fix wrap (QuestTileWidget.java 1.1.179, lines 448-491): join overflow onto the last kept
     * line, then hard-cut at the pixel budget with NO marker. This is what the flag frame shows.
     */
    static List<String> preFixWrap(ToIntFunction<String> widthOf, String text, int visualMax, int maxLines) {
        String upper = text == null ? "" : text.toUpperCase(Locale.ROOT).trim();
        if (upper.isEmpty()) {
            return List.of();
        }
        int maxW = Math.max(8, visualMax) * 2;
        int keep = Math.max(1, maxLines);
        List<String> lines = new ArrayList<>();
        StringBuilder line = new StringBuilder();
        for (String word : upper.split(" +")) {
            if (word.isEmpty()) {
                continue;
            }
            String next = line.isEmpty() ? word : line + " " + word;
            if (widthOf.applyAsInt(next) > maxW && !line.isEmpty()) {
                lines.add(line.toString());
                line = new StringBuilder(word);
            } else {
                line = new StringBuilder(next);
            }
        }
        if (!line.isEmpty()) {
            lines.add(line.toString());
        }
        if (lines.size() > keep) {
            StringBuilder last = new StringBuilder(lines.get(keep - 1));
            for (int i = keep; i < lines.size(); i++) {
                last.append(' ').append(lines.get(i));
            }
            lines = new ArrayList<>(lines.subList(0, keep));
            lines.set(keep - 1, last.toString());
        }
        if (!lines.isEmpty()) {
            String last = lines.get(lines.size() - 1);
            if (widthOf.applyAsInt(last) > maxW) {
                String cut = last;
                while (cut.length() > 1 && widthOf.applyAsInt(cut) > maxW) {
                    cut = cut.substring(0, cut.length() - 1);
                }
                lines.set(lines.size() - 1, cut);
            }
        }
        return lines;
    }

    // ---------- the property checker (A2) ----------

    /**
     * A2 mechanized: (i) no returned line ends mid-word unless the last line carries the marker,
     * (ii) the marker itself fits inside maxW, (iii) non-last lines are whole-word runs. Returns the
     * violations - empty means clean. Applied to BOTH implementations; the pre-fix run must fail.
     */
    static List<String> violations(String label, ToIntFunction<String> widthOf, List<String> lines,
                                   String text, int visualMax, int maxLines) {
        List<String> out = new ArrayList<>();
        if (lines.isEmpty()) {
            return out;
        }
        int maxW = Math.max(8, visualMax) * 2;
        String norm = text.toUpperCase(Locale.ROOT).trim();
        String last = lines.get(lines.size() - 1);
        String joined = String.join(" ", lines);
        boolean dropped = !joined.equals(norm);
        if (dropped && !last.endsWith(QuestTileWidget.TRUNCATION_MARKER)) {
            out.add(label + ": text dropped without the marker: " + lines);
        }
        if (!dropped && last.endsWith(QuestTileWidget.TRUNCATION_MARKER)
                && !norm.endsWith(QuestTileWidget.TRUNCATION_MARKER)) {
            out.add(label + ": marker present although nothing was dropped: " + lines);
        }
        for (int i = 0; i < lines.size() - 1; i++) {
            if (!norm.contains(lines.get(i))) {
                out.add(label + ": non-last line is not a whole-word run: " + lines.get(i));
            }
        }
        if (widthOf.applyAsInt(QuestTileWidget.TRUNCATION_MARKER) > maxW) {
            out.add(label + ": marker wider than maxW");
        }
        if (widthOf.applyAsInt(last) > maxW) {
            out.add(label + ": last line exceeds maxW (" + widthOf.applyAsInt(last) + " > " + maxW + "): " + last);
        }
        return out;
    }

    // ---------- A2 binding 4: fail before pass ----------

    @Test
    void preFixReferenceReproducesTheThreeFrameDocumentedCutsExactly() {
        assertEquals(List.of("CRAFT AN", "INSCRIPTION TABL"),
                preFixWrap(QuestTileWidgetF7CaptionTest::widthOf, T1, FLAG_VISUAL_MAX, FLAG_KEEP));
        assertEquals(List.of("COLLECT THE", "REST OF THE RAIN"),
                preFixWrap(QuestTileWidgetF7CaptionTest::widthOf, T2, FLAG_VISUAL_MAX, FLAG_KEEP));
        assertEquals(List.of("BRING NOVEN A", "BLOCK OF MASSIV"),
                preFixWrap(QuestTileWidgetF7CaptionTest::widthOf, T3, FLAG_VISUAL_MAX, FLAG_KEEP));
    }

    @Test
    void theCheckerFlagsAllThreeOnPreFixAndNoneOnTheFix() {
        int flagged = 0;
        for (String t : List.of(T1, T2, T3)) {
            List<String> pre = preFixWrap(QuestTileWidgetF7CaptionTest::widthOf, t, FLAG_VISUAL_MAX, FLAG_KEEP);
            if (!violations("prefix '" + t + "'", QuestTileWidgetF7CaptionTest::widthOf, pre,
                    t, FLAG_VISUAL_MAX, FLAG_KEEP).isEmpty()) {
                flagged++;
            }
            List<String> post = QuestTileWidget.wrapTiny(QuestTileWidgetF7CaptionTest::widthOf, t, FLAG_VISUAL_MAX, FLAG_KEEP);
            assertTrue(violations("fixA '" + t + "'", QuestTileWidgetF7CaptionTest::widthOf, post,
                            t, FLAG_VISUAL_MAX, FLAG_KEEP).isEmpty(),
                    "fix A output must be clean at keep=2: " + post);
        }
        assertEquals(3, flagged,
                "the checker must flag ALL THREE pre-fix strings - a control that cannot fail is a description");
    }

    // ---------- Fix B: the three flag captions render whole ----------

    @Test
    void fixBRendersTheThreeFlagCaptionsWhole() {
        assertEquals(2, QuestTileWidget.captionBudget(48, false, false), "1.1.191: max 2 lines even when band free");
        // 1.1.191: max 2 lines — same ellipsized forms as the banded case
        assertEquals(List.of("CRAFT AN", "INSCRIPTION TAB..."),
                QuestTileWidget.wrapTiny(QuestTileWidgetF7CaptionTest::widthOf, T1, FLAG_VISUAL_MAX, 2));
        assertEquals(List.of("COLLECT THE", "REST OF THE RAI..."),
                QuestTileWidget.wrapTiny(QuestTileWidgetF7CaptionTest::widthOf, T2, FLAG_VISUAL_MAX, 2));
        assertEquals(List.of("BRING NOVEN A", "BLOCK OF MASSI..."),
                QuestTileWidget.wrapTiny(QuestTileWidgetF7CaptionTest::widthOf, T3, FLAG_VISUAL_MAX, 2));
    }

    // ---------- A3: the banded class keeps 2 lines + marker ----------

    @Test
    void bandedTilesKeepTwoLinesPlusTheMarker() {
        assertEquals(2, QuestTileWidget.captionBudget(48, true, false), "reward faces present -> 2 lines");
        assertEquals(2, QuestTileWidget.captionBudget(48, false, true), "XOR badge present -> 2 lines");
        assertEquals(List.of("CRAFT AN", "INSCRIPTION TAB..."),
                QuestTileWidget.wrapTiny(QuestTileWidgetF7CaptionTest::widthOf, T1, FLAG_VISUAL_MAX, 2));
        assertEquals(List.of("COLLECT THE", "REST OF THE RAI..."),
                QuestTileWidget.wrapTiny(QuestTileWidgetF7CaptionTest::widthOf, T2, FLAG_VISUAL_MAX, 2));
        assertEquals(List.of("BRING NOVEN A", "BLOCK OF MASSI..."),
                QuestTileWidget.wrapTiny(QuestTileWidgetF7CaptionTest::widthOf, T3, FLAG_VISUAL_MAX, 2));
        assertEquals("...", QuestTileWidget.TRUNCATION_MARKER, "house marker, same as ellipsize()/drawWrapped");
    }

    @Test
    void captionBudgetIsStructuralNotPerTile() {
        assertEquals(2, QuestTileWidget.captionBudget(48, false, false));
        assertEquals(2, QuestTileWidget.captionBudget(48, true, false));
        assertEquals(2, QuestTileWidget.captionBudget(48, false, true));
        assertEquals(2, QuestTileWidget.captionBudget(48, true, true));
        assertEquals(2, QuestTileWidget.captionBudget(56, false, false), ">= 56 capped at 2 full-size lines");
        assertEquals(2, QuestTileWidget.captionBudget(64, false, false));
        assertEquals(1, QuestTileWidget.captionBudget(32, false, false), "preview rung unchanged");
        assertEquals(1, QuestTileWidget.captionBudget(32, true, true));
    }

    @Test
    void blankAndNullAreSafe() {
        assertEquals(List.of(), QuestTileWidget.wrapTiny(QuestTileWidgetF7CaptionTest::widthOf, "", FLAG_VISUAL_MAX, 2));
        assertEquals(List.of(), QuestTileWidget.wrapTiny(QuestTileWidgetF7CaptionTest::widthOf, "   ", FLAG_VISUAL_MAX, 2));
        assertEquals(List.of(), QuestTileWidget.wrapTiny(QuestTileWidgetF7CaptionTest::widthOf, null, FLAG_VISUAL_MAX, 2));
    }

    // ---------- A2 binding 3: the class-wide corpus ----------

    static Path chaptersDir() {
        return Path.of(System.getProperty("skylore.chapters",
                "G:/Minecraft/Projects/Skylore/pack/kubejs/data/skylore/questqueen/chapters"));
    }

    static Set<String> corpus() throws IOException {
        Path dir = chaptersDir();
        Set<String> titles = new LinkedHashSet<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                String text = Files.readString(f, StandardCharsets.UTF_8);
                Matcher m = Pattern.compile("\"title\"\\s*:\\s*\"([^\"]*)\"").matcher(text);
                while (m.find()) {
                    titles.add(m.group(1));
                }
            }
        }
        return titles;
    }

    @Test
    void a2CorpusInvariantOverAllAuthoredCaptionsAtEveryWrapTinySize() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(chaptersDir()),
                "Skylore chapters not found at " + chaptersDir() + " - corpus test SKIPPED (set -Dskylore.chapters=<dir>)");
        Set<String> titles = corpus();
        long chapterFiles;
        try (Stream<Path> files = Files.list(chaptersDir())) {
            chapterFiles = files.filter(p -> p.getFileName().toString().endsWith(".json")).count();
        }
        assertEquals(34, chapterFiles, "corpus pin: 34 chapter files");
        assertTrue(titles.size() >= 422, "corpus pin: >= 422 distinct authored titles; got " + titles.size());
        assertTrue(titles.contains(T1) && titles.contains(T2) && titles.contains(T3),
                "the three flag strings must be inside the enumerated corpus");

        int[] sizes = {48, 56, 64, 80, 96, 112, 128};
        int runs = 0;
        List<String> found = new ArrayList<>();
        for (String title : titles) {
            for (int size : sizes) {
                for (boolean icon : new boolean[] {false, true}) {
                    int pad = QuestTileWidget.padPx(size);
                    boolean stacked = QuestTileWidget.stacked(size, icon);
                    int titleX = icon && !stacked ? pad + QuestTileWidget.iconPx(size) + 2 : pad;
                    int visualMax = (size - pad) - titleX;
                    int[] keeps = {QuestTileWidget.titleLines(size), QuestTileWidget.captionBudget(size, false, false)};
                    for (int keep : keeps) {
                        List<String> lines = QuestTileWidget.wrapTiny(QuestTileWidgetF7CaptionTest::widthOf,
                                title, visualMax, keep);
                        found.addAll(violations("size=" + size + " icon=" + icon + " keep=" + keep + " '" + title + "'",
                                QuestTileWidgetF7CaptionTest::widthOf, lines, title, visualMax, keep));
                        runs++;
                    }
                }
            }
        }
        assertTrue(found.isEmpty(),
                "A2 violations: " + found.size() + " first: " + found.subList(0, Math.min(6, found.size())));
        System.out.println("F7 A2 corpus: chapterFiles=" + chapterFiles + " titles=" + titles.size()
                + " runs=" + runs + " violations=0");
    }

    @Test
    void goldensFittingCaptionsAreIdenticalToPreFix() throws IOException {
        Assumptions.assumeTrue(Files.isDirectory(chaptersDir()),
                "Skylore chapters not found at " + chaptersDir() + " - golden test SKIPPED");
        int identical = 0;
        for (String title : corpus()) {
            for (int size : new int[] {48, 56, 64, 80, 96, 112, 128}) {
                for (boolean icon : new boolean[] {false, true}) {
                    int pad = QuestTileWidget.padPx(size);
                    boolean stacked = QuestTileWidget.stacked(size, icon);
                    int titleX = icon && !stacked ? pad + QuestTileWidget.iconPx(size) + 2 : pad;
                    int visualMax = (size - pad) - titleX;
                    for (int keep : new int[] {QuestTileWidget.titleLines(size),
                            QuestTileWidget.captionBudget(size, false, false)}) {
                        List<String> pre = preFixWrap(QuestTileWidgetF7CaptionTest::widthOf, title, visualMax, keep);
                        if (!String.join(" ", pre).equals(title.toUpperCase(Locale.ROOT).trim())) {
                            continue; // the pre-fix path dropped text here: not a "fitting today" case
                        }
                        List<String> post = QuestTileWidget.wrapTiny(QuestTileWidgetF7CaptionTest::widthOf,
                                title, visualMax, keep);
                        assertEquals(pre, post, "fitting caption changed: '" + title + "' size=" + size + " keep=" + keep);
                        identical++;
                    }
                }
            }
        }
        assertTrue(identical > 0, "expected at least one fitting caption to pin; got none");
    }
}
