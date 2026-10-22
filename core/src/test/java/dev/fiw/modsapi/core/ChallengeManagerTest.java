package dev.fiw.modsapi.core;

import dev.fiw.modsapi.core.challenge.ChallengeManager;
import dev.fiw.modsapi.core.challenge.ChallengeManager.Outcome;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChallengeManagerTest {

    private static final long TIMEOUT = 10_000L;
    private final UUID player = UUID.randomUUID();

    @Test
    void correctNonceIsValidAndSingleUse() {
        ChallengeManager challenges = new ChallengeManager();
        byte[] nonce = challenges.create(player, false);

        ChallengeManager.Verification first = challenges.verify(player, nonce.clone(), TIMEOUT);
        assertEquals(Outcome.VALID, first.outcome());
        assertNotNull(first.pending());
        assertFalse(first.pending().capture());

        // replaying the same response finds nothing pending
        assertEquals(Outcome.NONE, challenges.verify(player, nonce, TIMEOUT).outcome());
    }

    @Test
    void wrongNonceIsMismatchAndConsumesTheChallenge() {
        ChallengeManager challenges = new ChallengeManager();
        byte[] nonce = challenges.create(player, false);
        byte[] wrong = nonce.clone();
        wrong[0] ^= 1;

        assertEquals(Outcome.MISMATCH, challenges.verify(player, wrong, TIMEOUT).outcome());
        assertFalse(challenges.isPending(player));
        // the real nonce can't be used afterwards either
        assertEquals(Outcome.NONE, challenges.verify(player, nonce, TIMEOUT).outcome());
    }

    @Test
    void emptyShortOrNullNonceIsMismatch() {
        ChallengeManager challenges = new ChallengeManager();
        challenges.create(player, false);
        assertEquals(Outcome.MISMATCH, challenges.verify(player, new byte[0], TIMEOUT).outcome());

        byte[] nonce = challenges.create(player, false);
        byte[] prefix = java.util.Arrays.copyOf(nonce, 16);
        assertEquals(Outcome.MISMATCH, challenges.verify(player, prefix, TIMEOUT).outcome());

        challenges.create(player, false);
        assertEquals(Outcome.MISMATCH, challenges.verify(player, null, TIMEOUT).outcome());
    }

    @Test
    void anotherPlayersNonceIsMismatch() {
        ChallengeManager challenges = new ChallengeManager();
        UUID other = UUID.randomUUID();
        challenges.create(player, false);
        byte[] otherNonce = challenges.create(other, false);

        assertEquals(Outcome.MISMATCH, challenges.verify(player, otherNonce, TIMEOUT).outcome());
        assertEquals(Outcome.VALID, challenges.verify(other, otherNonce, TIMEOUT).outcome());
    }

    @Test
    void lateJoinResponseIsExpiredEvenWithCorrectNonce() throws InterruptedException {
        ChallengeManager challenges = new ChallengeManager();
        byte[] nonce = challenges.create(player, false);
        Thread.sleep(5);

        assertEquals(Outcome.EXPIRED, challenges.verify(player, nonce, 1).outcome());
        assertFalse(challenges.isPending(player));
    }

    @Test
    void captureCannotReplaceAPendingJoinChallenge() {
        ChallengeManager challenges = new ChallengeManager();
        byte[] joinNonce = challenges.create(player, false);

        assertNull(challenges.createIfIdle(player, true));
        ChallengeManager.Verification check = challenges.verify(player, joinNonce, TIMEOUT);
        assertEquals(Outcome.VALID, check.outcome());
        assertFalse(check.pending().capture());
    }

    @Test
    void recheckCannotReplaceAPendingChallengeEither() {
        ChallengeManager challenges = new ChallengeManager();
        challenges.createIfIdle(player, true);
        assertNull(challenges.createIfIdle(player, false));
    }

    @Test
    void recheckIsAJoinStyleChallenge() throws InterruptedException {
        ChallengeManager challenges = new ChallengeManager();
        byte[] nonce = challenges.createIfIdle(player, false);
        assertNotNull(nonce);
        Thread.sleep(5);
        // unlike a capture, an unanswered recheck is reported for a timeout kick
        assertEquals(java.util.List.of(player), challenges.expired(1));
    }

    @Test
    void onlyLoginChallengesCountAsJoins() {
        ChallengeManager challenges = new ChallengeManager();
        byte[] join = challenges.create(player, false);
        assertTrue(challenges.verify(player, join, TIMEOUT).pending().join());

        byte[] recheck = challenges.createIfIdle(player, false);
        assertFalse(challenges.verify(player, recheck, TIMEOUT).pending().join());

        byte[] capture = challenges.createIfIdle(player, true);
        assertFalse(challenges.verify(player, capture, TIMEOUT).pending().join());
    }

    @Test
    void captureIsIssuedWhenIdle() {
        ChallengeManager challenges = new ChallengeManager();
        byte[] nonce = challenges.createIfIdle(player, true);
        assertNotNull(nonce);

        ChallengeManager.Verification check = challenges.verify(player, nonce, TIMEOUT);
        assertEquals(Outcome.VALID, check.outcome());
        assertTrue(check.pending().capture());
    }

    @Test
    void expiredCaptureIsDroppedNotReportedForKick() throws InterruptedException {
        ChallengeManager challenges = new ChallengeManager();
        UUID joiner = UUID.randomUUID();
        challenges.createIfIdle(player, true);
        challenges.create(joiner, false);
        Thread.sleep(5);

        assertEquals(java.util.List.of(joiner), challenges.expired(1));
        assertFalse(challenges.isPending(player));
        assertTrue(challenges.isPending(joiner));
    }

    @Test
    void lateCaptureReplyIsIgnored() throws InterruptedException {
        ChallengeManager challenges = new ChallengeManager();
        byte[] nonce = challenges.createIfIdle(player, true);
        Thread.sleep(5);

        assertEquals(Outcome.NONE, challenges.verify(player, nonce, 1).outcome());
    }
}
