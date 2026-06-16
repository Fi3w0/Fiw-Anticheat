package dev.fiw.modsapi.core;

import dev.fiw.modsapi.core.profile.PlayerProfile;
import dev.fiw.modsapi.core.profile.ProfileView;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ProfileViewTest {

    @Test
    void groupsLoaderNoiseAwayFromVisibleMods() {
        PlayerProfile profile = new PlayerProfile();
        profile.name = "Alex";
        profile.firstSeen = "now";
        profile.currentMods = List.of(
                new PlayerProfile.Mod("fabric-api", "1"),
                new PlayerProfile.Mod("fabric-networking-api-v1", "1"),
                new PlayerProfile.Mod("fabricloader", "1"),
                new PlayerProfile.Mod("fiw-mods-api", "2.0.0"),
                new PlayerProfile.Mod("sodium", "0.5.0"),
                new PlayerProfile.Mod("freecam", "1.0"));

        ProfileView.Groups groups = ProfileView.group(profile);

        assertEquals(List.of("sodium", "freecam"),
                groups.visibleMods().stream().map(PlayerProfile.Mod::id).toList());
        assertEquals(List.of("fabric-api", "fabric-networking-api-v1", "fabricloader", "fiw-mods-api"),
                groups.platformMods().stream().map(PlayerProfile.Mod::id).toList());
    }

    @Test
    void commandLinesShowReadableSections() {
        PlayerProfile profile = new PlayerProfile();
        profile.firstSeen = "now";
        profile.joins = 2;
        profile.currentMods = List.of(
                new PlayerProfile.Mod("neoforge", "21.1.91"),
                new PlayerProfile.Mod("fiw_mods_api", "2.0.0"),
                new PlayerProfile.Mod("freecam", "1.0"));

        List<String> lines = ProfileView.commandLines(profile, "Alex");

        assertTrue(lines.get(0).contains("1 mods, 2 platform entries"));
        assertTrue(lines.stream().anyMatch(s -> s.startsWith("Mods (1): freecam@1.0")));
        assertTrue(lines.stream().anyMatch(s -> s.startsWith("Platform (2): neoforge@21.1.91")));
    }
}
