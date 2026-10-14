package dev.aof.questqueen.client;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.settings.KeyConflictContext;
import net.neoforged.neoforge.client.settings.KeyModifier;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/**
 * The quest book's keys. All of them are rebindable under "Quest Queen" in Controls. The book keys use the GUI
 * conflict context: they only exist while a screen is open, so they never clash with gameplay keys, and inside
 * the quest book Quest Queen handles them before any other mod (see {@code QuestQueenClient}).
 */
public final class QuestKeybinds {
    private static final String CATEGORY = "key.categories.questqueen";

    public static final KeyMapping OPEN_BOOK = new KeyMapping(
            "key.questqueen.open",
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_J,
            CATEGORY
    );

    public static final KeyMapping UP = book("up", GLFW.GLFW_KEY_UP);
    public static final KeyMapping DOWN = book("down", GLFW.GLFW_KEY_DOWN);
    public static final KeyMapping LEFT = book("left", GLFW.GLFW_KEY_LEFT);
    public static final KeyMapping RIGHT = book("right", GLFW.GLFW_KEY_RIGHT);
    public static final KeyMapping SELECT = book("select", GLFW.GLFW_KEY_ENTER);
    public static final KeyMapping CLAIM = book("claim", GLFW.GLFW_KEY_C);
    public static final KeyMapping CLAIM_ALL = book("claim_all", KeyModifier.SHIFT, GLFW.GLFW_KEY_C);
    public static final KeyMapping PIN = book("pin", GLFW.GLFW_KEY_P);
    public static final KeyMapping EXPAND = book("expand", GLFW.GLFW_KEY_E);
    public static final KeyMapping PREV_CHAPTER = book("prev_chapter", GLFW.GLFW_KEY_PAGE_UP);
    public static final KeyMapping NEXT_CHAPTER = book("next_chapter", GLFW.GLFW_KEY_PAGE_DOWN);
    public static final KeyMapping ZOOM_IN = book("zoom_in", GLFW.GLFW_KEY_EQUAL);
    public static final KeyMapping ZOOM_OUT = book("zoom_out", GLFW.GLFW_KEY_MINUS);
    public static final KeyMapping FIT = book("fit", GLFW.GLFW_KEY_F);
    public static final KeyMapping PAN_UP = book("pan_up", KeyModifier.SHIFT, GLFW.GLFW_KEY_UP);
    public static final KeyMapping PAN_DOWN = book("pan_down", KeyModifier.SHIFT, GLFW.GLFW_KEY_DOWN);
    public static final KeyMapping PAN_LEFT = book("pan_left", KeyModifier.SHIFT, GLFW.GLFW_KEY_LEFT);
    public static final KeyMapping PAN_RIGHT = book("pan_right", KeyModifier.SHIFT, GLFW.GLFW_KEY_RIGHT);
    public static final KeyMapping SEARCH = book("search", GLFW.GLFW_KEY_SLASH);
    public static final KeyMapping LOG = book("log", GLFW.GLFW_KEY_L);
    public static final KeyMapping RECIPES = book("recipes", GLFW.GLFW_KEY_R);
    public static final KeyMapping USES = book("uses", GLFW.GLFW_KEY_U);
    public static final KeyMapping HELP = book("help", GLFW.GLFW_KEY_F1);

    /** Every book key, for registration. Modifier bindings first, so Shift+C is tried before C. */
    public static final List<KeyMapping> BOOK = List.of(
            CLAIM_ALL, PAN_UP, PAN_DOWN, PAN_LEFT, PAN_RIGHT,
            UP, DOWN, LEFT, RIGHT, SELECT, CLAIM, PIN, EXPAND, PREV_CHAPTER, NEXT_CHAPTER,
            ZOOM_IN, ZOOM_OUT, FIT, SEARCH, LOG, RECIPES, USES, HELP);

    /** One line of the key help: what it does and the keys that do it. */
    public record HelpRow(String key, String fallback, List<KeyMapping> mappings) {
    }

    public static final List<HelpRow> HELP_ROWS = List.of(
            new HelpRow("questqueen.keys.move", "Move between quests", List.of(UP, DOWN, LEFT, RIGHT)),
            new HelpRow("questqueen.keys.select", "Open quest / enter card / activate", List.of(SELECT)),
            new HelpRow("questqueen.keys.back", "Back (card, then book)", List.of()),
            new HelpRow("questqueen.keys.claim", "Claim or submit", List.of(CLAIM)),
            new HelpRow("questqueen.keys.claim_all", "Claim all", List.of(CLAIM_ALL)),
            new HelpRow("questqueen.keys.pin", "Pin or unpin", List.of(PIN)),
            new HelpRow("questqueen.keys.expand", "Read the description", List.of(EXPAND)),
            new HelpRow("questqueen.keys.lookup", "Recipes / uses of the focused item", List.of(RECIPES, USES)),
            new HelpRow("questqueen.keys.chapter", "Previous / next chapter", List.of(PREV_CHAPTER, NEXT_CHAPTER)),
            new HelpRow("questqueen.keys.zoom", "Zoom in / out", List.of(ZOOM_IN, ZOOM_OUT)),
            new HelpRow("questqueen.keys.fit", "Fit the chapter", List.of(FIT)),
            new HelpRow("questqueen.keys.pan", "Pan the board", List.of(PAN_UP, PAN_DOWN, PAN_LEFT, PAN_RIGHT)),
            new HelpRow("questqueen.keys.search", "Search", List.of(SEARCH)),
            new HelpRow("questqueen.keys.log", "Quest log", List.of(LOG)),
            new HelpRow("questqueen.keys.help", "This list", List.of(HELP)));

    private QuestKeybinds() {
    }

    private static KeyMapping book(String name, int key) {
        return book(name, KeyModifier.NONE, key);
    }

    private static KeyMapping book(String name, KeyModifier modifier, int key) {
        return new KeyMapping("key.questqueen." + name, KeyConflictContext.GUI, modifier,
                InputConstants.Type.KEYSYM, key, CATEGORY);
    }

    /** Does this key press (GLFW key and modifier bits) trigger {@code mapping} as it is bound now? */
    public static boolean pressed(KeyMapping mapping, int keyCode, int scanCode, int mods) {
        InputConstants.Key bound = mapping.getKey();
        if (bound.getType() != InputConstants.Type.KEYSYM && bound.getType() != InputConstants.Type.SCANCODE) {
            return false;
        }
        int needed = switch (mapping.getKeyModifier()) {
            case SHIFT -> KeyNav.SHIFT;
            case CONTROL -> KeyNav.CONTROL;
            case ALT -> KeyNav.ALT;
            default -> 0;
        };
        int pressedKey = bound.getType() == InputConstants.Type.SCANCODE ? scanCode : keyCode;
        return KeyNav.matches(bound.getValue(), needed, pressedKey, mods);
    }

    /** Is this key press any of the book's commands? */
    public static boolean anyBook(int keyCode, int scanCode, int mods) {
        for (KeyMapping mapping : BOOK) {
            if (pressed(mapping, keyCode, scanCode, mods)) {
                return true;
            }
        }
        return false;
    }
}
