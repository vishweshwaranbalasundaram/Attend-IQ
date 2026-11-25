package com.attendiq.web;

public record AccountPrincipal(
        long accountId,
        String email,
        String role,
        String fullName,
        Integer yearOfJoining,
        String department) {
    public AccountPrincipal(long accountId, String email, String role, String fullName) {
        this(accountId, email, role, fullName, null, null);
    }
}
