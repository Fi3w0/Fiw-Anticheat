package dev.fiw.modsapi.core.exemption;

import dev.fiw.modsapi.core.config.ModConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ExemptionViewTest {

    @Test
    void hidesExpiredGrantsAtTheBoundaryWithoutChangingConfig() {
        ModConfig config = new ModConfig();
        config.exemptions.player_overrides.put("past", new ModConfig.PlayerOverride("silent", null, 99L, null));
        config.exemptions.player_overrides.put("boundary", new ModConfig.PlayerOverride("bypass", null, 100L, null));
        config.exemptions.player_overrides.put("future", new ModConfig.PlayerOverride("preset", "strict", 101L, null));
        config.exemptions.player_overrides.put("permanent", new ModConfig.PlayerOverride("monitor", null, null, null));
        config.exemptions.bypass_players.add("legacy");
        var rows = ExemptionView.commandRows(config, 100);
        assertTrue(rows.get(0).text().contains("3 active"));
        assertTrue(rows.get(0).text().contains("UTC"));
        assertEquals(4, rows.size());
        assertFalse(rows.stream().anyMatch(row -> row.text().contains("past") || row.text().contains("boundary")));
        assertTrue(rows.stream().anyMatch(row -> row.text().contains("1970-01-01T00:00:00.101Z")));
        assertTrue(rows.stream().anyMatch(row -> row.text().contains("legacy")));
        assertEquals(4, config.exemptions.player_overrides.size());
    }

    @Test
    void onlyExpiredGrantsShowsNone() {
        ModConfig config = new ModConfig();
        config.exemptions.player_overrides.put("old", new ModConfig.PlayerOverride("bypass", null, 1L, null));
        var rows = ExemptionView.commandRows(config, 2);
        assertTrue(rows.get(0).text().contains("0 active"));
        assertEquals(ExemptionView.LineType.EMPTY, rows.get(1).type());
    }
}
