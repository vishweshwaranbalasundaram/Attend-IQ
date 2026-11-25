package com.attendiq;

import com.attendiq.web.EmailDomainPolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmailDomainPolicyTest {
    @Test void defaultInstitutionalSuffixesAreAccepted(){
        var policy=new EmailDomainPolicy(".edu,.edu.in,.ac.in");
        assertTrue(policy.allows("student@campus.edu"));
        assertTrue(policy.allows("faculty@dept.university.edu.in"));
        assertTrue(policy.allows("person@college.ac.in"));
        assertFalse(policy.allows("person@gmail.com"));
        assertFalse(policy.allows("person@campus.edu.attacker.com"));
    }

    @Test void campusDomainCanBeConfigured(){
        var policy=new EmailDomainPolicy("campus.example,partner.edu");
        assertTrue(policy.allows("learner@campus.example"));
        assertTrue(policy.allows("learner@cs.campus.example"));
        assertFalse(policy.allows("learner@other.example"));
    }
}
