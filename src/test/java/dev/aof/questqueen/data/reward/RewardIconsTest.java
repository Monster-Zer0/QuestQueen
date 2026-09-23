package dev.aof.questqueen.data.reward;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RewardIconsTest {
    @Test
    void xpAndChoiceAndAdvancementFillRealIcons() {
        assertEquals("experience_bottle", RewardIcons.iconId(new XpReward(8)).orElseThrow().getPath());
        assertEquals("experience_bottle", RewardIcons.iconId(new XpLevelReward(1)).orElseThrow().getPath());
        assertEquals("knowledge_book", RewardIcons.iconId(
                new AdvancementReward(ResourceLocation.parse("minecraft:adventure/root"))).orElseThrow().getPath());
        assertEquals("amethyst_shard", RewardIcons.iconId(new StageReward("questqueen_demo")).orElseThrow().getPath());
        assertEquals("diamond", RewardIcons.iconId(new ChoiceReward(List.of(
                new ItemReward(ResourceLocation.parse("minecraft:diamond"), 1),
                new ItemReward(ResourceLocation.parse("minecraft:emerald"), 8)
        ))).orElseThrow().getPath());
        assertEquals("apple", RewardIcons.iconId(
                new ItemReward(ResourceLocation.parse("minecraft:apple"), 1)).orElseThrow().getPath());
        assertEquals("paper", RewardIcons.iconId(new ToastReward("hi")).orElseThrow().getPath());
        assertEquals("chest", RewardIcons.iconId(
                new LootTableReward(ResourceLocation.parse("minecraft:chests/simple_dungeon"))).orElseThrow().getPath());
        assertEquals("command_block", RewardIcons.iconId(new CommandReward("say hi")).orElseThrow().getPath());
    }

    @Test
    void showcaseBarePlusTilesNowResolve() {
        assertTrue(RewardIcons.iconId(new XpReward(8)).isPresent());
        assertTrue(RewardIcons.iconId(new XpReward(5)).isPresent());
        assertTrue(RewardIcons.iconId(
                new AdvancementReward(ResourceLocation.parse("minecraft:adventure/root"))).isPresent());
        assertTrue(RewardIcons.iconId(new StageReward("questqueen_demo")).isPresent());
    }

    @Test
    void everyFactoryTypeHasARealIcon() {
        for (String type : RewardFactory.TYPES) {
            assertTrue(RewardIcons.iconId(RewardFactory.create(type)).isPresent(), type);
        }
    }
}
