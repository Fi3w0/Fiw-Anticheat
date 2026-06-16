package dev.fiw.modsapi.core.model;

/**
 * One mod as reported by a client (or enumerated on the server).
 *
 * @param id          loader mod id (e.g. "sodium")
 * @param version     version string from mod metadata (e.g. "0.5.3")
 * @param fingerprint SHA-256 hex of the mod jar, or "" when unavailable
 * @param markers     stable identity signals (see {@link ModMarkers})
 */
public record ModEntry(String id, String version, String fingerprint, ModMarkers markers)
        implements Comparable<ModEntry> {

    public ModEntry {
        if (id == null) throw new IllegalArgumentException("mod id cannot be null");
        version = version == null ? "" : version;
        fingerprint = fingerprint == null ? "" : fingerprint;
        markers = markers == null ? ModMarkers.EMPTY : markers;
    }

    /** Convenience for tests / simple cases without markers or fingerprint. */
    public ModEntry(String id, String version) {
        this(id, version, "", ModMarkers.EMPTY);
    }

    @Override
    public int compareTo(ModEntry o) {
        return id.compareTo(o.id);
    }
}
