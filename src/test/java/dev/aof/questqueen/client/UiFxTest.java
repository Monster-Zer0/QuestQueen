package dev.aof.questqueen.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The animation layer is all arithmetic and colour masking, so it is testable without a game. These cover
 * the invariants the effects rely on — most importantly that motion NEVER changes a colour's hue, which is
 * what keeps the locked red / failed red authority intact (see AGENTS.md).
 */
class UiFxTest {
    @Test
    void easingStaysInRangeAndHitsItsEndpoints() {
        assertEquals(0f, UiFx.easeOut(0f), 0.0001f);
        assertEquals(1f, UiFx.easeOut(1f), 0.0001f);
        assertEquals(0f, UiFx.easeInOut(0f), 0.0001f);
        assertEquals(1f, UiFx.easeInOut(1f), 0.0001f);
        // Ease-out must lead a linear ramp; ease-in-out must be symmetric about its midpoint.
        assertTrue(UiFx.easeOut(0.25f) > 0.25f, "ease-out front-loads");
        assertEquals(0.5f, UiFx.easeInOut(0.5f), 0.0001f);
        assertEquals(1f - UiFx.easeInOut(0.25f), UiFx.easeInOut(0.75f), 0.0001f);
    }

    @Test
    void clampAndLerpCoverTheirEdges() {
        assertEquals(0f, UiFx.clamp01(-3f));
        assertEquals(1f, UiFx.clamp01(9f));
        assertEquals(0.5f, UiFx.clamp01(0.5f));
        assertEquals(10, UiFx.lerpInt(10, 20, 0f));
        assertEquals(20, UiFx.lerpInt(10, 20, 1f));
        assertEquals(15, UiFx.lerpInt(10, 20, 0.5f));
        // Out-of-range t must clamp rather than overshoot.
        assertEquals(10, UiFx.lerpInt(10, 20, -5f));
        assertEquals(20, UiFx.lerpInt(10, 20, 5f));
        assertEquals(0.25f, UiFx.lerp(0f, 1f, 0.25f), 0.0001f);
        assertEquals(1f, UiFx.lerp(0f, 1f, 4f), 0.0001f);
        assertEquals(0f, UiFx.lerp(0f, 1f, -2f), 0.0001f);
    }

    @Test
    void alphaHelpersKeepHueAndRespectBounds() {
        int lockedEdge = 0xFF8C1A24;
        assertEquals(0x808C1A24, UiFx.withAlpha(lockedEdge, 0.5f));
        assertEquals(0x008C1A24, UiFx.withAlpha(lockedEdge, 0f), "zero alpha keeps the rgb, adds nothing");
        assertEquals(0xFF8C1A24, UiFx.withAlpha(lockedEdge, 4f), "alpha clamps at full");

        // scaleAlpha on a fully-transparent colour treats it as opaque, matching how the UI carries colour.
        assertEquals(0xFF8C1A24, UiFx.scaleAlpha(0x008C1A24, 1f));
        assertEquals(0x808C1A24, UiFx.scaleAlpha(0xFF8C1A24, 0.5f));

        // The hard invariant: hue bits are never touched.
        for (float f = 0f; f <= 1f; f += 0.125f) {
            assertEquals(0x8C1A24, UiFx.withAlpha(lockedEdge, f) & 0x00FFFFFF, "locked red hue must not shift");
            assertEquals(0x8C1A24, UiFx.scaleAlpha(lockedEdge, f) & 0x00FFFFFF, "locked red hue must not shift");
        }
        int failed = 0xFFE01018;
        assertEquals(0xE01018, UiFx.withAlpha(failed, 0.3f) & 0x00FFFFFF);
    }

    @Test
    void hashIsDeterministicAndScattered() {
        for (int i = 0; i < 50; i++) {
            float v = UiFx.hash01(1234, i);
            assertEquals(v, UiFx.hash01(1234, i), 0f, "same input must give the same value");
            assertTrue(v >= 0f && v < 1f, "hash must land in [0,1)");
        }
        // Different indices should not collapse onto one value.
        assertNotEquals(UiFx.hash01(7, 1), UiFx.hash01(7, 2));
        // Different seeds should decorrelate the same index.
        assertNotEquals(UiFx.hash01(7, 3), UiFx.hash01(8, 3));
    }

    @Test
    void flowStaysWithinItsBrightnessBand() {
        for (int t = 0; t < 200; t++) {
            float f = UiFx.flow(t * 37, 1800L, 0.45f);
            assertTrue(f >= 0.45f - 0.0001f, "flow must not dip below its floor: " + f);
            assertTrue(f <= 1f + 0.0001f, "flow must not exceed full brightness: " + f);
        }
        // Different anchors must desynchronise — otherwise every arrow pulses together.
        long probe = 123456789L;
        assertNotEquals(UiFx.flow(1, 1800L, 0.45f), UiFx.flow(99991, 1800L, 0.45f));
    }

    @Test
    void ambientAnchorsAreStablePerPort() {
        // Same coordinates must give the same flow each frame, or arrows would flicker rather than travel.
        // Pin the clock: with the wall clock the two reads could land in different milliseconds.
        var saved = UiFx.clock;
        UiFx.clock = () -> 1_000_000L;
        try {
            assertEquals(UiFx.flowAt(120, 88), UiFx.flowAt(120, 88), 0f);
            assertNotEquals(UiFx.flowAt(120, 88), UiFx.flowAt(121, 88));
        } finally {
            UiFx.clock = saved;
        }
    }
}
