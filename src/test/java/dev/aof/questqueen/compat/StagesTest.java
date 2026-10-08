package dev.aof.questqueen.compat;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.aof.questqueen.data.reward.Reward;
import dev.aof.questqueen.data.reward.StageReward;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Stage id rules and backend matching, for Progression and ProgressiveStages, with no game running. */
class StagesTest {
    @AfterEach
    void reset() {
        Stages.force(null);
    }

    @Test
    void stageIdsFollowProgressionsRule() {
        assertTrue(Stages.validId("iron_age"));
        assertTrue(Stages.validId("mypack:nether/deep-1.0"));
        assertTrue(Stages.validId("Iron_Age"), "Progression lowercases ids itself");
        assertFalse(Stages.validId(""));
        assertFalse(Stages.validId("iron age"), "no spaces");
        assertFalse(Stages.validId("x".repeat(65)), "at most 64 characters");
    }

    @Test
    void progressionMatchesExactlyAfterLowercasing() {
        Stages.force(Stages.Backend.PROGRESSION);
        Set<String> owned = Set.of("mypack:iron_age");
        assertTrue(Stages.matches(owned, "mypack:iron_age"));
        assertTrue(Stages.matches(owned, "MyPack:Iron_Age"));
        assertFalse(Stages.matches(owned, "iron_age"), "Progression ids have no implied namespace");
        assertFalse(Stages.matches(Set.of("iron_age"), "mypack:iron_age"));
    }

    @Test
    void progressiveStagesKeepsItsNamespaceShortcut() {
        Stages.force(Stages.Backend.PROGRESSIVE_STAGES);
        assertTrue(Stages.matches(Set.of("progressivestages:iron_age"), "iron_age"));
    }

    @Test
    void withNoStageModNothingMatches() {
        Stages.force(Stages.Backend.NONE);
        assertFalse(Stages.present());
        assertFalse(Stages.matches(Set.of("iron_age"), "iron_age"));
    }

    @Test
    void progressionIsAStageTypeName() {
        assertTrue(Stages.isStageType("stage"));
        assertTrue(Stages.isStageType("progression"));
        assertTrue(Stages.isStageType("progressivestages"));
        assertFalse(Stages.isStageType("advancement"));
        Reward reward = Reward.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"progression\",\"stage\":\"iron_age\"}")).getOrThrow();
        assertInstanceOf(StageReward.class, reward);
        assertEquals("iron_age", ((StageReward) reward).stage());
    }
}
