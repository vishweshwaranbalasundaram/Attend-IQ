package com.attendiq;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** Pure lockout policy so its timing rules can be tested without MySQL. */
public final class LoginAttemptPolicy {
    public static final int MAX_FAILURES = 5;
    public static final Duration LOCKOUT_DURATION = Duration.ofMinutes(5);

    private final Clock clock;

    public LoginAttemptPolicy(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public boolean isLocked(Instant lockedUntil) {
        return lockedUntil != null && clock.instant().isBefore(lockedUntil);
    }

    public AttemptState recordFailure(int previousFailures, Instant previousLock) {
        if (previousFailures < 0) {
            throw new IllegalArgumentException("Failed-attempt count cannot be negative.");
        }
        Instant now = clock.instant();
        int failures = previousLock != null && !now.isBefore(previousLock)
                ? 1 : previousFailures + 1;
        Instant lockedUntil = failures >= MAX_FAILURES
                ? now.plus(LOCKOUT_DURATION) : null;
        return new AttemptState(failures, lockedUntil);
    }

    public record AttemptState(int failedAttempts, Instant lockedUntil) {}
}
