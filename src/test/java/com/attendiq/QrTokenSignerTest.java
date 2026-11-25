package com.attendiq;

import com.attendiq.web.QrTokenSigner;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class QrTokenSignerTest {
    private final QrTokenSigner signer = new QrTokenSigner("unit-test-signing-secret-with-32-bytes");

    @Test
    void bindsTokensToSessionAndThirtySecondRotationBucket() {
        String token = signer.issue(12, 100);
        assertTrue(signer.verifies(token, 12, 100));
        assertFalse(signer.verifies(token, 13, 100));
        assertFalse(signer.verifies(token, 12, 101));
    }

    @Test
    void hashesOnlyTheOpaqueTokenAndRejectsTampering() {
        String token = signer.issue(12, 100);
        String altered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");
        assertNotEquals(token, signer.hash(token));
        assertFalse(signer.verifies(altered, 12, 100));
    }

    @Test
    void requiresAProductionLengthSecret() {
        assertThrows(IllegalArgumentException.class, () -> new QrTokenSigner("too-short"));
    }
}
