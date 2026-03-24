package com.iss.laps.util;

import com.iss.laps.model.PublicHoliday;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class LeaveCalculator {

    /**
     * Calculates the number of leave days between startDate and endDate (inclusive).
     * For annual leave:
     *   - If period <= 14 calendar days: weekends and public holidays are excluded.
     *   - If period > 14 calendar days: all days are counted.
     * For medical leave: all calendar days (weekends excluded but public holidays counted).
     */
    public double calculateAnnualLeaveDays(LocalDate startDate, LocalDate endDate,
                                            List<PublicHoliday> publicHolidays) {
        long calendarDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;

        if (calendarDays <= 14) {
            // Exclude weekends and public holidays
            Set<LocalDate> holidays = publicHolidays.stream()
                    .map(PublicHoliday::getHolidayDate)
                    .collect(Collectors.toSet());

            return startDate.datesUntil(endDate.plusDays(1))
                    .filter(date -> !isWeekend(date) && !holidays.contains(date))
                    .count();
        } else {
            // Count all calendar days
            return calendarDays;
        }
    }

    /**
     * Calculates medical leave days (excludes weekends only).
     */
    public double calculateMedicalLeaveDays(LocalDate startDate, LocalDate endDate) {
        return startDate.datesUntil(endDate.plusDays(1))
                .filter(date -> !isWeekend(date))
                .count();
    }

    /**
     * Checks if a date is a weekend.
     */
    public boolean isWeekend(LocalDate date) {
        DayOfWeek day = date.getDayOfWeek();
        return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
    }

    /**
     * Checks if a date is a public holiday.
     */
    public boolean isPublicHoliday(LocalDate date, List<PublicHoliday> publicHolidays) {
        return publicHolidays.stream()
                .anyMatch(ph -> ph.getHolidayDate().equals(date));
    }

    /**
     * Checks if a date is a working day (not weekend, not public holiday).
     */
    public boolean isWorkingDay(LocalDate date, List<PublicHoliday> publicHolidays) {
        return !isWeekend(date) && !isPublicHoliday(date, publicHolidays);
    }

    /**
     * Validates that start and end dates are working days for annual leave.
     */
    public boolean areWorkingDays(LocalDate startDate, LocalDate endDate,
                                   List<PublicHoliday> publicHolidays) {
        return isWorkingDay(startDate, publicHolidays) && isWorkingDay(endDate, publicHolidays);
    }

    /**
     * Calculates compensation leave days from overtime hours.
     * Every 4 hours = 0.5 day compensation.
     */
    public double calculateCompensationDays(int overtimeHours) {
        return (overtimeHours / 4) * 0.5;
    }
}
