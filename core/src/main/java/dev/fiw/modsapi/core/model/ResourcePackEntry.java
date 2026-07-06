package dev.fiw.modsapi.core.model;

/**
 * One resource pack reported by the companion client.
 *
 * @param id          stable-ish pack id, usually {@code file/<filename>} for local packs
 * @param name        readable pack name for staff output
 * @param fingerprint SHA-256 hex of the pack content when available
 * @param active      whether the pack is enabled in the client's current pack stack
 */
public record ResourcePackEntry(String id, String name, String fingerprint, boolean active)
        implements Comparable<ResourcePackEntry> {

    public ResourcePackEntry {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("resource pack id cannot be blank");
        name = name == null || name.isBlank() ? id : name;
        fingerprint = fingerprint == null ? "" : fingerprint;
    }

    @Override
    public int compareTo(ResourcePackEntry o) {
        int state = Boolean.compare(o.active, active);
        return state != 0 ? state : id.compareToIgnoreCase(o.id);
    }

    public String displayName() {
        return name == null || name.isBlank() ? id : name;
    }
}
