package com.iss.laps.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.iss.laps.service.CustomUserDetailsService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

/**
 * Validates JWT Bearer tokens on every API request.
 *
 * Security properties:
 * - Roles are loaded from the DB via CustomUserDetailsService, not from token claims (ASVS V4.1.1)
 * - Any validation failure returns 401 JSON — never a redirect (ASVS V13.1.3)
 * - Token parsing is pinned to HS256; alg:none rejected by JJWT 0.12.x (ASVS V3.5.2)
 * - exp validated automatically by JJWT parseSignedClaims() (ASVS V3.5.3)
 */
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTH_HEADER = "Authorization";
    /** Token endpoint does not require a Bearer token — skip filter. */
    private static final String TOKEN_ENDPOINT = "/api/v1/auth/token";

    private final JwtService jwtService;
    private final CustomUserDetailsService userDetailsService;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@jakarta.annotation.Nonnull HttpServletRequest request,
                                    @jakarta.annotation.Nonnull HttpServletResponse response,
                                    @jakarta.annotation.Nonnull FilterChain filterChain) throws ServletException, IOException {

        // Skip the token-issuance endpoint itself
        if (TOKEN_ENDPOINT.equals(request.getServletPath())) {
            filterChain.doFilter(request, response);
            return;
        }

        String authHeader = request.getHeader(AUTH_HEADER);
        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            sendUnauthorized(response);
            return;
        }

        String token = authHeader.substring(BEARER_PREFIX.length());

        if (!jwtService.validateToken(token)) {
            sendUnauthorized(response);
            return;
        }

        String username;
        try {
            username = jwtService.extractUsername(token);
        } catch (Exception e) {
            sendUnauthorized(response);
            return;
        }

        // Load authorities from DB — never trust claims in the token for role assignment
        UserDetails userDetails;
        try {
            userDetails = userDetailsService.loadUserByUsername(username);
        } catch (UsernameNotFoundException e) {
            sendUnauthorized(response);
            return;
        }

        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(authentication);

        filterChain.doFilter(request, response);
    }

    private void sendUnauthorized(HttpServletResponse response) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(),
                Map.of("error", "Unauthorized", "message", "Authentication failed"));
    }
}
