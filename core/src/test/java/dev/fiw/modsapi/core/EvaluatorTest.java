package dev.fiw.modsapi.core;

import dev.fiw.modsapi.core.config.ModConfig;
import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.model.ModMarkers;
import dev.fiw.modsapi.core.signature.Signature;
import dev.fiw.modsapi.core.signature.SignatureDatabase;
import dev.fiw.modsapi.core.verify.EvaluationResult;
import dev.fiw.modsapi.core.verify.Evaluator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EvaluatorTest {

    private SignatureDatabase db() {
        Signature freecam = sig("Freecam", "freecam", List.of("freecam"), List.of("net.xolt.freecam"));
        Signature minimap = sig("Xaero's Minimap", "minimap", List.of("xaerominimap"), List.of("xaero.hud"));
        return new SignatureDatabase(List.of(freecam, minimap));
    }

    private Signature sig(String name, String cat, List<String> ids, List<String> packages) {
        Signature s = new Signature();
        s.name = name;
        s.category = cat;
        s.match = new Signature.Match();
        s.match.ids = ids;
        s.match.packages = packages;
        return s;
    }

    private ModEntry mod(String id) {
        return new ModEntry(id, "1.0");
    }

    @Test
    void blacklistBalancedKicksFreecam() {
        ModConfig cfg = new ModConfig(); // blacklist + balanced default
        var result = Evaluator.evaluate(List.of(mod("sodium"), mod("freecam")), cfg, db(), false);
        assertTrue(result.kick());
        assertTrue(result.hasDetections());
        assertEquals("Freecam", result.detected().get(0).modName());
    }

    @Test
    void monitorOnlyLogsButDoesNotKick() {
        ModConfig cfg = new ModConfig();
        cfg.detection.monitor_only = true;
        var result = Evaluator.evaluate(List.of(mod("freecam")), cfg, db(), false);
        assertFalse(result.kick());
        assertTrue(result.hasDetections());
        assertTrue(result.logSummary().contains("MONITOR"));
    }

    @Test
    void allowOverridesUnbans() {
        ModConfig cfg = new ModConfig();
        cfg.detection.allow_overrides = List.of("freecam");
        var result = Evaluator.evaluate(List.of(mod("freecam")), cfg, db(), false);
        assertFalse(result.kick());
        assertFalse(result.hasDetections());
    }

    @Test
    void balancedAllowsMinimapButStrictBlocksIt() {
        ModConfig balanced = new ModConfig();
        assertFalse(Evaluator.evaluate(List.of(mod("xaerominimap")), balanced, db(), false).kick());

        ModConfig strict = new ModConfig();
        strict.detection.preset = "strict";
        assertTrue(Evaluator.evaluate(List.of(mod("xaerominimap")), strict, db(), false).kick());
    }

    @Test
    void signatureMatchesByPackageWhenModIdRenamed() {
        // attacker renamed the mod id, but the package marker still trips
        ModEntry renamed = new ModEntry("totally-legit-mod", "1.0", "",
                new ModMarkers(List.of(), List.of(), List.of("net.xolt.freecam.client")));
        var result = Evaluator.evaluate(List.of(renamed), new ModConfig(), db(), false);
        assertTrue(result.kick());
        assertEquals("Freecam", result.detected().get(0).modName());
    }

    @Test
    void exemptShortCircuitsToPass() {
        ModConfig cfg = new ModConfig();
        var result = Evaluator.evaluate(List.of(mod("freecam")), cfg, db(), true);
        assertFalse(result.kick());
        assertFalse(result.hasDetections());
    }

    @Test
    void whitelistKicksUnknownAndMissing() {
        ModConfig cfg = new ModConfig();
        cfg.mode = "whitelist";
        cfg.whitelist.require_all = true;
        cfg.whitelist.official_mods = List.of(
                new ModConfig.OfficialMod("sodium", "0.5.3", null),
                new ModConfig.OfficialMod("fabric-api", "0.86.0", null));

        // unknown extra mod -> kick
        assertTrue(Evaluator.evaluate(
                List.of(mod("sodium"), mod("fabric-api"), mod("hackpack")), cfg, db(), false).kick());

        // missing required -> kick
        assertTrue(Evaluator.evaluate(List.of(mod("sodium")), cfg, db(), false).kick());

        // exact official set -> pass
        assertFalse(Evaluator.evaluate(List.of(mod("sodium"), mod("fabric-api")), cfg, db(), false).kick());
    }

    @Test
    void whitelistFingerprintMismatchKicks() {
        ModConfig cfg = new ModConfig();
        cfg.mode = "whitelist";
        cfg.whitelist.require_all = false;
        cfg.whitelist.official_mods = List.of(
                new ModConfig.OfficialMod("sodium", "0.5.3", "abc123"));

        ModEntry impostor = new ModEntry("sodium", "0.5.3", "deadbeef", ModMarkers.EMPTY);
        assertTrue(Evaluator.evaluate(List.of(impostor), cfg, db(), false).kick());

        ModEntry genuine = new ModEntry("sodium", "0.5.3", "abc123", ModMarkers.EMPTY);
        assertFalse(Evaluator.evaluate(List.of(genuine), cfg, db(), false).kick());
    }

    @Test
    void whitelistSetupModeAllowsEveryone() {
        ModConfig cfg = new ModConfig();
        cfg.mode = "whitelist"; // no official mods captured
        assertFalse(Evaluator.evaluate(List.of(mod("anything")), cfg, db(), false).kick());
    }
}
