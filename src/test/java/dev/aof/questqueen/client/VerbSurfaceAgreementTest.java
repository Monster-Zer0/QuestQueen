package dev.aof.questqueen.client;

import dev.aof.questqueen.data.task.Task;
import dev.aof.questqueen.data.task.TaskFactory;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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
            // The EDIT MODE author mock's TYPE field chip, under the comment "Mock-style field chips:
            // TYPE | COUNT | TARGET". It shows the raw JSON field value deliberately.
            // OPEN QUESTION for Troi (player-facing effect) and Worf: does this surface reach players? If it
            // does, it takes the shared verb and this exemption is deleted rather than renewed.
            "dev/aof/questqueen/client/QuestBookScreen.java::drawOutlinedButton(graphics, x + 10, taskY, 72, 14, "
                    + "task.type().toUpperCase(Locale.ROOT), QuestColors.EDIT);",
            // Reward.type() is a REWARD type, not a task type; the enum name is its own player-facing label.
            "dev/aof/questqueen/data/reward/Reward.java::return type().toUpperCase(Locale.ROOT);");

    /**
     * The board's label takes the verb as a parameter, so its caller cannot substitute a type id for it. Pins
     * both halves: the shape is {@code <VERB> <value>/<need>}, and the verb slot really carries the shared
     * verb for every type the factory can build.
     */
    @Test
    void theBoardLabelCarriesTheSharedVerbForEveryType() {
        for (String type : TaskFactory.TYPES) {
            Task task = TaskFactory.create(type);
            // tile is only consulted for the null-task fallback, and this task is never null.
            String label = QuestBookScreen.objectiveLabel(ClientQuestState.taskVerb(task, null), 3, 16);
            assertFalse(label.contains("_"), type + " put an identifier fragment on the board: " + label);
            assertTrue(label.endsWith(" 3/16"), type + " lost the value/required shape: " + label);
            // Only a multi-word type can prove the difference: kill -> KILL and stat -> STAT are the correct
            // verbs AND the uppercased id, so equality there is not a defect. Asserting on those was an
            // assumption of mine, not a property of the code.
            if (type.contains("_")) {
                assertNotEquals(type.toUpperCase(Locale.ROOT) + " 3/16", label,
                        type + " still reaches the board as its raw identifier");
            }
        }
        // The two the pack measured on screen, spelled out rather than only compared in a loop.
        String tag = QuestBookScreen.objectiveLabel(
                ClientQuestState.taskVerb(TaskFactory.create("item_tag"), null), 16, 16);
        String xp = QuestBookScreen.objectiveLabel(
                ClientQuestState.taskVerb(TaskFactory.create("xp_levels"), null), 5, 5);
        assertFalse(tag.startsWith("ITEM_TAG"), "the board reads ITEM_TAG again: " + tag);
        assertFalse(xp.startsWith("XP_LEVELS"), "the board reads XP_LEVELS again: " + xp);

        // The BOARD and the CARD must render the same string for the same task. `item_tag` count 16 is the
        // exact row the pack photographed: the card read COLLECT 16/16 while the board read ITEM_TAG 16/16.
        Task tagTask = TaskFactory.create("item_tag");
        String card = ClientQuestState.taskVerb(tagTask, null) + " 16/16";
        String board = QuestBookScreen.objectiveLabel(ClientQuestState.taskVerb(tagTask, null), 16, 16);
        assertTrue(board.startsWith(card.substring(0, card.indexOf(' '))),
                "the board and the card disagree on the verb: board=" + board + " card=" + card);
    }

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
