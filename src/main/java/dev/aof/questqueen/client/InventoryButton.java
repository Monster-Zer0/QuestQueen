package dev.aof.questqueen.client;

import dev.aof.questqueen.QuestConfig;
import dev.aof.questqueen.QuestQueen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.ScreenEvent;

/**
 * A vanilla-style button with the quest book icon in the survival inventory, in the empty top-right corner above
 * the crafting output. It follows the panel when the recipe book shifts it.
 */
public final class InventoryButton {
    static final int W = 20;
    static final int H = 18;
    /** From the inventory panel's top-left: right of the "Crafting" label, above the output slot. */
    static final int DEFAULT_X = 152;
    static final int DEFAULT_Y = 5;
    private static final ResourceLocation ICON = QuestQueen.id("inventory/quest_book");
    private static final ResourceLocation BUTTON = ResourceLocation.withDefaultNamespace("widget/button");
    private static final ResourceLocation BUTTON_HOVER = ResourceLocation.withDefaultNamespace("widget/button_highlighted");

    private InventoryButton() {
    }

    private static final class BookButton extends Button {
        BookButton() {
            super(0, 0, W, H, Component.translatableWithFallback("questqueen.inventory.button", "Quest Book"),
                    button -> Minecraft.getInstance().setScreen(new QuestBookScreen()), DEFAULT_NARRATION);
            setTooltip(Tooltip.create(Component.translatableWithFallback("questqueen.inventory.button", "Quest Book")));
        }

        @Override
        protected void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
            graphics.blitSprite(isHoveredOrFocused() ? BUTTON_HOVER : BUTTON, getX(), getY(), W, H);
            graphics.blitSprite(ICON, getX() + 2, getY() + 1, 16, 16);
        }
    }

    private static boolean enabled() {
        try {
            return QuestConfig.INVENTORY_BUTTON.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    private static int[] offset() {
        try {
            return new int[]{QuestConfig.INVENTORY_BUTTON_X.get(), QuestConfig.INVENTORY_BUTTON_Y.get()};
        } catch (IllegalStateException notLoaded) {
            return new int[]{0, 0};
        }
    }

    public static void onInit(ScreenEvent.Init.Post event) {
        if (event.getScreen() instanceof InventoryScreen screen && enabled()) {
            BookButton button = new BookButton();
            place(screen, button);
            event.addListener(button);
        }
    }

    /** Re-place every frame: toggling the recipe book moves the panel without re-initialising the screen. */
    public static void onRender(ScreenEvent.Render.Pre event) {
        if (event.getScreen() instanceof InventoryScreen screen) {
            for (var child : screen.children()) {
                if (child instanceof BookButton button) {
                    place(screen, button);
                }
            }
        }
    }

    private static void place(InventoryScreen screen, BookButton button) {
        int[] off = offset();
        button.setX(screen.getGuiLeft() + DEFAULT_X + off[0]);
        button.setY(screen.getGuiTop() + DEFAULT_Y + off[1]);
    }
}
