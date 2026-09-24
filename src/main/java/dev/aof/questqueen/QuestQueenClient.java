package dev.aof.questqueen;

import dev.aof.questqueen.client.ClientQuestState;
import dev.aof.questqueen.client.EditorBridge;
import dev.aof.questqueen.client.QuestBookScreen;
import dev.aof.questqueen.client.QuestHudOverlay;
import dev.aof.questqueen.client.QuestKeybinds;
import dev.aof.questqueen.client.UiFx;
import dev.aof.questqueen.net.ClientSync;
import dev.aof.questqueen.net.QuestNetwork;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = QuestQueen.MODID, dist = Dist.CLIENT)
public class QuestQueenClient {
    public QuestQueenClient(IEventBus modEventBus, ModContainer container) {
        ClientSync.DEFINITIONS = ClientQuestState::setPack;
        ClientSync.PROGRESS = ClientQuestState::setProgress;
        ClientSync.EDITOR = EditorBridge::setEnabled;
        ClientSync.OPEN_BOOK = QuestBookScreen::openFromNetwork;
        modEventBus.addListener(QuestQueenClient::onRegisterKeys);
        modEventBus.addListener(QuestQueenClient::onRegisterGuiLayers);
        NeoForge.EVENT_BUS.addListener(QuestQueenClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(QuestQueenClient::onLoggingOut);
    }

    /**
     * Forget the last server's pack and progress. Without this the next server (or singleplayer world) showed
     * the previous one's pinned quest on the HUD until its own definitions arrived.
     */
    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientQuestState.reset();
        QuestNetwork.clearClientChunks();
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(QuestKeybinds.OPEN_BOOK);
    }

    private static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(QuestQueen.id("pin_hud"), QuestHudOverlay::render);
    }

    private static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        syncEffectConfig();
        EditorBridge.tick(minecraft);
        if (minecraft.player == null) {
            return;
        }
        while (QuestKeybinds.OPEN_BOOK.consumeClick()) {
            if (minecraft.screen instanceof QuestBookScreen) {
                minecraft.setScreen(null);
            } else if (minecraft.screen == null) {
                minecraft.setScreen(new QuestBookScreen());
            }
        }
    }

    /**
     * Push the effects config into {@link UiFx} once per tick. Config values are not safe to read during
     * rendering, and reading them per frame was the alternative.
     */
    private static void syncEffectConfig() {
        try {
            UiFx.setEnabled(QuestConfig.ANIMATIONS.get());
        } catch (Exception ignored) {
            // Config not loaded yet — leave the default (enabled) alone.
        }
    }
}
