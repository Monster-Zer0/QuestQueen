package dev.aof.questqueen.client;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Ten built-in chapter themes. Keep {@link QuestColors} midnight values as the source for {@link #MIDNIGHT_ROYALTY}.
 * Editor mirror: {@code editor/src/themes.ts}.
 */
public final class BookTheme {
    private static final Map<String, BookTheme> BY_ID = new LinkedHashMap<>();

    public static final BookTheme MIDNIGHT_ROYALTY = register("midnight_royalty", "Midnight Royalty", midnight());
    public static final BookTheme OCEAN_DEPTHS = register("ocean_depths", "Ocean Depths", ocean());
    public static final BookTheme EMBER_FORGE = register("ember_forge", "Ember Forge", ember());
    public static final BookTheme VERDANT_GROVE = register("verdant_grove", "Verdant Grove", verdant());
    public static final BookTheme DESERT_OASIS = register("desert_oasis", "Desert Oasis", desert());
    public static final BookTheme FROST_PEAK = register("frost_peak", "Frost Peak", frost());
    public static final BookTheme NETHER_SCAR = register("nether_scar", "Nether Scar", nether());
    public static final BookTheme END_CHORUS = register("end_chorus", "End Chorus", endChorus());
    public static final BookTheme COPPER_CIRCUIT = register("copper_circuit", "Copper Circuit", copper());
    public static final BookTheme PAPER_SCROLL = register("paper_scroll", "Paper Scroll", paper());

    private final String id;
    private final String displayName;
    private final BookPalette palette;

    private BookTheme(String id, String displayName, BookPalette palette) {
        this.id = id;
        this.displayName = displayName;
        this.palette = palette;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    public BookPalette palette() {
        return palette;
    }

    public static BookTheme byId(String id) {
        if (id == null || id.isBlank()) {
            return MIDNIGHT_ROYALTY;
        }
        return Optional.ofNullable(BY_ID.get(id.trim().toLowerCase(Locale.ROOT))).orElse(MIDNIGHT_ROYALTY);
    }

    public static Collection<BookTheme> all() {
        return BY_ID.values();
    }

    private static BookTheme register(String id, String displayName, BookPalette palette) {
        BookTheme theme = new BookTheme(id, displayName, palette);
        BY_ID.put(id, theme);
        return theme;
    }

    /** Current live QuestColors (mockupfull / STATUS). */
    private static BookPalette midnight() {
        return new BookPalette(
                0xFF24142C, 0xFF18101F, 0xFF4A3C62, 0xFF0A0610,
                0xFF1C1526, 0xFF3A3048, 0xFF2A2238, 0xFFB8A8C8, 0xFF8A7A9A,
                0xFF2B203A, 0xFF7A6A90,
                0xFF3DFF6E, 0xFF488BD4, 0xFFFFB84A, 0xFFFFF540, BookPalette.AUTH_FAILED, 0xFF6A6080,
                0xFF16121C, 0xFFF5F0E0, 0xFFA898B8,
                BookPalette.AUTH_LOCKED_EDGE, 0xFF6A6080, 0xFFFFF540, 0x66FFB84A, 0x668A82A0,
                BookPalette.AUTH_MODAL_PINK, BookPalette.AUTH_LOCKED_EDGE,
                0xFFC8C0D8, 0xFF4AD4FF, 0xFFFFF540, 0xFF488BD4, BookPalette.AUTH_MODAL_PINK
        );
    }

    private static BookPalette ocean() {
        return new BookPalette(
                0xFF0A1E28, 0xFF08161C, 0xFF2A5A68, 0xFF040C10,
                0xFF0C1A22, 0xFF1E4050, 0xFF143040, 0xFFA0C8D4, 0xFF6A9AAA,
                0xFF142830, 0xFF5A8898,
                0xFF3DFFC8, 0xFF3AA8E8, 0xFFFFC060, 0xFFE8F0FF, BookPalette.AUTH_FAILED, 0xFF5A7080,
                0xFF0C1418, 0xFFE8F4F8, 0xFF88A8B4,
                BookPalette.AUTH_LOCKED_EDGE, 0xFF5A7080, 0xFFE8F0FF, 0x663AA8E8, 0x66708898,
                BookPalette.AUTH_MODAL_PINK, BookPalette.AUTH_LOCKED_EDGE,
                0xFFA0C0C8, 0xFF4AD4FF, 0xFFE8F0FF, 0xFF3AA8E8, BookPalette.AUTH_MODAL_PINK
        );
    }

    private static BookPalette ember() {
        return new BookPalette(
                0xFF1A100C, 0xFF120C08, 0xFF5A3828, 0xFF080404,
                0xFF16100C, 0xFF3A2820, 0xFF241814, 0xFFD0B0A0, 0xFF9A7060,
                0xFF241810, 0xFF8A6860,
                0xFFFF7A3A, 0xFFE86828, 0xFFFFB040, 0xFFFFD080, BookPalette.AUTH_FAILED, 0xFF6A5850,
                0xFF100C08, 0xFFFFF0E0, 0xFFB89888,
                BookPalette.AUTH_LOCKED_EDGE, 0xFF6A5850, 0xFFFFD080, 0x66E86828, 0x668A7060,
                BookPalette.AUTH_MODAL_PINK, BookPalette.AUTH_LOCKED_EDGE,
                0xFFC8B0A0, 0xFFFF9050, 0xFFFFD080, 0xFFE86828, BookPalette.AUTH_MODAL_PINK
        );
    }

    private static BookPalette verdant() {
        return new BookPalette(
                0xFF0E1A12, 0xFF0A140E, 0xFF2E5A3A, 0xFF040A06,
                0xFF101A14, 0xFF284838, 0xFF182820, 0xFFA8C8B0, 0xFF6A9078,
                0xFF182820, 0xFF688878,
                0xFF5CFF7A, 0xFF4AC878, 0xFFFFC050, 0xFFFFE060, BookPalette.AUTH_FAILED, 0xFF587068,
                0xFF0C140E, 0xFFF0F8E8, 0xFF90B098,
                BookPalette.AUTH_LOCKED_EDGE, 0xFF587068, 0xFFFFE060, 0x664AC878, 0x66708870,
                BookPalette.AUTH_MODAL_PINK, BookPalette.AUTH_LOCKED_EDGE,
                0xFFB0C8B0, 0xFF50D090, 0xFFFFE060, 0xFF4AC878, BookPalette.AUTH_MODAL_PINK
        );
    }

    private static BookPalette desert() {
        return new BookPalette(
                0xFF2A2218, 0xFF1E1810, 0xFF6A5840, 0xFF100C08,
                0xFF221C14, 0xFF4A3C2C, 0xFF32281C, 0xFFD8C8A8, 0xFFA89070,
                0xFF2A2218, 0xFF908060,
                0xFF40E8C0, 0xFF38B8D0, 0xFFFFB050, 0xFFFFE8A0, BookPalette.AUTH_FAILED, 0xFF706858,
                0xFF18140C, 0xFFFFF8E8, 0xFFB8A888,
                BookPalette.AUTH_LOCKED_EDGE, 0xFF706858, 0xFFFFE8A0, 0x6638B8D0, 0x66908068,
                BookPalette.AUTH_MODAL_PINK, BookPalette.AUTH_LOCKED_EDGE,
                0xFFD0C0A0, 0xFF50D0E0, 0xFFFFE8A0, 0xFF38B8D0, BookPalette.AUTH_MODAL_PINK
        );
    }

    private static BookPalette frost() {
        return new BookPalette(
                0xFF121820, 0xFF0C1218, 0xFF3A5068, 0xFF060A0E,
                0xFF10161C, 0xFF2A3C50, 0xFF1A2838, 0xFFB0C8D8, 0xFF7890A8,
                0xFF182028, 0xFF688090,
                0xFF80E8FF, 0xFF68A8E8, 0xFFC0E0FF, 0xFFF0F8FF, BookPalette.AUTH_FAILED, 0xFF607080,
                0xFF0C1014, 0xFFF0F8FF, 0xFF98B0C0,
                BookPalette.AUTH_LOCKED_EDGE, 0xFF607080, 0xFFF0F8FF, 0x6668A8E8, 0x66788898,
                BookPalette.AUTH_MODAL_PINK, BookPalette.AUTH_LOCKED_EDGE,
                0xFFB0C0D0, 0xFF70D0FF, 0xFFF0F8FF, 0xFF68A8E8, BookPalette.AUTH_MODAL_PINK
        );
    }

    private static BookPalette nether() {
        return new BookPalette(
                0xFF100808, 0xFF0C0404, 0xFF5A2820, 0xFF060202,
                0xFF140A0A, 0xFF3A1814, 0xFF241010, 0xFFD0A090, 0xFF906858,
                0xFF1C0C0C, 0xFF805850,
                0xFFFF6020, 0xFFE84818, 0xFFFF9040, 0xFFFFC060, BookPalette.AUTH_FAILED, 0xFF685050,
                0xFF0C0404, 0xFFFFF0E8, 0xFFB88878,
                BookPalette.AUTH_LOCKED_EDGE, 0xFF685050, 0xFFFFC060, 0x66E84818, 0x66806050,
                BookPalette.AUTH_MODAL_PINK, BookPalette.AUTH_LOCKED_EDGE,
                0xFFC8A090, 0xFFFF7040, 0xFFFFC060, 0xFFE84818, BookPalette.AUTH_MODAL_PINK
        );
    }

    private static BookPalette endChorus() {
        return new BookPalette(
                0xFF1A1028, 0xFF120C1C, 0xFF4A3868, 0xFF080410,
                0xFF16101E, 0xFF3A2A50, 0xFF241830, 0xFFC8B0E0, 0xFF8A70B0,
                0xFF221830, 0xFF7860A0,
                0xFFE060FF, 0xFF68C0FF, 0xFFFF90D0, 0xFFF0C0FF, BookPalette.AUTH_FAILED, 0xFF685878,
                0xFF100C18, 0xFFF8F0FF, 0xFFA890C0,
                BookPalette.AUTH_LOCKED_EDGE, 0xFF685878, 0xFFF0C0FF, 0x6668C0FF, 0x668070A0,
                BookPalette.AUTH_MODAL_PINK, BookPalette.AUTH_LOCKED_EDGE,
                0xFFC0B0D8, 0xFF70D0FF, 0xFFF0C0FF, 0xFF68C0FF, BookPalette.AUTH_MODAL_PINK
        );
    }

    private static BookPalette copper() {
        return new BookPalette(
                0xFF121A18, 0xFF0C1412, 0xFF3A5A50, 0xFF060A08,
                0xFF101816, 0xFF2A4840, 0xFF1A3028, 0xFFA8C8B8, 0xFF6A9080,
                0xFF182820, 0xFF688878,
                0xFF50E8A8, 0xFF40B8A0, 0xFFE8A060, 0xFFD0E8C0, BookPalette.AUTH_FAILED, 0xFF587068,
                0xFF0C1410, 0xFFE8F8F0, 0xFF90B0A0,
                BookPalette.AUTH_LOCKED_EDGE, 0xFF587068, 0xFFD0E8C0, 0x6640B8A0, 0x66708878,
                BookPalette.AUTH_MODAL_PINK, BookPalette.AUTH_LOCKED_EDGE,
                0xFFB0C8B8, 0xFF50D0C0, 0xFFD0E8C0, 0xFF40B8A0, BookPalette.AUTH_MODAL_PINK
        );
    }

    private static BookPalette paper() {
        return new BookPalette(
                0xFF2A2418, 0xFF221C14, 0xFF5A4C38, 0xFF100C08,
                0xFF242018, 0xFF4A4030, 0xFF322C20, 0xFFD0C4A8, 0xFF9A8C70,
                0xFF2C2418, 0xFF8A7C60,
                0xFFC07030, 0xFF6880A0, 0xFFB85828, 0xFFD8B060, BookPalette.AUTH_FAILED, 0xFF6A6050,
                0xFF1C1810, 0xFFF8F0DC, 0xFFB0A488,
                BookPalette.AUTH_LOCKED_EDGE, 0xFF6A6050, 0xFFD8B060, 0x666880A0, 0x668A8070,
                BookPalette.AUTH_MODAL_PINK, BookPalette.AUTH_LOCKED_EDGE,
                0xFFC8BCA0, 0xFF70A0C0, 0xFFD8B060, 0xFF6880A0, BookPalette.AUTH_MODAL_PINK
        );
    }

}
