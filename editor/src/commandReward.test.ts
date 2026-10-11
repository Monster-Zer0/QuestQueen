import { describe, expect, it } from "vitest";
import { commandPreviewHtml, commandRewardProblems, iconKind, parseGive, rewardCount, rewardName } from "./commandReward";
import { validate } from "./validate";
import type { Chapter } from "./types";

const GLOCK = 'give @s tacz:modern_kinetic_gun[minecraft:custom_data={GunId:"tacz:glock_17",GunFireMode:"SEMI"}] 1';
const AMMO = 'give @s tacz:ammo[minecraft:custom_data={AmmoId:"tacz:9mm"}] 32';

describe("command rewards", () => {
  it("reads give commands like the mod does", () => {
    expect(parseGive(GLOCK)).toEqual({ spec: 'tacz:modern_kinetic_gun[minecraft:custom_data={GunId:"tacz:glock_17",GunFireMode:"SEMI"}]', item: "tacz:modern_kinetic_gun", count: 1 });
    expect(parseGive("/" + AMMO)?.count).toBe(32);
    expect(parseGive('give @s[name="A B"] minecraft:stone[custom_name=\'"Big Rock"\'] 3')?.spec).toBe('minecraft:stone[custom_name=\'"Big Rock"\']');
    expect(parseGive("effect give @s minecraft:speed 30")).toBeNull();
    expect(parseGive("give @s minecraft:diamond lots")).toBeNull();
    expect(parseGive("give @s")).toBeNull();
  });

  it("names the reward without ever showing the command", () => {
    expect(rewardName({ type: "command", command: AMMO })).toBe("ammo");
    expect(rewardCount({ type: "command", command: AMMO })).toBe(32);
    expect(rewardName({ type: "command", command: "effect give @s minecraft:speed 30" })).toBe("Bonus reward");
    expect(rewardName({ type: "command", command: GLOCK, name: "Academy Sidearm" })).toBe("Academy Sidearm");
    const html = commandPreviewHtml({ type: "command", command: GLOCK, description: "Loaded." }, (id) => `/icon/${id}`);
    expect(html).toContain("Loaded.");
    expect(html).not.toContain("give @s");
  });

  it("sorts icons into item, glyph and picture, and flags bad ones", () => {
    expect(iconKind("glyph:Rocket")).toEqual({ kind: "glyph", id: "rocket" });
    expect(iconKind("skylore:textures/gui/sidearm.png").kind).toBe("texture");
    expect(iconKind('tacz:ammo[minecraft:custom_data={AmmoId:"tacz:9mm"}]')).toMatchObject({ kind: "item", item: "tacz:ammo" });
    expect(commandRewardProblems({ type: "command", command: "say hi", icon: "glyph:dragon" })[0]).toContain("not a known glyph");
    expect(commandRewardProblems({ type: "command", command: "say hi", icon: "skylore:gui/sidearm.png" })[0]).toContain("picture path");
    expect(commandRewardProblems({ type: "command", command: "say hi", icon: "tacz:ammo[custom_data={}" })[0]).toContain("unclosed");
    expect(commandRewardProblems({ type: "command", command: "say hi", icon: "glyph:star" })).toEqual([]);
  });

  it("validation reports a bad icon on the quest", () => {
    const chapter: Chapter = { id: "p:c", title: "C", tiles: [{ id: "t", pos: { x: 0, y: 0 }, title: "T",
      tasks: [{ type: "checkmark" }], rewards: [{ type: "command", command: "say hi", icon: "glyph:dragon" }] }], links: [] };
    expect(validate(chapter, [chapter]).some((error) => error.includes("not a known glyph"))).toBe(true);
  });
});
