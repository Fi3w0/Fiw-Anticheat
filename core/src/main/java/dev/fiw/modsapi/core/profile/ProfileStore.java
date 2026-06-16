package dev.fiw.modsapi.core.profile;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import dev.fiw.modsapi.core.model.ModEntry;

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

        Map<String, String> oldSet = new LinkedHashMap<>();
        for (PlayerProfile.Mod m : profile.currentMods) {
            oldSet.put(m.id(), m.version());
        }
        Map<String, String> newSet = new LinkedHashMap<>();
        for (ModEntry m : mods) {
            newSet.put(m.id(), m.version());
        }

        List<PlayerProfile.Event> events = new ArrayList<>();
        // added / updated
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

        // refresh current set
        List<PlayerProfile.Mod> current = new ArrayList<>();
        for (Map.Entry<String, String> e : newSet.entrySet()) {
            current.add(new PlayerProfile.Mod(e.getKey(), e.getValue()));
        }
        profile.currentMods = current;
        ProfileView.Groups groups = ProfileView.groupMods(current);
        profile.visibleMods = groups.visibleMods();
        profile.platformMods = groups.platformMods();

        // append + cap history
        profile.history.addAll(events);
        if (maxHistory > 0 && profile.history.size() > maxHistory) {
            profile.history = new ArrayList<>(
                    profile.history.subList(profile.history.size() - maxHistory, profile.history.size()));
        }

        save(uuid, profile);
        return events;
    }

    public PlayerProfile load(UUID uuid) {
        Path file = profilesDir.resolve(uuid + ".json");
        if (!Files.exists(file)) return null;
        try (Reader r = Files.newBufferedReader(file)) {
            PlayerProfile profile = GSON.fromJson(r, PlayerProfile.class);
            if (profile != null) {
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
}
