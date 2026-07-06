package dev.fiw.modsapi.core.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.fiw.modsapi.core.model.ModEntry;
import dev.fiw.modsapi.core.model.ResourcePackEntry;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loads/saves per-player mod profiles under {@code <configDir>/profiles/<uuid>.json}.
 * One file per player avoids rewriting a single huge file on every join.
 * {@link #record} computes the add/remove/update diff and appends history.
 */
public final class ProfileStore {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private final Path profilesDir;

    public ProfileStore(Path configDir) {
        this.profilesDir = configDir.resolve("profiles");
    }

    /**
     * Update a player's profile from a freshly reported mod list: diff against the
     * stored current set, append change events, and persist. Returns the events
     * that were recorded this join (may be empty).
     */
    public synchronized List<PlayerProfile.Event> record(UUID uuid,
                                                         String name,
                                                         List<ModEntry> mods,
                                                         int maxHistory) throws IOException {
        PlayerProfile profile = load(uuid);
        String now = Instant.now().toString();

        if (profile == null) {
            profile = new PlayerProfile();
            profile.uuid = uuid.toString();
            profile.firstSeen = now;
        }
        profile.name = name;
        profile.lastSeen = now;
        profile.joins++;

        List<PlayerProfile.Event> events = diffMods(now, profile, mods);
        appendAndSave(uuid, profile, events, maxHistory);
        return events;
    }

    public synchronized List<PlayerProfile.Event> record(UUID uuid,
                                                         String name,
                                                         List<ModEntry> mods,
                                                         List<ResourcePackEntry> resourcePacks,
                                                         int maxHistory) throws IOException {
        PlayerProfile profile = load(uuid);
        String now = Instant.now().toString();

        if (profile == null) {
            profile = new PlayerProfile();
            profile.uuid = uuid.toString();
            profile.firstSeen = now;
        }
        profile.name = name;
        profile.lastSeen = now;
        profile.joins++;

        List<PlayerProfile.Event> events = new ArrayList<>();
        events.addAll(diffMods(now, profile, mods));
        events.addAll(diffResourcePacks(now, profile, resourcePacks));

        appendAndSave(uuid, profile, events, maxHistory);
        return events;
    }

    public synchronized List<PlayerProfile.Event> recordResourcePacks(UUID uuid,
                                                                      String name,
                                                                      List<ResourcePackEntry> resourcePacks,
                                                                      int maxHistory) throws IOException {
        PlayerProfile profile = load(uuid);
        String now = Instant.now().toString();

        if (profile == null) {
            profile = new PlayerProfile();
            profile.uuid = uuid.toString();
            profile.firstSeen = now;
        }
        profile.name = name;
        profile.lastSeen = now;

        List<PlayerProfile.Event> events = diffResourcePacks(now, profile, resourcePacks);
        appendAndSave(uuid, profile, events, maxHistory);
        return events;
    }

    private static List<PlayerProfile.Event> diffMods(String now, PlayerProfile profile, List<ModEntry> mods) {
        Map<String, String> oldSet = new LinkedHashMap<>();
        for (PlayerProfile.Mod m : safeMods(profile.currentMods)) {
            oldSet.put(m.id(), m.version());
        }
        Map<String, String> newSet = new LinkedHashMap<>();
        for (ModEntry m : mods == null ? List.<ModEntry>of() : mods) {
            newSet.put(m.id(), m.version());
        }

        List<PlayerProfile.Event> events = new ArrayList<>();
        for (Map.Entry<String, String> e : newSet.entrySet()) {
            if (!oldSet.containsKey(e.getKey())) {
                events.add(PlayerProfile.Event.added(now, e.getKey(), e.getValue()));
            } else {
                String prev = oldSet.get(e.getKey());
                if (!java.util.Objects.equals(prev, e.getValue())) {
                    events.add(PlayerProfile.Event.updated(now, e.getKey(), prev, e.getValue()));
                }
            }
        }
        // removed
        for (String id : oldSet.keySet()) {
            if (!newSet.containsKey(id)) {
                events.add(PlayerProfile.Event.removed(now, id));
            }
        }

        List<PlayerProfile.Mod> current = new ArrayList<>();
        for (Map.Entry<String, String> e : newSet.entrySet()) {
            current.add(new PlayerProfile.Mod(e.getKey(), e.getValue()));
        }
        profile.currentMods = current;
        ProfileView.Groups groups = ProfileView.groupMods(current);
        profile.visibleMods = groups.visibleMods();
        profile.platformMods = groups.platformMods();

        return events;
    }

    private static List<PlayerProfile.Event> diffResourcePacks(String now,
                                                               PlayerProfile profile,
                                                               List<ResourcePackEntry> resourcePacks) {
        Map<String, PackState> oldSet = new LinkedHashMap<>();
        for (PlayerProfile.ResourcePack pack : safePacks(profile.activeResourcePacks)) {
            oldSet.put(pack.id(), new PackState(pack, true));
        }
        for (PlayerProfile.ResourcePack pack : safePacks(profile.inactiveResourcePacks)) {
            oldSet.putIfAbsent(pack.id(), new PackState(pack, false));
        }

        Map<String, PackState> newSet = new LinkedHashMap<>();
        for (ResourcePackEntry pack : resourcePacks == null ? List.<ResourcePackEntry>of() : resourcePacks) {
            PlayerProfile.ResourcePack profilePack =
                    new PlayerProfile.ResourcePack(pack.id(), pack.displayName(), pack.fingerprint());
            newSet.put(pack.id(), new PackState(profilePack, pack.active()));
        }

        List<PlayerProfile.Event> events = new ArrayList<>();
        for (Map.Entry<String, PackState> e : newSet.entrySet()) {
            PackState current = e.getValue();
            PackState previous = oldSet.get(e.getKey());
            if (previous == null) {
                events.add(PlayerProfile.Event.resourcePack(now, "added", current.pack, current.active));
            } else {
                if (previous.active != current.active) {
                    events.add(PlayerProfile.Event.resourcePack(now,
                            current.active ? "enabled" : "disabled", current.pack, current.active));
                }
                if (!java.util.Objects.equals(previous.pack.fingerprint(), current.pack.fingerprint())) {
                    PlayerProfile.Event updated =
                            PlayerProfile.Event.resourcePack(now, "updated", current.pack, current.active);
                    updated.from = previous.pack.fingerprint();
                    updated.to = current.pack.fingerprint();
                    events.add(updated);
                }
            }
        }
        for (Map.Entry<String, PackState> e : oldSet.entrySet()) {
            if (!newSet.containsKey(e.getKey())) {
                events.add(PlayerProfile.Event.resourcePack(now, "removed", e.getValue().pack, e.getValue().active));
            }
        }

        List<PlayerProfile.ResourcePack> active = new ArrayList<>();
        List<PlayerProfile.ResourcePack> inactive = new ArrayList<>();
        for (PackState pack : newSet.values()) {
            if (pack.active) {
                active.add(pack.pack);
            } else {
                inactive.add(pack.pack);
            }
        }
        profile.activeResourcePacks = active;
        profile.inactiveResourcePacks = inactive;

        return events;
    }

    private void appendAndSave(UUID uuid,
                               PlayerProfile profile,
                               List<PlayerProfile.Event> events,
                               int maxHistory) throws IOException {
        // append + cap history
        if (profile.history == null) profile.history = new ArrayList<>();
        profile.history.addAll(events);
        if (maxHistory > 0 && profile.history.size() > maxHistory) {
            profile.history = new ArrayList<>(
                    profile.history.subList(profile.history.size() - maxHistory, profile.history.size()));
        }

        save(uuid, profile);
    }

    public PlayerProfile load(UUID uuid) {
        Path file = profilesDir.resolve(uuid + ".json");
        if (!Files.exists(file)) return null;
        try (Reader r = Files.newBufferedReader(file)) {
            PlayerProfile profile = GSON.fromJson(r, PlayerProfile.class);
            if (profile != null) {
                ensureLists(profile);
                ProfileView.Groups groups = ProfileView.group(profile);
                if (profile.visibleMods == null || profile.visibleMods.isEmpty()) profile.visibleMods = groups.visibleMods();
                if (profile.platformMods == null || profile.platformMods.isEmpty()) profile.platformMods = groups.platformMods();
            }
            return profile;
        } catch (Exception e) {
            return null;
        }
    }

    private void save(UUID uuid, PlayerProfile profile) throws IOException {
        Files.createDirectories(profilesDir);
        Path file = profilesDir.resolve(uuid + ".json");
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(profile, w);
        }
    }

    private static void ensureLists(PlayerProfile profile) {
        if (profile.currentMods == null) profile.currentMods = new ArrayList<>();
        if (profile.visibleMods == null) profile.visibleMods = new ArrayList<>();
        if (profile.platformMods == null) profile.platformMods = new ArrayList<>();
        if (profile.activeResourcePacks == null) profile.activeResourcePacks = new ArrayList<>();
        if (profile.inactiveResourcePacks == null) profile.inactiveResourcePacks = new ArrayList<>();
        if (profile.history == null) profile.history = new ArrayList<>();
    }

    private static List<PlayerProfile.Mod> safeMods(List<PlayerProfile.Mod> mods) {
        return mods == null ? List.of() : mods;
    }

    private static List<PlayerProfile.ResourcePack> safePacks(List<PlayerProfile.ResourcePack> packs) {
        return packs == null ? List.of() : packs;
    }

    private record PackState(PlayerProfile.ResourcePack pack, boolean active) {}
}
