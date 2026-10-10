package dev.aof.questqueen.client;

import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;

/**
 * Places {@link RichText} blocks on a column of a given width: word wrap across styled runs and icons, bullets,
 * headings, rules and pictures scaled to fit. Pure arithmetic over a {@link Measure}, so it is tested without a
 * game. The positions are relative to the column's top-left; {@link RichDraw} paints them.
 */
public final class RichLayout {
    /** Height of one line of base-size text. A blank source line is one of these, as it always was. */
    public static final int LINE = 10;
    /** Square an inline item or glyph icon is drawn in. */
    public static final int ICON = 10;
    public static final int BULLET_INDENT = 8;
    private static final int CAPTION_SIZE = 8;
    private static final int MAX_PIXEL_SCALE = 4;

    /** Text width at {@link RichText#BASE_SIZE}; larger and smaller sizes are scaled from it. */
    public interface Measure {
        int width(String text, boolean bold);
    }

    /** Native size of a picture, or {@code null} when it cannot be loaded. */
    public interface ImageSizer {
        int[] size(ResourceLocation texture);
    }

    public sealed interface Piece permits TextPiece, ItemPiece, GlyphPiece, ImagePiece, RulePiece {
        int y();

        int height();
    }

    /** A run of text; {@code color} 0 means the caller's default, {@code muted} asks for the muted colour. */
    public record TextPiece(int x, int y, int height, String text, int color, int size, boolean bold, boolean italic,
                            boolean underline, boolean muted) implements Piece {
        public int width(Measure m) {
            return Math.round(m.width(text, bold) * size / (float) RichText.BASE_SIZE);
        }
    }

    public record ItemPiece(int x, int y, ResourceLocation id) implements Piece {
        @Override
        public int height() {
            return ICON;
        }
    }

    public record GlyphPiece(int x, int y, String id, int color) implements Piece {
        @Override
        public int height() {
            return ICON;
        }
    }

    /** {@code missing} pictures are drawn as a labelled empty frame. */
    public record ImagePiece(int x, int y, int w, int h, ResourceLocation texture, int texW, int texH,
                             boolean missing) implements Piece {
        @Override
        public int height() {
            return h;
        }
    }

    public record RulePiece(int x, int y, int w) implements Piece {
        @Override
        public int height() {
            return 1;
        }
    }

    public record Laid(List<Piece> pieces, int height) {
        public Laid {
            pieces = List.copyOf(pieces);
        }
    }

    private RichLayout() {
    }

    /**
     * @param maxImageH tallest a picture may be drawn; a taller one is scaled down, keeping its shape
     */
    public static Laid layout(List<RichText.Block> blocks, int width, Measure measure, ImageSizer sizer,
                              int maxImageH) {
        int column = Math.max(16, width);
        List<Piece> out = new ArrayList<>();
        int y = 0;
        for (RichText.Block block : blocks) {
            switch (block) {
                case RichText.Gap ignored -> y += LINE;
                case RichText.Rule ignored -> {
                    y += 2;
                    out.add(new RulePiece(0, y, column));
                    y += 4;
                }
                case RichText.Image image -> y = image(out, image, y, column, measure, sizer, maxImageH);
                case RichText.Paragraph paragraph -> y = paragraph(out, paragraph, y, column, measure);
            }
        }
        return new Laid(out, y);
    }

    private static int image(List<Piece> out, RichText.Image image, int y, int column, Measure measure,
                             ImageSizer sizer, int maxImageH) {
        int[] size = sizer == null ? null : sizer.size(image.texture());
        int w;
        int h;
        boolean missing = size == null || size[0] <= 0 || size[1] <= 0;
        if (missing) {
            w = Math.min(column, 96);
            h = 28;
        } else {
            float fit = Math.min(column / (float) size[0], Math.max(8, maxImageH) / (float) size[1]);
            if (fit >= 1f) {
                // Small pictures grow in whole steps so pixel art stays crisp.
                fit = Math.min(MAX_PIXEL_SCALE, (float) Math.floor(fit));
            }
            w = Math.max(1, Math.round(size[0] * fit));
            h = Math.max(1, Math.round(size[1] * fit));
        }
        y += 2;
        out.add(new ImagePiece((column - w) / 2, y, w, h, image.texture(), missing ? 0 : size[0],
                missing ? 0 : size[1], missing));
        y += h + 2;
        if (!image.caption().isEmpty()) {
            for (String row : wrapPlain(image.caption(), column, CAPTION_SIZE, measure)) {
                int rowW = Math.round(measure.width(row, false) * CAPTION_SIZE / (float) RichText.BASE_SIZE);
                out.add(new TextPiece(Math.max(0, (column - rowW) / 2), y, LINE, row, 0, CAPTION_SIZE, false, true,
                        false, true));
                y += LINE;
            }
        }
        return y + 2;
    }

    private static List<String> wrapPlain(String text, int column, int size, Measure measure) {
        List<String> rows = new ArrayList<>();
        StringBuilder row = new StringBuilder();
        for (String word : text.split(" +")) {
            String next = row.isEmpty() ? word : row + " " + word;
            if (!row.isEmpty() && measure.width(next, false) * size / (float) RichText.BASE_SIZE > column) {
                rows.add(row.toString());
                row = new StringBuilder(word);
            } else {
                row = new StringBuilder(next);
            }
        }
        if (!row.isEmpty()) {
            rows.add(row.toString());
        }
        return rows;
    }

    /** One word, icon or fragment waiting to be placed on a line. */
    private record Token(RichText.Inline inline, String text, int width, int size, boolean leadingSpace) {
    }

    private static int paragraph(List<Piece> out, RichText.Paragraph paragraph, int y, int column, Measure measure) {
        float headingScale = switch (paragraph.heading()) {
            case TITLE -> 1.5f;
            case SUBTITLE -> 1.2f;
            case NONE -> 1f;
        };
        boolean heading = paragraph.heading() != RichText.Heading.NONE;
        int indent = paragraph.bullet() ? BULLET_INDENT : 0;
        int avail = Math.max(8, column - indent);
        if (heading && y > 0) {
            y += 2;
        }
        List<Token> tokens = tokens(paragraph, headingScale, measure);
        List<List<Token>> lines = new ArrayList<>();
        List<Token> row = new ArrayList<>();
        int used = 0;
        for (Token token : tokens) {
            int width = token.width();
            int lead = row.isEmpty() || !token.leadingSpace() ? 0 : spaceWidth(token, measure);
            if (!row.isEmpty() && used + lead + width > avail) {
                lines.add(row);
                row = new ArrayList<>();
                used = 0;
                lead = 0;
            }
            if (row.isEmpty() && width > avail && token.inline() instanceof RichText.Text) {
                // A word wider than the column: break it by characters rather than overflow.
                for (Token piece : breakWord(token, avail, measure)) {
                    if (!row.isEmpty() && used + piece.width() > avail) {
                        lines.add(row);
                        row = new ArrayList<>();
                        used = 0;
                    }
                    row.add(piece);
                    used += piece.width();
                }
                continue;
            }
            row.add(new Token(token.inline(), token.text(), token.width(), token.size(),
                    token.leadingSpace() && !row.isEmpty()));
            used += lead + width;
        }
        if (!row.isEmpty() || lines.isEmpty()) {
            lines.add(row);
        }
        boolean first = true;
        for (List<Token> line : lines) {
            int rowH = LINE;
            for (Token token : line) {
                rowH = Math.max(rowH, token.inline() instanceof RichText.Text ? token.size() + 1 : ICON);
            }
            if (paragraph.bullet() && first) {
                out.add(new TextPiece(0, y, rowH, "•", 0, RichText.BASE_SIZE, false, false, false, false));
            }
            int x = indent;
            for (Token token : line) {
                if (token.leadingSpace()) {
                    x += spaceWidth(token, measure);
                }
                x = place(out, token, x, y, rowH, heading);
            }
            y += rowH;
            first = false;
        }
        return y;
    }

    private static int spaceWidth(Token token, Measure measure) {
        // The gap before an icon is an ordinary space; only text scales it with its own size.
        int size = token.inline() instanceof RichText.Text ? token.size() : RichText.BASE_SIZE;
        return Math.max(1, Math.round(measure.width(" ", false) * size / (float) RichText.BASE_SIZE));
    }

    private static int place(List<Piece> out, Token token, int x, int y, int rowH, boolean heading) {
        switch (token.inline()) {
            case RichText.Text text -> {
                int size = token.size();
                out.add(new TextPiece(x, y + (rowH - size) / 2, size + 1, token.text(), text.color(), size,
                        text.bold() || heading, text.italic(), text.underline(), false));
            }
            case RichText.Item item -> {
                out.add(new ItemPiece(x, y, item.id()));
                if (item.count() > 1) {
                    out.add(new TextPiece(x + ICON + 1, y + 1, LINE, "x" + item.count(), 0, RichText.BASE_SIZE,
                            false, false, false, true));
                }
            }
            case RichText.Glyph glyph -> out.add(new GlyphPiece(x, y, glyph.id(), glyph.color()));
        }
        return x + token.width();
    }

    private static List<Token> tokens(RichText.Paragraph paragraph, float headingScale, Measure measure) {
        List<Token> tokens = new ArrayList<>();
        boolean lead = false;
        for (RichText.Inline inline : paragraph.inlines()) {
            switch (inline) {
                case RichText.Text text -> {
                    int size = Math.max(RichText.MIN_SIZE,
                            Math.min(RichText.MAX_SIZE, Math.round(text.size() * headingScale)));
                    boolean bold = text.bold() || headingScale != 1f;
                    String s = text.text();
                    int at = 0;
                    while (at < s.length()) {
                        int space = s.indexOf(' ', at);
                        if (space == at) {
                            lead = true;
                            at++;
                            continue;
                        }
                        int stop = space < 0 ? s.length() : space;
                        String word = s.substring(at, stop);
                        int width = Math.round(measure.width(word, bold) * size / (float) RichText.BASE_SIZE);
                        tokens.add(new Token(new RichText.Text(word, text.color(), size, bold, text.italic(),
                                text.underline()), word, width, size, lead));
                        lead = false;
                        at = stop;
                    }
                    if (s.endsWith(" ")) {
                        lead = true;
                    }
                }
                case RichText.Item item -> {
                    int extra = item.count() > 1
                            ? 1 + Math.round(measure.width("x" + item.count(), false)) : 0;
                    tokens.add(new Token(item, "", ICON + extra, ICON, lead));
                    lead = false;
                }
                case RichText.Glyph glyph -> {
                    tokens.add(new Token(glyph, "", ICON, ICON, lead));
                    lead = false;
                }
            }
        }
        return tokens;
    }

    private static List<Token> breakWord(Token token, int avail, Measure measure) {
        RichText.Text text = (RichText.Text) token.inline();
        List<Token> parts = new ArrayList<>();
        StringBuilder chunk = new StringBuilder();
        for (char c : token.text().toCharArray()) {
            String next = chunk.toString() + c;
            int width = Math.round(measure.width(next, text.bold()) * token.size() / (float) RichText.BASE_SIZE);
            if (width > avail && !chunk.isEmpty()) {
                parts.add(part(text, chunk.toString(), token.size(), measure));
                chunk = new StringBuilder();
            }
            chunk.append(c);
        }
        if (!chunk.isEmpty()) {
            parts.add(part(text, chunk.toString(), token.size(), measure));
        }
        return parts;
    }

    private static Token part(RichText.Text text, String s, int size, Measure measure) {
        int width = Math.round(measure.width(s, text.bold()) * size / (float) RichText.BASE_SIZE);
        return new Token(new RichText.Text(s, text.color(), size, text.bold(), text.italic(), text.underline()), s,
                width, size, false);
    }

    // ---- scrolling -----------------------------------------------------------------------------------------

    public static int maxScroll(int contentH, int viewH) {
        return Math.max(0, contentH - Math.max(0, viewH));
    }

    public static int clampScroll(int scroll, int contentH, int viewH) {
        return Math.max(0, Math.min(scroll, maxScroll(contentH, viewH)));
    }

    /** {@code {offset, length}} of the scrollbar thumb inside a track {@code trackH} tall. */
    public static int[] thumb(int trackH, int viewH, int contentH, int scroll) {
        if (contentH <= viewH || contentH <= 0) {
            return new int[]{0, trackH};
        }
        int len = Math.max(12, Math.min(trackH, Math.round(trackH * (viewH / (float) contentH))));
        int room = trackH - len;
        int max = maxScroll(contentH, viewH);
        int offset = max == 0 ? 0 : Math.round(room * (clampScroll(scroll, contentH, viewH) / (float) max));
        return new int[]{offset, len};
    }

    /** The scroll position a thumb dragged to {@code pointerInTrack} (pixels from the track top) stands for. */
    public static int scrollForThumb(int pointerInTrack, int trackH, int viewH, int contentH) {
        int[] thumb = thumb(trackH, viewH, contentH, 0);
        int room = Math.max(1, trackH - thumb[1]);
        float t = Math.max(0f, Math.min(1f, (pointerInTrack - thumb[1] / 2f) / room));
        return Math.round(t * maxScroll(contentH, viewH));
    }
}
