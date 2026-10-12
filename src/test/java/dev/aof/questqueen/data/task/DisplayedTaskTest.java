package dev.aof.questqueen.data.task;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertSame;

/** The optional display item ("icon") any task can carry, without changing how the task itself behaves. */
class DisplayedTaskTest {
    private static Task read(String json) {
        return Task.CODEC.parse(JsonOps.INSTANCE, JsonParser.parseString(json)).getOrThrow();
    }

    @Test
    void aTaskWithAnIconIsTheSameTaskPlusTheItem() {
        Task task = read("{\"type\":\"advancement\",\"advancement\":\"minecraft:story/mine_stone\",\"icon\":\"minecraft:stone_pickaxe\"}");
        assertEquals("minecraft:stone_pickaxe", task.displayItem().orElseThrow());
        assertEquals("advancement", task.type());
        assertEquals(ResourceLocation.parse("minecraft:story/mine_stone"), task.advancementId().orElseThrow());
        assertInstanceOf(AdvancementTask.class, Task.unwrap(task));
        JsonObject back = Task.CODEC.encodeStart(JsonOps.INSTANCE, task).getOrThrow().getAsJsonObject();
        assertEquals("minecraft:stone_pickaxe", back.get("icon").getAsString());
        assertEquals("advancement", back.get("type").getAsString());
        assertEquals(task, read(back.toString()));
    }

    @Test
    void withoutAnIconNothingChanges() {
        Task kill = read("{\"type\":\"kill\",\"entity\":\"minecraft:zombie\",\"count\":3}");
        assertInstanceOf(KillTask.class, kill);
        assertFalse(kill.displayItem().isPresent());
        assertFalse(Task.CODEC.encodeStart(JsonOps.INSTANCE, kill).getOrThrow().getAsJsonObject().has("icon"));
        assertSame(kill, Task.unwrap(kill));
        assertFalse(read("{\"type\":\"checkmark\",\"icon\":\"  \"}").displayItem().isPresent());
    }

    @Test
    void countsAndAliasesKeepTheIcon() {
        Task stat = read("{\"type\":\"stat\",\"stat\":\"minecraft:jump\",\"count\":5,\"icon\":\"minecraft:feather\"}");
        Task more = stat.withCount(10);
        assertEquals(10, more.required());
        assertEquals("minecraft:feather", more.displayItem().orElseThrow());
        Task visit = read("{\"type\":\"structure\",\"structure\":\"minecraft:village_plains\",\"icon\":\"minecraft:bell\"}");
        assertEquals("visit_structure", visit.type());
        assertEquals("minecraft:bell", visit.displayItem().orElseThrow());
    }
}
