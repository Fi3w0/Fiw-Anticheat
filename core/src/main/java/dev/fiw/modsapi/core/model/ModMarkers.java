package dev.fiw.modsapi.core.model;

import java.util.List;

/**
 * Stable, version-independent identity signals collected from a mod's metadata.
 * These let the signature database recognise a mod even if its file or mod id
 * has been renamed: the declared mixin configs, entrypoint classes, and the
 * mod's root package(s) do not change when a player simply renames the jar.
 */
public record ModMarkers(List<String> mixinConfigs, List<String> entrypoints, List<String> packages) {

    public static final ModMarkers EMPTY = new ModMarkers(List.of(), List.of(), List.of());

    public ModMarkers {
        mixinConfigs = mixinConfigs == null ? List.of() : List.copyOf(mixinConfigs);
        entrypoints = entrypoints == null ? List.of() : List.copyOf(entrypoints);
        packages = packages == null ? List.of() : List.copyOf(packages);
    }
}
