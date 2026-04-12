package com.iss.laps.dto;

import lombok.Getter;

/**
 * Response body for POST /api/v1/auth/token.
 * Contains only token metadata — no username, no role detail, no PII (ASVS V2.1.1).
 */
@Getter
public class AuthResponse {

    private final String accessToken;
    private final String tokenType = "Bearer";
    private final int expiresIn = 900;

    public AuthResponse(String accessToken) {
        this.accessToken = accessToken;
    }
}
