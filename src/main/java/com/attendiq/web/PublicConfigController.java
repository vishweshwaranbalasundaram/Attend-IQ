package com.attendiq.web;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PublicConfigController {
    private final String institution;
    private final String allowedDomains;
    public PublicConfigController(@Value("${attendiq.institution-name:Your Institution}") String institution,
                                  @Value("${attendiq.auth.allowed-email-domains:.edu,.edu.in,.ac.in}") String allowedDomains) {
        this.institution=institution;this.allowedDomains=allowedDomains;
    }
    @GetMapping("/api/public/config")
    public Map<String,Object> config(){return Map.of("institutionName",institution,"emailDomainHint",allowedDomains,"application","Attend-IQ");}
}
