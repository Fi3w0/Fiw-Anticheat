package dev.fiw.modsapi.core.challenge;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Issues and tracks single-use verification nonces. Each joining player gets a
 * fresh 32-byte nonce; it expires after a timeout. A challenge can be flagged as
 * a "capture" (used by {@code /fiwmods snapshot player}) so the next response is
 * recorded as the official set instead of being verified.
 */
public final class ChallengeManager {

    private static final SecureRandom RANDOM = new SecureRandom();

    public record Pending(byte[] nonce, long timestamp, boolean capture) {}

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    /** Create + store a nonce for a player. {@code capture} marks it as a snapshot capture. */
    public byte[] create(UUID player, boolean capture) {
        byte[] nonce = new byte[32];
        RANDOM.nextBytes(nonce);
        pending.put(player, new Pending(nonce, System.currentTimeMillis(), capture));
        return nonce;
    }

    /** Retrieve and remove a player's pending challenge, or null if none. */
    public Pending consume(UUID player) {
        return pending.remove(player);
    }

    public void remove(UUID player) {
        pending.remove(player);
    }

    public boolean isPending(UUID player) {
        return pending.containsKey(player);
    }

    /** Players whose challenge has exceeded {@code timeoutMs}. */
    public List<UUID> expired(long timeoutMs) {
        long now = System.currentTimeMillis();
        List<UUID> out = new ArrayList<>();
        pending.forEach((uuid, p) -> {
            if (now - p.timestamp() > timeoutMs) out.add(uuid);
        });
        return out;
    }
}
