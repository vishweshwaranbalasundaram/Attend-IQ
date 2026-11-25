package com.attendiq.web;

import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public final class InstitutionalIdentityPolicy {
    private final String domain;
    private final Pattern staffEmail;
    private final Pattern studentEmail;

    public InstitutionalIdentityPolicy(@Value("${attendiq.auth.allowed-email-domain:example.edu}") String allowedDomain) {
        domain = allowedDomain == null ? "" : allowedDomain.trim().toLowerCase(Locale.ROOT);
        if (domain.isBlank() || domain.contains("@") || domain.startsWith(".") || domain.endsWith(".")) {
            throw new IllegalArgumentException("A valid institutional email domain is required.");
        }
        String suffix = "@" + Pattern.quote(domain);
        staffEmail = Pattern.compile("^[a-z]{1,4}\\.[a-z]+" + suffix + "$");
        studentEmail = Pattern.compile("^([a-z]+)\\.([a-z]{1,4})\\.(20\\d{2})\\.([a-z]{2,10})"
                + suffix + "$");
    }

    public Identity identify(String email) {
        if (email == null || email.length() > 254) {
            throw new IllegalArgumentException("Use a valid institutional email address.");
        }
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        if (staffEmail.matcher(normalized).matches()) {
            return new Identity(normalized, "STAFF", null, null);
        }
        Matcher student = studentEmail.matcher(normalized);
        if (student.matches()) {
            return new Identity(normalized, "STUDENT", Integer.valueOf(student.group(3)), student.group(4));
        }
        throw new IllegalArgumentException("Email must match the institutional student or staff format.");
    }

    public String domain() {
        return domain;
    }

    public record Identity(String email, String role, Integer yearOfJoining, String department) {}
}
