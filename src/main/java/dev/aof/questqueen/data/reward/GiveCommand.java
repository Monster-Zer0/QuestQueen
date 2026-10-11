package dev.aof.questqueen.data.reward;

import net.minecraft.resources.ResourceLocation;

import java.util.Locale;
import java.util.Optional;

/**
 * Reads a {@code give} command as text: {@code give <target> <item>[components] [count]}. Pure (no registries), so the
 * book can name and count what a command reward hands out, and the client can parse the item spec into a real stack.
 */
public record GiveCommand(String itemSpec, ResourceLocation itemId, int count) {
    public static Optional<GiveCommand> parse(String command) {
        if (command == null) {
            return Optional.empty();
        }
        String text = command.trim();
        if (text.startsWith("/")) {
            text = text.substring(1);
        }
        int[] at = {0};
        String verb = token(text, at);
        if (!"give".equals(verb.toLowerCase(Locale.ROOT))) {
            return Optional.empty();
        }
        String target = token(text, at);
        String spec = token(text, at);
        if (target.isEmpty() || spec.isEmpty()) {
            return Optional.empty();
        }
        int bracket = spec.indexOf('[');
        String id = bracket < 0 ? spec : spec.substring(0, bracket);
        ResourceLocation itemId = ResourceLocation.tryParse(id);
        if (itemId == null || id.isEmpty()) {
            return Optional.empty();
        }
        String countText = token(text, at);
        int count = 1;
        if (!countText.isEmpty()) {
            try {
                count = Integer.parseInt(countText);
            } catch (NumberFormatException notACount) {
                return Optional.empty();
            }
            if (count < 1) {
                return Optional.empty();
            }
        }
        if (!token(text, at).isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(new GiveCommand(spec, itemId, count));
    }

    /** "32x 9mm" style text for logs and tests, from the item id alone. */
    public String plainLabel() {
        String name = itemId.getPath().replace('_', ' ');
        return count > 1 ? count + "x " + name : name;
    }

    /**
     * The next space-separated token, where spaces inside {@code [...]}, {@code {...}} or quotes do not split it, so
     * {@code @s[name="A B"]} and {@code item[custom_name='"A B"']} stay whole.
     */
    private static String token(String text, int[] at) {
        int i = at[0];
        while (i < text.length() && Character.isWhitespace(text.charAt(i))) {
            i++;
        }
        int start = i;
        int depth = 0;
        char quote = 0;
        while (i < text.length()) {
            char c = text.charAt(i);
            if (quote != 0) {
                if (c == '\\' && i + 1 < text.length()) {
                    i += 2;
                    continue;
                }
                if (c == quote) {
                    quote = 0;
                }
            } else if (c == '"' || c == '\'') {
                quote = c;
            } else if (c == '[' || c == '{') {
                depth++;
            } else if (c == ']' || c == '}') {
                depth = Math.max(0, depth - 1);
            } else if (Character.isWhitespace(c) && depth == 0) {
                break;
            }
            i++;
        }
        at[0] = i;
        return text.substring(start, i);
    }
}
