package dev.aof.questqueen.data;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChapterCodecTest {
    @Test
    void starterChapterRoundTripsThroughTheCodec() throws Exception {
        try (InputStream stream = ChapterCodecTest.class.getResourceAsStream("/data/questqueen/questqueen/chapters/starter.json")) {
            if (stream == null) {
                Chapter chapter = Chapter.blank(net.minecraft.resources.ResourceLocation.parse("questqueen:blank"));
                var encoded = Chapter.CODEC.encodeStart(JsonOps.INSTANCE, chapter).getOrThrow(RuntimeException::new);
                Chapter parsed = Chapter.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow(RuntimeException::new);
                assertEquals(chapter.id(), parsed.id());
                assertEquals(1, StartNodes.of(parsed).size());
                return;
            }
            var json = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8));
            Chapter parsed = Chapter.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(RuntimeException::new);
            assertEquals("questqueen:starter", parsed.id().toString());
            assertEquals(6, parsed.tiles().size());
            assertTrue(StartNodes.of(parsed).contains("make_chest"));
            var encoded = Chapter.CODEC.encodeStart(JsonOps.INSTANCE, parsed).getOrThrow(RuntimeException::new);
            Chapter again = Chapter.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow(RuntimeException::new);
            assertEquals(parsed.tiles().size(), again.tiles().size());
            assertEquals(parsed.links().size(), again.links().size());
        }
    }

    @Test
    void themeAndBackgroundRoundTrip() {
        Chapter chapter = Chapter.blank(net.minecraft.resources.ResourceLocation.parse("questqueen:themed"))
                .withTheme("ocean_depths", Optional.of(new ChapterBackground(
                        ChapterBackground.MODE_IMAGE,
                        Optional.of("#0A1E28"),
                        Optional.of(net.minecraft.resources.ResourceLocation.parse("questqueen:textures/gui/bg/ocean.png")),
                        0.85f
                )));
        var encoded = Chapter.CODEC.encodeStart(JsonOps.INSTANCE, chapter).getOrThrow(RuntimeException::new);
        Chapter parsed = Chapter.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow(RuntimeException::new);
        assertEquals("ocean_depths", parsed.theme());
        assertTrue(parsed.background().isPresent());
        ChapterBackground bg = parsed.background().get();
        assertEquals(ChapterBackground.MODE_IMAGE, bg.mode());
        assertEquals(Optional.of("#0A1E28"), bg.color());
        assertEquals("questqueen:textures/gui/bg/ocean.png", bg.image().orElseThrow().toString());
        assertEquals(0.85f, bg.opacity(), 0.001f);
    }

    @Test
    void missingThemeDefaultsToMidnight() {
        var json = JsonParser.parseString("{\"id\":\"questqueen:legacy\",\"tiles\":[{\"id\":\"a\",\"pos\":{\"x\":0,\"y\":0}}]}");
        Chapter parsed = Chapter.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(RuntimeException::new);
        assertEquals(Chapter.DEFAULT_THEME, parsed.theme());
        assertTrue(parsed.background().isEmpty());
        assertFalse(parsed.hideUntilUnlocked());
    }

    @Test
    void hideUntilUnlockedRoundTrips() {
        Chapter chapter = Chapter.blank(net.minecraft.resources.ResourceLocation.parse("questqueen:secret"))
                .withHideUntilUnlocked(true);
        var encoded = Chapter.CODEC.encodeStart(JsonOps.INSTANCE, chapter).getOrThrow(RuntimeException::new);
        Chapter parsed = Chapter.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow(RuntimeException::new);
        assertTrue(parsed.hideUntilUnlocked());
        assertTrue(parsed.withTitle("Secret").hideUntilUnlocked());
    }

    @Test
    void introAndGlyphRoundTrip() {
        Chapter chapter = Chapter.blank(net.minecraft.resources.ResourceLocation.parse("questqueen:act_i"))
                .withIntro(Optional.of(new ChapterIntro(
                        Optional.of(net.minecraft.resources.ResourceLocation.parse("skylore:textures/gui/intro/act_i.png")),
                        "# Act I\n{#C4A35A}You hit Atmos.{/#}"
                )))
                .withMeta(Optional.empty(), 0, Optional.of(Icon.glyph("rocket")), Gate.AND);
        var encoded = Chapter.CODEC.encodeStart(JsonOps.INSTANCE, chapter).getOrThrow(RuntimeException::new);
        Chapter parsed = Chapter.CODEC.parse(JsonOps.INSTANCE, encoded).getOrThrow(RuntimeException::new);
        assertTrue(parsed.intro().isPresent());
        assertEquals("skylore:textures/gui/intro/act_i.png", parsed.intro().get().image().orElseThrow().toString());
        assertTrue(parsed.intro().get().body().contains("Atmos"));
        assertEquals(Optional.of("rocket"), parsed.icon().flatMap(Icon::glyphId));
        assertTrue(parsed.icon().get().usesGlyph());
    }

    @Test
    void parentChapterMayOmitTiles() {
        var json = JsonParser.parseString("{\"id\":\"questqueen:act_i\",\"title\":\"Act I\",\"intro\":{\"body\":\"# Hello\"}}");
        Chapter parsed = Chapter.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(RuntimeException::new);
        assertTrue(parsed.tiles().isEmpty());
        assertEquals("# Hello", parsed.intro().map(ChapterIntro::body).orElse(""));
    }

    @Test
    void iconGlyphWithoutItemParses() {
        var json = JsonParser.parseString("{\"id\":\"questqueen:g\",\"tiles\":[{\"id\":\"a\",\"pos\":{\"x\":0,\"y\":0},\"icon\":{\"glyph\":\"sword\"}}]}");
        Chapter parsed = Chapter.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(RuntimeException::new);
        assertEquals(Optional.of("sword"), parsed.tiles().getFirst().icon().flatMap(Icon::glyphId));
        assertTrue(parsed.tiles().getFirst().icon().get().item().isEmpty());
    }
}
