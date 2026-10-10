package dev.aof.questqueen.client;

import net.minecraft.resources.ResourceLocation;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Wrapping, spacing, pictures and scroll maths, against a fixed-width font (6px a letter, 7 when bold). */
class RichLayoutTest {
    private static final RichLayout.Measure FONT = (text, bold) -> text.length() * (bold ? 7 : 6);
    private static final ResourceLocation TEX = ResourceLocation.parse("mypack:textures/a.png");

    private static RichLayout.Laid lay(String source, int width) {
        return RichLayout.layout(RichText.parse(source), width, FONT, t -> new int[]{64, 32}, 200);
    }

    private static List<RichLayout.TextPiece> texts(RichLayout.Laid laid) {
        return laid.pieces().stream().filter(RichLayout.TextPiece.class::isInstance)
                .map(RichLayout.TextPiece.class::cast).toList();
    }

    @Test
    void plainTextWrapsOnWordsAndEachSourceLineIsARow() {
        // 60px column holds ten letters: "hello" + " world" = 11 letters does not fit.
        RichLayout.Laid laid = lay("hello world\nok", 60);
        assertEquals(3 * RichLayout.LINE, laid.height());
        assertEquals(List.of("hello", "world", "ok"), texts(laid).stream().map(RichLayout.TextPiece::text).toList());
        assertEquals(0, texts(laid).get(1).x(), "a wrapped word starts at the left edge");
    }

    @Test
    void aBlankSourceLineCostsOneRowLikeBefore() {
        assertEquals(3 * RichLayout.LINE, lay("a\n\nb", 200).height());
    }

    @Test
    void boldRunsAreWiderAndKeepTheirSpacing() {
        RichLayout.Laid laid = lay("ab **cd** ef", 200);
        List<RichLayout.TextPiece> t = texts(laid);
        assertEquals(List.of("ab", "cd", "ef"), t.stream().map(RichLayout.TextPiece::text).toList());
        assertEquals(0, t.get(0).x());
        assertEquals(12 + 6, t.get(1).x(), "after 'ab' and one space");
        assertTrue(t.get(1).bold());
        assertEquals(12 + 6 + 14 + 6, t.get(2).x(), "after bold 'cd' (14px) and a space");
    }

    @Test
    void aLongWordIsBrokenByCharactersInsteadOfOverflowing() {
        RichLayout.Laid laid = lay("abcdefghijklmnopqrstuvwxyz", 60);
        for (RichLayout.TextPiece piece : texts(laid)) {
            assertTrue(piece.x() + piece.width(FONT) <= 60, piece.text() + " at " + piece.x());
        }
        assertEquals("abcdefghijklmnopqrstuvwxyz", texts(laid).stream().map(RichLayout.TextPiece::text)
                .reduce("", String::concat));
    }

    @Test
    void bulletsIndentAndHeadingsAreBiggerAndBold() {
        RichLayout.Laid bullet = lay("- one two three four five six", 80);
        List<RichLayout.TextPiece> t = texts(bullet);
        assertEquals("•", t.getFirst().text());
        assertEquals(0, t.getFirst().x());
        assertTrue(t.stream().skip(1).allMatch(p -> p.x() >= RichLayout.BULLET_INDENT), "text is indented");
        RichLayout.Laid heading = lay("# Big", 200);
        RichLayout.TextPiece big = texts(heading).getFirst();
        assertEquals(14, big.size(), "9px at 1.5x");
        assertTrue(big.bold());
        assertTrue(heading.height() > RichLayout.LINE);
    }

    @Test
    void iconsTakeASquareAndACountSitsBesideItem() {
        RichLayout.Laid laid = lay("get {item:minecraft:diamond x3} now", 200);
        RichLayout.ItemPiece item = laid.pieces().stream().filter(RichLayout.ItemPiece.class::isInstance)
                .map(RichLayout.ItemPiece.class::cast).findFirst().orElseThrow();
        assertEquals(24, item.x(), "after 'get' (18px) and a space (6px)");
        List<RichLayout.TextPiece> t = texts(laid);
        RichLayout.TextPiece count = t.stream().filter(p -> p.text().equals("x3")).findFirst().orElseThrow();
        assertEquals(24 + RichLayout.ICON + 1, count.x());
        RichLayout.TextPiece now = t.stream().filter(p -> p.text().equals("now")).findFirst().orElseThrow();
        assertEquals(24 + RichLayout.ICON + 1 + 12 + 6, now.x(), "the count's width is reserved before the next word");
    }

    @Test
    void picturesScaleToFitAndGrowInWholeSteps() {
        List<RichText.Block> blocks = RichText.parse("![](mypack:textures/a.png)");
        RichLayout.Laid wide = RichLayout.layout(blocks, 200, FONT, t -> new int[]{64, 32}, 200);
        RichLayout.ImagePiece image = (RichLayout.ImagePiece) wide.pieces().getFirst();
        assertEquals(192, image.w(), "3.125x rounds down to 3x so pixel art stays crisp");
        assertEquals(96, image.h());
        assertEquals(4, image.x(), "centred in the column");
        assertEquals(102, wide.height());

        RichLayout.ImagePiece capped = (RichLayout.ImagePiece) RichLayout.layout(blocks, 200, FONT,
                t -> new int[]{64, 32}, 40).pieces().getFirst();
        assertEquals(64, capped.w(), "a height cap holds the picture to its own size here");
        assertEquals(32, capped.h());

        RichLayout.ImagePiece narrow = (RichLayout.ImagePiece) RichLayout.layout(blocks, 32, FONT,
                t -> new int[]{64, 32}, 200).pieces().getFirst();
        assertEquals(32, narrow.w());
        assertEquals(16, narrow.h(), "shrinks to the column, keeping its shape");
    }

    @Test
    void aCaptionSitsUnderThePictureAndAMissingPictureGetsAFrame() {
        RichLayout.Laid laid = lay("![A castle](mypack:textures/a.png)", 200);
        RichLayout.TextPiece caption = texts(laid).getFirst();
        assertEquals("A castle", caption.text());
        assertTrue(caption.muted());
        assertEquals(112, laid.height());
        RichLayout.Laid missing = RichLayout.layout(RichText.parse("![](mypack:textures/a.png)"), 200, FONT,
                t -> null, 200);
        RichLayout.ImagePiece frame = (RichLayout.ImagePiece) missing.pieces().getFirst();
        assertTrue(frame.missing());
        assertEquals(96, frame.w());
    }

    @Test
    void rulesTakeAFewPixels() {
        RichLayout.Laid laid = lay("a\n---\nb", 200);
        assertTrue(laid.pieces().stream().anyMatch(RichLayout.RulePiece.class::isInstance));
        assertEquals(2 * RichLayout.LINE + 6, laid.height());
    }

    @Test
    void scrollMath() {
        assertEquals(60, RichLayout.maxScroll(100, 40));
        assertEquals(0, RichLayout.maxScroll(30, 40));
        assertEquals(60, RichLayout.clampScroll(80, 100, 40));
        assertEquals(0, RichLayout.clampScroll(-5, 100, 40));
        assertEquals(0, RichLayout.clampScroll(10, 30, 40));

        int[] top = RichLayout.thumb(40, 40, 100, 0);
        assertEquals(0, top[0]);
        assertEquals(16, top[1]);
        int[] bottom = RichLayout.thumb(40, 40, 100, 60);
        assertEquals(24, bottom[0], "the thumb rests at the bottom of the track");
        assertEquals(40, RichLayout.thumb(40, 40, 30, 0)[1], "nothing to scroll: the thumb fills the track");

        assertEquals(0, RichLayout.scrollForThumb(0, 40, 40, 100));
        assertEquals(60, RichLayout.scrollForThumb(40, 40, 40, 100));
        assertEquals(30, RichLayout.scrollForThumb(20, 40, 40, 100));
    }
}
