package dev.fiw.modsapi.core;

import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.signature.SignatureDatabase;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class SignatureDatabaseTest {

    @Test
    void bundledDatabaseParsesAndMatches() {
        SignatureDatabase db = SignatureDatabase.loadBundled();
        assertTrue(db.size() > 0, "bundled signatures.json should load entries");

        var match = db.firstMatch(new ModEntry("wurst", "7.0"));
        assertTrue(match.isPresent());
        assertEquals("cheat_clients", match.get().category);
    }

    @Test
    void bundledDatabaseMatchesCommonAliases() {
        SignatureDatabase db = SignatureDatabase.loadBundled();

        assertCategory(db, "liquidbounce", "cheat_clients");
        assertCategory(db, "aoba-client", "cheat_clients");
        assertCategory(db, "mod-detection-preventer", "cheat_clients");
        assertCategory(db, "advanced-xray", "xray");
        assertCategory(db, "seedcrackerx", "xray");
        assertCategory(db, "gamma-utils", "fullbright");
        assertCategory(db, "true-fullbright", "fullbright");
        assertCategory(db, "easy-freecam", "freecam");
        assertCategory(db, "click-crystals", "autoclicker");
        assertCategory(db, "schematic-printer", "schematic_printer");
    }

    private static void assertCategory(SignatureDatabase db, String id, String category) {
        var match = db.firstMatch(new ModEntry(id, "1.0"));
        assertTrue(match.isPresent(), id + " should match bundled signatures");
        assertEquals(category, match.get().category, id);
    }
}
