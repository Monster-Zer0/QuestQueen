package dev.aof.questqueen.client;

import dev.aof.questqueen.data.QuestGlyphIds;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Markup for the book header title. A string with no tags is drawn as before.
 * Letters can be uppercased; the tags themselves are not.
 */
public final class TitleMarkup {
    public record Run(String text, int color, boolean glow, boolean pulse) {
    }

    public record Parsed(String glyph, List<Run> runs) {
        public Parsed {
            runs = runs == null ? List.of() : List.copyOf(runs);
        }
    }

    private TitleMarkup() {
    }

    public static boolean pulsing(String raw) {
        return raw != null && raw.contains("{pulse}");
    }

    public static boolean glowing(String raw) {
        return raw != null && raw.contains("{glow}");
    }

    public static String glyphOf(String raw) {
        return parse(raw, 0xFFFFFFFF, false).glyph();
    }

    /** Letters the player sees, tags removed. */
    public static String visible(String raw, boolean uppercase) {
        StringBuilder out = new StringBuilder();
        for (Run run : parse(raw, 0xFFFFFFFF, uppercase).runs()) {
            out.append(run.text());
        }
        return out.toString();
    }

    public static Parsed parse(String raw, int defaultColor, boolean uppercase) {
        String text = raw == null ? "" : raw;
        String glyph = null;
        int glyphAt = indexOfIgnoreCase(text, "{glyph:");
        if (glyphAt >= 0) {
            int end = text.indexOf('}', glyphAt + 7);
            if (end > glyphAt) {
                String id = text.substring(glyphAt + 7, end).trim().toLowerCase(Locale.ROOT);
                if (QuestGlyphIds.isKnown(id)) {
                    glyph = id;
                }
                text = text.substring(0, glyphAt) + text.substring(end + 1);
            }
        }
        text = text.replaceAll("(?i)\\{glyph:[a-z0-9_]+\\}", "");
        List<Run> runs = new ArrayList<>();
        parseRuns(text, 0, text.length(), defaultColor, false, false, uppercase, runs);
        return new Parsed(glyph, runs);
    }

    public static String toggleWrap(String title, String open, String close) {
        String raw = title == null ? "" : title;
        int start = indexOfIgnoreCase(raw, open);
        int end = indexOfIgnoreCase(raw, close);
        if (start >= 0 && end > start) {
            return raw.substring(0, start) + raw.substring(start + open.length(), end) + raw.substring(end + close.length());
        }
        return open + raw + close;
    }

    /** Cycles the single glyph, then removes it after the last id. */
    public static String cycleGlyph(String title) {
        String raw = title == null ? "" : title;
        String current = glyphOf(raw);
        List<String> ids = QuestGlyphIds.ALL;
        if (current == null) {
            return "{glyph:" + ids.getFirst() + "}" + raw;
        }
        int idx = ids.indexOf(current);
        String without = raw.replaceAll("(?i)\\{glyph:" + current + "\\}", "");
        if (idx < 0 || idx >= ids.size() - 1) {
            return without;
        }
        return "{glyph:" + ids.get(idx + 1) + "}" + without;
    }

    private static void parseRuns(String text, int start, int end, int color, boolean glow, boolean pulse,
                                  boolean uppercase, List<Run> out) {
        StringBuilder buf = new StringBuilder();
        int i = start;
        while (i < end) {
            if (region(text, i, end, "{glow}", "{/glow}")) {
                flush(buf, color, glow, pulse, uppercase, out);
                int close = indexOfIgnoreCase(text, "{/glow}", i + 6);
                parseRuns(text, i + 6, close, color, true, pulse, uppercase, out);
                i = close + 7;
                continue;
            }
            if (region(text, i, end, "{pulse}", "{/pulse}")) {
                flush(buf, color, glow, pulse, uppercase, out);
                int close = indexOfIgnoreCase(text, "{/pulse}", i + 7);
                parseRuns(text, i + 7, close, color, glow, true, uppercase, out);
                i = close + 8;
                continue;
            }
            if (matchAt(text, i, "{#") && i + 9 < end && text.charAt(i + 8) == '}') {
                int close = indexOfIgnoreCase(text, "{/#}", i + 9);
                if (close > i) {
                    flush(buf, color, glow, pulse, uppercase, out);
                    int rgb = parseHex(text.substring(i + 2, i + 8));
                    int argb = (color & 0xFF000000) | rgb;
                    if ((argb >>> 24) == 0) {
                        argb |= 0xFF000000;
                    }
                    parseRuns(text, i + 9, close, argb, glow, pulse, uppercase, out);
                    i = close + 4;
                    continue;
                }
            }
            buf.append(text.charAt(i));
            i++;
        }
        flush(buf, color, glow, pulse, uppercase, out);
    }

    private static boolean region(String text, int i, int end, String open, String close) {
        return matchAt(text, i, open) && indexOfIgnoreCase(text, close, i + open.length()) >= 0
                && indexOfIgnoreCase(text, close, i + open.length()) <= end;
    }

    private static void flush(StringBuilder buf, int color, boolean glow, boolean pulse, boolean uppercase, List<Run> out) {
        if (buf.isEmpty()) {
            return;
        }
        String text = uppercase ? buf.toString().toUpperCase(Locale.ROOT) : buf.toString();
        buf.setLength(0);
        if (!text.isEmpty()) {
            out.add(new Run(text, color, glow, pulse));
        }
    }

    private static boolean matchAt(String text, int i, String token) {
        return i >= 0 && i + token.length() <= text.length()
                && text.regionMatches(true, i, token, 0, token.length());
    }

    private static int indexOfIgnoreCase(String text, String token) {
        return indexOfIgnoreCase(text, token, 0);
    }

    private static int indexOfIgnoreCase(String text, String token, int from) {
        if (from < 0) {
            from = 0;
        }
        int last = text.length() - token.length();
        for (int i = from; i <= last; i++) {
            if (text.regionMatches(true, i, token, 0, token.length())) {
                return i;
            }
        }
        return -1;
    }

    private static int parseHex(String hex) {
        try {
            return Integer.parseInt(hex, 16) & 0xFFFFFF;
        } catch (NumberFormatException ignored) {
            return 0xFFFFFF;
        }
    }
}
