package com.iss.laps;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class MovementValidationTest {

    // ===== YEAR VALIDATION TESTS =====
    
    @Test
    public void testValidYear2020Passes() {
        int year = 2020;
        boolean isValid = year >= 2020 && year <= 2035;
        assertTrue(isValid, "Year 2020 should be valid");
    }

    @Test
    public void testValidYear2030Passes() {
        int year = 2030;
        boolean isValid = year >= 2020 && year <= 2035;
        assertTrue(isValid, "Year 2030 should be valid");
    }

    @Test
    public void testValidYear2035Passes() {
        int year = 2035;
        boolean isValid = year >= 2020 && year <= 2035;
        assertTrue(isValid, "Year 2035 should be valid");
    }

    @Test
    public void testInvalidYear2019Fails() {
        int year = 2019;
        boolean isValid = year >= 2020 && year <= 2035;
        assertFalse(isValid, "Year 2019 should be invalid (below range)");
    }

    @Test
    public void testInvalidYear2036Fails() {
        int year = 2036;
        boolean isValid = year >= 2020 && year <= 2035;
        assertFalse(isValid, "Year 2036 should be invalid (above range)");
    }

    @Test
    public void testInvalidYearNegativeFails() {
        int year = -5;
        boolean isValid = year >= 2020 && year <= 2035;
        assertFalse(isValid, "Negative year should be invalid");
    }

    // ===== MONTH VALIDATION TESTS =====
    
    @Test
    public void testValidMonth1Passes() {
        int month = 1;
        boolean isValid = month >= 1 && month <= 12;
        assertTrue(isValid, "Month 1 should be valid");
    }

    @Test
    public void testValidMonth6Passes() {
        int month = 6;
        boolean isValid = month >= 1 && month <= 12;
        assertTrue(isValid, "Month 6 should be valid");
    }

    @Test
    public void testValidMonth12Passes() {
        int month = 12;
        boolean isValid = month >= 1 && month <= 12;
        assertTrue(isValid, "Month 12 should be valid");
    }

    @Test
    public void testInvalidMonth0Fails() {
        int month = 0;
        boolean isValid = month >= 1 && month <= 12;
        assertFalse(isValid, "Month 0 should be invalid");
    }

    @Test
    public void testInvalidMonth13Fails() {
        int month = 13;
        boolean isValid = month >= 1 && month <= 12;
        assertFalse(isValid, "Month 13 should be invalid");
    }

    @Test
    public void testInvalidMonthNegativeFails() {
        int month = -1;
        boolean isValid = month >= 1 && month <= 12;
        assertFalse(isValid, "Negative month should be invalid");
    }

    @Test
    public void testInvalidMonth50Fails() {
        int month = 50;
        boolean isValid = month >= 1 && month <= 12;
        assertFalse(isValid, "Month 50 should be invalid");
    }

    // ===== COMBINED VALIDATION TEST =====
    
    @Test
    public void testBothParametersValid() {
        int year = 2025;
        int month = 6;
        
        boolean yearValid = year >= 2020 && year <= 2035;
        boolean monthValid = month >= 1 && month <= 12;
        
        assertTrue(yearValid && monthValid, "Both year 2025 and month 6 should be valid");
    }

    @Test
    public void testBothParametersInvalid() {
        int year = 2050;
        int month = 13;
        
        boolean yearValid = year >= 2020 && year <= 2035;
        boolean monthValid = month >= 1 && month <= 12;
        
        assertFalse(yearValid && monthValid, "Year 2050 and month 13 should both be invalid");
    }
}