package dev.aof.questqueen.progress;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.aof.questqueen.data.Tile;
import dev.aof.questqueen.data.reward.ChoiceReward;
import dev.aof.questqueen.data.reward.ItemReward;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for deferred reward claims (1.1.163) and the choice-reward settle path.
 *
 * <p>{@code maybeCompleteTile} marks the tile done but does <em>not</em> grant rewards or write
 * {@code claimed}. Plain loot waits for CLAIM; choice tiles wait for TAKE A·B ({@code claimed} stays
 * false until {@code claimChoice}). A choice reward with no options is rejected at parse time.
 */
class ChoiceRewardClaimTest {
    private static ItemReward diamond() {
        return new ItemReward(ResourceLocation.parse("minecraft:diamond"), 1);
    }

    @Test
    void choiceTileIsDetectedAsUnsettled() {
        Tile withChoice = Tile.blank("pick", 0, 0).withRewards(List.of(new ChoiceReward(List.of(
                diamond(), new ItemReward(ResourceLocation.parse("minecraft:emerald"), 8)))));
        assertTrue(ProgressService.hasChoiceReward(withChoice));
    }

    @Test
    void plainRewardsAreNotChoiceTiles() {
        assertFalse(ProgressService.hasChoiceReward(
                Tile.blank("plain", 0, 0).withRewards(List.of(diamond()))));
        assertFalse(ProgressService.hasChoiceReward(Tile.blank("empty", 0, 0)));
    }

    @Test
    void choiceWithNoOptionsIsRejected() {
        // RecordCodecBuilder runs the canonical constructor inside the map, so the refusal surfaces as a
        // thrown exception rather than a DataResult error.
        assertThrows(IllegalArgumentException.class, () -> dev.aof.questqueen.data.reward.Reward.CODEC.parse(
                JsonOps.INSTANCE, JsonParser.parseString("{\"type\":\"choice\",\"options\":[]}")));
    }
}
