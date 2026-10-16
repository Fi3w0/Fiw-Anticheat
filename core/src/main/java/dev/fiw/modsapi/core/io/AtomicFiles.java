package dev.fiw.modsapi.core.io;

import java.io.IOException;
import java.io.Writer;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** Never replaces the live file until a complete temporary file has been closed. */
public final class AtomicFiles {

    private AtomicFiles() {}

    @FunctionalInterface
    public interface WriteAction {
        void write(Writer writer) throws IOException;
    }

    @FunctionalInterface
    interface Mover {
        void move(Path from, Path to) throws IOException;
    }

    public static void write(Path file, WriteAction action) throws IOException {
        write(file, action, (from, to) ->
                Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING));
    }

    static void write(Path file, WriteAction action, Mover atomicMove) throws IOException {
        Path target = file.toAbsolutePath();
        Files.createDirectories(target.getParent());
        Path temp = Files.createTempFile(target.getParent(), target.getFileName() + "-", ".tmp");
        try {
            try (Writer writer = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                action.write(writer);
            }
            try {
                atomicMove.move(temp, target);
            } catch (AtomicMoveNotSupportedException e) {
                // Some network or panel-hosted storage cannot rename atomically; still save.
                Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
