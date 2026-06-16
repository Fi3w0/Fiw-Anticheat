package dev.fiw.modsapi.core.freeze;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Tracks which players are frozen (awaiting verification). Pure bookkeeping by
 * UUID — the loader adapter performs the actual teleport/movement cancellation.
 */
public final class FreezeTracker {

    private final Map<UUID, FreezeState> frozen = new ConcurrentHashMap<>();

    public void freeze(UUID player, FreezeState state) {
        frozen.put(player, state);
    }

    public void unfreeze(UUID player) {
        frozen.remove(player);
    }

    public boolean isFrozen(UUID player) {
        return frozen.containsKey(player);
    }

    public FreezeState get(UUID player) {
        return frozen.get(player);
    }
}
