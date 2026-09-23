package dev.aof.questqueen.client;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pins the FIT camera objective from Troi's F4 review and her two follow-ups.
 *
 * <p>These assert the properties the ruling asks for rather than golden numbers, so they still mean
 * something if the candidate set or the viewport constants change: the anchor is framed rather than the
 * hollow middle, the camera is clamped to the content so it cannot spend the viewport on empty grid, the
 * choice is idempotent and order-independent, the COUNT flag really maximises tile count instead of
 * inheriting the anchor constraint, and the in-view count differs from the wholly-visible count. Since
 * F6x the floor's target is the gutter bounded form (whole inside the clamp's inset, stride - inset on the
 * live class), and every rim anchor must reach it.
 *
 * <p>The viewport is the measured live case (gui 640x360, sidebar 148, so a 492-wide board under a 26px
 * bar) with tilePx 48 and gapPx 9, which is the ladder's floor rung.
 */
class FitCameraTest {

    private static FitCamera.View view() {
        return new FitCamera.View(148, 492, 334, 26, 48, 9);
    }

    private static List<FitCamera.Cell> grid(int cols, int rows) {
        List<FitCamera.Cell> cells = new ArrayList<>();
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                cells.add(new FitCamera.Cell(x, y));
            }
        }
        return cells;
    }

    /** A ring: every cell on the border of a cols x rows grid, hollow in the middle. */
    private static List<FitCamera.Cell> ring(int cols, int rows) {
        List<FitCamera.Cell> cells = new ArrayList<>();
        for (int y = 0; y < rows; y++) {
            for (int x = 0; x < cols; x++) {
                if (x == 0 || y == 0 || x == cols - 1 || y == rows - 1) {
                    cells.add(new FitCamera.Cell(x, y));
                }
            }
        }
        return cells;
    }

    private static List<FitCamera.Cell> shift(List<FitCamera.Cell> cells, int dx, int dy) {
        List<FitCamera.Cell> out = new ArrayList<>();
        for (FitCamera.Cell c : cells) {
            out.add(new FitCamera.Cell(c.x() + dx, c.y() + dy));
        }
        return out;
    }

    @Test
    void aChapterThatFitsIsShownWhole() {
        List<FitCamera.Cell> cells = grid(3, 2);
        FitCamera.Choice choice = FitCamera.choose(cells, new FitCamera.Cell(0, 0), FitCamera.Target.ANCHOR, view());
        assertEquals(cells.size(), choice.visible());
        assertEquals(cells.size(), choice.total());
        assertTrue(choice.anchored(), "a chapter that fits has room for the anchor's margin band");
    }

    @Test
    void aRingFramesBetterThanTheHollowMiddleDoes() {
        List<FitCamera.Cell> cells = ring(14, 10);
        FitCamera.View v = view();
        FitCamera.Cell anchor = new FitCamera.Cell(0, 4);
        FitCamera.Choice choice = FitCamera.choose(cells, anchor, FitCamera.Target.ANCHOR, v);

        // What the shipped code does: centre the bounding box, which for a ring is the hole.
        int bboxRight = 13 * v.stridePx() + v.tilePx();
        int bboxBottom = 9 * v.stridePx() + v.tilePx();
        int centredSx = (int) Math.round(bboxRight / 2.0 - v.contentWidth() / 2.0);
        int centredSy = (int) Math.round(bboxBottom / 2.0 - v.topH() - v.contentHeight() / 2.0);
        int atHollowMiddle = FitCamera.visibleCount(cells, centredSx, centredSy, v);

        assertTrue(choice.visible() > atHollowMiddle,
                "ring framing must beat the bbox centre: chosen " + choice.visible() + " vs centre " + atHollowMiddle);
        assertTrue(FitCamera.partlyVisible(anchor, choice.camSx(), choice.camSy(), v),
                "the anchor must be on screen");
        assertNotEquals("anchor-offscreen", choice.rule(), "the anchor must be placed, not abandoned");
    }

    /**
     * The clamp is allowed to trade the margin band away for content, and on a rim anchor it does: the band
     * would need 12px of overhang where the clamp permits 6. That is Troi's F4 follow-up (1) choosing
     * content over breathing room, so the label must say so rather than pretending the band was honoured.
     */
    @Test
    void aRimAnchorGivesUpTheBandToTheClampAndSaysSo() {
        FitCamera.View v = view();
        List<FitCamera.Cell> cells = ring(14, 10);
        FitCamera.Cell anchor = new FitCamera.Cell(0, 4);
        FitCamera.Choice choice = FitCamera.choose(cells, anchor, FitCamera.Target.ANCHOR, v);
        assertFalse(choice.anchored(), "the clamp forbids the overhang the band wants here");
        assertEquals("anchor-edge", choice.rule());
        assertTrue(FitCamera.partlyVisible(anchor, choice.camSx(), choice.camSy(), v));
    }

    @Test
    void theAnchorIsOnScreenForEveryRimPosition() {
        List<FitCamera.Cell> cells = ring(14, 10);
        FitCamera.View v = view();
        for (int y = 0; y < 10; y++) {
            FitCamera.Cell anchor = new FitCamera.Cell(0, y);
            FitCamera.Choice choice = FitCamera.choose(cells, anchor, FitCamera.Target.ANCHOR, v);
            assertTrue(FitCamera.partlyVisible(anchor, choice.camSx(), choice.camSy(), v),
                    "anchor (" + anchor.x() + "," + anchor.y() + ") must be on screen");
        }
    }

    /**
     * Troi's F4 follow-up (1), on the exact shape of the live defect: Sustenance's tiles start at (1,1), so
     * the grid column x=0 is empty ground the chapter does not occupy. The old band spent a full empty
     * column plus about a row and a half on it and cost two to three tiles.
     */
    @Test
    void noEmptyGridIsShownOnASideWhereTheContentContinues() {
        FitCamera.View v = view();
        List<FitCamera.Cell> cells = shift(ring(14, 10), 1, 1);
        FitCamera.Cell anchor = new FitCamera.Cell(1, 1);
        FitCamera.Choice choice = FitCamera.choose(cells, anchor, FitCamera.Target.ANCHOR, v);

        int bboxLeft = 1 * v.stridePx();
        int bboxRight = 14 * v.stridePx() + v.tilePx();
        int bboxTop = 1 * v.stridePx();
        int bboxBottom = 10 * v.stridePx() + v.tilePx();

        assertTrue(choice.camSx() >= bboxLeft - v.inset(),
                "the camera may overhang the content's left edge by at most the inset");
        assertTrue(choice.camSx() + v.contentWidth() <= bboxRight + v.inset(),
                "the camera may overhang the content's right edge by at most the inset");
        // The board viewport starts at topH, so the content-space window is offset by it. Asserting camSy
        // directly is what let the first clamp pass its own test while pinning the top row under the chrome
        // bar: the live 1.1.173 run clamped camSy to bboxTop - inset and whole-visible tiles fell 8 -> 4.
        int windowTop = choice.camSy() + v.topH();
        int windowBottom = choice.camSy() + v.topH() + v.contentHeight();
        assertTrue(windowTop >= bboxTop - v.inset(),
                "the window may overhang the content's top edge by at most the inset");
        assertTrue(windowBottom <= bboxBottom + v.inset(),
                "the window may overhang the content's bottom edge by at most the inset");
        assertTrue(FitCamera.fullyVisible(new FitCamera.Cell(1, 1), choice.camSx(), choice.camSy(), v),
                "the chapter's first tile must be fully visible, not tucked under the chrome bar");

        // The empty column itself must be gone, not merely mostly gone.
        assertFalse(FitCamera.partlyVisible(new FitCamera.Cell(0, 1), choice.camSx(), choice.camSy(), v),
                "the empty grid column the chapter does not occupy must not be on screen at all");
    }

    /** The clamp must not degrade the case it does not apply to: a fitting chapter keeps its margin band. */
    @Test
    void aFittingChapterStillGetsTheMarginBand() {
        FitCamera.View v = view();
        List<FitCamera.Cell> cells = shift(grid(4, 3), 2, 2);
        FitCamera.Cell anchor = new FitCamera.Cell(2, 2);
        FitCamera.Choice choice = FitCamera.choose(cells, anchor, FitCamera.Target.ANCHOR, v);
        assertTrue(choice.anchored(), "no clamp applies here, so the band must still be honoured");
        assertEquals("anchor", choice.rule());
    }

    @Test
    void theChoiceIsIdempotent() {
        List<FitCamera.Cell> cells = ring(14, 10);
        FitCamera.Cell anchor = new FitCamera.Cell(0, 4);
        FitCamera.Choice first = FitCamera.choose(cells, anchor, FitCamera.Target.ANCHOR, view());
        FitCamera.Choice second = FitCamera.choose(cells, anchor, FitCamera.Target.ANCHOR, view());
        assertEquals(first, second, "same chapter and viewport must give the identical camera");
    }

    @Test
    void theChoiceDoesNotDependOnInputOrder() {
        List<FitCamera.Cell> ordered = ring(14, 10);
        List<FitCamera.Cell> shuffled = new ArrayList<>(ordered);
        Collections.shuffle(shuffled, new java.util.Random(20260917L));
        FitCamera.Cell anchor = new FitCamera.Cell(0, 4);
        // A search whose strict comparison leaned on iteration order would fail this, and it would fail
        // intermittently on the live board rather than here.
        assertEquals(FitCamera.choose(ordered, anchor, FitCamera.Target.ANCHOR, view()),
                FitCamera.choose(shuffled, anchor, FitCamera.Target.ANCHOR, view()));
    }

    /**
     * The defect this pins: the first F4 build passed {@code requireAnchor} unconditionally, so COUNT
     * inherited the anchor constraint and returned the anchor rule's own camera - which is why the
     * comparison frames bought nothing on Sustenance. A lone anchor with a dense cluster elsewhere must
     * make counting follow the crowd and show STRICTLY more, not merely differ.
     */
    @Test
    void countTargetMaximisesTilesFreely() {
        List<FitCamera.Cell> cells = new ArrayList<>();
        cells.add(new FitCamera.Cell(0, 0));
        cells.addAll(shift(grid(6, 5), 10, 0));

        FitCamera.Cell anchor = new FitCamera.Cell(0, 0);
        FitCamera.Choice anchored = FitCamera.choose(cells, anchor, FitCamera.Target.ANCHOR, view());
        FitCamera.Choice counted = FitCamera.choose(cells, anchor, FitCamera.Target.COUNT, view());

        assertTrue(FitCamera.partlyVisible(anchor, anchored.camSx(), anchored.camSy(), view()),
                "the anchor rule must keep the player's tile on screen");
        assertTrue(counted.visible() > anchored.visible(),
                "count-max must show strictly more here: counted " + counted.visible()
                        + " vs anchored " + anchored.visible());
        assertNotEquals(new int[] {anchored.camSx(), anchored.camSy()},
                new int[] {counted.camSx(), counted.camSy()},
                "the two targets must frame this chapter differently, or the comparison proves nothing");
        assertEquals("count", counted.rule());
    }

    /** Troi's F4 follow-up (2): the cue counts tiles with any visible area, which is more than whole ones. */
    @Test
    void inViewCountIncludesClippedTiles() {
        FitCamera.View v = view();
        List<FitCamera.Cell> cells = shift(ring(14, 10), 1, 1);
        FitCamera.Choice choice = FitCamera.choose(cells, new FitCamera.Cell(1, 1), FitCamera.Target.ANCHOR, v);

        int whole = FitCamera.visibleCount(cells, choice.camSx(), choice.camSy(), v);
        int inView = FitCamera.inViewCount(cells, choice.camSx(), choice.camSy(), v);

        assertTrue(inView >= whole, "every wholly visible tile is also in view");
        assertTrue(inView > whole,
                "a clamped frame clips tiles at the edge, so in-view must exceed whole: "
                        + inView + " vs " + whole);
        assertTrue(inView <= cells.size());
    }

    @Test
    void anEmptyChapterIsHandledWithoutAFraming() {
        FitCamera.Choice choice = FitCamera.choose(new ArrayList<>(), null, FitCamera.Target.ANCHOR, view());
        assertEquals(0, choice.total());
        assertEquals(0, choice.visible());
        assertEquals("empty", choice.rule());
    }

    @Test
    void visibilityMatchesTheRenderersPlacement() {
        // A single row at the origin, with the camera pulled up so the row's top sits exactly on the
        // board's top edge. The viewport's y origin is the 26px chrome bar, not the screen top, so a row
        // at camSy 0 is hidden behind the chrome and correctly does not count as visible.
        List<FitCamera.Cell> cells = grid(4, 1);
        FitCamera.View v = view();
        int camY = -v.topH();
        assertEquals(cells.size(), FitCamera.visibleCount(cells, 0, camY, v));
        assertEquals(0, FitCamera.visibleCount(cells, 0, 0, v),
                "a row tucked under the chrome bar is not inside the board viewport");
        assertEquals(0, FitCamera.visibleCount(cells, 100_000, camY, v), "a far camera must show nothing");
    }

    // ----------------------------------------------------------------------------------------------
    // Worf's F4 anchor-floor ruling (2026-09-17): the intersection-AREA predicate (F1), the minimum-shift
    // floor of form (b), the clamp intersection (F8), and the inertness the two passed frames depend on.
    // Troi's F6x refinement (2026-09-18) moved the floor's target to the gutter bounded form, tile + inset;
    // the range, inertness and rim-coverage tests below are his properties in that F6x form.
    // ----------------------------------------------------------------------------------------------

    private static final FitCamera.Cell ANCHOR_11 = new FitCamera.Cell(1, 1);

    /**
     * Worf's F1 table, reproduced. The predicate is an AREA, so it is symmetric: it rejects the 7px sliver
     * (camSx 98) AND closes the hole the one-sided bound form left open (camSx -479, anchor wholly off the
     * RIGHT edge with zero visible area, which {@code camSx <= 81} accepted). camSy is pinned so the y axis
     * contributes its full 48px and these are x-axis numbers, which is how his table was built.
     */
    @Test
    void theHalfVisiblePredicateReproducesWorfsTable() {
        FitCamera.View v = view();
        int camSy = 31; // the anchor's top edge lands exactly on the viewport top, so y is clear
        assertTrue(FitCamera.anchorHalfVisible(ANCHOR_11, 51, camSy, v), "camSx 51 - whole anchor");
        assertTrue(FitCamera.anchorHalfVisible(ANCHOR_11, 57, camSy, v), "camSx 57 - whole anchor");
        assertTrue(FitCamera.anchorHalfVisible(ANCHOR_11, 81, camSy, v),
                "camSx 81 is EXACTLY half (24 x 48 of 2304), and exactly-half must PASS");
        assertFalse(FitCamera.anchorHalfVisible(ANCHOR_11, 98, camSy, v), "camSx 98 - the 7px sliver");
        assertFalse(FitCamera.anchorHalfVisible(ANCHOR_11, -479, camSy, v),
                "camSx -479 - wholly off the RIGHT edge, zero area, and the bound form accepted it");
        assertFalse(FitCamera.anchorHalfVisible(ANCHOR_11, -1000, camSy, v), "far off the right edge");

        assertEquals(336, FitCamera.visibleArea(ANCHOR_11, 98, camSy, v), "7 x 48 - Worf's F1 figure");
        assertEquals(1152, FitCamera.visibleArea(ANCHOR_11, 81, camSy, v), "24 x 48 - exactly half");
        assertEquals(2304, FitCamera.visibleArea(ANCHOR_11, 51, camSy, v), "48 x 48 - whole");
        assertEquals(0, FitCamera.visibleArea(ANCHOR_11, -479, camSy, v), "the right-edge hole closes");
    }

    /**
     * The floor's target is the WHOLE anchor INSIDE the clamp's gutter (Troi's closing ruling, then her F6x
     * refinement): from the live sliver at camSx 98 it moves by the MINIMUM that reaches 51 --
     * anchorX*stridePx - inset, the bound she named as "stride - inset" -- and not merely to the half bound
     * at 81. Stopping at half would leave the caption band sliced, which her standing clause forbids; the
     * flush bound at 57 leaves 0 px where the clamp's own state leaves the inset its name comes from.
     */
    @Test
    void theFloorShiftsByTheMinimumThatMakesTheAnchorWhole() {
        FitCamera.View v = view();
        List<FitCamera.Cell> cells = shift(ring(14, 10), 1, 1);
        FitCamera.Choice sliver = new FitCamera.Choice(98, 31, 17, cells.size(), 1, 1, false, "anchor-edge", null);
        FitCamera.Choice floored = FitCamera.withFloor(sliver, cells, ANCHOR_11, v);

        assertEquals(51, floored.camSx(), "the shift lands exactly on stride - inset, the gutter bound");
        assertEquals(25, floored.camSy(), "y was whole but flush at 31; the target is the gutter, so it moves");
        assertEquals(-47, floored.floor().shiftSx());
        assertEquals(-6, floored.floor().shiftSy());
        assertEquals(2304, floored.floor().area(), "the whole tile area: 48 x 48");
        assertEquals("whole", floored.floor().state());
        assertNotEquals(81, floored.camSx(), "stopping at the half bound would still slice the caption band");
        assertTrue(FitCamera.wholeInGutter(ANCHOR_11, floored.camSx(), floored.camSy(), v));
    }

    /**
     * A framing that already carries the gutter must not move at all: the accepted by_air frame sits at
     * (51, 25) and both its axes satisfy the F6x target, so the floor is inert there and the refinement
     * cannot change that frame in principle.
     */
    @Test
    void theFloorIsInertOnAFramingThatAlreadyPasses() {
        FitCamera.View v = view();
        List<FitCamera.Cell> cells = shift(ring(14, 10), 1, 1);
        FitCamera.Choice whole = new FitCamera.Choice(51, 25, 17, cells.size(), 1, 1, false, "anchor-edge", null);
        assertTrue(FitCamera.wholeInGutter(ANCHOR_11, whole.camSx(), whole.camSy(), v),
                "the frame under test must already carry the gutter, or inertness proves nothing");
        FitCamera.Choice floored = FitCamera.withFloor(whole, cells, ANCHOR_11, v);

        assertEquals(51, floored.camSx(), "camSx 51 is the gutter state of the accepted by_air frame");
        assertEquals(25, floored.camSy(), "camSy 25 likewise carries its gutter and must not move");
        assertEquals(0, floored.floor().shiftSx() + floored.floor().shiftSy(), "no shift");
        assertEquals("whole", floored.floor().state());
        assertEquals(whole.visible(), floored.visible(), "no shift means no cost");
    }

    /**
     * F6x's own case: a framing that is WHOLE but FLUSH is no longer accepted as-is. The y axis here is
     * whole with the anchor's top edge exactly on the viewport top (camSy 31), which the old target left
     * untouched; the gutter target moves it to camSy 25 by the least amount. The x axis already carries its
     * gutter and must not move -- that is what "minimum" means per axis, and it is why the live Sustenance
     * shift is (-47, 0) and not (-47, -6).
     */
    @Test
    void aWholeButFlushAxisMovesToTheGutter() {
        FitCamera.View v = view();
        List<FitCamera.Cell> cells = shift(ring(14, 10), 1, 1);
        FitCamera.Choice flush = new FitCamera.Choice(51, 31, 17, cells.size(), 1, 1, false, "anchor-edge", null);
        assertFalse(FitCamera.wholeInGutter(ANCHOR_11, flush.camSx(), flush.camSy(), v),
                "the frame under test must actually be flush somewhere, or it proves nothing");
        FitCamera.Choice floored = FitCamera.withFloor(flush, cells, ANCHOR_11, v);

        assertEquals(51, floored.camSx(), "x already carries its gutter and must not move");
        assertEquals(25, floored.camSy(), "the flush y axis moves to the gutter by the least amount");
        assertEquals(0, floored.floor().shiftSx());
        assertEquals(-6, floored.floor().shiftSy(), "the gutter is inset px: 6 at the 48 px rung");
        assertEquals("whole", floored.floor().state());
        assertTrue(FitCamera.wholeInGutter(ANCHOR_11, floored.camSx(), floored.camSy(), v));
    }

    /**
     * Worf's F8, as an assertion rather than a shrug: the whole-anchor TARGET range and the clamp range
     * always intersect, so the floor can never be forced outside the clamp and the clamp can never undo the
     * floor. Since F6x the target is the gutter bounded form -- tile + inset, stride - inset on the high
     * side -- and the structural reason survives it: the anchor lies inside the chapter's bbox, so
     * bboxLeft <= pos and pos + tilePx <= bboxRight, and those are exactly the two inequalities the
     * intersection needs. At a content-corner anchor, like the live class's, the intersection is the
     * clamp's own flush state: one point, one gutter, which is what the refinement is asking every class
     * to share.
     */
    @Test
    void theWholeAnchorTargetAndTheClampAlwaysIntersect() {
        FitCamera.View v = view();
        int bboxLeft = 1 * v.stridePx();
        int bboxRight = 14 * v.stridePx() + v.tilePx();
        int[] clamp = FitCamera.clampRange(bboxLeft, bboxRight, v.contentWidth(), v.inset());
        assertTrue(clamp != null, "this content is wider than the viewport, so the clamp applies");
        int[] whole = FitCamera.axisRange(1, v.tilePx() + v.inset(), v, true);
        assertTrue(Math.max(clamp[0], whole[0]) <= Math.min(clamp[1], whole[1]),
                "floor and clamp must intersect: clamp " + clamp[0] + ".." + clamp[1]
                        + ", whole-in-gutter " + whole[0] + ".." + whole[1]);
        assertEquals(1 * v.stridePx() - v.inset(), whole[1],
                "the target's high bound is stride - inset, not the flush stride bound");
        assertEquals(clamp[0], whole[1],
                "at a corner anchor the gutter bound IS the clamp's own bound: one state, one gutter");
    }

    /**
     * Both axes clipped at once. The whole-in-gutter target reaches past the balanced candidate, so this
     * asserts the stronger property: the anchor comes out whole on BOTH axes with its gutter, not half on
     * each -- and per-axis half was never sufficient anyway, since a quarter of a tile on each axis is a
     * quarter of the AREA.
     */
    @Test
    void theFloorHandlesACameraThatClipsBothAxes() {
        FitCamera.View v = view();
        List<FitCamera.Cell> cells = shift(ring(14, 10), 1, 1);
        FitCamera.Choice corner = new FitCamera.Choice(90, 45, 17, cells.size(), 1, 1, false, "anchor-edge", null);
        FitCamera.Choice floored = FitCamera.withFloor(corner, cells, ANCHOR_11, v);

        assertTrue(FitCamera.fullyVisible(ANCHOR_11, floored.camSx(), floored.camSy(), v),
                "the whole target must hold when both axes clip, not only when one does");
        assertTrue(FitCamera.wholeInGutter(ANCHOR_11, floored.camSx(), floored.camSy(), v),
                "and it must land in the gutter, not at the flush bound");
        assertEquals("whole", floored.floor().state());
        assertEquals(floored.camSx() - corner.camSx(), floored.floor().shiftSx(),
                "the reported shift must describe the camera actually returned");
        assertEquals(floored.camSy() - corner.camSy(), floored.floor().shiftSy());
    }

    /**
     * The strongest form of the property, now in its F6x form: for EVERY rim anchor, whatever the search
     * happened to choose, the floor ends with the anchor whole INSIDE the gutter -- not merely on screen,
     * and not merely whole-but-flush. Worf's F8 is what makes this assertable at all -- the target range
     * and the clamp range always intersect -- and it holds across all four edges because the predicate is
     * a rectangle containment, not a one-sided bound.
     */
    @Test
    void everyRimAnchorEndsUpWhole() {
        List<FitCamera.Cell> cells = ring(14, 10);
        FitCamera.View v = view();
        for (int y = 0; y < 10; y++) {
            for (int x : new int[] {0, 13}) {
                FitCamera.Cell anchor = new FitCamera.Cell(x, y);
                FitCamera.Choice choice = FitCamera.choose(cells, anchor, FitCamera.Target.ANCHOR, v);
                assertTrue(FitCamera.wholeInGutter(anchor, choice.camSx(), choice.camSy(), v),
                        "anchor (" + x + "," + y + ") must end WHOLE IN THE GUTTER: state="
                                + choice.floor().state() + " area=" + choice.floor().area()
                                + " cam=" + choice.camSx() + "," + choice.camSy());
            }
        }
    }

    /** COUNT exists to be the anchor-free comparison; flooring it would corrupt the comparison itself. */
    @Test
    void theFloorDoesNotApplyToCountFrames() {
        List<FitCamera.Cell> cells = new ArrayList<>();
        cells.add(new FitCamera.Cell(0, 0));
        cells.addAll(shift(grid(6, 5), 10, 0));
        FitCamera.Choice counted = FitCamera.choose(cells, new FitCamera.Cell(0, 0), FitCamera.Target.COUNT, view());
        assertEquals(null, counted.floor(), "a COUNT frame carries no floor");
        assertEquals("count", counted.rule());
    }
}
