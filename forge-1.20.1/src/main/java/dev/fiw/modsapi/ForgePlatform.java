package dev.fiw.modsapi;

import dev.fiw.modsapi.core.Platform;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.nio.file.Path;

/** MinecraftForge implementation of the core {@link Platform} surface. */
public final class ForgePlatform implements Platform {

    private final Logger logger;

    public ForgePlatform(Logger logger) {
        this.logger = logger;
    }

    @Override
    public Path configDir() {
        return FMLPaths.CONFIGDIR.get().resolve("fiw-mods-api");
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
