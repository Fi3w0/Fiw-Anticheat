package dev.fiw.modsapi.core;

import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.model.ResourcePackEntry;
import dev.fiw.modsapi.core.profile.PlayerProfile;
import dev.fiw.modsapi.core.profile.ProfileStore;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ProfileStoreTest {

    @Test
    void recordsAddedRemovedUpdated(@TempDir Path dir) throws IOException {
        ProfileStore store = new ProfileStore(dir);
        UUID uuid = UUID.randomUUID();

        // first join: sodium + freecam -> both added
        var first = store.record(uuid, "Steve",
                List.of(new ModEntry("sodium", "0.5.2"), new ModEntry("freecam", "1.0")), 200);
        assertEquals(2, first.size());
        assertTrue(first.stream().allMatch(e -> e.event.equals("added")));

        // second join: freecam removed, sodium updated, xaero added
        var second = store.record(uuid, "Steve",
                List.of(new ModEntry("sodium", "0.5.3"), new ModEntry("xaerominimap", "23.0")), 200);

        assertEquals(3, second.size());
        assertTrue(second.stream().anyMatch(e -> e.event.equals("removed") && e.id.equals("freecam")));
        assertTrue(second.stream().anyMatch(e -> e.event.equals("updated") && e.id.equals("sodium")
                && e.from.equals("0.5.2") && e.to.equals("0.5.3")));
        assertTrue(second.stream().anyMatch(e -> e.event.equals("added") && e.id.equals("xaerominimap")));

        PlayerProfile loaded = store.load(uuid);
        assertNotNull(loaded);
        assertEquals(2, loaded.joins);
        assertEquals(2, loaded.currentMods.size());
    }

    @Test
    void historyIsCapped(@TempDir Path dir) throws IOException {
        ProfileStore store = new ProfileStore(dir);
        UUID uuid = UUID.randomUUID();
        // alternate a mod in/out many times against a small cap
        for (int i = 0; i < 20; i++) {
            store.record(uuid, "Alex",
                    (i % 2 == 0) ? List.of(new ModEntry("toggle", "1")) : List.of(), 5);
        }
        PlayerProfile loaded = store.load(uuid);
        assertNotNull(loaded);
        assertTrue(loaded.history.size() <= 5, "history should be capped at 5");
    }

    @Test
    void recordsResourcePackStateChanges(@TempDir Path dir) throws IOException {
        ProfileStore store = new ProfileStore(dir);
        UUID uuid = UUID.randomUUID();

        store.record(uuid, "Steve", List.of(new ModEntry("sodium", "1")),
                List.of(new ResourcePackEntry("file/xray.zip", "xray.zip", "aaa", false)), 200);

        var enabled = store.recordResourcePacks(uuid, "Steve",
                List.of(new ResourcePackEntry("file/xray.zip", "xray.zip", "aaa", true)), 200);
        assertTrue(enabled.stream().anyMatch(e -> e.event.equals("enabled")
                && "resource_pack".equals(e.type)
                && e.name.equals("xray.zip")));

        var removed = store.recordResourcePacks(uuid, "Steve", List.of(), 200);
        assertTrue(removed.stream().anyMatch(e -> e.event.equals("removed")
                && "resource_pack".equals(e.type)
                && e.name.equals("xray.zip")));

        PlayerProfile loaded = store.load(uuid);
        assertNotNull(loaded);
        assertEquals(0, loaded.activeResourcePacks.size());
        assertEquals(0, loaded.inactiveResourcePacks.size());
    }
}
