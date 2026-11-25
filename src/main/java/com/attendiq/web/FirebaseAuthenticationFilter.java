package com.attendiq.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import com.google.firebase.auth.FirebaseAuthException;

@Component
public class FirebaseAuthenticationFilter extends OncePerRequestFilter {
    private final ObjectProvider<JwtService> jwt;
    private final ObjectProvider<FirebaseIdTokenVerifier> firebase;
    private final InstitutionalAccountDirectory accounts;
    private final InstitutionalIdentityPolicy identityPolicy;
    private final boolean devAuthBypass;
    private final Environment environment;

    public FirebaseAuthenticationFilter(ObjectProvider<JwtService> jwt, ObjectProvider<FirebaseIdTokenVerifier> firebase,
            InstitutionalAccountDirectory accounts,
            InstitutionalIdentityPolicy identityPolicy,
            @Value("${attendiq.auth.dev-auth-bypass:false}") boolean devAuthBypass,
            Environment environment) {
        this.jwt = jwt;
        this.firebase = firebase;
        this.accounts = accounts;
        this.identityPolicy = identityPolicy;
        this.devAuthBypass = devAuthBypass;
        this.environment = environment;
        if (devAuthBypass && environment.acceptsProfiles(Profiles.of("prod"))) {
            throw new IllegalStateException("DEV_AUTH_BYPASS cannot be enabled with the prod profile.");
        }
    }

    @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)
            throws ServletException,IOException {
        String authorization=request.getHeader(HttpHeaders.AUTHORIZATION);
        if(authorization!=null&&authorization.startsWith("Bearer ")){
            try{
                String token = authorization.substring(7).trim();
                AccountPrincipal principal = devAuthBypass
                        ? resolveDevelopmentToken(token)
                        : resolveFirebaseToken(token);
                var authentication=new UsernamePasswordAuthenticationToken(principal,null,
                        List.of(new SimpleGrantedAuthority("ROLE_"+principal.role())));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } catch (ResponseStatusException e) {
                response.setStatus(e.getStatusCode().value());
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"" + jsonMessage(e.getReason()) + "\"}");
                return;
            } catch (FirebaseAuthException | IllegalArgumentException e) {
                response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Invalid or expired Firebase ID token.\"}");
                return;
            }
        }
        chain.doFilter(request,response);
    }

    private AccountPrincipal resolveFirebaseToken(String token) throws FirebaseAuthException {
        FirebaseIdTokenVerifier verifier = firebase.getIfAvailable();
        if (verifier == null) {
            throw new IllegalStateException("Firebase Authentication is not configured.");
        }
        var claims = verifier.verify(token).getClaims();
        Object emailValue = claims.get("email");
        Object verifiedValue = claims.get("email_verified");
        if (!(emailValue instanceof String email) || !Boolean.TRUE.equals(verifiedValue)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Verify your institutional email before signing in.");
        }
        InstitutionalIdentityPolicy.Identity identity;
        try {
            identity = identityPolicy.identify(email);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, e.getMessage());
        }
        AccountPrincipal account = accounts.accountFor(identity.email());
        if (!account.role().equals(identity.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Your account role does not match your institutional email.");
        }
        return new AccountPrincipal(account.accountId(), account.email(), account.role(), account.fullName(),
                identity.yearOfJoining(), identity.department());
    }

    private AccountPrincipal resolveDevelopmentToken(String token) {
        JwtService developmentJwt = jwt.getIfAvailable();
        if (developmentJwt == null) throw new IllegalStateException("Development authentication is not configured.");
        AccountPrincipal signed = developmentJwt.verify(token);
        return accounts.accountFor(signed.email().toLowerCase(Locale.ROOT));
    }

    private String jsonMessage(String message) {
        if (message == null) return "Request denied.";
        return message.replace("\\", "\\\\").replace("\"", "\\\"")
                .replace("\r", " ").replace("\n", " ");
    }
}
