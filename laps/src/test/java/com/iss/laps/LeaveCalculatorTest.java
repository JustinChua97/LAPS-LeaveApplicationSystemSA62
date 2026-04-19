package com.iss.laps;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.iss.laps.model.PublicHoliday;
import com.iss.laps.util.LeaveCalculator;

@DisplayName("LeaveCalculator Unit Tests")
class LeaveCalculatorTest {

    private LeaveCalculator calculator;
    private List<PublicHoliday> holidays2026;

    @BeforeEach
    void setUp() {
        calculator = new LeaveCalculator();
        // Singapore public holidays relevant to test dates in 2026
        holidays2026 = List.of(
            new PublicHoliday(LocalDate.of(2026, 1, 1), "New Year's Day"),
            new PublicHoliday(LocalDate.of(2026, 5, 1), "Labour Day"),
            new PublicHoliday(LocalDate.of(2026, 8, 9), "National Day"),
            new PublicHoliday(LocalDate.of(2026, 12, 25), "Christmas Day")
        );
    }

    // ── Annual leave calculation ──────────────────────────────

    @Test
    @DisplayName("Annual leave <= 14 cal days excludes weekends and public holidays")
    void annualLeave_shortPeriod_excludesWeekendsAndHolidays() {
        // Week of 2026-04-27 (Mon) to 2026-05-01 (Fri, Labour Day)
        // Mon-Thu = 4 working days; Fri is a PH → excluded
        LocalDate start = LocalDate.of(2026, 4, 27);
        LocalDate end   = LocalDate.of(2026, 5, 1);
        double days = calculator.calculateAnnualLeaveDays(start, end, holidays2026);
        assertEquals(4.0, days, "Labour Day on Friday should be excluded");
    }

    @Test
    @DisplayName("Annual leave > 14 cal days counts all calendar days")
    void annualLeave_longPeriod_countsAllDays() {
        // 2026-01-05 to 2026-01-26 = 22 calendar days
        LocalDate start = LocalDate.of(2026, 1, 5);
        LocalDate end   = LocalDate.of(2026, 1, 26);
        double days = calculator.calculateAnnualLeaveDays(start, end, holidays2026);
        assertEquals(22.0, days, "Period > 14 days should count all calendar days");
    }

    @Test
    @DisplayName("Annual leave exactly 14 calendar days still excludes weekends")
    void annualLeave_exactly14Days_excludesWeekends() {
        // 2026-01-05 (Mon) to 2026-01-18 (Sun) = 14 calendar days
        LocalDate start = LocalDate.of(2026, 1, 5);
        LocalDate end   = LocalDate.of(2026, 1, 18);
        double days = calculator.calculateAnnualLeaveDays(start, end, holidays2026);
        // Weekends: 10-11, 17-18 = 4 days off → 10 working days
        assertEquals(10.0, days, "14-day period should exclude weekends");
    }

    @Test
    @DisplayName("Cross-year annual leave uses both years' holidays")
    void annualLeave_crossYear_usesHolidaysFromBothYears() {
        List<PublicHoliday> holidays = List.of(
            new PublicHoliday(LocalDate.of(2026, 12, 25), "Christmas Day"),
            new PublicHoliday(LocalDate.of(2027, 1, 1), "New Year's Day")
        );
        // 11 calendar days (<=14): 2026-12-24 to 2027-01-03
        LocalDate start = LocalDate.of(2026, 12, 24);
        LocalDate end = LocalDate.of(2027, 1, 3);

        double days = calculator.calculateAnnualLeaveDays(start, end, holidays);

        // Total 11 - weekends (26,27,2,3) 4 - PH (25 Dec, 1 Jan) 2 = 5
        assertEquals(5.0, days);
    }

    // ── Medical leave calculation ─────────────────────────────

    @Test
    @DisplayName("Medical leave excludes weekends only")
    void medicalLeave_excludesWeekendsOnly() {
        // Mon 2026-03-02 to Fri 2026-03-06 = 5 working days
        LocalDate start = LocalDate.of(2026, 3, 2);
        LocalDate end   = LocalDate.of(2026, 3, 6);
        double days = calculator.calculateMedicalLeaveDays(start, end);
        assertEquals(5.0, days);
    }

    @Test
    @DisplayName("Medical leave spanning a weekend counts only weekdays")
    void medicalLeave_spanningWeekend_countsWeekdaysOnly() {
        // Fri 2026-03-06 to Mon 2026-03-09 = 3 days (Sat+Sun excluded)
        LocalDate start = LocalDate.of(2026, 3, 6);
        LocalDate end   = LocalDate.of(2026, 3, 9);
        double days = calculator.calculateMedicalLeaveDays(start, end);
        assertEquals(2.0, days, "Only Fri and Mon should count");
    }

    // ── Weekend / working day checks ─────────────────────────

    @Test
    @DisplayName("Saturday is detected as weekend")
    void isWeekend_Saturday_returnsTrue() {
        assertTrue(calculator.isWeekend(LocalDate.of(2026, 3, 7)));
    }

    @Test
    @DisplayName("Sunday is detected as weekend")
    void isWeekend_Sunday_returnsTrue() {
        assertTrue(calculator.isWeekend(LocalDate.of(2026, 3, 8)));
    }

    @Test
    @DisplayName("Monday is not a weekend")
    void isWeekend_Monday_returnsFalse() {
        assertFalse(calculator.isWeekend(LocalDate.of(2026, 3, 9)));
    }

    @Test
    @DisplayName("Public holiday is not a working day")
    void isWorkingDay_publicHoliday_returnsFalse() {
        LocalDate labourDay = LocalDate.of(2026, 5, 1);
        assertFalse(calculator.isWorkingDay(labourDay, holidays2026));
    }

    @Test
    @DisplayName("Weekday without PH is a working day")
    void isWorkingDay_normalWeekday_returnsTrue() {
        LocalDate tuesday = LocalDate.of(2026, 3, 10);
        assertTrue(calculator.isWorkingDay(tuesday, holidays2026));
    }

    @Test
    @DisplayName("Observed Monday after Sunday PH is excluded from deduction")
    void annualLeave_observedMondayAfterSundayPH_excludedFromDeduction() {
    List<PublicHoliday> holidays = List.of(
        new PublicHoliday(LocalDate.of(2026, 8, 9), "National Day") // Sunday
    );

    LocalDate start = LocalDate.of(2026, 8, 3); // Mon
    LocalDate end = LocalDate.of(2026, 8, 16);  // Sun, exactly 14 calendar days

    double days = calculator.calculateAnnualLeaveDays(start, end, holidays);
    // 14 calendar days, weekends = 4 days, and Monday 10 Aug observed as PH off-in-lieu
    assertEquals(9.0, days);
    }

    @Test
    @DisplayName("Double holiday cascades observed day to Tuesday")
    void annualLeave_doubleHoliday_cascadesToTuesday() {
    List<PublicHoliday> holidays = List.of(
        new PublicHoliday(LocalDate.of(2026, 2, 15), "CNY Day 1"), // Sunday
        new PublicHoliday(LocalDate.of(2026, 2, 16), "CNY Day 2")  // Monday
    );

    // 15th (Sun, PH) -> 16th (Mon, PH) -> 17th (Tue, Observed PH)
    // If we take leave from 13th (Fri) to 18th (Wed)
    LocalDate start = LocalDate.of(2026, 2, 13);
    LocalDate end = LocalDate.of(2026, 2, 18);

    double days = calculator.calculateAnnualLeaveDays(start, end, holidays);
    // Total calendar days: 6 (13,14,15,16,17,18)
    // Weekends: 2 (14, 15)
    // Holidays on weekdays: 16 (Mon), 17 (Tue)
    // Working days to be deducted: 13 (Fri), 18 (Wed) = 2 days
    assertEquals(2.0, days);
    }

    // ── Compensation leave calculation ────────────────────────

    @Test
    @DisplayName("4 overtime hours earns 0.5 compensation day")
    void compensationDays_4hours_returns0point5() {
        assertEquals(0.5, calculator.calculateCompensationDays(4));
    }

    @Test
    @DisplayName("1 overtime hour earns 0.125 compensation day")
    void compensationDays_1hour_returns0point125() {
        assertEquals(0.125, calculator.calculateCompensationDays(1));
    }

    @Test
    @DisplayName("2 overtime hours earns 0.25 compensation day")
    void compensationDays_2hours_returns0point25() {
        assertEquals(0.25, calculator.calculateCompensationDays(2));
    }

    @Test
    @DisplayName("3 overtime hours earns 0.375 compensation day")
    void compensationDays_3hours_returns0point375() {
        assertEquals(0.375, calculator.calculateCompensationDays(3));
    }
}
