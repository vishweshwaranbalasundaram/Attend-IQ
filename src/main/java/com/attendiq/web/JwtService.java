package com.attendiq.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "attendiq.auth.dev-auth-bypass", havingValue = "true")
public class JwtService {
    private final byte[] secret;
    private final int tokenHours;
    private final ObjectMapper json;

    public JwtService(@Value("${attendiq.auth.jwt-secret}") String secret,
                      @Value("${attendiq.auth.token-hours:8}") int tokenHours,
                      ObjectMapper json) {
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        if (this.secret.length < 32) throw new IllegalArgumentException("ATTENDIQ_JWT_SECRET must be at least 32 bytes.");
        if (tokenHours < 1 || tokenHours > 24) throw new IllegalArgumentException("ATTENDIQ_TOKEN_HOURS must be between 1 and 24.");
        this.tokenHours = tokenHours;
        this.json = json;
    }

    public IssuedToken issue(AccountPrincipal account) {
        long now = Instant.now().getEpochSecond();
        long expiry = now + tokenHours * 3600L;
        try {
            String header = encode(json.writeValueAsBytes(Map.of("alg", "HS256", "typ", "JWT")));
            Map<String, Object> claims = new LinkedHashMap<>();
            claims.put("sub", Long.toString(account.accountId()));
            claims.put("email", account.email());
            claims.put("name", account.fullName());
            claims.put("role", account.role());
            claims.put("iat", now);
            claims.put("exp", expiry);
            String payload = encode(json.writeValueAsBytes(claims));
            String signingInput = header + "." + payload;
            return new IssuedToken(signingInput + "." + encode(hmac(signingInput)), expiry);
        } catch (Exception e) { throw new IllegalStateException("Could not issue access token.", e); }
    }

    public AccountPrincipal verify(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) throw new IllegalArgumentException("Invalid access token.");
            String signingInput = parts[0] + "." + parts[1];
            byte[] supplied = Base64.getUrlDecoder().decode(parts[2]);
            if (!java.security.MessageDigest.isEqual(hmac(signingInput), supplied)) throw new IllegalArgumentException("Invalid access token signature.");
            JsonNode claims = json.readTree(Base64.getUrlDecoder().decode(parts[1]));
            if (!"HS256".equals(json.readTree(Base64.getUrlDecoder().decode(parts[0])).path("alg").asText())) throw new IllegalArgumentException("Unsupported access token.");
            if (claims.path("exp").asLong(0) <= Instant.now().getEpochSecond()) throw new IllegalArgumentException("Access token expired.");
            String role = claims.path("role").asText();
            if (!role.equals("STUDENT") && !role.equals("STAFF")) throw new IllegalArgumentException("Invalid role claim.");
            return new AccountPrincipal(Long.parseLong(claims.path("sub").asText()), claims.path("email").asText(), role, claims.path("name").asText());
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException iae) throw iae;
            throw new IllegalArgumentException("Invalid access token.", e);
        }
    }

    private byte[] hmac(String input) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret, "HmacSHA256"));
        return mac.doFinal(input.getBytes(StandardCharsets.UTF_8));
    }
    private String encode(byte[] bytes) { return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    public record IssuedToken(String value, long expiresAt) {}
}
