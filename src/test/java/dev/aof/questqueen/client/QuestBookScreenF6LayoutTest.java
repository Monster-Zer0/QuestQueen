package dev.aof.questqueen.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/**
 * F6 layout pins (bundle t_5a9d5cde). RED-first: PHASE 1 helpers mirror the pre-fix tree
 * (dfb9f6c5...) and the A/C pins FAIL against them by design; PHASE 2 flips the helpers and wires
 * the draw sites to them. Every pin must fail before it passes (Troi's condition iv).
 *
 * <p>Domain discipline (Worf's re-freeze rule): every pin declares camera-invariance. A/C pins take
 * (w, h[, inset]) only - the log panel and sidebar are screen-fixed chrome, no camera term exists.
 * D pins are camera-invariant AND state-quantified: bar sweeps 96..260 rather than sampling one
 * capture. The seam names its regime - clamp-bound: bar + inset(tilePx) via FitCamera.View.inset();
 * flush: bar + 0, asserted through the discriminating click, never as a bare literal tautology.
 */
class QuestBookScreenF6LayoutTest {

    private static final int[] CLASSES_W = {342, 512, 581, 640, 684};
    private static final int[] CLASSES_H = {256, 360, 384, 428, 512};

    // ---------- A: log panel ----------

    @Test
    void clipBottomPinnedAndClearanceAtOrAboveFour() {
        for (int h : CLASSES_H) {
            assertEquals(h - 22, QuestBookScreen.logClipBottom(h), "clipBottom constant, h=" + h);
            double clearance = QuestBookScreen.logInnerBottom(h) - QuestBookScreen.logClipBottom(h);
            assertTrue(clearance >= 4.0,
                    "inner-bottom clearance >= 4.0 gui px on the h-17.5 plane; got " + clearance + " at h=" + h);
        }
    }

    @Test
    void clipRightPinnedAndClearanceAtOrAboveFour() {
        for (int w : CLASSES_W) {
            assertEquals(w - 21, QuestBookScreen.logClipRight(w), "clipRight constant, w=" + w);
            assertTrue((w - 17) - QuestBookScreen.logClipRight(w) >= 4, "inner-right clearance >= 4 at w=" + w);
        }
    }

    @Test
    void viewHeightIsOneFunction() {
        for (int h : CLASSES_H) {
            int y = 32;
            int viaParts = Math.max(8, QuestBookScreen.logClipBottom(h) - QuestBookScreen.logClipTop(y));
            assertEquals(viaParts, QuestBookScreen.logViewH(h, y), "logViewH disagrees with its own parts, h=" + h);
            // both call sites MUST call this one function - a source property verified on the landed
            // form (Worf C6), not by matching numbers at two sites.
        }
    }

    @Test
    void rowIsDrawnOnlyFullyInside() {
        int top = QuestBookScreen.logClipTop(32);
        int bottom = QuestBookScreen.logClipBottom(428); // 36 / 406 post-fix
        assertTrue(QuestBookScreen.logRowFits(397, 9, top, bottom), "row ending exactly at the plane draws");
        assertFalse(QuestBookScreen.logRowFits(405, 9, top, bottom), "row crossing the bottom plane must not draw");
        assertFalse(QuestBookScreen.logRowFits(30, 9, top, bottom), "row starting above the top plane must not draw");
    }

    @Test
    void wrapWidthAgreesWithClipRight() {
        for (int w : CLASSES_W) {
            for (int inset : new int[] {16, 24}) {
                int textLeft = 16 + inset;
                assertTrue(textLeft + QuestBookScreen.logWrapWidth(w, inset) <= QuestBookScreen.logClipRight(w),
                        "wrap and clip must share one right margin: w=" + w + " inset=" + inset);
            }
        }
    }

    @Test
    void blankFloorCannotTrigger() {
        for (int w : CLASSES_W) {
            int avail = QuestBookScreen.logWrapWidth(w, 24); // the widest row inset
            assertTrue(avail > QuestBookScreen.SIDEBAR_LABEL_MIN,
                    "ellipsize blank floor unreachable: avail=" + avail + " w=" + w);
        }
    }

    // ---------- C: sidebar ----------

    @Test
    void sidebarRowOnlyFullyInside() {
        int top = QuestBookScreen.TOP_H;
        int bottom = 428;
        assertTrue(QuestBookScreen.sidebarRowFullyInside(top + 10, 16, top, bottom));
        assertFalse(QuestBookScreen.sidebarRowFullyInside(top - 4, 16, top, bottom), "partial at the top edge");
        assertFalse(QuestBookScreen.sidebarRowFullyInside(bottom - 8, 16, top, bottom), "partial at the bottom edge");
    }

    @Test
    void scrollMaxReservesInsetAndLastRowIsReachable() {
        int h = 428;
        int top = QuestBookScreen.TOP_H;
        int listH = 600;
        int max = QuestBookScreen.sidebarMaxScrollFor(listH, h);
        int lastRowBottomAtMax = top + 8 + listH - max;
        assertTrue(max > 0, "content taller than the view must be scrollable");
        assertTrue(lastRowBottomAtMax <= h - 20,
                "at max scroll the last row sits at least the reserved inset above the bottom; got "
                        + lastRowBottomAtMax + " vs " + (h - 20));
    }

    @Test
    void thumbAlphaNeverDies() {
        for (float idle : new float[] {0f, 0.5f, 1f, 1.5f, 2.5f, 5f, 100f}) {
            float a = QuestBookScreen.sidebarThumbAlphaFor(idle);
            assertTrue(a >= 0.4f, "thumb floor 0.4: idle=" + idle + " alpha=" + a);
            assertTrue(a <= 1f, "alpha stays in range");
        }
        assertEquals(1f, QuestBookScreen.sidebarThumbAlphaFor(0f));
    }

    @Test
    void moreAffordanceTracksRemaining() {
        assertTrue(QuestBookScreen.moreBelow(0, 100));
        assertFalse(QuestBookScreen.moreBelow(100, 100));
        assertFalse(QuestBookScreen.moreBelow(0, 0));
    }

    // ---------- D: tab (camera-invariant; state-quantified) ----------

    @Test
    void clickAtOrRightOfBarIsNeverInsideTheTabBox() {
        for (int bar = 96; bar <= 260; bar += 4) {
            for (int h : new int[] {256, 384, 428}) {
                int tabX = bar - 12;
                int tabY = QuestBookScreen.tabYFor(h);
                double midY = tabY + 18;
                assertTrue(QuestBookScreen.over(tabX, tabY, 12, 36, bar - 0.5, midY), "inside the box, bar=" + bar);
                assertFalse(QuestBookScreen.over(tabX, tabY, 12, 36, bar + 0.1, midY),
                        "flush state: first tile at bar+0 - the retired +2 box would swallow this click");
                assertFalse(QuestBookScreen.over(tabX, tabY, 12, 36, bar + 1.0, midY),
                        "discriminator against any reintroduced positive shift");
                assertFalse(QuestBookScreen.over(tabX, tabY, 12, 36, bar + 2.0, midY));
                // boundary convention pinned from over() (line 5452, half-open [x, x + w)):
                assertFalse(QuestBookScreen.over(tabX, tabY, 12, 36, bar, midY),
                        "mx == bar is excluded: the box is half-open");
            }
        }
    }

    @Test
    void collapsedTabNeverCrossesTheBoardEdge() {
        int tabY = QuestBookScreen.tabYFor(428);
        // collapsed: tabX() = 0 (line 5188 branch), box [0, TAB_W); boardLeft() = TAB_W (line 4778).
        assertTrue(QuestBookScreen.over(0, tabY, 12, 36, 11.5, tabY + 18), "inside the collapsed box");
        assertFalse(QuestBookScreen.over(0, tabY, 12, 36, 12.5, tabY + 18), "right of the collapsed box");
    }

    @Test
    void seamNamesItsRegime() {
        assertEquals(6, firstTileOffsetAtClampBound(48),
                "clamp-bound regime, literal six; a swap to margin() reads 12 and fails loudly");
        assertEquals(4, firstTileOffsetAtClampBound(32), "preview-ladder rung");
        assertEquals(8, firstTileOffsetAtClampBound(128));
        // flush state: offset 0, carried by the discriminating click assertions above
        // (bar+0.1 and bar+1.0 out of the box) - never asserted as a bare literal.
    }

    /** The seam relation, test-side: the clamp-bound first-tile offset is the inset() source of truth. */
    static int firstTileOffsetAtClampBound(int tilePx) {
        return new FitCamera.View(0, 0, 0, 0, tilePx, 0).inset();
    }
}
