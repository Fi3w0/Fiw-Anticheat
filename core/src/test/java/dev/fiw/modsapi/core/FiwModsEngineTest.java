package dev.fiw.modsapi.core;

import dev.fiw.modsapi.core.config.ModConfig;
import dev.fiw.modsapi.core.exemption.ExemptionTier;
import dev.fiw.modsapi.core.verify.EvaluationResult;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class FiwModsEngineTest {

    private FiwModsEngine engine(Path configDir) {
        return new FiwModsEngine(new TestPlatform(configDir));
    }

    private EvaluationResult detectedResult() {
        return new EvaluationResult(true, "kicked", "blocked mods: Freecam [freecam]",
                List.of(new EvaluationResult.Detected("Freecam", "freecam", "freecam")), false);
    }

    @Test
    void playerOverridesTakePriorityOverLegacyBypassList(@TempDir Path dir) {
        FiwModsEngine engine = engine(dir);
        engine.config().exemptions.bypass_players.add("steve");
        engine.addExemption("steve", ExemptionTier.SILENT, null, null);

        assertEquals(ExemptionTier.SILENT, engine.resolveExemption("steve", null).tier());
    }

    @Test
    void legacyBypassListStillWorksWithNoOverride(@TempDir Path dir) {
        FiwModsEngine engine = engine(dir);
        engine.config().exemptions.bypass_players.add("steve");

        assertEquals(ExemptionTier.BYPASS, engine.resolveExemption("steve", null).tier());
    }

    @Test
    void expiredOverrideIsPurgedAndFallsBackToNone(@TempDir Path dir) {
        FiwModsEngine engine = engine(dir);
        engine.config().exemptions.player_overrides.put("steve",
                new ModConfig.PlayerOverride("silent", null, System.currentTimeMillis() - 1000, "test"));

        assertEquals(ExemptionTier.NONE, engine.resolveExemption("steve", null).tier());
        assertFalse(engine.config().exemptions.player_overrides.containsKey("steve"), "expired grant should be purged");
    }

    @Test
    void addExemptionRejectsUnknownTier(@TempDir Path dir) {
        FiwModsEngine engine = engine(dir);
        String err = engine.addExemption("steve", null, null, null);
        assertNotNull(err);
    }

    @Test
    void addExemptionRejectsPresetTierWithoutPresetName(@TempDir Path dir) {
        FiwModsEngine engine = engine(dir);
        String err = engine.addExemption("steve", ExemptionTier.PRESET, null, null);
        assertNotNull(err);
    }

    @Test
    void addExemptionRejectsUnknownPresetName(@TempDir Path dir) {
        FiwModsEngine engine = engine(dir);
        String err = engine.addExemption("steve", ExemptionTier.PRESET, "made_up_preset", null);
        assertNotNull(err);
    }

    @Test
    void addExemptionWithHoursSetsExpiry(@TempDir Path dir) {
        FiwModsEngine engine = engine(dir);
        String err = engine.addExemption("steve", ExemptionTier.MONITOR, null, 4);
        assertNull(err);
        assertEquals(ExemptionTier.MONITOR, engine.resolveExemption("steve", null).tier());
        assertNotNull(engine.config().exemptions.player_overrides.get("steve").expires_at);
    }

    @Test
    void removeExemptionClearsBothOverrideAndLegacyList(@TempDir Path dir) {
        FiwModsEngine engine = engine(dir);
        engine.config().exemptions.bypass_players.add("steve");
        engine.addExemption("griefer", ExemptionTier.FORCE_BLOCK, null, null);

        assertTrue(engine.removeExemption("steve"));
        assertTrue(engine.removeExemption("griefer"));
        assertEquals(ExemptionTier.NONE, engine.resolveExemption("steve", null).tier());
        assertEquals(ExemptionTier.NONE, engine.resolveExemption("griefer", null).tier());
        assertFalse(engine.removeExemption("nobody"));
    }

    @Test
    void escalationRuleAutoAppliesActionTierAfterThreshold(@TempDir Path dir) {
        FiwModsEngine engine = engine(dir);
        ModConfig.EscalationRule rule = new ModConfig.EscalationRule();
        rule.enabled = true;
        rule.detection_count = 2;
        rule.window_hours = 1;
        rule.action_tier = "force_block";
        rule.duration_hours = 1;
        engine.config().exemptions.escalation_rules = new ArrayList<>(List.of(rule));

        UUID uuid = UUID.randomUUID();
        engine.checkEscalation(uuid, "griefer", detectedResult());
        assertEquals(ExemptionTier.NONE, engine.resolveExemption("griefer", uuid).tier(), "below threshold yet");

        engine.checkEscalation(uuid, "griefer", detectedResult());
        assertEquals(ExemptionTier.FORCE_BLOCK, engine.resolveExemption("griefer", uuid).tier(),
                "threshold met — should auto force_block");
    }

    @Test
    void escalationNeverRestacksOnTopOfExistingGrant(@TempDir Path dir) {
        FiwModsEngine engine = engine(dir);
        ModConfig.EscalationRule rule = new ModConfig.EscalationRule();
        rule.enabled = true;
        rule.detection_count = 1;
        rule.window_hours = 1;
        rule.action_tier = "force_block";
        rule.duration_hours = 1;
        engine.config().exemptions.escalation_rules = new ArrayList<>(List.of(rule));

        UUID uuid = UUID.randomUUID();
        engine.addExemption(uuid.toString(), ExemptionTier.SILENT, null, null);

        engine.checkEscalation(uuid, "trusted", detectedResult());
        assertEquals(ExemptionTier.SILENT, engine.resolveExemption("trusted", uuid).tier(),
                "an existing admin grant must not be overwritten by auto-escalation");
    }

    private static final class TestPlatform implements Platform {
        private final Path configDir;

        TestPlatform(Path configDir) {
            this.configDir = configDir;
        }

        @Override
        public Path configDir() {
            return configDir;
        }

        @Override
        public void logInfo(String message) {}

        @Override
        public void logWarn(String message) {}

        @Override
        public void logError(String message, Throwable error) {}
    }
}
