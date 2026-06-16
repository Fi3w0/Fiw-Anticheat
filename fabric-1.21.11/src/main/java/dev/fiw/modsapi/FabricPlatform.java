package dev.fiw.modsapi;

import dev.fiw.modsapi.core.Platform;
import net.fabricmc.loader.api.FabricLoader;
import org.slf4j.Logger;

import java.nio.file.Path;

/** Fabric implementation of the core {@link Platform} surface. */
public final class FabricPlatform implements Platform {

    private final Logger logger;

    public FabricPlatform(Logger logger) {
        this.logger = logger;
    }

    @Override
    public Path configDir() {
        return FabricLoader.getInstance().getConfigDir().resolve(FiwModsApi.MOD_ID);
    }

    @Override
    public void logInfo(String message) {
        logger.info("[FiwAntiCheat] {}", message);
    }

    @Override
    public void logWarn(String message) {
        logger.warn("[FiwAntiCheat] {}", message);
    }

    @Override
    public void logError(String message, Throwable error) {
        logger.error("[FiwAntiCheat] {}", message, error);
    }
}
