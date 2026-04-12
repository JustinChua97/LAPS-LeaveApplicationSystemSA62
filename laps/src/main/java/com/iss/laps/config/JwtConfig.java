package com.iss.laps.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Binds app.jwt.* properties from application.properties.
 * Secret is sourced from the JWT_SECRET environment variable — never hardcoded.
 *
 * Explicit getters/setters (no Lombok) so Spring Boot's configuration
 * metadata processor generates correct property hints.
 */
@Configuration
@ConfigurationProperties(prefix = "app.jwt")
public class JwtConfig {

    /** HS256 signing secret — must be >= 256 bits. Sourced from JWT_SECRET env var. */
    private String secret;

    /** Access token lifetime in milliseconds (default: 900000 = 15 min). */
    private long expirationMs;

    public String getSecret() {
        return secret;
    }

    public void setSecret(String secret) {
        this.secret = secret;
    }

    public long getExpirationMs() {
        return expirationMs;
    }

    public void setExpirationMs(long expirationMs) {
        this.expirationMs = expirationMs;
    }
}
