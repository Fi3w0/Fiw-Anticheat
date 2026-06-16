package dev.fiw.modsapi.core.signature;

import com.google.gson.Gson;
import dev.fiw.modsapi.core.model.ModEntry;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

/**
 * The bundled known-bad-mod database, loaded from {@code /signatures.json} on the
 * classpath. Pure data — adding mods or categories is a resource edit, no code change.
 */
public final class SignatureDatabase {

    private static final Gson GSON = new Gson();

    private final List<Signature> signatures;

    public SignatureDatabase(List<Signature> signatures) {
        this.signatures = signatures == null ? List.of() : List.copyOf(signatures);
    }

    /** Load the database bundled inside the mod jar. Never throws — falls back to empty. */
    public static SignatureDatabase loadBundled() {
        try (InputStream in = SignatureDatabase.class.getResourceAsStream("/signatures.json")) {
            if (in == null) return new SignatureDatabase(List.of());
            return fromReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        } catch (Exception e) {
            return new SignatureDatabase(List.of());
        }
    }

    public static SignatureDatabase fromReader(Reader reader) {
        Signature[] arr = GSON.fromJson(reader, Signature[].class);
        return new SignatureDatabase(arr == null ? List.of() : List.of(arr));
    }

    /** First signature that matches the given mod, if any. */
    public Optional<Signature> firstMatch(ModEntry mod) {
        for (Signature s : signatures) {
            if (s.matches(mod)) return Optional.of(s);
        }
        return Optional.empty();
    }

    public int size() {
        return signatures.size();
    }
}
