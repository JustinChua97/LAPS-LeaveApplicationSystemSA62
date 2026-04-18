package com.iss.laps;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration tests for REST API JSON error handling (issue #46).
 *
 * Verifies that /api/v1 endpoints return structured JSON errors
 * — never HTML pages or stack traces — for all error conditions.
 *
 * Uses the 'test' profile (H2 in-memory). Seed accounts from DataInitializer.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("REST API JSON Error Handling Tests (issue #46)")
class LeaveRestApiTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @Value("${app.seed.password}")
    String seedPassword;

    private static final String TOKEN_URL = "/api/v1/auth/token";

    // -----------------------------------------------------------------------
    // AC2 — missing leave ID returns JSON 404, not HTML
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AC2: missing leave ID returns JSON 404 with error/message fields")
    void apiLeave_missingId_returnsJson404() throws Exception {
        String token = obtainToken("emp.tan", seedPassword);

        mockMvc.perform(get("/api/v1/leaves/999999")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Not Found"))
                .andExpect(jsonPath("$.message").value("Resource not found"));
    }

    @Test
    @DisplayName("AC2: non-numeric leave ID returns JSON 400, not HTML")
    void apiLeave_nonNumericId_returnsJson400() throws Exception {
        String token = obtainToken("emp.tan", seedPassword);

        mockMvc.perform(get("/api/v1/leaves/abc")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Bad Request"))
                .andExpect(jsonPath("$.message").value("Invalid request"));
    }

    // -----------------------------------------------------------------------
    // AC3 regression — unauthenticated requests still return existing 401 JSON
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("AC3 regression: unauthenticated /api/v1/leaves/{id} still returns 401 JSON")
    void apiLeaves_unauthenticated_stillReturns401Json() throws Exception {
        mockMvc.perform(get("/api/v1/leaves/1"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.error").value("Unauthorized"));
    }

    // -----------------------------------------------------------------------
    // ASVS V7.4.1 — error bodies must not contain stack traces or internals
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("SEC: 404 error body does not contain stack trace or exception class names")
    void apiLeave_404_doesNotLeakInternals() throws Exception {
        String token = obtainToken("emp.tan", seedPassword);

        String body = mockMvc.perform(get("/api/v1/leaves/999999")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound())
                .andReturn().getResponse().getContentAsString();

        // Must not contain stack trace markers or internal class names
        assert !body.contains("at com.iss") : "Response leaks stack trace";
        assert !body.contains("Exception")  : "Response leaks exception class";
        assert !body.contains("SQL")        : "Response leaks SQL detail";
    }

    // -----------------------------------------------------------------------
    // Helper
    // -----------------------------------------------------------------------

    private String obtainToken(String username, String password) throws Exception {
        String body = mockMvc.perform(post(TOKEN_URL)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                        "username", username,
                        "password", password))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        int start = body.indexOf("\"accessToken\":\"") + 15;
        int end = body.indexOf("\"", start);
        return body.substring(start, end);
    }
}
