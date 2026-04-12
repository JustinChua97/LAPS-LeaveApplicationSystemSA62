package com.iss.laps.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Request body for POST /api/v1/auth/token.
 * Validated server-side via @Valid at the controller boundary (ASVS V5.1.3).
 */
@Getter
@Setter
public class AuthRequest {

    @NotBlank
    @Size(max = 50)
    private String username;

    @NotBlank
    @Size(max = 128)
    private String password;
}
