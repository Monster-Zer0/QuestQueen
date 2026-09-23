package dev.aof.questqueen.client;

import dev.aof.questqueen.data.Tile;
import dev.aof.questqueen.data.reward.ChoiceReward;
import dev.aof.questqueen.data.reward.ItemReward;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QuestBookRewardUiTest {
    @Test
    void rewardPlusWhenMultipleItemRewards() {
        Tile tile = Tile.blank("chest", 0, 0).withRewards(List.of(
                new ItemReward(ResourceLocation.parse("minecraft:oak_log"), 8),
                new ItemReward(ResourceLocation.parse("minecraft:apple"), 3)
        ));
        assertTrue(QuestBookScreen.showsRewardPlus(tile));
        assertEquals("Rewards: 8x oak log, 3x apple", QuestBookScreen.rewardsCaption(tile));
    }

    @Test
    void rewardPlusForChoiceReward() {
        Tile tile = Tile.blank("pick", 0, 0).withRewards(List.of(
                new ChoiceReward(List.of(
                        new ItemReward(ResourceLocation.parse("minecraft:diamond"), 1),
                        new ItemReward(ResourceLocation.parse("minecraft:emerald"), 8)
                ))
        ));
        assertTrue(QuestBookScreen.showsRewardPlus(tile));
        assertEquals("Rewards: 1x diamond, 8x emerald", QuestBookScreen.rewardsCaption(tile));
    }

    @Test
    void inspectBodyStripsTrailingRewardsProse() {
        Tile tile = Tile.blank("chest", 0, 0)
                .withDescription("Craft or find a chest. Rewards: 8x oak log, 3x apple");
        assertEquals("Craft or find a chest.", QuestBookScreen.inspectBody(tile));
        assertFalse(QuestBookScreen.inspectBody(tile).toLowerCase().contains("rewards"));
    }

    @Test
    void inspectBodyKeepsGeordiStrippedStarterCopy() {
        Tile tile = Tile.blank("chest", 0, 0)
                .withDescription("Craft or find a chest. A place to keep what you gather.");
        assertEquals("Craft or find a chest. A place to keep what you gather.", QuestBookScreen.inspectBody(tile));
    }

    @Test
    void singleItemRewardHasNoPlus() {
        Tile tile = Tile.blank("one", 0, 0).withRewards(List.of(
                new ItemReward(ResourceLocation.parse("minecraft:bread"), 4)
        ));
        assertFalse(QuestBookScreen.showsRewardPlus(tile));
        assertFalse(QuestBookScreen.showsRewardPlus(Tile.blank("empty", 1, 1)));
    }
}
