package com.suntravels.controller;

import com.suntravels.dto.AuthenticatedUserResponse;
import com.suntravels.dto.LoginRequest;
import com.suntravels.security.DatabaseUserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository contextRepository;
    private final SessionAuthenticationStrategy sessionStrategy;
    private final CsrfTokenRepository csrfRepository;
    private final SecurityContextHolderStrategy contextHolder = SecurityContextHolder.getContextHolderStrategy();

    // Initializes the auth controller and its required dependencies.
    public AuthController(AuthenticationManager authenticationManager,
            SecurityContextRepository contextRepository, SessionAuthenticationStrategy sessionStrategy,
            CsrfTokenRepository csrfRepository) {
        this.authenticationManager = authenticationManager;
        this.contextRepository = contextRepository;
        this.sessionStrategy = sessionStrategy;
        this.csrfRepository = csrfRepository;
    }

    // Generates and saves a CSRF token for subsequent authenticated requests.
    @GetMapping("/csrf")
    public Map<String, String> csrf(HttpServletRequest request, HttpServletResponse response) {
        CsrfToken token = csrfRepository.generateToken(request);
        csrfRepository.saveToken(token, request, response);
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    // Authenticates staff credentials and saves the signed-in session.
    @PostMapping("/login")
    public ResponseEntity<AuthenticatedUserResponse> login(@Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest, HttpServletResponse response) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated(request.username().trim(),
                            request.password()));
            sessionStrategy.onAuthentication(authentication, httpRequest, response);
            SecurityContext context = contextHolder.createEmptyContext();
            context.setAuthentication(authentication);
            contextHolder.setContext(context);
            contextRepository.saveContext(context, httpRequest, response);
            return ResponseEntity.ok(principal(authentication).safeProfile());
        } catch (org.springframework.security.core.AuthenticationException exception) {
            throw new BadCredentialsException("Invalid username or password");
        }
    }

    // Returns the signed-in staff member’s safe profile.
    @GetMapping("/me")
    public AuthenticatedUserResponse me(Authentication authentication) {
        return principal(authentication).safeProfile();
    }

    // Invalidates the staff session and clears its security context.
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(Authentication authentication, HttpServletRequest request,
            HttpServletResponse response) {
        new SecurityContextLogoutHandler().logout(request, response, authentication);
        return ResponseEntity.noContent().build();
    }

    // Retrieves the authenticated database user or rejects unauthenticated access.
    private DatabaseUserPrincipal principal(Authentication authentication) {
        if (authentication == null ||
                !(authentication.getPrincipal() instanceof DatabaseUserPrincipal principal)) {
            throw new BadCredentialsException("Authentication is required");
        }
        return principal;
    }

    // Returns a safe unauthorized response for invalid sign-in credentials.
    @ExceptionHandler(BadCredentialsException.class)
    ResponseEntity<Map<String, String>> invalidCredentials() {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(
                Map.of("message", "Invalid username or password."));
    }
}
