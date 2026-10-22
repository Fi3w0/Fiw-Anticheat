package dev.fiw.modsapi.core.challenge;

import java.security.MessageDigest;
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

    /**
     * @param join true for the challenge sent at login; snapshot captures and rechecks of an
     *             already-online player are not joins and must not count as one in the profile
     */
    public record Pending(byte[] nonce, long timestamp, boolean capture, boolean join) {}

    /** How a response compared against the player's pending challenge. */
    public enum Outcome {
        /** Nonce matched a live challenge — evaluate the report. */
        VALID,
        /** Wrong nonce — the challenge is spent; the player must be disconnected. */
        MISMATCH,
        /** Correct or not, the challenge had already timed out — treat as a timeout. */
        EXPIRED,
        /** No live challenge (unsolicited/duplicate response, or a late capture reply) — ignore it. */
        NONE
    }

    public record Verification(Outcome outcome, Pending pending) {}

    private final Map<UUID, Pending> pending = new ConcurrentHashMap<>();

    /** Create + store a nonce for a player. {@code capture} marks it as a snapshot capture. */
    public byte[] create(UUID player, boolean capture) {
        byte[] nonce = newNonce();
        pending.put(player, new Pending(nonce, System.currentTimeMillis(), capture, true));
        return nonce;
    }

    /**
     * Create a challenge for an already-online player (snapshot capture or recheck), unless
     * one is already pending (e.g. they are still being verified on join). Returns null in
     * that case so it can never replace — and silently skip — a pending verification.
     */
    public byte[] createIfIdle(UUID player, boolean capture) {
        byte[] nonce = newNonce();
        Pending existing = pending.putIfAbsent(player, new Pending(nonce, System.currentTimeMillis(), capture, false));
        return existing == null ? nonce : null;
    }

    /**
     * Consume the player's pending challenge and check the echoed nonce and its age.
     * The challenge is single-use: it is removed whatever the outcome, so callers must
     * disconnect on {@link Outcome#MISMATCH} / {@link Outcome#EXPIRED} — the timeout
     * sweep can no longer catch this player.
     */
    public Verification verify(UUID player, byte[] nonceEcho, long timeoutMs) {
        Pending p = pending.remove(player);
        if (p == null) return new Verification(Outcome.NONE, null);
        if (System.currentTimeMillis() - p.timestamp() > timeoutMs) {
            // A late capture reply is just dropped, matching the timeout sweep in expired().
            return new Verification(p.capture() ? Outcome.NONE : Outcome.EXPIRED, p);
        }
        if (nonceEcho == null || !MessageDigest.isEqual(p.nonce(), nonceEcho)) {
            return new Verification(Outcome.MISMATCH, p);
        }
        return new Verification(Outcome.VALID, p);
    }

    public void remove(UUID player) {
        pending.remove(player);
    }

    public boolean isPending(UUID player) {
        return pending.containsKey(player);
    }

    /**
     * Players whose join challenge has exceeded {@code timeoutMs}. Expired snapshot
     * captures are dropped here instead of being returned: they never froze the player,
     * so an unanswered capture must not kick them.
     */
    public List<UUID> expired(long timeoutMs) {
        long now = System.currentTimeMillis();
        List<UUID> out = new ArrayList<>();
        pending.forEach((uuid, p) -> {
            if (now - p.timestamp() <= timeoutMs) return;
            if (p.capture()) {
                pending.remove(uuid, p);
            } else {
                out.add(uuid);
            }
        });
        return out;
    }

    private static byte[] newNonce() {
        byte[] nonce = new byte[32];
        RANDOM.nextBytes(nonce);
        return nonce;
    }
}
