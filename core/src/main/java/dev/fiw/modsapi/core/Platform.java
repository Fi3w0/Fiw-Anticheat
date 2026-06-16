package dev.fiw.modsapi.core;

import java.nio.file.Path;

/**
 * The small surface the loader adapters provide to the core engine: where config
 * lives and how to log. All Minecraft-specific actions (kick, teleport, op
 * broadcast) stay in the adapter and are driven by the engine's return values.
 */
public interface Platform {

    /** Directory for this mod's config, e.g. {@code config/fiw-mods-api}. */
    Path configDir();

    void logInfo(String message);

    void logWarn(String message);

    void logError(String message, Throwable error);
}
