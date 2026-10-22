package dev.fiw.modsapi.core;

import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.model.ModMarkers;
import dev.fiw.modsapi.core.signature.Signature;

import java.util.List;
import java.util.Locale;
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
        assertEquals("cheat_clients", match.get().signature().category);
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

    @Test
    void packagePrefixMatchesOnlyAnExactPackageOrDescendant() {
        Signature signature = new Signature();
        signature.match.packages = List.of("com.example.cheat");
        assertTrue(signature.matches(withMarkers("com.example.cheat", null)));
        assertTrue(signature.matches(withMarkers("com.example.cheat.client", null)));
        assertTrue(signature.matches(withMarkers("COM.EXAMPLE.CHEAT.Client", null)));
        assertFalse(signature.matches(withMarkers("com.example.cheater", null)));
        assertFalse(signature.matches(withMarkers("com.example.cheat_tools", null)));
        assertFalse(signature.matches(withMarkers("com.example", null)));
        assertFalse(signature.matches(withMarkers("other.com.example.cheat", null)));
    }

    @Test
    void entrypointPrefixAlsoRespectsTheDotBoundary() {
        Signature signature = new Signature();
        signature.match.entrypoints = List.of("com.example.cheat");
        assertTrue(signature.matches(withMarkers(null, "com.example.cheat.Main")));
        assertTrue(signature.matches(withMarkers(null, "com.example.cheat")));
        assertFalse(signature.matches(withMarkers(null, "com.example.cheater.Main")));
        signature.match.entrypoints = List.of("");
        assertFalse(signature.matches(withMarkers(null, "anything.Main")));
    }

    @Test
    void prefixCaseFoldingIsIndependentOfServerLocale() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            Signature signature = new Signature();
            signature.match.packages = List.of("COM.IDENTITY");
            assertTrue(signature.matches(withMarkers("com.identity.client", null)));
            assertFalse(signature.matches(withMarkers("com.identityextra", null)));
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void hitReportsMatchTypeAndReportedValue() {
        Signature signature = SignatureDatabase.fromReader(new java.io.StringReader("""
                [{"name":"Cheat","category":"cheat_clients",
                  "match":{"ids":["cheat"],"packages":["com.example.cheat"],"entrypoints":["org.cheat"]}}]
                """)).firstMatch(new ModEntry("cheat", "1")).orElseThrow().signature();

        assertEquals(new Signature.Hit("id", "CHEAT"), signature.findHit(new ModEntry("CHEAT", "1")));
        assertEquals(new Signature.Hit("package", "com.example.cheat.render"),
                signature.findHit(withMarkers("com.example.cheat.render", null)));
        assertEquals(new Signature.Hit("entrypoint", "org.cheat.Main"),
                signature.findHit(withMarkers(null, "org.cheat.Main")));
        assertNull(signature.findHit(withMarkers("com.example.cheater", null)));
    }

    private static ModEntry withMarkers(String pkg, String entrypoint) {
        return new ModEntry("renamed", "1", "", new ModMarkers(List.of(),
                entrypoint == null ? List.of() : List.of(entrypoint), pkg == null ? List.of() : List.of(pkg)));
    }

    private static void assertCategory(SignatureDatabase db, String id, String category) {
        var match = db.firstMatch(new ModEntry(id, "1.0"));
        assertTrue(match.isPresent(), id + " should match bundled signatures");
        assertEquals(category, match.get().signature().category, id);
    }
}
