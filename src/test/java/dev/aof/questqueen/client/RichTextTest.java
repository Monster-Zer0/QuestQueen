package dev.aof.questqueen.client;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The description markup: what parses, what stays literal, and the plain text search sees. */
class RichTextTest {
    private static RichText.Paragraph only(String source) {
        List<RichText.Block> blocks = RichText.parse(source);
        assertEquals(1, blocks.size(), blocks.toString());
        return assertInstanceOf(RichText.Paragraph.class, blocks.getFirst());
    }

    private static List<RichText.Text> texts(RichText.Paragraph p) {
        return p.inlines().stream().filter(RichText.Text.class::isInstance).map(RichText.Text.class::cast).toList();
    }

    @Test
    void plainTextIsOneUnstyledRun() {
        RichText.Paragraph p = only("Dig down a little.");
        assertEquals(1, p.inlines().size());
        RichText.Text t = texts(p).getFirst();
        assertEquals("Dig down a little.", t.text());
        assertFalse(t.bold() || t.italic() || t.underline());
        assertEquals(0, t.color());
        assertEquals(RichText.BASE_SIZE, t.size());
    }

    @Test
    void emphasisAndColour() {
        RichText.Paragraph p = only("a **bold** and *italic* and __under__ and {#FF8800}orange{/#}");
        List<RichText.Text> runs = texts(p);
        assertTrue(runs.stream().anyMatch(t -> t.text().equals("bold") && t.bold()));
        assertTrue(runs.stream().anyMatch(t -> t.text().equals("italic") && t.italic() && !t.bold()));
        assertTrue(runs.stream().anyMatch(t -> t.text().equals("under") && t.underline()));
        assertTrue(runs.stream().anyMatch(t -> t.text().equals("orange") && t.color() == 0xFFFF8800));
    }

    @Test
    void stylesNest() {
        RichText.Paragraph p = only("**bold and *both* here**");
        assertTrue(texts(p).stream().anyMatch(t -> t.text().equals("both") && t.bold() && t.italic()));
        RichText.Paragraph coloured = only("{#00FF00}**green bold**{/#}");
        RichText.Text t = texts(coloured).getFirst();
        assertTrue(t.bold());
        assertEquals(0xFF00FF00, t.color());
    }

    @Test
    void unclosedOrSpacedMarkupStaysLiteral() {
        assertEquals("**not closed", texts(only("**not closed")).getFirst().text());
        assertEquals("5 * 3 * 2", texts(only("5 * 3 * 2")).getFirst().text());
        assertEquals("{#GGGGGG}x{/#}", texts(only("{#GGGGGG}x{/#}")).getFirst().text());
        assertEquals("{#FF0000}no close", texts(only("{#FF0000}no close")).getFirst().text());
        assertEquals("{size:abc}x{/size}", texts(only("{size:abc}x{/size}")).getFirst().text());
    }

    @Test
    void aBackslashKeepsMarkupLiteral() {
        RichText.Paragraph p = only("\\*not italic\\* and \\{item:minecraft:dirt}");
        assertEquals("*not italic* and {item:minecraft:dirt}", texts(p).stream().map(RichText.Text::text)
                .reduce("", String::concat));
        assertTrue(p.inlines().stream().noneMatch(RichText.Item.class::isInstance));
    }

    @Test
    void sizeIsClamped() {
        assertEquals(RichText.MAX_SIZE, texts(only("{size:99}big{/size}")).getFirst().size());
        assertEquals(RichText.MIN_SIZE, texts(only("{size:1}small{/size}")).getFirst().size());
    }

    @Test
    void headingsAndBullets() {
        assertEquals(RichText.Heading.TITLE, only("# Big").heading());
        assertEquals(RichText.Heading.SUBTITLE, only("## Smaller").heading());
        assertEquals(RichText.Heading.NONE, only("#hashtag").heading());
        RichText.Paragraph bullet = only("- first thing");
        assertTrue(bullet.bullet());
        assertEquals("first thing", texts(bullet).getFirst().text());
        assertFalse(only("-5 degrees").bullet());
    }

    @Test
    void inlineItemsWithAndWithoutACount() {
        RichText.Paragraph p = only("Bring {item:minecraft:diamond} or {item:minecraft:iron_ingot x3}.");
        List<RichText.Item> items = p.inlines().stream().filter(RichText.Item.class::isInstance)
                .map(RichText.Item.class::cast).toList();
        assertEquals(2, items.size());
        assertEquals(ResourceLocation.parse("minecraft:diamond"), items.get(0).id());
        assertEquals(1, items.get(0).count());
        assertEquals(3, items.get(1).count());
        // A bad id or count stays on screen as typed.
        assertEquals("{item:Not An Id}", texts(only("{item:Not An Id}")).getFirst().text());
        assertEquals("{item:minecraft:dirt xtwo}", texts(only("{item:minecraft:dirt xtwo}")).getFirst().text());
    }

    @Test
    void glyphsMustBeKnown() {
        RichText.Paragraph unknown = only("{glyph:definitely_not_a_glyph}");
        assertTrue(unknown.inlines().stream().noneMatch(RichText.Glyph.class::isInstance));
        assertEquals("{glyph:definitely_not_a_glyph}", texts(unknown).getFirst().text());
    }

    @Test
    void picturesNeedALoadableTexturePath() {
        List<RichText.Block> ok = RichText.parse("![The castle](mypack:textures/quest/castle.png)");
        RichText.Image image = assertInstanceOf(RichText.Image.class, ok.getFirst());
        assertEquals(ResourceLocation.parse("mypack:textures/quest/castle.png"), image.texture());
        assertEquals("The castle", image.caption());
        for (String bad : List.of("![x](mypack:quest/castle.png)", "![x](mypack:textures/quest/castle.jpg)",
                "![x](https://example.com/a.png)", "![x](nope)")) {
            assertInstanceOf(RichText.Paragraph.class, RichText.parse(bad).getFirst(), bad);
        }
        // Not alone on its line: it is just text.
        assertInstanceOf(RichText.Paragraph.class,
                RichText.parse("see ![x](mypack:textures/a.png) here").getFirst());
    }

    @Test
    void textureValidation() {
        assertTrue(RichText.validTexture("mypack:textures/quest/castle.png"));
        assertFalse(RichText.validTexture("mypack:castle.png"));
        assertFalse(RichText.validTexture("mypack:textures/castle"));
        assertFalse(RichText.validTexture(""));
        assertFalse(RichText.validTexture(null));
    }

    @Test
    void rulesAndBlankLines() {
        List<RichText.Block> blocks = RichText.parse("one\n\n\n---\ntwo\n");
        assertEquals(4, blocks.size(), blocks.toString());
        assertInstanceOf(RichText.Paragraph.class, blocks.get(0));
        assertInstanceOf(RichText.Gap.class, blocks.get(1));
        assertInstanceOf(RichText.Rule.class, blocks.get(2));
        assertInstanceOf(RichText.Paragraph.class, blocks.get(3));
        assertEquals(0, RichText.parse("\n\n  \n").size());
        assertEquals(0, RichText.parse(null).size());
    }

    @Test
    void plainTextForSearch() {
        String markup = "# Title\n**Bold** text with {item:minecraft:dirt} and {#FF0000}red{/#}.\n---\n"
                + "![The map](mypack:textures/map.png)\n- listed";
        assertEquals("Title\nBold text with  and red.\nThe map\nlisted", RichText.plain(markup));
        assertEquals("just words", RichText.plain("just words"));
        assertEquals("", RichText.plain(null));
    }

    @Test
    void neverThrowsOnOddInput() {
        for (String s : List.of("{", "{#", "{#FF00", "{#FF0000}", "**", "*", "__", "\\", "{item:", "{item:}",
                "![", "![]()", "![](", "{size:", "{/#}", "{/size}", "* *", "** **", "• ")) {
            RichText.parse(s);
            RichText.plain(s);
        }
    }
}
