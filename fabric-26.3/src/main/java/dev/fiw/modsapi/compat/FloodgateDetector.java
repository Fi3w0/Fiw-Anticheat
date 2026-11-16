package dev.fiw.modsapi.compat;

import java.lang.reflect.Method;
import java.util.UUID;

/**
 * Soft, reflection-based bridge to Floodgate (Geyser/Bedrock). Bedrock players
 * cannot run Java client mods and never answer the verification handshake, so
 * they are exempted. If Floodgate is not installed this is a harmless no-op.
 */
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
            // Floodgate not present — fine.
        }
        AVAILABLE = ok;
    }

    private FloodgateDetector() {}

    public static boolean isAvailable() {
        return AVAILABLE;
    }

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
