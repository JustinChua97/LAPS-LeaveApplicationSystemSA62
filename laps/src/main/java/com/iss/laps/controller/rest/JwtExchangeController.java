package com.iss.laps.controller.rest;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import com.iss.laps.dto.AuthResponse;
import com.iss.laps.security.JwtService;

import lombok.RequiredArgsConstructor;

// Exchanges the JSESSIONID session cookie for a JWT
// We do this instead of asking the user to login again to get the JWT
@RestController
@RequiredArgsConstructor
public class JwtExchangeController {

    private final JwtService jwtService;

    @GetMapping("/auth/jwt")
    public ResponseEntity<AuthResponse> exchange(Authentication authentication) {
        UserDetails userDetails = (UserDetails) authentication.getPrincipal();
        return ResponseEntity.ok(new AuthResponse(jwtService.generateToken(userDetails)));
    }
}
