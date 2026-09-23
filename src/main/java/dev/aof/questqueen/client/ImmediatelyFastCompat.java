package dev.aof.questqueen.client;

/**
 * Optional: pause ImmediatelyFast HUD batching around atlas blits.
 * Missing/old IF is a no-op.
 */
public final class ImmediatelyFastCompat {
    private ImmediatelyFastCompat() {
    }

    public static void around(Runnable draw) {
        Object batching = null;
        boolean resume = false;
        try {
            Class<?> api = Class.forName("net.raphimc.immediatelyfastapi.ImmediatelyFastApi");
            Object impl = api.getMethod("getApiImpl").invoke(null);
            batching = impl.getClass().getMethod("getBatching").invoke(impl);
            Boolean hud = (Boolean) batching.getClass().getMethod("isHudBatching").invoke(batching);
            if (Boolean.TRUE.equals(hud)) {
                batching.getClass().getMethod("endHudBatching").invoke(batching);
                resume = true;
            }
        } catch (Throwable ignored) {
            batching = null;
        }
        try {
            draw.run();
        } finally {
            if (resume && batching != null) {
                try {
                    batching.getClass().getMethod("beginHudBatching").invoke(batching);
                } catch (Throwable ignored) {
                }
            }
        }
    }
}
