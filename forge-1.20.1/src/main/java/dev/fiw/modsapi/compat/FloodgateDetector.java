package dev.fiw.modsapi.compat;

import java.lang.reflect.Method;
import java.util.UUID;

/** Reflection bridge to Floodgate. If Floodgate is absent this is a no-op. */
public final class FloodgateDetector {

    private static final boolean AVAILABLE;
    private static Object apiInstance;
    private static Method isFloodgatePlayer;

    static {
        boolean ok = false;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Method getInstance = apiClass.getMethod("getInstance");
            apiInstance = getInstance.invoke(null);
            isFloodgatePlayer = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            ok = apiInstance != null;
        } catch (Throwable ignored) {
        }
        AVAILABLE = ok;
    }

    private FloodgateDetector() {}

    public static boolean isBedrockPlayer(UUID uuid) {
        if (!AVAILABLE) return false;
        try {
            Object result = isFloodgatePlayer.invoke(apiInstance, uuid);
            return result instanceof Boolean b && b;
        } catch (Throwable t) {
            return false;
        }
    }
}
