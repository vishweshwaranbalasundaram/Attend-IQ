package com.attendiq.web;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@ConditionalOnProperty(name = "attendiq.auth.dev-auth-bypass", havingValue = "true")
public class AuthService {
    private final JdbcTemplate db;
    private final EmailDomainPolicy emailPolicy;
    private final InstitutionalAccountDirectory accountDirectory;
    private final JwtService jwt;
    private final ObjectProvider<JavaMailSender> mailProvider;
    private final SecureRandom random = new SecureRandom();
    private final byte[] otpPepper;
    private final String fromAddress;
    private final boolean previewEnabled;
    private final int expiryMinutes;

    public AuthService(JdbcTemplate db, JwtService jwt, ObjectProvider<JavaMailSender> mailProvider,
            InstitutionalAccountDirectory accountDirectory,
            EmailDomainPolicy emailPolicy,
            @Value("${attendiq.auth.jwt-secret}") String secret,
            @Value("${attendiq.auth.otp-preview-enabled:true}") boolean previewEnabled,
            @Value("${attendiq.auth.otp-expiry-minutes:10}") int expiryMinutes,
            @Value("${attendiq.mail.from:no-reply@attendiq.local}") String fromAddress) {
        this.db=db; this.jwt=jwt; this.mailProvider=mailProvider; this.accountDirectory=accountDirectory;
        this.emailPolicy=emailPolicy;
        this.otpPepper=secret.getBytes(StandardCharsets.UTF_8);
        this.previewEnabled=previewEnabled; this.expiryMinutes=expiryMinutes; this.fromAddress=fromAddress;
    }

    @Transactional
    public Map<String,Object> requestOtp(String email, String requestedRole) {
        String normalized = normalizeEmail(email);
        if (!emailPolicy.allows(normalized)) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use an approved institutional email address.");
        AccountPrincipal account = accountFor(normalized);
        if (requestedRole != null && !requestedRole.isBlank() && !account.role().equalsIgnoreCase(requestedRole))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "This email is registered for the " + account.role().toLowerCase(Locale.ROOT) + " portal.");
        var recent=db.query("SELECT created_at FROM auth_otp WHERE email=?",(rs,n)->rs.getTimestamp(1),normalized);
        if(!recent.isEmpty()&&recent.getFirst().toInstant().plusSeconds(45).isAfter(Instant.now()))
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,"Wait before requesting another verification code.");
        String otp = String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
        db.update("INSERT INTO auth_otp(email,role,otp_hash,expires_at,failed_attempts,created_at) VALUES(?,?,?,DATE_ADD(CURRENT_TIMESTAMP, INTERVAL ? MINUTE),0,CURRENT_TIMESTAMP) ON DUPLICATE KEY UPDATE role=VALUES(role), otp_hash=VALUES(otp_hash), expires_at=VALUES(expires_at), failed_attempts=0, created_at=CURRENT_TIMESTAMP",
                normalized, account.role(), digest(normalized, otp), expiryMinutes);
        JavaMailSender sender = mailProvider.getIfAvailable();
        if (sender != null) {
            try {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setFrom(fromAddress); message.setTo(normalized); message.setSubject("Your Attend-IQ verification code");
                message.setText("Your Attend-IQ sign-in code is " + otp + ". It expires in " + expiryMinutes + " minutes.");
                sender.send(message);
            } catch (RuntimeException e) {
                if (!previewEnabled) throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery is unavailable. Try again later.");
            }
        } else if (!previewEnabled) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Email delivery is not configured.");
        }
        Map<String,Object> response = new java.util.LinkedHashMap<>();
        response.put("message", "A verification code was sent to " + mask(normalized));
        response.put("email", normalized); response.put("role", account.role()); response.put("expiresInSeconds", expiryMinutes * 60);
        if (previewEnabled) { response.put("previewOtp", otp); response.put("preview", true); }
        return response;
    }

    @Transactional(noRollbackFor=ResponseStatusException.class)
    public Map<String,Object> verifyOtp(String email, String code) {
        String normalized=normalizeEmail(email);
        if (code == null || !code.matches("\\d{6}")) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter the six-digit verification code.");
        Map<String,Object> otp;
        try { otp=db.queryForMap("SELECT role,otp_hash,expires_at,failed_attempts FROM auth_otp WHERE email=? FOR UPDATE", normalized); }
        catch (org.springframework.dao.EmptyResultDataAccessException e) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Request a new verification code."); }
        if (((Number)otp.get("failed_attempts")).intValue() >= 5) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many incorrect codes. Request a new code.");
        java.sql.Timestamp expires=(java.sql.Timestamp)otp.get("expires_at");
        if (expires.toInstant().isBefore(Instant.now())) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The verification code expired. Request a new one.");
        String role=(String)otp.get("role");
        String actual=(String)otp.get("otp_hash");
        if (!MessageDigest.isEqual(actual.getBytes(StandardCharsets.US_ASCII), digest(normalized,code).getBytes(StandardCharsets.US_ASCII))) {
            db.update("UPDATE auth_otp SET failed_attempts=failed_attempts+1 WHERE email=?", normalized);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "The verification code is incorrect.");
        }
        db.update("DELETE FROM auth_otp WHERE email=?", normalized);
        AccountPrincipal account=accountFor(normalized);
        if (!account.role().equals(role)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account role changed. Request a new code.");
        JwtService.IssuedToken token=jwt.issue(account);
        return Map.of("token",token.value(),"expiresAt",token.expiresAt(),"account",Map.of("id",account.accountId(),"email",account.email(),"fullName",account.fullName(),"role",account.role()));
    }

    public AccountPrincipal accountFor(String email) {
        return accountDirectory.accountFor(email);
    }

    public boolean isAllowedEmail(String email) { return emailPolicy.allows(email); }
    private String normalizeEmail(String email) { return email==null?"":email.trim().toLowerCase(Locale.ROOT); }
    private String digest(String email,String otp) {
        try { MessageDigest sha=MessageDigest.getInstance("SHA-256");return HexFormat.of().formatHex(sha.digest((email+":"+otp+":"+HexFormat.of().formatHex(otpPepper)).getBytes(StandardCharsets.UTF_8))); }
        catch(Exception e){throw new IllegalStateException("OTP digest unavailable",e);}
    }
    private String mask(String email){int at=email.indexOf('@');String name=email.substring(0,at);return (name.length()<3?name.substring(0,1):name.substring(0,2))+"•••"+email.substring(at);}
}
