package com.attendiq.web;

import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@ConditionalOnProperty(name = "attendiq.auth.dev-auth-bypass", havingValue = "true")
public class AuthController {
    private final AuthService auth;
    public AuthController(AuthService auth){this.auth=auth;}

    @PostMapping("/request-otp")
    public Map<String,Object> requestOtp(@RequestBody OtpRequest request){return auth.requestOtp(request.email(),request.role());}

    @PostMapping("/verify-otp")
    public Map<String,Object> verifyOtp(@RequestBody OtpVerification request){return auth.verifyOtp(request.email(),request.code());}

    public record OtpRequest(String email,String role){}
    public record OtpVerification(String email,String code){}
}
