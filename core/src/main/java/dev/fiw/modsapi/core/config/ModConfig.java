package dev.fiw.modsapi.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The whole mod configuration, persisted as {@code config/fiw-mods-api/config.json}.
 * Plain mutable fields so Gson can (de)serialise directly; defaults below are the
 * out-of-the-box config that gets written on first run.
 */
public final class ModConfig {

    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    public String mode = "blacklist"; // "blacklist" | "whitelist"
    public int timeout_seconds = 10;
    public String kick_message = "You are using a mod not allowed on this server.";
    public String timeout_message = "Mod verification timed out. Please rejoin.";

    public Detection detection = new Detection();
    public ResourcePacks resource_packs = new ResourcePacks();
    public Exemptions exemptions = new Exemptions();
    public Profiling profiling = new Profiling();
    public Whitelist whitelist = new Whitelist();

    public static final class Detection {
        public String preset = "balanced"; // strict | balanced | lenient | custom
        public boolean monitor_only = false;
        public boolean alert_staff = true;
        public Map<String, Boolean> block = defaultBlock(); // used only when preset == "custom"
        public List<String> allow_overrides = new ArrayList<>();
        public List<String> banned_mods = new ArrayList<>();

        private static Map<String, Boolean> defaultBlock() {
            Map<String, Boolean> m = new LinkedHashMap<>();
            for (String cat : Preset.CATEGORIES) {
                // mirror the "balanced" preset as a friendly starting point for custom edits
                boolean on = switch (cat) {
                    case "cheat_clients", "xray", "fullbright", "freecam",
                         "autoclicker", "schematic_printer" -> true;
                    default -> false;
                };
                m.put(cat, on);
            }
            return m;
        }
    }

    public static final class Exemptions {
        public boolean floodgate_auto = true;
        public List<String> bypass_players = new ArrayList<>(); // names or UUIDs; legacy full-bypass shortcut
        public Map<String, PlayerOverride> player_overrides = new LinkedHashMap<>(); // key = lowercase name or UUID
        public List<EscalationRule> escalation_rules = new ArrayList<>();
    }

    /** A single admin- or escalation-granted per-player tier (see {@code exemption.ExemptionTier}). */
    public static final class PlayerOverride {
        public String tier;       // silent | monitor | quiet_kick | preset | force_block | bypass
        public String preset;     // required when tier == "preset": strict|balanced|lenient|custom
        public Long expires_at;   // epoch millis; null = permanent
        public String reason;     // free text, e.g. "auto-escalation rule"

        public PlayerOverride() {}

        public PlayerOverride(String tier, String preset, Long expiresAt, String reason) {
            this.tier = tier;
            this.preset = preset;
            this.expires_at = expiresAt;
            this.reason = reason;
        }
    }

    /** Auto-applies {@code action_tier} for {@code duration_hours} once a player hits {@code detection_count}
     *  detections within {@code window_hours}. Only the first enabled rule matched per event fires. */
    public static final class EscalationRule {
        public boolean enabled = true;
        public int detection_count;
        public int window_hours;
        public String action_tier;
        public int duration_hours;
    }

    public static final class ResourcePacks {
        public boolean log = true;
        public boolean kick_on_banned = false;
        public List<String> banned_packs = new ArrayList<>(); // exact id or display name, active or inactive
        public List<String> banned_fingerprints = new ArrayList<>(); // SHA-256 hex
    }

    public static final class Profiling {
        public boolean enabled = true;
        public int max_history = 200;
    }

    public static final class Whitelist {
        public boolean require_all = true;
        public List<OfficialMod> official_mods = new ArrayList<>(); // filled by /fiwmods snapshot
    }

    public static final class OfficialMod {
        public String id;
        public String version;      // informational / optional constraint
        public String fingerprint;  // SHA-256 hex; when present, enforced (anti-impersonation)

        public OfficialMod() {}

        public OfficialMod(String id, String version, String fingerprint) {
            this.id = id;
            this.version = version;
            this.fingerprint = fingerprint;
        }
    }

    // --- behaviour helpers ---

    public boolean isWhitelistMode() {
        return "whitelist".equalsIgnoreCase(mode);
    }

    /** Resolve the active category→enabled map from the preset (or custom block). */
    public Map<String, Boolean> resolvedBlock() {
        return Preset.fromString(detection.preset).resolve(detection.block);
    }

    // --- persistence ---

    /** Loads config from {@code <configDir>/config.json}, writing defaults if absent. */
    public static ModConfig load(Path configDir) throws IOException {
        Path file = configDir.resolve("config.json");
        if (!Files.exists(file)) {
            Files.createDirectories(configDir);
            ModConfig def = new ModConfig();
            def.save(configDir);
            return def;
        }
        try (Reader r = Files.newBufferedReader(file)) {
            ModConfig cfg = GSON.fromJson(r, ModConfig.class);
            return cfg == null ? new ModConfig() : cfg.withDefaults();
        }
    }

    public void save(Path configDir) throws IOException {
        Files.createDirectories(configDir);
        Path file = configDir.resolve("config.json");
        try (Writer w = Files.newBufferedWriter(file)) {
            GSON.toJson(this, w);
        }
    }

    /** Fill in any nested objects a hand-edited / older config may be missing. */
    private ModConfig withDefaults() {
        if (detection == null) detection = new Detection();
        if (detection.block == null) detection.block = Detection.defaultBlock();
        if (detection.allow_overrides == null) detection.allow_overrides = new ArrayList<>();
        if (detection.banned_mods == null) detection.banned_mods = new ArrayList<>();
        if (detection.preset == null) detection.preset = "balanced";
        if (resource_packs == null) resource_packs = new ResourcePacks();
        if (resource_packs.banned_packs == null) resource_packs.banned_packs = new ArrayList<>();
        if (resource_packs.banned_fingerprints == null) resource_packs.banned_fingerprints = new ArrayList<>();
        if (exemptions == null) exemptions = new Exemptions();
        if (exemptions.bypass_players == null) exemptions.bypass_players = new ArrayList<>();
        if (exemptions.player_overrides == null) exemptions.player_overrides = new LinkedHashMap<>();
        if (exemptions.escalation_rules == null) exemptions.escalation_rules = new ArrayList<>();
        if (profiling == null) profiling = new Profiling();
        if (whitelist == null) whitelist = new Whitelist();
        if (whitelist.official_mods == null) whitelist.official_mods = new ArrayList<>();
        if (mode == null) mode = "blacklist";
        return this;
    }
}
