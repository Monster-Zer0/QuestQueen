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
    /** Load Test Range / blank / starter / nether. Default false. */
    public static final ModConfigSpec.BooleanValue INCLUDE_DEV_CHAPTERS;
    /** Master switch for the book's motion layer (path flow, flares, pips, shimmer). */
    public static final ModConfigSpec.BooleanValue ANIMATIONS;
    /** Reveal chapter-intro body text progressively on first open. */
    public static final ModConfigSpec.BooleanValue INTRO_TYPEWRITER;
    /** Per-theme drifting board particles (stars / embers / caustics). */
    public static final ModConfigSpec.BooleanValue AMBIENT_THEME_FX;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        GITHUB_ISSUES_URL = builder
                .comment("If set, the book shows a report button that opens this issues/new URL with chapter and tile ids.")
                .define("githubIssuesUrl", "");
        builder.push("effects");
        ANIMATIONS = builder
                .comment("Book motion: travelling path arrows, completion flares, gate pips, modal shimmer,",
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
        INCLUDE_DEV_CHAPTERS = builder
                .comment("Load jar Test Range / blank / starter / nether chapters. Leave false for distribution.")
                .define("includeDevChapters", false);
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
        SPEC = builder.build();
    }

    private QuestConfig() {
    }
}