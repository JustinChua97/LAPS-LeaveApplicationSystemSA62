package com.iss.laps.security;

import com.iss.laps.config.JwtConfig;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

/**
 * JWT generation and validation service.
 *
 * Algorithm: HS256 (pinned — JJWT 0.12.x rejects alg:none by default).
 * Claims encoded: sub (username), iat, exp, roles (Spring authority strings).
 * No PII (email, password) is included in the token payload (ASVS V2.1.1).
 */
@Service
@RequiredArgsConstructor
public class JwtService {

    private final JwtConfig jwtConfig;

    /**
     * Generate a signed JWT for the given user.
     * Expiry is set to jwtConfig.expirationMs milliseconds from now.
     */
    public String generateToken(UserDetails userDetails) {
        List<String> roles = userDetails.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(userDetails.getUsername())
                .claim("roles", roles)
                .issuedAt(new Date(now))
                .expiration(new Date(now + jwtConfig.getExpirationMs()))
                .signWith(signingKey(), Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Validate a token: verifies signature (HS256 pinned) and expiry.
     * Returns false — never throws — on any validation failure so callers
     * can produce a uniform 401 response without leaking internal detail.
     */
    public boolean validateToken(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException e) {
            return false;
        }
    }

    /**
     * Extract the subject (username) from a token.
     * Callers must call validateToken() first.
     */
    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    // --- private helpers ---

    private Claims parseClaims(String token) {
        return Jwts.parser()
                .verifyWith(signingKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    private SecretKey signingKey() {
        return Keys.hmacShaKeyFor(jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8));
    }
}
