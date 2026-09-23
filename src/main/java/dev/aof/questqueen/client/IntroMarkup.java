package dev.aof.questqueen.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.List;

/**
 * Small markup for chapter intro bodies. No MiniMessage / no § codes.
 *
 * <pre>
 * # title
 * ## subtitle
 * **bold** *italic*
 * {#RRGGBB}text{/#}
 * {size:N}text{/size}
 * blank line → paragraph gap
 * </pre>
 */
public final class IntroMarkup {
    public static final int MIN_SIZE = 8;
    public static final int MAX_SIZE = 24;
    private static final int BASE_SIZE = 9;

    public enum Heading {
        NONE, TITLE, SUBTITLE
    }

    public record Span(String text, int color, int size, boolean bold, boolean italic) {
        public Span {
            text = text == null ? "" : text;
            size = Mth.clamp(size, MIN_SIZE, MAX_SIZE);
        }
    }

    public record Block(Heading heading, List<Span> spans, boolean blank) {
        public Block {
            heading = heading == null ? Heading.NONE : heading;
            spans = spans == null ? List.of() : List.copyOf(spans);
        }

        public static Block gap() {
            return new Block(Heading.NONE, List.of(), true);
        }
    }

    private IntroMarkup() {
    }

    public static String resolveBody(String introBody, String title, String firstTileDescription) {
        if (introBody != null && !introBody.isBlank()) {
            return introBody;
        }
        if (title != null && !title.isBlank()) {
            return "# " + title.trim();
        }
        if (firstTileDescription != null && !firstTileDescription.isBlank()) {
            return firstTileDescription;
        }
        return "";
    }

    public static List<Block> parse(String body, int defaultColor) {
        List<Block> blocks = new ArrayList<>();
        if (body == null || body.isEmpty()) {
            return blocks;
        }
        String[] lines = body.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        boolean pendingGap = false;
        for (String raw : lines) {
            if (raw.isBlank()) {
                pendingGap = true;
                continue;
            }
            if (pendingGap && !blocks.isEmpty()) {
                blocks.add(Block.gap());
            }
            pendingGap = false;
            Heading heading = Heading.NONE;
            String rest = raw;
            if (rest.startsWith("##")) {
                heading = Heading.SUBTITLE;
                rest = rest.substring(2);
            } else if (rest.startsWith("#")) {
                heading = Heading.TITLE;
                rest = rest.substring(1);
            }
            if ((heading == Heading.TITLE || heading == Heading.SUBTITLE) && rest.startsWith(" ")) {
                rest = rest.substring(1);
            }
            StyleState base = new StyleState(defaultColor, BASE_SIZE, false, false);
            blocks.add(new Block(heading, parseInline(rest, base), false));
        }
        return blocks;
    }

    public static int contentHeight(Font font, String body, int width, int defaultColor) {
        int y = 0;
        for (Block block : parse(body, defaultColor)) {
            if (block.blank()) {
                y += 8;
                continue;
            }
            for (Line line : wrap(font, block, width)) {
                y += lineHeight(line);
            }
            y += 3;
        }
        return y;
    }

    public static int draw(GuiGraphics graphics, Font font, String body, int x, int y, int width, int defaultColor, int scroll) {
        int cursor = y - Math.max(0, scroll);
        int bottom = 0;
        for (Block block : parse(body, defaultColor)) {
            if (block.blank()) {
                cursor += 8;
                bottom = cursor;
                continue;
            }
            for (Line line : wrap(font, block, width)) {
                int drawY = cursor;
                int xCursor = x;
                int rowH = lineHeight(line);
                for (Span span : line.spans()) {
                    if (span.text().isEmpty()) {
                        continue;
                    }
                    float scale = span.size() / (float) BASE_SIZE;
                    Component text = Component.literal(span.text()).withStyle(styleFor(span));
                    var pose = graphics.pose();
                    pose.pushPose();
                    pose.translate(xCursor, drawY, 0);
                    pose.scale(scale, scale, 1f);
                    graphics.drawString(font, text, 0, 0, span.color(), false);
                    pose.popPose();
                    xCursor += Math.round(font.width(text) * scale);
                }
                cursor += rowH;
            }
            cursor += 3;
            bottom = cursor;
        }
        return Math.max(0, bottom - y + Math.max(0, scroll));
    }

    private static Style styleFor(Span span) {
        Style style = Style.EMPTY.withBold(span.bold()).withItalic(span.italic());
        int rgb = span.color() & 0xFFFFFF;
        return style.withColor(TextColor.fromRgb(rgb));
    }

    private static float headingScale(Heading heading) {
        return switch (heading) {
            case TITLE -> 1.5f;
            case SUBTITLE -> 1.2f;
            case NONE -> 1f;
        };
    }

    private static int lineHeight(Line line) {
        int max = BASE_SIZE;
        for (Span span : line.spans()) {
            max = Math.max(max, span.size());
        }
        return max + 2;
    }

    private static List<Span> parseInline(String text, StyleState base) {
        List<Span> spans = new ArrayList<>();
        parseInline(text, 0, text.length(), base, spans);
        return spans;
    }

    private static void parseInline(String text, int start, int end, StyleState style, List<Span> out) {
        int i = start;
        StringBuilder buf = new StringBuilder();
        while (i < end) {
            if (match(text, i, end, "{#") && hexColorAt(text, i + 2) && findClose(text, i, end, "{/#}") > i) {
                flush(buf, style, out);
                int close = findClose(text, i, end, "{/#}");
                int color = parseHex(text.substring(i + 2, i + 8));
                int innerStart = i + 8;
                if (innerStart < close && text.charAt(innerStart) == '}') {
                    innerStart++;
                }
                parseInline(text, innerStart, close, style.withColor(color), out);
                i = close + 4;
                continue;
            }
            if (match(text, i, end, "{size:")) {
                int specEnd = text.indexOf('}', i + 6);
                int close = findClose(text, i, end, "{/size}");
                if (specEnd > i && close > specEnd) {
                    flush(buf, style, out);
                    int size = parseSize(text.substring(i + 6, specEnd));
                    parseInline(text, specEnd + 1, close, style.withSize(size), out);
                    i = close + 7;
                    continue;
                }
            }
            if (match(text, i, end, "**")) {
                int close = indexOfUnescaped(text, i + 2, end, "**");
                if (close > i) {
                    flush(buf, style, out);
                    parseInline(text, i + 2, close, style.withBold(true), out);
                    i = close + 2;
                    continue;
                }
            }
            if (text.charAt(i) == '*' && !match(text, i, end, "**")) {
                int close = indexOfSingleStar(text, i + 1, end);
                if (close > i) {
                    flush(buf, style, out);
                    parseInline(text, i + 1, close, style.withItalic(true), out);
                    i = close + 1;
                    continue;
                }
            }
            buf.append(text.charAt(i));
            i++;
        }
        flush(buf, style, out);
    }

    private static void flush(StringBuilder buf, StyleState style, List<Span> out) {
        if (buf.isEmpty()) {
            return;
        }
        out.add(new Span(buf.toString(), style.color, style.size, style.bold, style.italic));
        buf.setLength(0);
    }

    private static boolean match(String text, int i, int end, String token) {
        return i + token.length() <= end && text.startsWith(token, i);
    }

    private static boolean hexColorAt(String text, int i) {
        if (i + 6 > text.length()) {
            return false;
        }
        for (int n = 0; n < 6; n++) {
            if (Character.digit(text.charAt(i + n), 16) < 0) {
                return false;
            }
        }
        return i + 6 < text.length() && text.charAt(i + 6) == '}';
    }

    private static int parseHex(String hex) {
        return 0xFF000000 | Integer.parseInt(hex, 16);
    }

    private static int parseSize(String raw) {
        try {
            return Mth.clamp(Integer.parseInt(raw.trim()), MIN_SIZE, MAX_SIZE);
        } catch (NumberFormatException ignored) {
            return BASE_SIZE;
        }
    }

    private static int findClose(String text, int from, int end, String token) {
        int at = text.indexOf(token, from + 1);
        return at >= 0 && at < end ? at : -1;
    }

    private static int indexOfUnescaped(String text, int from, int end, String token) {
        int at = text.indexOf(token, from);
        return at >= 0 && at < end ? at : -1;
    }

    private static int indexOfSingleStar(String text, int from, int end) {
        for (int i = from; i < end; i++) {
            if (text.charAt(i) == '*' && !match(text, i, end, "**")) {
                return i;
            }
        }
        return -1;
    }

    private static List<Line> wrap(Font font, Block block, int width) {
        List<Line> lines = new ArrayList<>();
        List<Span> row = new ArrayList<>();
        int used = 0;
        int max = Math.max(8, width);
        for (Span span : applyHeadingSize(block)) {
            String rest = span.text();
            while (!rest.isEmpty()) {
                int breakAt = rest.indexOf(' ');
                String word = breakAt < 0 ? rest : rest.substring(0, breakAt);
                rest = breakAt < 0 ? "" : rest.substring(breakAt + 1);
                boolean leadSpace = !row.isEmpty();
                String piece = leadSpace ? " " + word : word;
                int wordW = Math.round(font.width(piece) * (span.size() / (float) BASE_SIZE));
                if (!row.isEmpty() && used + wordW > max) {
                    lines.add(new Line(block.heading(), List.copyOf(row)));
                    row.clear();
                    used = 0;
                    piece = word;
                    wordW = Math.round(font.width(piece) * (span.size() / (float) BASE_SIZE));
                }
                row.add(new Span(piece, span.color(), span.size(), span.bold(), span.italic()));
                used += wordW;
            }
        }
        if (!row.isEmpty() || lines.isEmpty()) {
            lines.add(new Line(block.heading(), List.copyOf(row)));
        }
        return lines;
    }

    private static List<Span> applyHeadingSize(Block block) {
        float scale = headingScale(block.heading());
        if (scale == 1f) {
            return block.spans();
        }
        List<Span> scaled = new ArrayList<>();
        for (Span span : block.spans()) {
            int size = Mth.clamp(Math.round(span.size() * scale), MIN_SIZE, MAX_SIZE);
            scaled.add(new Span(span.text(), span.color(), size, span.bold(), span.italic()));
        }
        return scaled;
    }

    private record Line(Heading heading, List<Span> spans) {
    }

    private static final class StyleState {
        private final int color;
        private final int size;
        private final boolean bold;
        private final boolean italic;

        private StyleState(int color, int size, boolean bold, boolean italic) {
            this.color = color;
            this.size = size;
            this.bold = bold;
            this.italic = italic;
        }

        private StyleState withColor(int next) {
            return new StyleState(next, size, bold, italic);
        }

        private StyleState withSize(int next) {
            return new StyleState(color, next, bold, italic);
        }

        private StyleState withBold(boolean next) {
            return new StyleState(color, size, next, italic);
        }

        private StyleState withItalic(boolean next) {
            return new StyleState(color, size, bold, next);
        }
    }
}
