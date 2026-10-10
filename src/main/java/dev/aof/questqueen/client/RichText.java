package dev.aof.questqueen.client;

import dev.aof.questqueen.data.QuestGlyphIds;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Markup for quest descriptions. It is the chapter-intro dialect ({@link IntroMarkup}) plus lists, rules, inline
 * icons and pictures, so an author learns one syntax. A description with no markup reads exactly as plain text.
 *
 * <pre>
 * # Heading            ## Smaller heading
 * **bold**  *italic*  __underline__
 * {#RRGGBB}coloured{/#}      {size:12}bigger{/size}
 * - a bullet                  ---   (a rule)
 * {item:minecraft:diamond}    {item:minecraft:iron_ingot x3}    {glyph:sword}
 * ![caption](mypack:textures/quest/castle.png)      (a picture, alone on its line)
 * \*  a backslash keeps the next markup character literal
 * </pre>
 *
 * <p>Parsing never throws and never drops text: anything that is not valid markup (an unclosed tag, an unknown
 * glyph, a bad resource location) stays on screen as the literal characters the author typed.
 */
public final class RichText {
    public static final int BASE_SIZE = 9;
    public static final int MIN_SIZE = 8;
    public static final int MAX_SIZE = 24;
    /** An item icon's count text is capped here so a typo cannot stretch a line. */
    public static final int MAX_ITEM_COUNT = 9999;

    private static final String ESCAPABLE = "\\*_{}#-![]()";

    public enum Heading { NONE, TITLE, SUBTITLE }

    /** Something that sits on a text line. */
    public sealed interface Inline permits Text, Item, Glyph {
    }

    /** {@code color} 0 means "the caller's default colour". */
    public record Text(String text, int color, int size, boolean bold, boolean italic, boolean underline)
            implements Inline {
        public Text {
            text = text == null ? "" : text;
            size = Math.max(MIN_SIZE, Math.min(MAX_SIZE, size));
        }
    }

    public record Item(ResourceLocation id, int count) implements Inline {
    }

    public record Glyph(String id, int color) implements Inline {
    }

    /** A vertical piece of the description. */
    public sealed interface Block permits Paragraph, Image, Rule, Gap {
    }

    public record Paragraph(Heading heading, boolean bullet, List<Inline> inlines) implements Block {
        public Paragraph {
            heading = heading == null ? Heading.NONE : heading;
            inlines = inlines == null ? List.of() : List.copyOf(inlines);
        }
    }

    /** {@code texture} is a full resource location, e.g. {@code mypack:textures/quest/castle.png}. */
    public record Image(ResourceLocation texture, String caption) implements Block {
        public Image {
            caption = caption == null ? "" : caption;
        }
    }

    public record Rule() implements Block {
    }

    /** A blank line in the source. */
    public record Gap() implements Block {
    }

    private RichText() {
    }

    /** A picture path the book can load: a resource location under {@code textures/}, ending in {@code .png}. */
    public static boolean validTexture(String raw) {
        ResourceLocation id = raw == null ? null : ResourceLocation.tryParse(raw.trim());
        return id != null && id.getPath().startsWith("textures/") && id.getPath().endsWith(".png");
    }

    public static List<Block> parse(String source) {
        List<Block> blocks = new ArrayList<>();
        if (source == null || source.isBlank()) {
            return blocks;
        }
        String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        boolean pendingGap = false;
        for (String raw : lines) {
            if (raw.isBlank()) {
                pendingGap = true;
                continue;
            }
            if (pendingGap && !blocks.isEmpty()) {
                blocks.add(new Gap());
            }
            pendingGap = false;
            blocks.add(parseLine(raw));
        }
        return blocks;
    }

    private static Block parseLine(String raw) {
        String line = raw.strip();
        if (line.length() >= 3 && line.chars().allMatch(c -> c == '-')) {
            return new Rule();
        }
        Image image = parseImage(line);
        if (image != null) {
            return image;
        }
        Heading heading = Heading.NONE;
        boolean bullet = false;
        String rest = raw.stripLeading();
        if (rest.startsWith("## ")) {
            heading = Heading.SUBTITLE;
            rest = rest.substring(3);
        } else if (rest.startsWith("# ")) {
            heading = Heading.TITLE;
            rest = rest.substring(2);
        } else if (rest.startsWith("- ")) {
            bullet = true;
            rest = rest.substring(2);
        } else if (rest.startsWith("• ")) {
            bullet = true;
            rest = rest.substring(2);
        } else {
            rest = raw;
        }
        List<Inline> out = new ArrayList<>();
        parseInline(rest, 0, rest.length(), new State(0, BASE_SIZE, false, false, false), out);
        return new Paragraph(heading, bullet, out);
    }

    /** {@code ![caption](path)} filling the whole line, with a loadable texture path. */
    private static Image parseImage(String line) {
        if (!line.startsWith("![")) {
            return null;
        }
        int captionEnd = line.indexOf("](");
        if (captionEnd < 0 || !line.endsWith(")")) {
            return null;
        }
        String path = line.substring(captionEnd + 2, line.length() - 1).trim();
        if (!validTexture(path)) {
            return null;
        }
        return new Image(ResourceLocation.parse(path), line.substring(2, captionEnd).strip());
    }

    private static void parseInline(String s, int start, int end, State st, List<Inline> out) {
        StringBuilder buf = new StringBuilder();
        int i = start;
        while (i < end) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < end && ESCAPABLE.indexOf(s.charAt(i + 1)) >= 0) {
                buf.append(s.charAt(i + 1));
                i += 2;
                continue;
            }
            if (c == '{') {
                int next = braceTag(s, i, end, st, buf, out);
                if (next > i) {
                    i = next;
                    continue;
                }
            }
            if (c == '*' && s.startsWith("**", i)) {
                int close = indexOf(s, "**", i + 2, end);
                if (close > i + 2) {
                    flush(buf, st, out);
                    parseInline(s, i + 2, close, st.bold(true), out);
                    i = close + 2;
                    continue;
                }
            }
            if (c == '_' && s.startsWith("__", i)) {
                int close = indexOf(s, "__", i + 2, end);
                if (close > i + 2) {
                    flush(buf, st, out);
                    parseInline(s, i + 2, close, st.underline(true), out);
                    i = close + 2;
                    continue;
                }
            }
            if (c == '*' && !s.startsWith("**", i) && i + 1 < end && !Character.isWhitespace(s.charAt(i + 1))) {
                int close = singleStar(s, i + 1, end);
                if (close > i + 1) {
                    flush(buf, st, out);
                    parseInline(s, i + 1, close, st.italic(true), out);
                    i = close + 1;
                    continue;
                }
            }
            buf.append(c);
            i++;
        }
        flush(buf, st, out);
    }

    /** A {@code {...}} tag at {@code i}; returns the index after it, or {@code i} when it is not valid markup. */
    private static int braceTag(String s, int i, int end, State st, StringBuilder buf, List<Inline> out) {
        if (s.startsWith("{#", i) && i + 9 <= end && s.charAt(i + 8) == '}' && isHex(s, i + 2)) {
            int close = indexOf(s, "{/#}", i + 9, end);
            if (close >= 0) {
                flush(buf, st, out);
                int color = 0xFF000000 | Integer.parseInt(s.substring(i + 2, i + 8), 16);
                parseInline(s, i + 9, close, st.color(color), out);
                return close + 4;
            }
            return i;
        }
        if (s.startsWith("{size:", i)) {
            int spec = s.indexOf('}', i + 6);
            int close = spec < 0 ? -1 : indexOf(s, "{/size}", spec + 1, end);
            if (spec > 0 && spec < end && close >= 0) {
                Integer size = parseInt(s.substring(i + 6, spec));
                if (size != null) {
                    flush(buf, st, out);
                    parseInline(s, spec + 1, close, st.size(Math.max(MIN_SIZE, Math.min(MAX_SIZE, size))), out);
                    return close + 7;
                }
            }
            return i;
        }
        if (s.startsWith("{item:", i)) {
            int close = s.indexOf('}', i + 6);
            if (close > 0 && close < end) {
                Item item = parseItem(s.substring(i + 6, close));
                if (item != null) {
                    flush(buf, st, out);
                    out.add(item);
                    return close + 1;
                }
            }
            return i;
        }
        if (s.startsWith("{glyph:", i)) {
            int close = s.indexOf('}', i + 7);
            if (close > 0 && close < end) {
                String id = s.substring(i + 7, close).trim().toLowerCase(Locale.ROOT);
                if (QuestGlyphIds.isKnown(id)) {
                    flush(buf, st, out);
                    out.add(new Glyph(id, st.color));
                    return close + 1;
                }
            }
        }
        return i;
    }

    /** {@code minecraft:diamond} or {@code minecraft:iron_ingot x3}. */
    private static Item parseItem(String spec) {
        String[] parts = spec.trim().split("\\s+");
        if (parts.length == 0 || parts.length > 2) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(parts[0]);
        if (id == null || id.getPath().isEmpty()) {
            return null;
        }
        int count = 1;
        if (parts.length == 2) {
            String n = parts[1].startsWith("x") || parts[1].startsWith("X") ? parts[1].substring(1) : "";
            if (!n.matches("\\d{1,9}")) {
                return null;
            }
            count = Math.max(1, Math.min(MAX_ITEM_COUNT, Integer.parseInt(n)));
        }
        return new Item(id, count);
    }

    /** A whole number of at most nine digits, optionally signed: the same rule the editor's preview uses. */
    private static Integer parseInt(String raw) {
        String n = raw.trim();
        return n.matches("[+-]?\\d{1,9}") ? Integer.valueOf(Integer.parseInt(n)) : null;
    }

    private static boolean isHex(String s, int at) {
        for (int n = 0; n < 6; n++) {
            if (Character.digit(s.charAt(at + n), 16) < 0) {
                return false;
            }
        }
        return true;
    }

    private static int indexOf(String s, String token, int from, int end) {
        int at = s.indexOf(token, from);
        return at >= 0 && at + token.length() <= end ? at : -1;
    }

    /** Next lone {@code *} that follows a non-space, so {@code 5 * 3 * 2} is not italic. */
    private static int singleStar(String s, int from, int end) {
        for (int i = from; i < end; i++) {
            if (s.charAt(i) == '*' && !s.startsWith("**", i) && s.charAt(i - 1) != '*'
                    && !Character.isWhitespace(s.charAt(i - 1))) {
                return i;
            }
        }
        return -1;
    }

    private static void flush(StringBuilder buf, State st, List<Inline> out) {
        if (!buf.isEmpty()) {
            out.add(new Text(buf.toString(), st.color, st.size, st.bold, st.italic, st.underline));
            buf.setLength(0);
        }
    }

    /** The words a player can read, markup removed: used for search. Pictures contribute their caption. */
    public static String plain(String source) {
        StringBuilder out = new StringBuilder();
        for (Block block : parse(source)) {
            String line = switch (block) {
                case Paragraph p -> {
                    StringBuilder text = new StringBuilder();
                    for (Inline inline : p.inlines()) {
                        if (inline instanceof Text t) {
                            text.append(t.text());
                        }
                    }
                    yield text.toString();
                }
                case Image image -> image.caption();
                case Rule ignored -> "";
                case Gap ignored -> "";
            };
            if (!line.isBlank()) {
                if (!out.isEmpty()) {
                    out.append('\n');
                }
                out.append(line);
            }
        }
        return out.toString();
    }

    private record State(int color, int size, boolean bold, boolean italic, boolean underline) {
        State color(int next) {
            return new State(next, size, bold, italic, underline);
        }

        State size(int next) {
            return new State(color, next, bold, italic, underline);
        }

        State bold(boolean next) {
            return new State(color, size, next, italic, underline);
        }

        State italic(boolean next) {
            return new State(color, size, bold, next, underline);
        }

        State underline(boolean next) {
            return new State(color, size, bold, italic, next);
        }
    }
}
