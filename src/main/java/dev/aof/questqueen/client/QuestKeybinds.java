package dev.aof.questqueen.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.lwjgl.glfw.GLFW;

public final class QuestKeybinds {
    public static final KeyMapping OPEN_BOOK = new KeyMapping(
            "key.questqueen.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            "key.categories.questqueen"
    );

    private QuestKeybinds() {
    }
}
