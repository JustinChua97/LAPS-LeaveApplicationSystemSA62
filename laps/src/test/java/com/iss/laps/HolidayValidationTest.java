package com.iss.laps;

import com.iss.laps.model.PublicHoliday;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDate;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class HolidayValidationTest {

    @Autowired
    private Validator validator;

    private PublicHoliday validHoliday;

    @BeforeEach
    void setUp() {
        validHoliday = new PublicHoliday();
        validHoliday.setHolidayDate(LocalDate.of(2026, 12, 25));
        validHoliday.setDescription("Christmas");
    }

    // ===== DATE VALIDATION TESTS =====

    @Test
    public void testHolidayWithValidDate_passes() {
        Set<ConstraintViolation<PublicHoliday>> violations = validator.validate(validHoliday);
        assertEquals(0, violations.size(), "Valid holiday should have no violations");
    }

    @Test
    public void testHolidayWithNullDate_fails() {
        validHoliday.setHolidayDate(null);
        Set<ConstraintViolation<PublicHoliday>> violations = validator.validate(validHoliday);
        assertEquals(1, violations.size(), "Holiday with null date should fail");
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Holiday date is required")));
    }

    // ===== DESCRIPTION VALIDATION TESTS =====

    @Test
    public void testHolidayWithValidDescription_passes() {
        Set<ConstraintViolation<PublicHoliday>> violations = validator.validate(validHoliday);
        assertEquals(0, violations.size(), "Valid description should pass");
    }

    @Test
    public void testHolidayWithBlankDescription_fails() {
        validHoliday.setDescription("");
        Set<ConstraintViolation<PublicHoliday>> violations = validator.validate(validHoliday);
        assertEquals(1, violations.size(), "Blank description should fail");
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Description is required")));
    }

    @Test
    public void testHolidayWithNullDescription_fails() {
        validHoliday.setDescription(null);
        Set<ConstraintViolation<PublicHoliday>> violations = validator.validate(validHoliday);
        assertEquals(1, violations.size(), "Null description should fail");
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Description is required")));
    }

    @Test
    public void testHolidayWithLongDescription_fails() {
        String longDesc = "a".repeat(101); // 101 characters
        validHoliday.setDescription(longDesc);
        Set<ConstraintViolation<PublicHoliday>> violations = validator.validate(validHoliday);
        assertEquals(1, violations.size(), "Description > 100 chars should fail");
        assertTrue(violations.stream()
                .anyMatch(v -> v.getMessage().contains("Description must not exceed 100 characters")));
    }

    @Test
    public void testHolidayWithMaxDescription_passes() {
        String maxDesc = "a".repeat(100); // Exactly 100 characters
        validHoliday.setDescription(maxDesc);
        Set<ConstraintViolation<PublicHoliday>> violations = validator.validate(validHoliday);
        assertEquals(0, violations.size(), "Description of 100 chars should pass");
    }

    // ===== COMBINED VALIDATION TESTS =====

    @Test
    public void testHolidayWithBothFieldsValid_passes() {
        Set<ConstraintViolation<PublicHoliday>> violations = validator.validate(validHoliday);
        assertEquals(0, violations.size(), "Valid holiday with date and description should pass");
    }

    @Test
    public void testHolidayWithNullDateAndBlankDescription_fails() {
        validHoliday.setHolidayDate(null);
        validHoliday.setDescription("");
        Set<ConstraintViolation<PublicHoliday>> violations = validator.validate(validHoliday);
        assertEquals(2, violations.size(), "Both null date and blank description should fail");
    }
}