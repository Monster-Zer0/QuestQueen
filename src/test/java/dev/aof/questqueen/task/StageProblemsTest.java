package dev.aof.questqueen.task;

import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.Gate;
import dev.aof.questqueen.data.GateCondition;
import dev.aof.questqueen.data.GateOp;
import dev.aof.questqueen.data.GridPos;
import dev.aof.questqueen.data.Tile;
import dev.aof.questqueen.data.reward.StageReward;
import dev.aof.questqueen.data.task.CheckmarkTask;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Stage ids that can never match are reported at load, like StageLock's own "undefined stage" warning. */
class StageProblemsTest {
    private static Chapter chapter() {
        Tile gated = new Tile("gated", new GridPos(0, 0), "Gated", "", Optional.empty(), List.of(new CheckmarkTask()),
                List.of(new StageReward("Bad Stage")), List.of(), Optional.empty(), Optional.empty(),
                Optional.of("mypack:iron_age"));
        return new Chapter(ResourceLocation.parse("p:stages"), "Stages", List.of(gated), List.of())
                .withMeta(Optional.empty(), 0, Optional.empty(),
                        new Gate(GateOp.AND, List.of(new GateCondition("stagelock", "iron_age"),
                                new GateCondition("progression", "mypack:old_name"))));
    }

    @Test
    void anInvalidStageIdIsReported() {
        List<String> problems = TaskHooks.stageProblems(List.of(chapter()), Set.of());
        assertEquals(1, problems.size(), problems.toString());
        assertTrue(problems.getFirst().contains("\"Bad Stage\" is not a valid stage id"));
    }

    @Test
    void withDefinitionsAnUndefinedStageIsReportedToo() {
        List<String> problems = TaskHooks.stageProblems(List.of(chapter()), Set.of("mypack:iron_age", "bad"));
        assertTrue(problems.stream().anyMatch(p -> p.contains("chapter unlock: stage \"iron_age\" is not defined")),
                "the namespacing trap: iron_age vs mypack:iron_age\n" + problems);
        assertTrue(problems.stream().noneMatch(p -> p.contains("required_stage")), "mypack:iron_age is defined");
        assertTrue(problems.stream().anyMatch(p -> p.contains("\"mypack:old_name\" is not defined")),
                "the old \"progression\" type name is still a stage condition\n" + problems);
    }
}
