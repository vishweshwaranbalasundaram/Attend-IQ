package com.attendiq.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class QrTokenSigner {
    private final byte[] secret;

    public QrTokenSigner(@Value("${attendiq.attendance.qr-hmac-secret}") String secret) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) {
            throw new IllegalArgumentException("ATTENDIQ_QR_HMAC_SECRET must be at least 32 bytes.");
        }
    }

    public String issue(long qrSessionId, long bucket) {
        String payload = qrSessionId + ":" + bucket;
        return payload + "." + Base64.getUrlEncoder().withoutPadding().encodeToString(sign(payload));
    }

    public boolean verifies(String token, long expectedSessionId, long expectedBucket) {
        if (token == null || token.length() > 256) return false;
        String[] parts = token.split("\\.", -1);
        if (parts.length != 2) return false;
        String payload = expectedSessionId + ":" + expectedBucket;
        if (!parts[0].equals(payload)) return false;
        try {
            byte[] signature = Base64.getUrlDecoder().decode(parts[1]);
            return MessageDigest.isEqual(sign(payload), signature);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    public String hash(String token) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is unavailable.", e);
        }
    }

    private byte[] sign(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (java.security.GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 is unavailable.", e);
        }
    }
}
