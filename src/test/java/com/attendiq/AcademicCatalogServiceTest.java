package com.attendiq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Proxy;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class AcademicCatalogServiceTest {
    @Test
    void validatesCatalogInputsBeforeRepositoryCalls() {
        AcademicCatalogService service = new AcademicCatalogService(repository(new AtomicInteger()));

        assertEquals("IDs must be positive.", assertThrows(IllegalArgumentException.class,
                () -> service.findFaculty(0)).getMessage());
        assertEquals("Full name is required.", assertThrows(IllegalArgumentException.class,
                () -> service.createFaculty(" ", "faculty@example.edu")).getMessage());
        assertEquals("Enter a valid email address.", assertThrows(IllegalArgumentException.class,
                () -> service.createFaculty("Name", "bad-email")).getMessage());
        assertEquals("Subject code is required.", assertThrows(IllegalArgumentException.class,
                () -> service.createSubject("", "Name", 1)).getMessage());
        assertEquals("Enter a valid date range with the end on or after the start.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.sessionsForSubjectAndDates(1,
                                LocalDate.now(), LocalDate.now().minusDays(1))).getMessage());
        assertEquals("Class date is required.", assertThrows(IllegalArgumentException.class,
                () -> service.createSession(1, null, 1)).getMessage());
        assertEquals("Period must be between 1 and 12.", assertThrows(IllegalArgumentException.class,
                () -> service.createSession(1, LocalDate.now(), 0)).getMessage());
        assertEquals("Target percentage must be between 1 and 100.",
                assertThrows(IllegalArgumentException.class,
                        () -> service.shortageReports(0)).getMessage());
    }

    @Test
    void delegatesCatalogOperationsAfterNormalizingAndValidatingInputs() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        AcademicCatalogService service = new AcademicCatalogService(repository(calls));

        service.listFaculty();
        service.findFaculty(1);
        service.createFaculty(" Faculty ", "faculty@example.edu ");
        service.updateFaculty(1, " Faculty ", "faculty@example.edu ");
        service.deleteFaculty(1);
        service.listSubjects();
        service.findSubject(1);
        service.subjectsForFaculty(1);
        service.createSubject(" CS1 ", " Course ", 1);
        service.updateSubject(1, " CS1 ", " Course ", 1);
        service.deleteSubject(1);
        service.listEnrollments();
        service.subjectsForStudent(1);
        service.studentsForSubject(1);
        service.createEnrollment(1, 1);
        service.deleteEnrollment(1, 1);
        service.listSessions();
        service.findSession(1);
        service.sessionsForSubjectAndDates(1, LocalDate.now(), LocalDate.now());
        service.createSession(1, LocalDate.now(), 1);
        service.updateSession(1, 1, LocalDate.now(), 2);
        service.deleteSession(1);
        service.attendancePercentage(1, 1);
        service.subjectAttendanceSummaries();
        service.shortageReports(75);

        assertEquals(25, calls.get());
    }

    private AcademicCatalogRepository repository(AtomicInteger calls) {
        return (AcademicCatalogRepository) Proxy.newProxyInstance(
                AcademicCatalogRepository.class.getClassLoader(),
                new Class<?>[]{AcademicCatalogRepository.class},
                (proxy, method, arguments) -> {
                    calls.incrementAndGet();
                    Class<?> resultType = method.getReturnType();
                    if (resultType == List.class) return List.of();
                    if (resultType == Optional.class) {
                        return Optional.of(switch (method.getName()) {
                            case "findFaculty" -> new Faculty(1, "Faculty", "faculty@example.edu");
                            case "findSubject" -> new Subject(1, "CS1", "Course", 1);
                            default -> new ClassSession(1, 1, LocalDate.now(), 1,
                                    LocalDateTime.now());
                        });
                    }
                    if (resultType == boolean.class) return false;
                    if (resultType == Faculty.class) return new Faculty(1, "Faculty", "faculty@example.edu");
                    if (resultType == Subject.class) return new Subject(1, "CS1", "Course", 1);
                    if (resultType == Enrollment.class) return new Enrollment(1, 1, LocalDateTime.now());
                    if (resultType == ClassSession.class) {
                        return new ClassSession(1, 1, LocalDate.now(), 1, LocalDateTime.now());
                    }
                    if (resultType == AttendancePercentage.class) {
                        return new AttendancePercentage(1, 1, 0, 0, 0, 0, 0, 0);
                    }
                    return null;
                });
    }
}
