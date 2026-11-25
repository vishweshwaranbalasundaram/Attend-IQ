package com.attendiq.web;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class OnDutyController {
    private final OnDutyWorkflowService workflow;

    public OnDutyController(OnDutyWorkflowService workflow) {
        this.workflow = workflow;
    }

    @PostMapping("/student/requests/od")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> submit(@RequestBody OnDutyWorkflowService.SubmitRequest request,
            @AuthenticationPrincipal AccountPrincipal student) {
        return workflow.submit(request, student);
    }

    @GetMapping("/student/requests/od")
    public List<Map<String, Object>> ownRequests(@AuthenticationPrincipal AccountPrincipal student) {
        return workflow.ownRequests(student);
    }

    @GetMapping("/staff/requests/od")
    public List<Map<String, Object>> queue(@AuthenticationPrincipal AccountPrincipal staff) {
        return workflow.staffQueue(staff);
    }

    @PutMapping("/staff/requests/od/{requestId}")
    public Map<String, Object> review(@PathVariable("requestId") long requestId,
            @RequestBody OnDutyWorkflowService.ReviewRequest request,
            @AuthenticationPrincipal AccountPrincipal staff, HttpServletRequest servletRequest) {
        return workflow.review(requestId, request, staff, servletRequest.getRemoteAddr());
    }
}
