package com.attendiq.web;

import java.util.Locale;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class InstitutionalAccountDirectory {
    private final JdbcTemplate db;

    public InstitutionalAccountDirectory(JdbcTemplate db) {
        this.db = db;
    }

    public AccountPrincipal accountFor(String email) {
        String normalized = email.trim().toLowerCase(Locale.ROOT);
        var students = db.query("""
                SELECT student_id id,full_name name FROM students WHERE LOWER(email)=LOWER(?)
                """, (rs, row) -> new AccountPrincipal(rs.getLong("id"), normalized, "STUDENT", rs.getString("name")), normalized);
        var staff = db.query("""
                SELECT faculty_id id,full_name name FROM faculty WHERE LOWER(email)=LOWER(?)
                """, (rs, row) -> new AccountPrincipal(rs.getLong("id"), normalized, "STAFF", rs.getString("name")), normalized);
        if (students.size() + staff.size() == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No Attend-IQ account is registered for this email.");
        }
        if (students.size() + staff.size() > 1) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Email is assigned to more than one account role. Contact an administrator.");
        }
        return students.isEmpty() ? staff.getFirst() : students.getFirst();
    }
}
