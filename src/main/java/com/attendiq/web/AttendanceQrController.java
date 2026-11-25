package com.attendiq.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AttendanceQrController {
    private final QrAttendanceService attendance;

    public AttendanceQrController(QrAttendanceService attendance) {
        this.attendance = attendance;
    }

    @PostMapping("/staff/sessions/{sessionId}/qr")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> startQr(@PathVariable("sessionId") long sessionId,
            @RequestBody(required = false) StartQrRequest request,
            @AuthenticationPrincipal AccountPrincipal staff) {
        return attendance.start(sessionId, request == null ? null : request.durationMinutes(), staff);
    }

    @GetMapping("/staff/sessions/{sessionId}/qr")
    public Map<String, Object> currentQr(@PathVariable("sessionId") long sessionId,
            @AuthenticationPrincipal AccountPrincipal staff) {
        return attendance.current(sessionId, staff);
    }

    @PostMapping("/student/scan")
    public Map<String, Object> scan(@RequestBody QrAttendanceService.ScanRequest request,
            @AuthenticationPrincipal AccountPrincipal student, HttpServletRequest servletRequest) {
        return attendance.scan(request, student, servletRequest.getRemoteAddr());
    }

    public record StartQrRequest(Integer durationMinutes) {}
}
