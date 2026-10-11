package dev.aof.questqueen.client;

import java.util.Optional;

import dev.aof.questqueen.data.Chapter;
import dev.aof.questqueen.data.ChapterBackground;

/**
 * Semantic book chrome colors for one theme. Apply via {@link QuestColors#apply(BookPalette)}.
 */
public record BookPalette(
        int voidColor,
        int cell,
        int cellLine,
        int cellShadow,
        int sidebar,
        int sidebarEdge,
        int sidebarActive,
        int sidebarText,
        int sidebarHeader,
        int locked,
        int lockedText,
        int current,
        int neu,
        int edit,
        int completed,
        int failed,
        int closed,
        int card,
        int text,
        int muted,
        int portRed,
        int portDim,
        int reward,
        int path,
        int add,
        int modalPink,
        int lockedEdge,
        int gateAnd,
        int gateOr,
        int gateXor,
        int xorEdge,
        int gateNot,
        int claimTint,
        int claimEdge
) {
    /** Shared across every preset — STATUS authority. */
    public static final int AUTH_LOCKED_EDGE = 0xFF8C1A24;
    public static final int AUTH_FAILED = 0xFFE01018;
    public static final int AUTH_MODAL_PINK = 0xFFE88AB0;
    /** Wash laid over a done-but-unclaimed tile's face, and its frame: a warm gold that reads across the board. */
    public static final int DEFAULT_CLAIM_TINT = 0x33FFD54F;
    public static final int DEFAULT_CLAIM_EDGE = 0xFFFFD54F;

    public BookPalette withVoid(int argb) {
        return new BookPalette(
                argb, cell, cellLine, cellShadow, sidebar, sidebarEdge, sidebarActive, sidebarText, sidebarHeader,
                locked, lockedText, current, neu, edit, completed, failed, closed, card, text, muted,
                portRed, portDim, reward, path, add, modalPink, lockedEdge, gateAnd, gateOr, gateXor, xorEdge, gateNot,
                claimTint, claimEdge
        );
    }

    public BookPalette withClaim(int tint, int edge) {
        return new BookPalette(
                voidColor, cell, cellLine, cellShadow, sidebar, sidebarEdge, sidebarActive, sidebarText, sidebarHeader,
                locked, lockedText, current, neu, edit, completed, failed, closed, card, text, muted,
                portRed, portDim, reward, path, add, modalPink, lockedEdge, gateAnd, gateOr, gateXor, xorEdge, gateNot,
                tint, edge
        );
    }

    /**
     * A config colour as hex ARGB ({@code 33FFD54F}, optional {@code #}); six digits mean opaque. Blank or
     * unreadable gives {@code fallback}.
     */
    public static int parseArgb(String hex, int fallback) {
        if (hex == null || hex.isBlank()) {
            return fallback;
        }
        String clean = hex.trim();
        clean = clean.startsWith("#") ? clean.substring(1) : clean;
        if (clean.length() != 6 && clean.length() != 8) {
            return fallback;
        }
        try {
            int argb = (int) Long.parseLong(clean, 16);
            return clean.length() == 6 ? argb | 0xFF000000 : argb;
        } catch (NumberFormatException bad) {
            return fallback;
        }
    }

    public static BookPalette resolve(Chapter chapter) {
        BookPalette base = BookTheme.byId(chapter.theme()).palette();
        Optional<ChapterBackground> bg = chapter.background();
        if (bg.isEmpty()) {
            return base;
        }
        return bg.get().parsedColor().map(base::withVoid).orElse(base);
    }

    public static BookPalette resolveOrDefault(Chapter chapter) {
        return chapter == null ? BookTheme.MIDNIGHT_ROYALTY.palette() : resolve(chapter);
    }
}
