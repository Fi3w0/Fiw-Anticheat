package dev.fiw.modsapi.core.profile;

import java.util.ArrayList;
import java.util.List;

/**
 * Persistent per-player record of which mods they run and how that set has
 * changed over time. Mutable fields for direct Gson (de)serialisation.
 */
public final class PlayerProfile {

    public String uuid;
    public String name;
    public String firstSeen;
    public String lastSeen;
    public int joins;
    public List<Mod> currentMods = new ArrayList<>();
    public List<Mod> visibleMods = new ArrayList<>();
    public List<Mod> platformMods = new ArrayList<>();
    public List<Event> history = new ArrayList<>();

    public record Mod(String id, String version) {}

    /** One change in the player's mod set. {@code from}/{@code to} used for "updated". */
    public static final class Event {
        public String time;
        public String event; // added | removed | updated
        public String id;
        public String version; // for added
        public String from;    // for updated
        public String to;      // for updated

        public Event() {}

        public static Event added(String time, String id, String version) {
            Event e = new Event();
            e.time = time; e.event = "added"; e.id = id; e.version = version;
            return e;
        }

        public static Event removed(String time, String id) {
            Event e = new Event();
            e.time = time; e.event = "removed"; e.id = id;
            return e;
        }

        public static Event updated(String time, String id, String from, String to) {
            Event e = new Event();
            e.time = time; e.event = "updated"; e.id = id; e.from = from; e.to = to;
            return e;
        }
    }
}
