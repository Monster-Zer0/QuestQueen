package dev.aof.questqueen.client;

/**
 * A progress bar's drawn fraction. A new target eases in from wherever the bar is drawn now, instead of jumping, so
 * a count going up reads as movement. The first target lands at once: a bar that is just appearing has nowhere to
 * come from.
 */
final class BarTween {
    static final long DURATION_MS = 400L;

    private float from;
    private float to;
    private long startMs = Long.MIN_VALUE;
    private boolean set;

    /** Point the bar at {@code target} (clamped to 0..1); a change starts a new ease from the current value. */
    void retarget(float target, long nowMs) {
        float clamped = UiFx.clamp01(target);
        if (!set) {
            from = clamped;
            to = clamped;
            set = true;
            return;
        }
        if (Math.abs(clamped - to) < 0.0005f) {
            return;
        }
        from = value(nowMs);
        to = clamped;
        startMs = nowMs;
    }

    /** Fraction to draw at {@code nowMs}. */
    float value(long nowMs) {
        return UiFx.lerp(from, to, UiFx.easeOut(t(nowMs)));
    }

    /** True while easing, which is when the bright leading edge shows. */
    boolean moving(long nowMs) {
        return set && startMs != Long.MIN_VALUE && t(nowMs) < 1f;
    }

    /** Leading-edge alpha: bright as the bar starts moving, gone when it lands. */
    float edgeAlpha(long nowMs) {
        return moving(nowMs) ? 1f - t(nowMs) : 0f;
    }

    private float t(long nowMs) {
        if (startMs == Long.MIN_VALUE || !UiFx.enabled()) {
            return 1f;
        }
        return UiFx.clamp01((nowMs - startMs) / (float) DURATION_MS);
    }
}
