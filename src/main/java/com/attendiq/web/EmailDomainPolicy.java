package com.attendiq.web;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class EmailDomainPolicy {
    private static final Pattern EMAIL = Pattern.compile("^[A-Z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?(?:\\.[A-Z0-9](?:[A-Z0-9-]{0,61}[A-Z0-9])?)+$", Pattern.CASE_INSENSITIVE);
    private final List<String> allowedDomains;

    public EmailDomainPolicy(@Value("${attendiq.auth.allowed-email-domains:.edu,.edu.in,.ac.in}") String configuredDomains) {
        this.allowedDomains = Arrays.stream(configuredDomains.split(","))
                .map(String::trim).map(s -> s.replaceFirst("^@", "").replaceFirst("^\\.", ""))
                .filter(s -> !s.isBlank()).map(s -> s.toLowerCase(Locale.ROOT)).toList();
        if (allowedDomains.isEmpty()) throw new IllegalArgumentException("At least one allowed email domain is required.");
    }

    public boolean allows(String email) {
        if (email == null || email.length() > 254 || !EMAIL.matcher(email).matches()) return false;
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        String domain = normalized.substring(normalized.lastIndexOf('@') + 1);
        return allowedDomains.stream().anyMatch(allowed -> domain.equals(allowed) || domain.endsWith("." + allowed));
    }

    public List<String> allowedDomains() { return allowedDomains; }
}
