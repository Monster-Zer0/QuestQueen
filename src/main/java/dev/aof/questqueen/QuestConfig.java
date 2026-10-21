package dev.aof.questqueen;

import net.neoforged.neoforge.common.ModConfigSpec;

public final class QuestConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.ConfigValue<String> GITHUB_ISSUES_URL;
    /** Empty = use pack book.json / BookChrome.DEFAULT. */
    public static final ModConfigSpec.ConfigValue<String> SIDEBAR_TITLE;
    /** Hex ARGB like FF8A7A9A; empty = use pack. */
    public static final ModConfigSpec.ConfigValue<String> SIDEBAR_TITLE_COLOR;
    /** -1 = use pack; otherwise 0.5–2.0. */
    public static final ModConfigSpec.DoubleValue SIDEBAR_TITLE_SCALE;
    /** -1 = use pack; 0 = false; 1 = true. */
    public static final ModConfigSpec.IntValue SIDEBAR_TITLE_SHADOW;
    /** auto | always | never — see DemoChapters. */
    public static final ModConfigSpec.ConfigValue<String> DEMO_CHAPTERS;
    /** Master switch for the book's motion layer (path flow, flares, pips, shimmer). */
    public static final ModConfigSpec.BooleanValue ANIMATIONS;
    /** Reveal chapter-intro body text progressively on first open. */
    public static final ModConfigSpec.BooleanValue INTRO_TYPEWRITER;
    /** Per-theme drifting board particles (stars / embers / caustics). */
    public static final ModConfigSpec.BooleanValue AMBIENT_THEME_FX;
    /** Hex ARGB wash over a claimable tile; empty = the theme's own. */
    public static final ModConfigSpec.ConfigValue<String> CLAIM_TINT;
    /** Hex ARGB frame of a claimable tile; empty = the theme's own. */
    public static final ModConfigSpec.ConfigValue<String> CLAIM_EDGE;
    /** Where the pinned quest sits on the HUD: top_left (default), top_center, top_right, middle_left, middle_right, bottom_left, bottom_right. */
    /** The keyboard icon in the book's top bar that lists the keys on hover. */
    public static final ModConfigSpec.BooleanValue SHOW_KEY_HELP_ICON;
    /** Quest book button in the survival inventory. */
    public static final ModConfigSpec.BooleanValue INVENTORY_BUTTON;
    public static final ModConfigSpec.IntValue INVENTORY_BUTTON_X;
    public static final ModConfigSpec.IntValue INVENTORY_BUTTON_Y;
    public static final ModConfigSpec.ConfigValue<String> PIN_POSITION;
    public static final ModConfigSpec.IntValue PIN_OFFSET_X;
    public static final ModConfigSpec.IntValue PIN_OFFSET_Y;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        GITHUB_ISSUES_URL = builder
                .comment("If set, the book shows a report button that opens this issues/new URL with chapter and tile ids.")
                .define("githubIssuesUrl", "");
        builder.push("effects");
        ANIMATIONS = builder
                .comment("Book motion: traveling path arrows, completion flares, gate pips, modal shimmer,",
                        "breathing CLAIM, sidebar slide, inspect settle, log drawer, claim sparks,",
                        "choice arrival, selection halo, task fill. Turn off for a completely static book.")
                .define("animations", true);
        INTRO_TYPEWRITER = builder
                .comment("Type chapter-intro body text in on first open of a chapter.")
                .define("introTypewriter", true);
        AMBIENT_THEME_FX = builder
                .comment("Per-theme ambient board particles (midnight stars, nether embers, ocean caustics).")
                .define("ambientThemeEffects", true);
        builder.pop();        builder.push("demo");
        DEMO_CHAPTERS = builder
                .comment("Built-in Feature Showcase: auto (hide when a pack has chapters), always, or never.")
                .define("demoChapters", "auto");
        builder.pop();
        builder.push("sidebarTitleChrome");
        SIDEBAR_TITLE = builder
                .comment("Override the book sidebar title. Empty uses pack book.json (default CHAPTERS).")
                .define("sidebarTitle", "");
        SIDEBAR_TITLE_COLOR = builder
                .comment("Override title color as hex ARGB (e.g. FF8A7A9A). Empty uses pack.")
                .define("sidebarTitleColor", "");
        SIDEBAR_TITLE_SCALE = builder
                .comment("Override title scale (0.5–2.0). -1 uses pack.")
                .defineInRange("sidebarTitleScale", -1.0, -1.0, 2.0);
        SIDEBAR_TITLE_SHADOW = builder
                .comment("Override title shadow: -1 pack, 0 off, 1 on.")
                .defineInRange("sidebarTitleShadow", -1, -1, 1);
        builder.pop();
        builder.push("keyboard");
        SHOW_KEY_HELP_ICON = builder
                .comment("Show the keyboard icon in the quest book's top bar; hovering it lists the book's keys.",
                        "The keys themselves are rebound in Controls > Key Binds > Quest Queen.")
                .define("showKeyHelpIcon", true);
        builder.pop();
        builder.push("inventoryButton");
        INVENTORY_BUTTON = builder
                .comment("Show a quest book button in the survival inventory (top right, above the crafting output).")
                .define("enabled", true);
        INVENTORY_BUTTON_X = builder
                .comment("Move the button sideways from its spot, in GUI pixels (negative is left), e.g. to clear another mod's button.")
                .defineInRange("offsetX", 0, -400, 400);
        INVENTORY_BUTTON_Y = builder
                .comment("Move the button up or down from its spot, in GUI pixels (negative is up).")
                .defineInRange("offsetY", 0, -400, 400);
        builder.pop();
        builder.push("pinnedQuest");
        PIN_POSITION = builder
                .comment("Where the pinned quest shows on screen: top_left, top_center, top_right, middle_left,",
                        "middle_right, bottom_left or bottom_right. Hover it with the cursor free (e.g. chat open) for details.")
                .define("position", "top_left");
        PIN_OFFSET_X = builder
                .comment("Nudge the pinned quest sideways, in GUI pixels (positive moves it away from its edge).")
                .defineInRange("offsetX", 0, -1000, 1000);
        PIN_OFFSET_Y = builder
                .comment("Nudge the pinned quest up or down, in GUI pixels (positive moves it away from its edge).")
                .defineInRange("offsetY", 0, -1000, 1000);
        builder.pop();
        builder.push("claimChrome");
        CLAIM_TINT = builder
                .comment("Wash over a finished-but-unclaimed quest tile, as hex ARGB (default 33FFD54F, a light warm gold).",
                        "Keep the alpha low so the tile's text still reads. Empty uses the chapter theme's.")
                .define("claimTint", "");
        CLAIM_EDGE = builder
                .comment("Frame of a finished-but-unclaimed quest tile, as hex ARGB (default FFFFD54F). Empty uses the theme's.")
                .define("claimEdge", "");
        builder.pop();
        SPEC = builder.build();
    }

    private QuestConfig() {
    }
}