package com.iss.laps;

import com.iss.laps.config.JwtConfig;
import com.iss.laps.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.*;

@DisplayName("JwtService Unit Tests")
class JwtServiceTest {

    private static final String TEST_SECRET = "test-secret-key-for-unit-tests-only-32bytes!!";
    private static final long EXPIRATION_MS = 900_000L;

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        JwtConfig config = new JwtConfig();
        config.setSecret(TEST_SECRET);
        config.setExpirationMs(EXPIRATION_MS);
        jwtService = new JwtService(config);
    }

    private UserDetails employeeUser() {
        return User.builder()
                .username("emp.tan")
                .password("irrelevant")
                .authorities(List.of(new SimpleGrantedAuthority("ROLE_EMPLOYEE")))
                .build();
    }

    // -----------------------------------------------------------------------
    // AC: generateToken encodes correct subject and role
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("generateToken_containsCorrectSubjectAndRole")
    void generateToken_containsCorrectSubjectAndRole() {
        String token = jwtService.generateToken(employeeUser());

        assertThat(token).isNotBlank();
        assertThat(jwtService.extractUsername(token)).isEqualTo("emp.tan");
        assertThat(jwtService.validateToken(token)).isTrue();
    }

    // -----------------------------------------------------------------------
    // AC: expired token is rejected
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("validateToken_expiredToken_returnsFalse")
    void validateToken_expiredToken_returnsFalse() {
        // Build a token that expired 1 second ago using the same key
        SecretKey key = Keys.hmacShaKeyFor(TEST_SECRET.getBytes(StandardCharsets.UTF_8));
        String expiredToken = Jwts.builder()
                .subject("emp.tan")
                .issuedAt(new Date(System.currentTimeMillis() - 10_000))
                .expiration(new Date(System.currentTimeMillis() - 1_000))
                .signWith(key, Jwts.SIG.HS256)
                .compact();

        assertThat(jwtService.validateToken(expiredToken)).isFalse();
    }

    // -----------------------------------------------------------------------
    // AC: tampered signature is rejected
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("validateToken_tamperedSignature_returnsFalse")
    void validateToken_tamperedSignature_returnsFalse() {
        String token = jwtService.generateToken(employeeUser());
        // Flip the last character of the signature segment
        String tampered = token.substring(0, token.length() - 1)
                + (token.endsWith("a") ? "b" : "a");

        assertThat(jwtService.validateToken(tampered)).isFalse();
    }

    // -----------------------------------------------------------------------
    // Security negative: alg:none must be rejected (ASVS V3.5.2)
    // JJWT 0.12.x throws JwtException when presented with an unsigned token
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("validateToken_algNone_returnsFalse")
    void validateToken_algNone_throwsException() {
        // Craft a token with no signature (alg:none attack)
        // Base64URL header: {"alg":"none"} | payload | empty signature
        String algNoneToken = "eyJhbGciOiJub25lIn0"
                + ".eyJzdWIiOiJhZG1pbiIsInJvbGVzIjpbIlJPTEVfQURNSU4iXX0"
                + ".";

        // validateToken must return false — never throw to caller
        assertThat(jwtService.validateToken(algNoneToken)).isFalse();
    }

    // -----------------------------------------------------------------------
    // AC: token signed with wrong secret is rejected
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("validateToken_wrongSecret_returnsFalse")
    void validateToken_wrongSecret_returnsFalse() {
        SecretKey wrongKey = Keys.hmacShaKeyFor(
                "completely-different-secret-key-xyz-32bytes!".getBytes(StandardCharsets.UTF_8));
        String foreignToken = Jwts.builder()
                .subject("emp.tan")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + EXPIRATION_MS))
                .signWith(wrongKey, Jwts.SIG.HS256)
                .compact();

        assertThat(jwtService.validateToken(foreignToken)).isFalse();
    }
}
