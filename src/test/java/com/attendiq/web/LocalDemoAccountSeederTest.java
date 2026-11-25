package com.attendiq.web;

import java.util.HashSet;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class LocalDemoAccountSeederTest {
    private final InstitutionalIdentityPolicy identityPolicy =
            new InstitutionalIdentityPolicy("example.edu");

    @Test
    void generatesUniqueInstitutionalDemoAccountsForBothRoles() {
        List<LocalDemoAccountSeeder.StudentSeed> students =
                LocalDemoAccountSeeder.generateStudents("example.edu");
        List<LocalDemoAccountSeeder.StaffSeed> staff =
                LocalDemoAccountSeeder.generateStaff("example.edu");
        var emails = new HashSet<String>();

        assertEquals(450, students.size());
        assertEquals(50, staff.size());
        for (LocalDemoAccountSeeder.StudentSeed student : students) {
            InstitutionalIdentityPolicy.Identity identity = identityPolicy.identify(student.email());
            assertEquals("STUDENT", identity.role());
            assertEquals(student.joiningYear(), identity.yearOfJoining());
            assertEquals(student.department(), identity.department());
            assertTrue(emails.add(student.email()));
        }
        for (LocalDemoAccountSeeder.StaffSeed member : staff) {
            assertEquals("STAFF", identityPolicy.identify(member.email()).role());
            assertTrue(emails.add(member.email()));
        }
    }
}
