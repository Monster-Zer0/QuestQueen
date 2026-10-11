package dev.aof.questqueen.data.reward;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Command rewards: reading give commands, the icon/name/description fields, and never showing the command. */
class CommandRewardTest {
    private static final String GLOCK = "give @s tacz:modern_kinetic_gun[minecraft:custom_data={GunId:\"tacz:glock_17\",GunFireMode:\"SEMI\",GunCurrentAmmoCount:17,HasBulletInBarrel:1b}] 1";
    private static final String AMMO = "give @s tacz:ammo[minecraft:custom_data={AmmoId:\"tacz:9mm\"}] 32";

    @Test
    void readsGiveCommands() {
        GiveCommand glock = GiveCommand.parse(GLOCK).orElseThrow();
        assertEquals("tacz:modern_kinetic_gun", glock.itemId().toString());
        assertTrue(glock.itemSpec().endsWith("HasBulletInBarrel:1b}]"));
        assertEquals(1, glock.count());
        GiveCommand ammo = GiveCommand.parse("/" + AMMO).orElseThrow();
        assertEquals(32, ammo.count());
        assertEquals("32x ammo", ammo.plainLabel());
        assertEquals(1, GiveCommand.parse("give @p minecraft:diamond").orElseThrow().count());
        GiveCommand spaced = GiveCommand.parse("give @s[name=\"A B\"] minecraft:stone[custom_name='\"Big Rock\"'] 3").orElseThrow();
        assertEquals("minecraft:stone[custom_name='\"Big Rock\"']", spaced.itemSpec());
        assertEquals(3, spaced.count());
    }

    @Test
    void anythingElseIsNotAGive() {
        assertFalse(GiveCommand.parse("effect give @s minecraft:speed 30").isPresent());
        assertFalse(GiveCommand.parse("give @s").isPresent());
        assertFalse(GiveCommand.parse("give @s minecraft:diamond lots").isPresent());
        assertFalse(GiveCommand.parse("give @s minecraft:diamond 0").isPresent());
        assertFalse(GiveCommand.parse("give @s minecraft:diamond 2 extra").isPresent());
        assertFalse(GiveCommand.parse("").isPresent());
        assertFalse(GiveCommand.parse(null).isPresent());
    }

    @Test
    void thePlayerNeverSeesTheCommand() {
        assertEquals("Bonus reward", new CommandReward("effect give @s minecraft:speed 30").describe());
        assertEquals("32x ammo", new CommandReward(AMMO).describe());
        assertEquals(32, new CommandReward(AMMO).count());
        assertEquals("Academy Sidearm", new CommandReward(GLOCK, "", " Academy Sidearm ", "").describe());
        for (String command : new String[]{GLOCK, AMMO, "say hi", "effect give @s minecraft:speed 30"}) {
            assertFalse(new CommandReward(command).describe().contains("@s"), command);
        }
    }

    @Test
    void iconKinds() {
        CommandReward glyph = new CommandReward("say hi", "glyph:Rocket", "", "");
        assertEquals("rocket", glyph.iconGlyph().orElseThrow());
        assertFalse(glyph.iconItemSpec().isPresent());
        CommandReward picture = new CommandReward("say hi", "skylore:textures/gui/reward/sidearm.png", "", "");
        assertEquals("skylore:textures/gui/reward/sidearm.png", picture.iconTexture().orElseThrow().toString());
        assertFalse(picture.iconItemSpec().isPresent());
        CommandReward item = new CommandReward("say hi", "tacz:ammo[minecraft:custom_data={AmmoId:\"tacz:9mm\"}]", "", "");
        assertEquals("tacz:ammo[minecraft:custom_data={AmmoId:\"tacz:9mm\"}]", item.iconItemSpec().orElseThrow());
        assertFalse(new CommandReward("say hi").iconItemSpec().isPresent());
    }

    @Test
    void codecRoundTripsAndOldRewardsStillLoad() {
        Reward old = Reward.CODEC.parse(JsonOps.INSTANCE,
                JsonParser.parseString("{\"type\":\"command\",\"command\":\"say hi\"}")).getOrThrow();
        assertEquals(new CommandReward("say hi"), old);
        CommandReward full = new CommandReward(AMMO, "glyph:star", "Rounds", "Thirty-two of them.");
        JsonObject json = Reward.CODEC.encodeStart(JsonOps.INSTANCE, full).getOrThrow().getAsJsonObject();
        assertEquals("Rounds", json.get("name").getAsString());
        assertEquals(full, Reward.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow());
        JsonObject plain = Reward.CODEC.encodeStart(JsonOps.INSTANCE, old).getOrThrow().getAsJsonObject();
        assertFalse(plain.has("icon"), "unset fields are not written back: " + plain);
    }
}
