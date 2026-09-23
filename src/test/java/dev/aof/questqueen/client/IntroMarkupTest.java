package dev.aof.questqueen.client;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IntroMarkupTest {
    private static final int INK = 0xFFF5F0E0;

    @Test
    void colorTagPaintsHexInk() {
        List<IntroMarkup.Block> blocks = IntroMarkup.parse("{#C4A35A}gold{/#}", INK);
        assertEquals(1, blocks.size());
        IntroMarkup.Span span = blocks.getFirst().spans().getFirst();
        assertEquals("gold", span.text());
        assertEquals(0xFFC4A35A, span.color());
    }

    @Test
    void sizeTagClampsToReadableRange() {
        IntroMarkup.Span big = IntroMarkup.parse("{size:20}big{/size}", INK).getFirst().spans().getFirst();
        assertEquals(20, big.size());
        IntroMarkup.Span tiny = IntroMarkup.parse("{size:3}tiny{/size}", INK).getFirst().spans().getFirst();
        assertEquals(IntroMarkup.MIN_SIZE, tiny.size());
        IntroMarkup.Span huge = IntroMarkup.parse("{size:99}huge{/size}", INK).getFirst().spans().getFirst();
        assertEquals(IntroMarkup.MAX_SIZE, huge.size());
    }

    @Test
    void headingsUseTitleAndSubtitle() {
        IntroMarkup.Block title = IntroMarkup.parse("# Act I", INK).getFirst();
        IntroMarkup.Block sub = IntroMarkup.parse("## Camp", INK).getFirst();
        assertEquals(IntroMarkup.Heading.TITLE, title.heading());
        assertEquals("Act I", title.spans().getFirst().text());
        assertEquals(IntroMarkup.Heading.SUBTITLE, sub.heading());
        assertEquals("Camp", sub.spans().getFirst().text());
    }

    @Test
    void boldAndItalicMarkers() {
        IntroMarkup.Span bold = IntroMarkup.parse("**bold**", INK).getFirst().spans().getFirst();
        IntroMarkup.Span italic = IntroMarkup.parse("*italic*", INK).getFirst().spans().getFirst();
        assertTrue(bold.bold());
        assertFalse(bold.italic());
        assertTrue(italic.italic());
        assertFalse(italic.bold());
    }

    @Test
    void blankLineBecomesParagraphGap() {
        List<IntroMarkup.Block> blocks = IntroMarkup.parse("one\n\ntwo", INK);
        assertEquals(3, blocks.size());
        assertTrue(blocks.get(1).blank());
        assertEquals("two", blocks.get(2).spans().getFirst().text());
    }

    @Test
    void fallbackPrefersIntroThenTitleThenTileDescription() {
        assertEquals("body", IntroMarkup.resolveBody("body", "Title", "desc"));
        assertEquals("# Title", IntroMarkup.resolveBody("  ", "Title", "desc"));
        assertEquals("desc", IntroMarkup.resolveBody("", "", "desc"));
        assertEquals("", IntroMarkup.resolveBody("", "", ""));
    }
}
