package dev.aof.questqueen.client;

import net.minecraft.Util;

/**
 * Shared animation clock and easing helpers for the book's effects.
 *
 * <p>Every effect is built from two primitives only, because this renderer cannot rotate or scale:
 * <b>alpha</b> (via {@code setColor} around a 1:1 blit) and <b>geometry</b> (which pixels get plotted).
 * Nothing here allocates per frame.
 *
 * <p>Timing comes from {@link Util#getMillis()} rather than level time so the book still animates while
 * the game is paused or the level is unloading — the existing path-arrow pulse already worked this way.
 */
public final class UiFx {
    /** Master switch; set from config so a player can turn the whole layer off. */
    private static boolean enabled = true;

    private UiFx() {
    }

    public static void setEnabled(boolean value) {
        enabled = value;
    }

    public static boolean enabled() {
        return enabled;
    }

    /** Time source; tests pin it so two reads in one assertion cannot straddle a millisecond. */
    static java.util.function.LongSupplier clock = Util::getMillis;

    public static long nowMs() {
        return clock.getAsLong();
    }

    /** Milliseconds elapsed since {@code startMs}, floored at 0. */
    public static long elapsed(long startMs) {
        return Math.max(0L, nowMs() - startMs);
    }

    /**
     * Sampled phase of a loop in [0,1): 0 at the start of the period, 1 just before it repeats.
     * Returns 0 when effects are disabled so callers degrade to a static frame.
     */
    public static float phase(long periodMs, long offsetMs) {
        if (!enabled || periodMs <= 0L) {
            return 0f;
        }
        long t = nowMs() + offsetMs;
        long m = t % periodMs;
        if (m < 0L) {
            m += periodMs;
        }
        return m / (float) periodMs;
    }

    /** Sine wave in [0,1] over the period — the smooth "breathing" primitive. */
    public static float wave(long periodMs, long offsetMs) {
        return 0.5f + 0.5f * (float) Math.sin(phase(periodMs, offsetMs) * Math.PI * 2.0);
    }

    /** Progress of a one-shot in [0,1]; 1 means finished. Disabled effects read as already finished. */
    public static float progress(long startMs, long durationMs) {
        if (!enabled || durationMs <= 0L) {
            return 1f;
        }
        float p = elapsed(startMs) / (float) durationMs;
        return p > 1f ? 1f : p;
    }

    public static boolean finished(long startMs, long durationMs) {
        return progress(startMs, durationMs) >= 1f;
    }

    /** Ease-out cubic: fast start, gentle settle. Good for one-shots. */
    public static float easeOut(float t) {
        float inv = 1f - clamp01(t);
        return 1f - inv * inv * inv;
    }

    /** Ease-in-out: for slides and position interpolation. */
    public static float easeInOut(float t) {
        float c = clamp01(t);
        return c < 0.5f ? 4f * c * c * c : 1f - (float) Math.pow(-2f * c + 2f, 3f) / 2f;
    }

    public static float clamp01(float t) {
        return t < 0f ? 0f : (t > 1f ? 1f : t);
    }

    public static int lerpInt(int from, int to, float t) {
        return from + Math.round((to - from) * clamp01(t));
    }

    public static float lerp(float from, float to, float t) {
        return from + (to - from) * clamp01(t);
    }

    /** Replace a colour's alpha, keeping its hue — the only colour change allowed on locked/failed chrome. */
    public static int withAlpha(int argb, float alpha) {
        int a = Math.round(255f * clamp01(alpha));
        if (a <= 0) {
            return argb & 0x00FFFFFF;
        }
        return (a << 24) | (argb & 0x00FFFFFF);
    }

    /** Scale a colour's existing alpha by {@code factor}, keeping its hue. */
    public static int scaleAlpha(int argb, float factor) {
        int a = (argb >>> 24) & 0xFF;
        if (a == 0) {
            a = 255;
        }
        int scaled = Math.round(a * clamp01(factor));
        if (scaled <= 0) {
            return argb & 0x00FFFFFF;
        }
        return (scaled << 24) | (argb & 0x00FFFFFF);
    }

    /** Primary loop period for the travelling path-arrow highlight. */
    public static final long FLOW_PERIOD_MS = 1800L;
    /** How dim an arrow falls between highlight passes (never zero — the path must stay readable). */
    public static final float FLOW_MIN = 0.45f;

    /** Flow factor for a port anchored at (x,y) in board space; hue is never touched. */
    public static float flowAt(int x, int y) {
        int anchor = x * 73856093 ^ y * 19349663;
        return flow(anchor, FLOW_PERIOD_MS, FLOW_MIN);
    }

    /**
     * Deterministic pseudo-random in [0,1) for ambient scattering. Stable per (seed, index) so particles
     * do not flicker between frames, and cheap enough to call per particle per frame.
     */
    public static float hash01(int seed, int index) {
        int h = seed * 0x9E3779B9 ^ index * 0x85EBCA6B;
        h ^= h >>> 15;
        h *= 0x2545F491;
        h ^= h >>> 13;
        return (h & 0x7FFFFFFF) / (float) 0x7FFFFFFF;
    }

    /**
     * Travelling-highlight factor for a path arrow: a soft bright segment that sweeps along the link from
     * source to destination, so the eye reads direction instead of a uniform blink.
     *
     * <p>{@code anchor} seeds the phase so parallel links do not pulse in lockstep. Returns a brightness
     * multiplier in [min,1] applied to the arrow's alpha — hue is untouched, which is what keeps the locked
     * red authoritative.
     */
    public static float flow(int anchor, long periodMs, float min) {
        if (!enabled) {
            return 1f;
        }
        // Cheap integer mixer so neighbouring ports get clearly different phases.
        int h = anchor * 0x9E3779B9;
        h ^= h >>> 16;
        long offset = (h & 0xFFFFL) * 37L;
        float p = phase(periodMs, offset);
        float peak = 0.5f + 0.5f * (float) Math.cos((p * 2f - 1f) * Math.PI);
        return min + (1f - min) * peak * peak;
    }
}
