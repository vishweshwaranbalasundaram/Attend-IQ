package com.attendiq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class LoginAttemptPolicyTest {
    @Test
    void locksAfterFiveFailuresAndUnlocksAfterFiveMinutes() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-20T00:00:00Z"));
        LoginAttemptPolicy policy = new LoginAttemptPolicy(clock);
        LoginAttemptPolicy.AttemptState state = new LoginAttemptPolicy.AttemptState(0, null);

        for (int failure = 0; failure < LoginAttemptPolicy.MAX_FAILURES; failure++) {
            state = policy.recordFailure(state.failedAttempts(), state.lockedUntil());
        }

        assertEquals(5, state.failedAttempts());
        assertTrue(policy.isLocked(state.lockedUntil()));
        assertEquals(clock.instant().plus(LoginAttemptPolicy.LOCKOUT_DURATION), state.lockedUntil());

        clock.advance(LoginAttemptPolicy.LOCKOUT_DURATION);
        assertFalse(policy.isLocked(state.lockedUntil()));
        LoginAttemptPolicy.AttemptState afterExpiry =
                policy.recordFailure(state.failedAttempts(), state.lockedUntil());
        assertEquals(1, afterExpiry.failedAttempts());
        assertNull(afterExpiry.lockedUntil());
    }

    @Test
    void doesNotTreatExactExpiryInstantAsLocked() {
        MutableClock clock = new MutableClock(Instant.parse("2026-10-20T00:00:00Z"));
        LoginAttemptPolicy policy = new LoginAttemptPolicy(clock);
        Instant lockedUntil = clock.instant().plus(Duration.ofMinutes(5));

        assertTrue(policy.isLocked(lockedUntil.minusNanos(1)));
        clock.advance(Duration.ofMinutes(5));
        assertFalse(policy.isLocked(lockedUntil));
    }

    private static final class MutableClock extends Clock {
        private Instant current;

        private MutableClock(Instant current) {
            this.current = current;
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return current;
        }

        private void advance(Duration duration) {
            current = current.plus(duration);
        }
    }
}
