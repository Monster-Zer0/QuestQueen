package dev.aof.questqueen.client;

import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;

/**
 * Pure camera chooser for the quest board's FIT behaviour.
 *
 * <p>Split out of {@code QuestBookScreen} deliberately: Worf's F4 review (V4) requires the selection to be
 * a pure function of the tiles, the viewport and the zoom so that idempotence can be asserted by a test
 * rather than commented. This class holds no state, reads no field of the screen, and never touches the
 * current camera, so calling it twice on the same chapter and viewport returns the identical Choice. It
 * also does not derive candidates from the live camera, which is the one design V4 says would break that.
 *
 * <h2>Objective (Troi's F4 review, T1)</h2>
 * The camera stops landing in a chapter's hollow middle. TILE COUNT IS THE TIE-BREAKER, NOT THE OBJECTIVE:
 * <ol>
 *   <li>the anchor tile must sit on screen, preferably inside a margin band rather than jammed at an edge;</li>
 *   <li>among framings that satisfy that, take the one showing the most tiles;</li>
 *   <li>among those ties, prefer the framing whose viewport centre is nearest the weighted centroid of
 *       tiles near the ANCHOR - not the bounding box centre. This clause is what carries ring chapters:
 *       the anchor is on the rim, so its neighbourhood has content, while the bbox centre is the hole.</li>
 * </ol>
 * The anchor itself is supplied by the caller: the tile the player is on, else the chapter's entry tile.
 *
 * <h2>Content clamp (Troi's F4 follow-up 1)</h2>
 * The band must not spend the viewport on grid the chapter does not occupy. The first F4 build framed the
 * anchor inside a band a quarter of a tile wide, which on Sustenance bought a full empty left column plus
 * about a row and a half of empty grid and cost two to three tiles. The camera is now clamped to the
 * CONTENT bounding box with a small fixed inset: the viewport may overhang the content by at most
 * {@link View#inset()} pixels a side, and where that is impossible - a chapter much smaller than the
 * viewport, where empty grid cannot be avoided at all - the search is left unclamped and the anchor rule
 * places the camera, which costs no tiles. Because the clamp can legitimately put an edge tile flush
 * against the viewport edge, "inside the margin band" is a preference rather than a hard requirement, and
 * {@link Choice#rule()} reports which of {@code anchor}, {@code anchor-edge} or {@code anchor-offscreen}
 * was achieved, so the difference is visible in the log rather than inferred from a picture.
 *
 * <h2>Targets</h2>
 * {@link Target#COUNT} makes tile count the objective instead, and it has to actually do so: the first
 * build passed {@code requireAnchor} unconditionally, so COUNT silently inherited the anchor constraint and
 * returned the anchor rule's own camera - which is why the comparison frames bought nothing. COUNT is for
 * comparison frames only, and the screen's default must never be it: Troi accepted the flag only on the
 * condition that the comparison cannot become the shipped behaviour by inaction.
 *
 * <h2>Counting tiles (Troi's F4 follow-up 2)</h2>
 * Two counts, because they mean different things. {@link #visibleCount} counts tiles WHOLLY inside the
 * viewport; {@link #inViewCount} counts tiles with ANY visible area, which is what a player perceives. The
 * board's cue reports the in-view count and says "in view", because a cue that disagrees with what the
 * player can see reads as a bug. Scoring still uses the wholly-visible count; the score is never shown.
 *
 * <h2>Units and space (Troi's E1)</h2>
 * Everything here is in the renderer's own INTEGER screen pixels, because that is the space the board is
 * actually drawn in and the space the hit test maps against. A cell at grid (gx, gy) is placed at
 * {@code (boardLeft + gx*stridePx - camSx, gy*stridePx - camSy)} with size {@code tilePx}; there is no
 * world-unit arithmetic in this class at all. The caller converts a result back to the screen's world-unit
 * camera with {@code cameraX = camSx / zoom}, which is exact up to the rounding the renderer already does.
 * The earlier proposal omitted this, and a reviewer could not check a camera change without it.
 *
 * <h2>Determinism (Worf's V3)</h2>
 * Candidates live in sorted sets and ties are broken by a stated order: most tiles, then nearest the
 * anchor-local centroid, then lowest camSx, then lowest camSy. The result is therefore independent of
 * iteration order. Candidates sit on cell boundaries, which is also what keeps a camera move visible to
 * {@code widgetLayoutChanged()}, since that compares the ROUNDED camSx/camSy.
 */
public final class FitCamera {

    /** What FIT is optimising for. */
    public enum Target {
        /** Ship default: anchor-first, tile count as the tie-breaker. */
        ANCHOR,
        /** Tile count as the objective, free of the anchor constraint. Comparison frames only. */
        COUNT
    }

    /** How well the anchor ended up placed; used only to label the result. */
    private enum AnchorFit {
        /** Anchor wholly inside the margin band. */
        IN_BAND,
        /** Anchor has visible area even though it is flush against an edge. */
        ON_SCREEN,
        /** Anchor not visible at all, or there is no anchor. */
        NONE
    }

    /** A tile's grid position, in cells. */
    public record Cell(int x, int y) {}

    /** The board viewport and the tile metrics the renderer places tiles with. */
    public record View(int boardLeft, int contentWidth, int contentHeight, int topH, int tilePx, int gapPx) {
        public int stridePx() {
            return tilePx + gapPx;
        }

        public int viewRight() {
            return boardLeft + contentWidth;
        }

        public int viewBottom() {
            return topH + contentHeight;
        }

        /** How far a tile must stay from the viewport edge to count as "not jammed against it". */
        public int margin() {
            return Math.max(4, tilePx / 4);
        }

        /**
         * The clamp inset, Troi's F4 follow-up (1): the most grid the camera may show beyond the content on
         * a side where the chapter does not continue. The old whole-cell margin let the band spend itself on
         * empty grid - a full empty column plus a row and a half on Sustenance, costing two to three tiles -
         * so the overhang is now capped at a few pixels instead of a quarter of a tile.
         */
        public int inset() {
            return Math.max(4, Math.min(8, tilePx / 8));
        }
    }

    /**
     * The chosen camera, plus the numbers the fit log line reports.
     *
     * @param camSx    screen-pixel camera offset, x, AFTER the floor. Divide by zoom for the world camera.
     * @param camSy    screen-pixel camera offset, y, after the floor.
     * @param visible  tiles wholly inside the viewport under this camera, counted after the floor.
     * @param total    tiles in the chapter.
     * @param anchorX  anchor cell, or -1 when there is no anchor.
     * @param anchorY  anchor cell, or -1 when there is no anchor.
     * @param anchored whether the anchor ended up inside the margin band (not merely on screen).
     * @param rule     which SEARCH TIER chose this camera: anchor, anchor-edge, anchor-offscreen, count,
     *                 or empty. It deliberately says nothing about the floor: Worf's F4 requires a field's
     *                 meaning not to shift under its own name, so the floor's result is reported separately
     *                 in {@link #floor()} rather than folded into this string.
     * @param floor    what the minimum-shift floor did, or null when there is no anchor or no camera.
     */
    public record Choice(int camSx, int camSy, int visible, int total,
                         int anchorX, int anchorY, boolean anchored, String rule, Floor floor) {}

    /**
     * The result of the minimum-shift floor (Worf's revised F4 ruling, form (b)).
     *
     * <p>The floor does not re-select. The search picks the camera exactly as it always did, and the floor
     * then moves it by the least amount that brings the anchor to at least half visible. Selection being
     * untouched is the point: a framing that already satisfies the criterion cannot change at all, and the
     * count cost is computable from the shift rather than being the open risk his F0 named.
     *
     * @param state         whole, half, partial (the floor could not reach half - LOUD), or none. {@code
     *                      whole} means the whole anchor inside the clamp's gutter where the viewport can
     *                      carry one (stride - inset at the live class, Troi's F6x refinement); the pre-F6x
     *                      flush bound is the fallback on a viewport too narrow for a gutter, and still
     *                      reads whole.
     * @param shiftSx       pixels the camera moved in x; 0 when the criterion already held.
     * @param shiftSy       pixels the camera moved in y.
     * @param visibleBefore wholly-visible tile count before the floor, so the cost is explicit in the log.
     * @param area          the anchor's visible area after the floor, in square screen pixels. The acceptance
     *                      test reads this against tilePx*tilePx, which the fit log line also carries.
     */
    public record Floor(String state, int shiftSx, int shiftSy, int visibleBefore, int area) {}

    /**
     * The anchor's visible area under a camera, in square screen pixels: the area of the intersection of the
     * cell's rectangle with the board viewport. Symmetric on all four edges by construction, which is the
     * repair Worf's F1 requires: the one-sided bound form accepted a camera putting the anchor wholly off the
     * RIGHT edge (camSx = -479, zero visible area) because it bounded only how far the anchor could slide off
     * the left. His F1 table is reproduced verbatim by the tests.
     */
    public static int visibleArea(Cell c, int camSx, int camSy, View view) {
        int sx = view.boardLeft() + c.x() * view.stridePx() - camSx;
        int sy = c.y() * view.stridePx() - camSy;
        int overlapX = Math.min(sx + view.tilePx(), view.viewRight()) - Math.max(sx, view.boardLeft());
        int overlapY = Math.min(sy + view.tilePx(), view.viewBottom()) - Math.max(sy, view.topH());
        return Math.max(0, overlapX) * Math.max(0, overlapY);
    }

    /**
     * True when at least half the anchor's area is inside the viewport: the criterion Troi pre-registered,
     * expressed as the predicate rather than as the literal 81 her worked example produced, so it holds at
     * every zoom rung instead of only at tilePx = 48 (Worf's F3).
     *
     * <p>Written as {@code area * 2 >= tilePx * tilePx} rather than {@code area >= tilePx*tilePx/2} so that
     * integer division cannot make exactly-half fail: at tilePx = 48 the threshold is 1152, and a camera at
     * camSx = 81 produces exactly 1152, which must PASS.
     */
    public static boolean anchorHalfVisible(Cell anchor, int camSx, int camSy, View view) {
        return visibleArea(anchor, camSx, camSy, view) * 2 >= view.tilePx() * view.tilePx();
    }

    /** Cells within this many cells of the anchor contribute to the anchor-local centroid. */
    private static final int ANCHOR_RADIUS = 2;

    private FitCamera() {}

    /**
     * Chooses a camera for the given tiles and viewport.
     *
     * @param cells  every placed tile; may be empty, in which case the caller keeps its old framing.
     * @param anchor the tile the player is on, else the chapter's entry tile, else null.
     */
    public static Choice choose(List<Cell> cells, Cell anchor, Target target, View view) {
        if (cells == null || cells.isEmpty()) {
            return new Choice(0, 0, 0, 0, -1, -1, false, "empty", null);
        }
        List<Cell> safe = new ArrayList<>(cells);
        int total = safe.size();
        int stridePx = view.stridePx();

        int bboxLeft = Integer.MAX_VALUE;
        int bboxTop = Integer.MAX_VALUE;
        int bboxRight = Integer.MIN_VALUE;
        int bboxBottom = Integer.MIN_VALUE;
        for (Cell c : safe) {
            bboxLeft = Math.min(bboxLeft, c.x() * stridePx);
            bboxTop = Math.min(bboxTop, c.y() * stridePx);
            bboxRight = Math.max(bboxRight, c.x() * stridePx + view.tilePx());
            bboxBottom = Math.max(bboxBottom, c.y() * stridePx + view.tilePx());
        }

        TreeSet<Integer> rawXs = new TreeSet<>();
        TreeSet<Integer> rawYs = new TreeSet<>();
        for (Cell c : safe) {
            int left = c.x() * stridePx;
            int top = c.y() * stridePx;
            // Three framings per cell, so the search always contains the edge-aligned and centred options.
            rawXs.add(left - view.boardLeft() - view.margin());
            rawXs.add(left + view.tilePx() - view.viewRight() + view.margin());
            rawXs.add(left + view.tilePx() / 2 - view.contentWidth() / 2);
            rawYs.add(top - view.topH() - view.margin());
            rawYs.add(top + view.tilePx() - view.viewBottom() + view.margin());
            rawYs.add(top + view.tilePx() / 2 - view.contentHeight() / 2);
        }

        // The anchor-local centroid, as an explicit candidate too, since it is the preference of last
        // resort and need not coincide with any cell-aligned framing above.
        double centroidX = Double.NaN;
        double centroidY = Double.NaN;
        if (anchor != null) {
            long sx = 0;
            long sy = 0;
            int n = 0;
            for (Cell c : safe) {
                if (Math.abs(c.x() - anchor.x()) <= ANCHOR_RADIUS && Math.abs(c.y() - anchor.y()) <= ANCHOR_RADIUS) {
                    sx += c.x() * stridePx + view.tilePx() / 2;
                    sy += c.y() * stridePx + view.tilePx() / 2;
                    n++;
                }
            }
            if (n > 0) {
                centroidX = sx / (double) n;
                centroidY = sy / (double) n;
                rawXs.add((int) Math.round(centroidX) - view.boardLeft() - view.contentWidth() / 2);
                rawYs.add((int) Math.round(centroidY) - view.topH() - view.contentHeight() / 2);
            }
        }

        // Clamp the search to the content, then project the cell-aligned candidates into that window rather
        // than discarding them, so the framings that survive are the ones the anchor rule would have picked.
        int[] xRange = clampRange(bboxLeft, bboxRight, view.contentWidth(), view.inset());
        // The y axis carries the chrome bar's offset and the x axis does not: a tile is placed at
        // gy*stridePx - camSy but the viewport starts at topH, so the content-space window is
        // [camSy + topH, camSy + topH + contentHeight] while in x it is [camSx, camSx + contentWidth].
        // Omitting topH here pinned the content's top row UNDER the bar: the live 1.1.173 run clamped
        // camSy to bboxTop - inset and whole-visible tiles fell from 8 to 4 on Sustenance.
        int[] yRange = clampRange(bboxTop - view.topH(), bboxBottom - view.topH(),
                view.contentHeight(), view.inset());
        TreeSet<Integer> xs = project(rawXs, xRange);
        TreeSet<Integer> ys = project(rawYs, yRange);

        boolean countMax = target == Target.COUNT || anchor == null;

        // Anchor-first, then the fallbacks, each weaker than the last and each labelled honestly:
        // in the margin band; on screen even if flush; and only then unconstrained. COUNT skips straight to
        // the unconstrained pass - that is the whole point of it, and the first build got this wrong.
        // The FLOOR is applied to whichever framing won, and only for the anchor target: COUNT exists to be
        // the anchor-free comparison, so flooring it would corrupt the very comparison it is for.
        if (!countMax) {
            Choice inBand = best(safe, xs, ys, anchor, centroidX, centroidY, view, countMax, AnchorFit.IN_BAND);
            if (inBand != null) {
                return withFloor(inBand, safe, anchor, view);
            }
            Choice onScreen = best(safe, xs, ys, anchor, centroidX, centroidY, view, countMax, AnchorFit.ON_SCREEN);
            if (onScreen != null) {
                return withFloor(onScreen, safe, anchor, view);
            }
        }
        Choice free = best(safe, xs, ys, anchor, centroidX, centroidY, view, countMax, AnchorFit.NONE);
        if (free != null) {
            return countMax ? free : withFloor(free, safe, anchor, view);
        }
        Choice empty = new Choice(0, 0, visibleCount(safe, 0, 0, view), total,
                anchor == null ? -1 : anchor.x(), anchor == null ? -1 : anchor.y(), false, "empty", null);
        return countMax ? empty : withFloor(empty, safe, anchor, view);
    }

    /**
     * The minimum-shift floor: move the selected camera by the least amount that puts the anchor WHOLE
     * INSIDE the clamp's gutter, then report what the move cost. The search is not re-run and its tiers are
     * not touched, so a framing that already carries that gutter does not move at all - which is why the
     * camSx = 51 frames Troi passed, the clamp's own flush state, cannot change even in principle.
     *
     * <p>The target is WHOLE, not half (Troi's closing ruling, 2026-09-17): the half state slices the caption
     * band, and her standing clause is "never clip the anchor". The shift is therefore the least movement
     * that reaches the whole-anchor range, and because that constraint is separable per axis, clamping each
     * axis into its own range gives the minimum, not merely a movement that works. Her F6x refinement
     * (item 7 of the 1.1.176 verdict) then moved that range from the flush bound to stride - inset: at the
     * flush bound the anchor is whole but sits 0-2 px from the viewport edge where the clamp's own state
     * leaves the {@code inset()} px the inset exists for, so one build showed two different anchor offsets
     * depending only on how far the shift had travelled. The target is now the whole anchor with that
     * gutter on every side -- stride - inset on the x axis of the live class -- so every window class
     * shares one gutter, and the price is MEASURED in the F6x record rather than estimated.
     *
     * <p>A half-target fallback survives underneath for a camera where whole is unreachable -- which Worf's
     * F8 says cannot happen on a viewport wide enough to carry the gutter: the anchor lies inside the
     * chapter's bbox, so the whole-anchor range and the clamp range always intersect (his F8, re-asserted on
     * the gutter bounded form). A viewport narrower than a tile plus two gutters carries no gutter target at
     * all; there the pre-F6x flush bound is used, and only if even that failed does the fallback carry on.
     * The fallback takes the smallest of {half on x / whole on y, whole on x / half on y, balanced at
     * ceil(tilePx / sqrt(2)) on both} that satisfies the AREA
     * predicate (his F1) -- per-axis half is NOT sufficient when both axes clip, because a quarter of a tile
     * on each axis is a quarter of the AREA. If even the fallback failed, the state would read "partial" and
     * the fit log line would say so, rather than quietly reporting success on a sliver.
     */
    static Choice withFloor(Choice c, List<Cell> cells, Cell anchor, View view) {
        if (c == null || anchor == null) {
            return c;
        }
        int t = view.tilePx();
        int g = view.inset();
        int area0 = visibleArea(anchor, c.camSx(), c.camSy(), view);
        // Troi's closing ruling, 2026-09-17: the anchor must be WHOLE, not half. The half state slices the
        // caption band and her standing clause is "never clip the anchor" -- so a merely-half framing is
        // shifted like any other rather than accepted.
        if (wholeInGutter(anchor, c.camSx(), c.camSy(), view)) {
            return new Choice(c.camSx(), c.camSy(), c.visible(), c.total(), c.anchorX(), c.anchorY(),
                    c.anchored(), c.rule(), new Floor("whole", 0, 0, c.visible(), area0));
        }
        // Pass 1 is the ruled target: the LEAST shift that puts the anchor whole inside the gutter, the
        // F6x form of the constraint. It is separable per axis, so clamping each axis into its own range
        // gives the minimum total movement that reaches it, not merely a movement that does. Pass 1b is
        // the pre-F6x flush bound, reachable only where the viewport is narrower than a tile plus two
        // gutters; pass 2 is the old half-target, kept only as a net for a camera where whole is
        // unreachable -- which Worf's F8 says cannot happen, since the anchor lies inside the bbox and
        // the whole-anchor range and the clamp range therefore always intersect.
        int[] whole = moveTo(c.camSx(), c.camSy(), anchor, t + g, t + g, view);
        boolean wholeOk = whole != null && wholeInGutter(anchor, whole[0], whole[1], view);
        int[] best = whole;
        if (!wholeOk) {
            int[] flush = moveTo(c.camSx(), c.camSy(), anchor, t, t, view);
            wholeOk = flush != null && fullyVisible(anchor, flush[0], flush[1], view);
            best = wholeOk ? flush : halfFallback(c, anchor, view);
        }
        if (best == null) {
            // Even a whole-anchor camera failed, which F8 says cannot happen. Say so loudly and leave the
            // chosen camera alone rather than reporting a floor that did not happen.
            return new Choice(c.camSx(), c.camSy(), c.visible(), c.total(), c.anchorX(), c.anchorY(),
                    c.anchored(), c.rule(), new Floor("partial", 0, 0, c.visible(), area0));
        }
        String state = wholeOk ? "whole" : "half";
        // The cost is COUNTED on the camera actually used, never adjusted from the old number: the floor
        // changes which tiles are whole, and this value is what the fit log reports as the price.
        int visible = visibleCount(cells, best[0], best[1], view);
        return new Choice(best[0], best[1], visible, c.total(), c.anchorX(), c.anchorY(), c.anchored(),
                c.rule(), new Floor(state, best[0] - c.camSx(), best[1] - c.camSy(), c.visible(),
                        visibleArea(anchor, best[0], best[1], view)));
    }

    /**
     * The camera moved by the least amount that keeps at least {@code needX} / {@code needY} px of the
     * anchor-inside-the-viewport span: {@code tilePx} keeps the cell whole flush, and {@code tilePx + inset}
     * keeps it whole with the clamp's gutter still there, which is the F6x target.
     */
    private static int[] moveTo(int camSx, int camSy, Cell anchor, int needX, int needY, View view) {
        int[] xr = axisRange(anchor.x(), needX, view, true);
        int[] yr = axisRange(anchor.y(), needY, view, false);
        return new int[] {clampInt(camSx, xr[0], xr[1]), clampInt(camSy, yr[0], yr[1])};
    }

    /** The half-target fallback: the least shift that leaves the anchor at least half visible, or null. */
    private static int[] halfFallback(Choice c, Cell anchor, View view) {
        int t = view.tilePx();
        int balanced = (int) Math.ceil(Math.sqrt(t * (double) t / 2.0));
        int[][] candidates = { {t / 2, t}, {t, t / 2}, {balanced, balanced} };
        int[] best = null;
        int bestShift = Integer.MAX_VALUE;
        for (int[] need : candidates) {
            int[] at = moveTo(c.camSx(), c.camSy(), anchor, need[0], need[1], view);
            if (!anchorHalfVisible(anchor, at[0], at[1], view)) {
                continue;
            }
            int shift = Math.abs(at[0] - c.camSx()) + Math.abs(at[1] - c.camSy());
            if (shift < bestShift) {
                bestShift = shift;
                best = at;
            }
        }
        return best;
    }

    /**
     * The camera values on one axis that keep at least {@code required} pixels of the cell-inside-the-
     * viewport span, as a closed interval [lo, hi]: {@code tilePx} bounds the whole cell and
     * {@code tilePx + inset} the whole cell with the clamp's gutter still there. Derived from the renderer's
     * own transform - in x the cell is placed at
     * {@code boardLeft + gx*stridePx - camSx}, in y at {@code gy*stridePx - camSy} with the viewport
     * starting at {@code topH}, the offset whose omission pinned the top row under the chrome bar in
     * 1.1.173.
     */
    public static int[] axisRange(int coord, int required, View view, boolean xAxis) {
        int pos = coord * view.stridePx();
        int lo;
        int hi;
        if (xAxis) {
            lo = view.boardLeft() + pos - view.viewRight() + required;
            hi = pos + view.tilePx() - required;
        } else {
            lo = pos - view.viewBottom() + required;
            hi = pos + view.tilePx() - required - view.topH();
        }
        return new int[] {Math.min(lo, hi), Math.max(lo, hi)};
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    /**
     * The range of camSx (or camSy) that keeps the visible window within the content, overhanging by at most
     * {@code inset} pixels a side; or null when the content is so much smaller than the viewport span that
     * no such range exists and the overhang is unavoidable, in which case the search is left unclamped so
     * the anchor rule can place the camera instead of a clamp that cannot help.
     *
     * <p>Public because Worf's F8 requires the floor to re-check itself against the clamp: the test asserts
     * that the whole-anchor TARGET range (the whole anchor inside the gutter, the F6x form) and this range
     * intersect, which is his proof that the floor can never be forced outside the clamp and the clamp can
     * never undo the floor.
     */
    public static int[] clampRange(int lo, int hi, int span, int inset) {
        if (hi - lo >= span - 2 * inset) {
            return new int[] {lo - inset, hi + inset - span};
        }
        return null;
    }

    /** Projects every candidate into the clamped range, keeping the range's own endpoints reachable. */
    private static TreeSet<Integer> project(TreeSet<Integer> candidates, int[] range) {
        if (range == null) {
            return candidates;
        }
        TreeSet<Integer> out = new TreeSet<>();
        for (int v : candidates) {
            out.add(Math.max(range[0], Math.min(range[1], v)));
        }
        out.add(range[0]);
        out.add(range[1]);
        return out;
    }

    private static Choice best(List<Cell> cells, TreeSet<Integer> xs, TreeSet<Integer> ys, Cell anchor,
            double centroidX, double centroidY, View view, boolean countMax, AnchorFit require) {
        Choice chosen = null;
        int bestVisible = -1;
        double bestDistance = Double.MAX_VALUE;
        int bestSx = 0;
        int bestSy = 0;
        for (int camSx : xs) {
            for (int camSy : ys) {
                AnchorFit fit = anchor == null ? AnchorFit.NONE : anchorFit(anchor, camSx, camSy, view);
                if (require == AnchorFit.IN_BAND && fit != AnchorFit.IN_BAND) {
                    continue;
                }
                if (require == AnchorFit.ON_SCREEN && fit == AnchorFit.NONE) {
                    continue;
                }
                boolean anchored = fit == AnchorFit.IN_BAND;
                int visible = visibleCount(cells, camSx, camSy, view);
                // Distance of the viewport centre to the anchor-local centroid; ties fall through to the
                // lowest camSx then lowest camSy, which is what makes the result order-independent.
                double distance = Double.isNaN(centroidX) ? 0.0
                        : Math.abs((view.boardLeft() + view.contentWidth() / 2.0) - (centroidX - camSx))
                                + Math.abs((view.topH() + view.contentHeight() / 2.0) - (centroidY - camSy));
                boolean better = visible > bestVisible
                        || (visible == bestVisible && distance < bestDistance - 1e-9)
                        || (visible == bestVisible && Math.abs(distance - bestDistance) <= 1e-9
                                && (camSx < bestSx || (camSx == bestSx && camSy < bestSy)));
                if (chosen == null || better) {
                    chosen = new Choice(camSx, camSy, visible, cells.size(),
                            anchor == null ? -1 : anchor.x(), anchor == null ? -1 : anchor.y(), anchored,
                            countMax ? "count" : rule(fit), null); // the floor is applied by withFloor
                    bestVisible = visible;
                    bestDistance = distance;
                    bestSx = camSx;
                    bestSy = camSy;
                }
            }
        }
        return chosen;
    }

    private static String rule(AnchorFit fit) {
        switch (fit) {
            case IN_BAND:
                return "anchor";
            case ON_SCREEN:
                return "anchor-edge";
            default:
                return "anchor-offscreen";
        }
    }

    private static AnchorFit anchorFit(Cell anchor, int camSx, int camSy, View view) {
        if (anchorInBand(anchor, camSx, camSy, view)) {
            return AnchorFit.IN_BAND;
        }
        return partlyVisible(anchor, camSx, camSy, view) ? AnchorFit.ON_SCREEN : AnchorFit.NONE;
    }

    /** True when the anchor cell is wholly inside the viewport, inset by the margin band. */
    public static boolean anchorInBand(Cell anchor, int camSx, int camSy, View view) {
        int sx = view.boardLeft() + anchor.x() * view.stridePx() - camSx;
        int sy = anchor.y() * view.stridePx() - camSy;
        return sx >= view.boardLeft() + view.margin()
                && sx + view.tilePx() <= view.viewRight() - view.margin()
                && sy >= view.topH() + view.margin()
                && sy + view.tilePx() <= view.viewBottom() - view.margin();
    }

    /**
     * True when the cell is wholly inside the board viewport. This is the renderer's own placement and the
     * hit test's own mapping: {@code boardLeft + gx*stridePx - camSx} with size {@code tilePx}.
     */
    public static boolean fullyVisible(Cell c, int camSx, int camSy, View view) {
        int sx = view.boardLeft() + c.x() * view.stridePx() - camSx;
        int sy = c.y() * view.stridePx() - camSy;
        return sx >= view.boardLeft() && sx + view.tilePx() <= view.viewRight()
                && sy >= view.topH() && sy + view.tilePx() <= view.viewBottom();
    }

    /**
     * True when the cell is wholly inside the viewport with the clamp's own {@link View#inset()} as a
     * gutter on every side: the state the floor TARGETS since Troi's F6x refinement (item 7 of her 1.1.176
     * verdict). At the flush bound (camSx = anchorX*stridePx) the anchor is whole but its left border lands
     * on the viewport edge with a 0-2 px gutter, where the clamp's own state leaves the 6 px the inset
     * exists for -- so the same build showed two anchor offsets depending only on how far the minimum
     * shift had travelled. For an anchor at the content's corner this predicate is exactly the clamp's own
     * flush state, which is what makes one gutter appear at every window class.
     */
    public static boolean wholeInGutter(Cell c, int camSx, int camSy, View view) {
        int g = view.inset();
        int sx = view.boardLeft() + c.x() * view.stridePx() - camSx;
        int sy = c.y() * view.stridePx() - camSy;
        return sx >= view.boardLeft() + g && sx + view.tilePx() <= view.viewRight() - g
                && sy >= view.topH() + g && sy + view.tilePx() <= view.viewBottom() - g;
    }

    /** True when any part of the cell is inside the board viewport. */
    public static boolean partlyVisible(Cell c, int camSx, int camSy, View view) {
        int sx = view.boardLeft() + c.x() * view.stridePx() - camSx;
        int sy = c.y() * view.stridePx() - camSy;
        return sx < view.viewRight() && sx + view.tilePx() > view.boardLeft()
                && sy < view.viewBottom() && sy + view.tilePx() > view.topH();
    }

    /** How many cells are wholly inside the viewport under this camera. Used for scoring. */
    public static int visibleCount(List<Cell> cells, int camSx, int camSy, View view) {
        int n = 0;
        for (Cell c : cells) {
            if (fullyVisible(c, camSx, camSy, view)) {
                n++;
            }
        }
        return n;
    }

    /** How many cells have any visible area under this camera. This is what the player perceives. */
    public static int inViewCount(List<Cell> cells, int camSx, int camSy, View view) {
        int n = 0;
        for (Cell c : cells) {
            if (partlyVisible(c, camSx, camSy, view)) {
                n++;
            }
        }
        return n;
    }
}
