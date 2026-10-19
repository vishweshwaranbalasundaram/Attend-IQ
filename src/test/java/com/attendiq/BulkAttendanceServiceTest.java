package com.attendiq;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class BulkAttendanceServiceTest {
    @Test
    void skipsExistingAndDuplicateRosterRowsAndReportsPartialFailure() throws SQLException {
        StubAttendanceService attendance = new StubAttendanceService();
        attendance.existing.add(2L);
        attendance.failOn.add(3L);
        BulkAttendanceService service = new BulkAttendanceService(attendance);
        List<BulkAttendanceRequest> requests = new ArrayList<>(List.of(
                request(1, AttendanceStatus.PRESENT), request(2, AttendanceStatus.ABSENT),
                request(3, AttendanceStatus.ON_DUTY), request(1, AttendanceStatus.ABSENT)));
        requests.add(null);

        BulkAttendanceResult result = service.mark(4, requests);

        assertEquals(List.of(1L), result.savedStudentIds());
        assertEquals(List.of(2L), result.skippedStudentIds());
        assertEquals(3, result.failures().size());
        assertEquals(Set.of(1L), attendance.saved);
    }

    @Test
    void rejectsInvalidSessionBeforeCallingAttendanceService() {
        BulkAttendanceService service = new BulkAttendanceService(new StubAttendanceService());

        assertEquals("Session ID must be positive.", assertThrows(IllegalArgumentException.class,
                () -> service.mark(0, List.of())).getMessage());
    }

    private BulkAttendanceRequest request(long id, AttendanceStatus status) {
        return new BulkAttendanceRequest(id, status);
    }

    private static final class StubAttendanceService extends AttendanceService {
        private final Set<Long> existing = new HashSet<>();
        private final Set<Long> failOn = new HashSet<>();
        private final Set<Long> saved = new HashSet<>();

        private StubAttendanceService() {
            super(new AttendanceRepository());
        }

        @Override
        public Optional<AttendanceRecord> getAttendance(long sessionId, long studentId) {
            if (existing.contains(studentId)) {
                return Optional.of(record(sessionId, studentId));
            }
            return Optional.empty();
        }

        @Override
        public AttendanceRecord markAttendance(long sessionId, long studentId,
                                               AttendanceStatus status) throws SQLException {
            if (failOn.contains(studentId)) {
                throw new SQLException("Private driver detail", "23000");
            }
            saved.add(studentId);
            return record(sessionId, studentId);
        }

        private AttendanceRecord record(long sessionId, long studentId) {
            return new AttendanceRecord(1, sessionId, studentId, "R" + studentId,
                    "Student " + studentId, AttendanceStatus.PRESENT, LocalDateTime.now());
        }
    }
}