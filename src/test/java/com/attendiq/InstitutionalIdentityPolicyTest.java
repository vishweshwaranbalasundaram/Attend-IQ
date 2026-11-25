package com.attendiq;

import com.attendiq.web.InstitutionalIdentityPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InstitutionalIdentityPolicyTest {
    private final InstitutionalIdentityPolicy policy =
            new InstitutionalIdentityPolicy("example.edu");

    @Test
    void identifiesStaffAndStudentFromExactInstitutionalFormats() {
        assertEquals("STAFF", policy.identify("k.arun@example.edu").role());

        var student = policy.identify("vishweshwaran.v.2025.cse@example.edu");
        assertEquals("STUDENT", student.role());
        assertEquals(2025, student.yearOfJoining());
        assertEquals("cse", student.department());
    }

    @Test
    void rejectsUnapprovedDomainsAndNearMatchFormats() {
        assertThrows(IllegalArgumentException.class, () -> policy.identify("k.arun@sub.example.edu"));
        assertThrows(IllegalArgumentException.class, () -> policy.identify("k.arun@gmail.com"));
        assertThrows(IllegalArgumentException.class, () -> policy.identify("name.initial.2019.cse@example.edu"));
        assertThrows(IllegalArgumentException.class, () -> policy.identify("name.i.2025.c@example.edu"));
    }

    @Test
    void usesConfiguredDomainWithoutAcceptingSubdomains() {
        var custom = new InstitutionalIdentityPolicy("campus.example");
        assertEquals("STAFF", custom.identify("ab.name@campus.example").role());
        assertThrows(IllegalArgumentException.class, () -> custom.identify("ab.name@dept.campus.example"));
    }
}
