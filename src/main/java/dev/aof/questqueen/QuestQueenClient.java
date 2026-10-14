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
        ClientSync.ADVANCEMENTS = ClientQuestState::setAdvancements;
        ClientSync.OPEN_BOOK = QuestBookScreen::openFromNetwork;
        modEventBus.addListener(QuestQueenClient::onRegisterKeys);
        modEventBus.addListener(QuestQueenClient::onRegisterGuiLayers);
        NeoForge.EVENT_BUS.addListener(QuestQueenClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(QuestQueenClient::onLoggingOut);
        NeoForge.EVENT_BUS.addListener(dev.aof.questqueen.client.InventoryButton::onInit);
        NeoForge.EVENT_BUS.addListener(dev.aof.questqueen.client.InventoryButton::onRender);
        NeoForge.EVENT_BUS.addListener(QuestHudOverlay::onScreenRender);
        // In the quest book, Quest Queen's keys are handled first and go no further: no other mod (recipe viewers'
        // R/U, inventory sorters, ...) acts on them. Keys that are not Quest Queen's pass through untouched.
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, QuestQueenClient::onBookKeyPressed);
        NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.HIGHEST, QuestQueenClient::onBookKeyReleased);
        if (QuestBookScreen.TEST_HOOKS) {
            // Dev runs only: a stand-in for "another mod" listening for keys on any screen, so the e2e can check
            // it never receives the book's keys.
            NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre event) -> {
                if (event.getScreen() instanceof QuestBookScreen) {
                    QuestBookScreen.OTHER_MOD_KEYS.add(event.getKeyCode());
                }
            });
        }
        NeoForge.EVENT_BUS.addListener((net.neoforged.neoforge.client.event.RecipesUpdatedEvent event) ->
                dev.aof.questqueen.client.RecipeBlocks.clear());
    }

    /**
     * Forget the last server's pack and progress. Without this the next server (or singleplayer world) showed
     * the previous one's pinned quest on the HUD until its own definitions arrived.
     */
    private static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        ClientQuestState.reset();
        QuestNetwork.clearClientChunks();
    }

    private static void onBookKeyPressed(net.neoforged.neoforge.client.event.ScreenEvent.KeyPressed.Pre event) {
        if (!(event.getScreen() instanceof QuestBookScreen book)) {
            return;
        }
        if (book.typingInSearch()) {
            // Typing in the book's search box: the box gets every key, and no other mod sees them.
            book.keyPressed(event.getKeyCode(), event.getScanCode(), event.getModifiers());
            event.setCanceled(true);
        } else if (book.handleQuestKey(event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    private static void onBookKeyReleased(net.neoforged.neoforge.client.event.ScreenEvent.KeyReleased.Pre event) {
        if (event.getScreen() instanceof QuestBookScreen book
                && book.consumesKey(event.getKeyCode(), event.getScanCode(), event.getModifiers())) {
            event.setCanceled(true);
        }
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent event) {
        event.register(QuestKeybinds.OPEN_BOOK);
        QuestKeybinds.BOOK.forEach(event::register);
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
