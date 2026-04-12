package com.iss.laps.controller.rest;

import com.iss.laps.dto.AuthRequest;
import com.iss.laps.dto.AuthResponse;
import com.iss.laps.security.JwtService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Issues JWT access tokens for the stateless REST API.
 *
 * POST /api/v1/auth/token — no authentication required (permitted in API SecurityFilterChain).
 *
 * Security properties:
 * - Generic 401 on any failure — no username enumeration (ASVS V7.4.1)
 * - No PII in response body (ASVS V2.1.1)
 * - Input validated via @Valid before authentication attempt (ASVS V5.1.3)
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class JwtAuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    @PostMapping("/token")
    public ResponseEntity<?> issueToken(@Valid @RequestBody AuthRequest request) {
        Authentication auth = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
        );
        UserDetails userDetails = (UserDetails) auth.getPrincipal();
        String token = jwtService.generateToken(userDetails);
        return ResponseEntity.ok(new AuthResponse(token));
    }

    /**
     * Catch all Spring Security authentication failures (bad credentials, disabled account, etc.)
     * and return a generic 401. Never expose e.getMessage() to the caller (ASVS V7.4.1).
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, String>> handleAuthenticationException(AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(Map.of("error", "Unauthorized", "message", "Authentication failed"));
    }

    /**
     * Handle @Valid failures on AuthRequest (blank username/password, size violations).
     * Returns 400 JSON so the REST client gets a proper error — not the Thymeleaf HTML
     * view that GlobalExceptionHandler would otherwise render (CLAUDE.md: reject-not-sanitize).
     */
    @org.springframework.web.bind.annotation.ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, String>> handleValidationException(MethodArgumentNotValidException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Bad Request", "message", "Invalid request parameters"));
    }
}
