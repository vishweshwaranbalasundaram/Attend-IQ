package com.attendiq.web;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class CurrentAccountController {
    @GetMapping("/me")
    public AccountPrincipal currentAccount(@AuthenticationPrincipal AccountPrincipal principal) {
        return principal;
    }
}
