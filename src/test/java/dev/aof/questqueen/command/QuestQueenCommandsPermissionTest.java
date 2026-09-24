package dev.aof.questqueen.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.tree.CommandNode;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code trigger} and {@code dialog} complete quests and set team flags. They were open to every player, so a
 * survival player could type {@code /questqueen trigger boss_defeated} and skip the content. They now need
 * permission level 2, which command blocks, functions and NPC mods have and a normal player does not.
 */
class QuestQueenCommandsPermissionTest {

    private static CommandSourceStack sourceAt(int level) {
        return new CommandSourceStack(CommandSource.NULL, Vec3.ZERO, Vec2.ZERO, null, level, "test",
                Component.literal("test"), null, null);
    }

    private static CommandNode<CommandSourceStack> node(String child) {
        CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
        QuestQueenCommands.register(dispatcher);
        CommandNode<CommandSourceStack> root = dispatcher.getRoot().getChild("questqueen");
        assertNotNull(root, "the /questqueen root must be registered");
        CommandNode<CommandSourceStack> node = root.getChild(child);
        assertNotNull(node, "/questqueen " + child + " must be registered");
        return node;
    }

    @Test
    void triggerAndDialogRefuseAPlainPlayer() {
        for (String child : new String[]{"trigger", "dialog"}) {
            CommandNode<CommandSourceStack> node = node(child);
            assertFalse(node.canUse(sourceAt(0)), child + " must be refused at permission level 0");
            assertTrue(node.canUse(sourceAt(2)), child + " must stay usable for command blocks and functions");
        }
    }

    @Test
    void playerFacingSubcommandsStayOpen() {
        assertTrue(node("team").canUse(sourceAt(0)), "team management is for every player");
        assertTrue(node("book").canUse(sourceAt(0)), "opening the book is for every player");
    }
}
