package com.attendiq.web;

import java.util.Arrays;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
public class SecurityConfiguration {
    @Bean SecurityFilterChain attendIqSecurity(HttpSecurity http,FirebaseAuthenticationFilter jwtFilter,
            @Value("${attendiq.auth.dev-auth-bypass:false}") boolean devAuthBypass)throws Exception{
        var authorization = http.cors(cors -> {}).csrf(csrf->csrf.disable())
            .sessionManagement(s->s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .exceptionHandling(e->e.authenticationEntryPoint((request,response,exception)->{
                response.setStatus(HttpStatus.UNAUTHORIZED.value());response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Authentication is required.\"}");
            }).accessDeniedHandler((request,response,exception)->{
                response.setStatus(HttpStatus.FORBIDDEN.value());response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"You do not have access to this resource.\"}");
            }))
            .authorizeHttpRequests(auth->{
                auth.requestMatchers("/","/index.html","/favicon.ico","/error","/images/**","/css/**","/js/**").permitAll()
                .requestMatchers(HttpMethod.GET,"/api/public/config").permitAll()
                .requestMatchers("/api/health").permitAll();
                if(devAuthBypass) auth.requestMatchers(HttpMethod.POST,"/api/auth/request-otp","/api/auth/verify-otp").permitAll();
                auth.requestMatchers("/api/student/**").hasRole("STUDENT")
                .requestMatchers("/api/staff/**").hasRole("STAFF")
                .requestMatchers("/api/auth/me").authenticated()
                .requestMatchers("/api/**").authenticated()
                .anyRequest().permitAll();
            })
            .addFilterBefore(jwtFilter,UsernamePasswordAuthenticationFilter.class);
        return authorization.build();
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(
            @Value("${attendiq.web.allowed-origins:http://localhost:3000}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(origin -> !origin.isEmpty())
                .toList());
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("Content-Disposition"));
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", configuration);
        return source;
    }
}
