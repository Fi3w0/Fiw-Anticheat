package dev.fiw.modsapi.core.io;

import dev.fiw.modsapi.core.config.ModConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class AtomicFilesTest {

    @Test
    void failedWritePreservesPreviousFileAndRemovesTemporaryFile(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.json");
        Files.writeString(file, "previous complete config");
        assertThrows(IOException.class, () -> AtomicFiles.write(file, writer -> {
            writer.write("partial replacement");
            throw new IOException("simulated write failure");
        }));
        assertEquals("previous complete config", Files.readString(file));
        try (var files = Files.list(dir)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void savesWhenStorageCannotMoveAtomically(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("config.json");
        Files.writeString(file, "old");
        AtomicFiles.write(file, writer -> writer.write("new"), (from, to) -> {
            throw new java.nio.file.AtomicMoveNotSupportedException(from.toString(), to.toString(), "test");
        });
        assertEquals("new", Files.readString(file));
        try (var files = Files.list(dir)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void savingAgainKeepsGrantsAndReplacesTheCompleteConfig(@TempDir Path dir) throws IOException {
        ModConfig config = ModConfig.load(dir);
        config.exemptions.player_overrides.put("steve", new ModConfig.PlayerOverride("preset", "strict", 1000L, "test"));
        config.save(dir);
        config.timeout_seconds = 20;
        config.save(dir);
        ModConfig reloaded = ModConfig.load(dir);
        assertEquals(20, reloaded.timeout_seconds);
        assertEquals("strict", reloaded.exemptions.player_overrides.get("steve").preset);
        try (var files = Files.list(dir)) {
            assertEquals(1, files.count());
        }
    }
}
