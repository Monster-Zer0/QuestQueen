package dev.aof.questqueen.data.reward;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Regression coverage for the {@code ClaimChoiceC2S} bounds fix (1.1.154). The client supplies the
 * option index, and {@code ProgressService.claimChoice} used to persist it as the one-time "choice"
 * value even when no option existed, burning the claim and granting nothing.
 */
class ChoiceRewardBoundsTest {
    private static ChoiceReward reward(int optionCount) {
        List<ItemReward> options = new ArrayList<>();
        for (int i = 0; i < optionCount; i++) {
            options.add(new ItemReward(ResourceLocation.parse("minecraft:stone"), 1));
        }
        return new ChoiceReward(options);
    }

    @Test
    void acceptsEveryRealOption() {
        ChoiceReward choice = reward(3);
        assertTrue(choice.validChoiceIndex(0));
        assertTrue(choice.validChoiceIndex(1));
        assertTrue(choice.validChoiceIndex(2));
    }

    @Test
    void rejectsIndicesOutsideTheList() {
        ChoiceReward choice = reward(3);
        assertFalse(choice.validChoiceIndex(3), "index one past the end must be rejected");
        assertFalse(choice.validChoiceIndex(99), "a forged high index must be rejected");
        assertFalse(choice.validChoiceIndex(-1), "a negative index must be rejected");
    }

    @Test
    void anOptionlessChoiceCannotBeConstructedAtAll() {
        // 1.1.155 tightened this: an empty choice used to be legal and would burn the one-time `choice`
        // flag while granting nothing. The bounds rule below is now unreachable for that case.
        assertThrows(IllegalArgumentException.class, () -> reward(0));
    }
}
