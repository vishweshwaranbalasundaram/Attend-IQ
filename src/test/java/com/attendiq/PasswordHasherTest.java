package com.attendiq;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class PasswordHasherTest {
    @Test
    void correctPasswordVerifies() {
        String stored = PasswordHasher.hash("correct-horse".toCharArray());
        assertTrue(PasswordHasher.verify("correct-horse".toCharArray(), stored));
    }

    @Test
    void wrongPasswordIsRejected() {
        String stored = PasswordHasher.hash("correct-horse".toCharArray());
        assertFalse(PasswordHasher.verify("battery-staple".toCharArray(), stored));
    }

    @Test
    void sameStoredValueIsNeverRepeatedBecauseSaltIsRandom() {
        char[] password = "correct-horse".toCharArray();
        assertNotEquals(PasswordHasher.hash(password), PasswordHasher.hash(password));
    }

    @Test
    void shortPasswordIsRefused() {
        assertThrows(IllegalArgumentException.class,
                () -> PasswordHasher.hash("short".toCharArray()));
    }

    @Test
    void malformedOrMissingStoredValueFailsClosed() {
        char[] password = "correct-horse".toCharArray();
        assertFalse(PasswordHasher.verify(password, null));
        assertFalse(PasswordHasher.verify(password, ""));
        assertFalse(PasswordHasher.verify(password, "not-a-hash"));
        assertFalse(PasswordHasher.verify(password, "pbkdf2_sha256$x$y$z"));
    }
}
