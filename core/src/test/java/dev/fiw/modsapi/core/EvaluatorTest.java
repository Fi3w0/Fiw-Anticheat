package dev.fiw.modsapi.core;

import dev.fiw.modsapi.core.config.ModConfig;
import dev.fiw.modsapi.core.exemption.ExemptionResolution;
import dev.fiw.modsapi.core.exemption.ExemptionTier;
import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.model.ModMarkers;
import dev.fiw.modsapi.core.model.ResourcePackEntry;
import dev.fiw.modsapi.core.signature.Signature;
import dev.fiw.modsapi.core.signature.SignatureDatabase;
import dev.fiw.modsapi.core.verify.Evaluator;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class EvaluatorTest {

    private static final ExemptionResolution NONE = ExemptionResolution.NONE;

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
        var result = Evaluator.evaluate(List.of(mod("sodium"), mod("freecam")), cfg, db(), NONE);
        assertTrue(result.kick());
        assertTrue(result.hasDetections());
        assertEquals("Freecam", result.detected().get(0).modName());
        assertFalse(result.suppressAlert());
    }

    @Test
    void monitorOnlyLogsButDoesNotKick() {
        ModConfig cfg = new ModConfig();
        cfg.detection.monitor_only = true;
        var result = Evaluator.evaluate(List.of(mod("freecam")), cfg, db(), NONE);
        assertFalse(result.kick());
        assertTrue(result.hasDetections());
        assertTrue(result.logSummary().contains("MONITOR"));
    }

    @Test
    void allowOverridesUnbans() {
        ModConfig cfg = new ModConfig();
        cfg.detection.allow_overrides = List.of("freecam");
        var result = Evaluator.evaluate(List.of(mod("freecam")), cfg, db(), NONE);
        assertFalse(result.kick());
        assertFalse(result.hasDetections());
    }

    @Test
    void balancedAllowsMinimapButStrictBlocksIt() {
        ModConfig balanced = new ModConfig();
        assertFalse(Evaluator.evaluate(List.of(mod("xaerominimap")), balanced, db(), NONE).kick());

        ModConfig strict = new ModConfig();
        strict.detection.preset = "strict";
        assertTrue(Evaluator.evaluate(List.of(mod("xaerominimap")), strict, db(), NONE).kick());
    }

    @Test
    void signatureMatchesByPackageWhenModIdRenamed() {
        // attacker renamed the mod id, but the package marker still trips
        ModEntry renamed = new ModEntry("totally-legit-mod", "1.0", "",
                new ModMarkers(List.of(), List.of(), List.of("net.xolt.freecam.client")));
        var result = Evaluator.evaluate(List.of(renamed), new ModConfig(), db(), NONE);
        assertTrue(result.kick());
        assertEquals("Freecam", result.detected().get(0).modName());
    }

    @Test
    void bypassTierSkipsScanEntirely() {
        ModConfig cfg = new ModConfig();
        var result = Evaluator.evaluate(List.of(mod("freecam")), cfg, db(), ExemptionResolution.of(ExemptionTier.BYPASS));
        assertFalse(result.kick());
        assertFalse(result.hasDetections());
        assertTrue(result.suppressAlert());
    }

    @Test
    void forceBlockTierAlwaysKicksWithoutScanning() {
        ModConfig cfg = new ModConfig();
        var result = Evaluator.evaluate(List.of(mod("sodium")), cfg, db(), ExemptionResolution.of(ExemptionTier.FORCE_BLOCK));
        assertTrue(result.kick());
        assertFalse(result.hasDetections()); // clean mod list, but still force-kicked
    }

    @Test
    void silentTierScansButNeverKicksOrAlerts() {
        ModConfig cfg = new ModConfig();
        var result = Evaluator.evaluate(List.of(mod("freecam")), cfg, db(), ExemptionResolution.of(ExemptionTier.SILENT));
        assertFalse(result.kick());
        assertTrue(result.hasDetections()); // still scanned/logged
        assertTrue(result.suppressAlert());
    }

    @Test
    void monitorTierScansAlertsButNeverKicks() {
        ModConfig cfg = new ModConfig();
        var result = Evaluator.evaluate(List.of(mod("freecam")), cfg, db(), ExemptionResolution.of(ExemptionTier.MONITOR));
        assertFalse(result.kick());
        assertTrue(result.hasDetections());
        assertFalse(result.suppressAlert());
    }

    @Test
    void quietKickTierKicksNormallyButSuppressesAlert() {
        ModConfig cfg = new ModConfig();
        var result = Evaluator.evaluate(List.of(mod("freecam")), cfg, db(), ExemptionResolution.of(ExemptionTier.QUIET_KICK));
        assertTrue(result.kick()); // real detection still kicks
        assertTrue(result.hasDetections());
        assertTrue(result.suppressAlert());
    }

    @Test
    void presetTierOverridesGlobalPreset() {
        ModConfig cfg = new ModConfig(); // global preset = balanced (allows minimap)
        var resolution = new ExemptionResolution(ExemptionTier.PRESET, "strict");
        var result = Evaluator.evaluate(List.of(mod("xaerominimap")), cfg, db(), resolution);
        assertTrue(result.kick(), "player-pinned strict preset should block minimap even though global preset is balanced");
        assertFalse(result.suppressAlert());
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
                List.of(mod("sodium"), mod("fabric-api"), mod("hackpack")), cfg, db(), NONE).kick());

        // missing required -> kick
        assertTrue(Evaluator.evaluate(List.of(mod("sodium")), cfg, db(), NONE).kick());

        // exact official set -> pass
        assertFalse(Evaluator.evaluate(List.of(mod("sodium"), mod("fabric-api")), cfg, db(), NONE).kick());
    }

    @Test
    void whitelistFingerprintMismatchKicks() {
        ModConfig cfg = new ModConfig();
        cfg.mode = "whitelist";
        cfg.whitelist.require_all = false;
        cfg.whitelist.official_mods = List.of(
                new ModConfig.OfficialMod("sodium", "0.5.3", "abc123"));

        ModEntry impostor = new ModEntry("sodium", "0.5.3", "deadbeef", ModMarkers.EMPTY);
        assertTrue(Evaluator.evaluate(List.of(impostor), cfg, db(), NONE).kick());

        ModEntry genuine = new ModEntry("sodium", "0.5.3", "abc123", ModMarkers.EMPTY);
        assertFalse(Evaluator.evaluate(List.of(genuine), cfg, db(), NONE).kick());
    }

    @Test
    void whitelistSetupModeAllowsEveryone() {
        ModConfig cfg = new ModConfig();
        cfg.mode = "whitelist"; // no official mods captured
        assertFalse(Evaluator.evaluate(List.of(mod("anything")), cfg, db(), NONE).kick());
    }

    @Test
    void resourcePackBanIsLogOnlyByDefault() {
        ModConfig cfg = new ModConfig();
        cfg.resource_packs.banned_packs = List.of("xray-pack.zip");

        var result = Evaluator.evaluate(List.of(mod("sodium")),
                List.of(new ResourcePackEntry("file/xray-pack.zip", "xray-pack.zip", "", true)),
                cfg, db(), NONE);

        assertFalse(result.kick());
        assertTrue(result.hasDetections());
        assertTrue(result.logSummary().contains("log only"));
    }

    @Test
    void resourcePackBanCanKickWhenEnabled() {
        ModConfig cfg = new ModConfig();
        cfg.resource_packs.kick_on_banned = true;
        cfg.resource_packs.banned_fingerprints = List.of("deadbeef");

        var result = Evaluator.evaluate(List.of(mod("sodium")),
                List.of(new ResourcePackEntry("file/renamed.zip", "renamed.zip", "deadbeef", false)),
                cfg, db(), NONE);

        assertTrue(result.kick());
        assertEquals("inactive_resource_pack", result.detected().get(0).category());
    }
}
