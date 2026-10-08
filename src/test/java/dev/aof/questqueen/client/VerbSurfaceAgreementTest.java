package dev.aof.questqueen.client;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the <em>caller</em> side of the raw-verb defect, which the verb's own test could not reach.
 *
 * <p>{@code TaskTypeCoverageTest.everyTypeHasAVerbAndNoTypeLeaksItsRawId} pins
 * {@link dev.aof.questqueen.data.task.TaskVerbs} and does it well — but the defect that reached the pack was
 * a surface that never called it. {@code QuestBookScreen.tileObjective} uppercased {@code task.type()}
 * itself, so on one build the tile card's own task rows read {@code COLLECT 16/16} while the board behind
 * that card read {@code ITEM_TAG 16/16}. Same task, same frame, two labels. A test on the helper is blind to
 * a caller that bypasses the helper, so the guard has to live at the caller.
 */
class VerbSurfaceAgreementTest {

    /** The anti-pattern, spelled once so the scan and its failure message agree. */
    private static final String RAW_ID_UPPERCASE = "type().toUpperCase";

    /**
     * {@code file::trimmed-line} pairs that render a raw type <em>on purpose</em>. Each is a decision
     * somebody made, which is the point: the guard exists to stop a surface leaking an identifier by
     * accident, not to forbid a surface that shows one deliberately. Keyed on the line's content rather than
     * its number, so moving code around cannot smuggle a new leak in behind an exemption.
     */
    private static final Set<String> DELIBERATE = Set.of(
            // Reward.type() is a REWARD type, not a task type; the enum name is its own player-facing label.
            "dev/aof/questqueen/data/reward/Reward.java::return type().toUpperCase(Locale.ROOT);");

    /**
     * No render surface may spell a raw task type. This is the guard that would have caught the board half of
     * the defect, and it is deliberately a source scan rather than a unit assertion: the failure mode is a
     * <em>new</em> call site bypassing the shared verb, which no amount of testing the shared verb can see.
     * If this fails, do not widen the exemptions without saying why — route the offending surface through
     * {@code ClientQuestState.taskVerb} / {@code TaskVerbs}.
     */
    @Test
    void noRenderSurfaceUppercasesARawTaskType() throws IOException {
        Path root = Path.of("src", "main", "java");
        assertTrue(Files.isDirectory(root),
                "expected the project root as the working directory; looked for " + root.toAbsolutePath());

        List<String> offenders = new ArrayList<>();
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path file : walk.filter(p -> p.toString().endsWith(".java")).toList()) {
                String relative = root.relativize(file).toString().replace('\\', '/');
                List<String> lines = Files.readAllLines(file);
                for (String raw : lines) {
                    String line = raw.trim();
                    if (line.isEmpty() || line.startsWith("*") || line.startsWith("//") || line.startsWith("/*")) {
                        continue;   // prose about the defect is not an instance of it
                    }
                    if (line.contains(RAW_ID_UPPERCASE) && !DELIBERATE.contains(relative + "::" + line)) {
                        offenders.add(relative + "  " + line);
                    }
                }
            }
        }
        assertTrue(offenders.isEmpty(),
                "a render surface is spelling a raw task type instead of using the shared verb. This is the "
                        + "defect where the tile card's rows read COLLECT 16/16 while the board behind the same "
                        + "card read ITEM_TAG 16/16:\n  " + String.join("\n  ", offenders));
    }
}
