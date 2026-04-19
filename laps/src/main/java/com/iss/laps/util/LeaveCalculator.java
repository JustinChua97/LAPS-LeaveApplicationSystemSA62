package com.iss.laps.util;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.iss.laps.model.PublicHoliday;

@Component
public class LeaveCalculator {

    /**
     * Calculates the number of leave days between startDate and endDate (inclusive).
     * For annual leave:
     *   - If period <= 14 calendar days: weekends and public holidays are excluded.
     *   - If period > 14 calendar days: all days are counted.
     * For medical leave: all calendar days (weekends excluded but public holidays counted).
     */
    public double calculateAnnualLeaveDays(LocalDate startDate, LocalDate endDate, List<PublicHoliday> publicHolidays) {
        long calendarDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;

        if (calendarDays <= 14) {
            // Exclude weekends and public holidays
            Set<LocalDate> holidays = HolidaysWithObservedMondays(publicHolidays);

            return startDate.datesUntil(endDate.plusDays(1))
                    .filter(date -> !isWeekend(date) && !holidays.contains(date))
                    .count();
        }
        // Count all calendar days
        return calendarDays;
    }
    

    //To determine which public holidays have Mondays included, so the following Tuesday is considered a public holiday.
    //E.g. First day of Chinese New Year in 2023 fell on a Sunday, and MOM declared Tuesday to be a public holiday.
    private Set<LocalDate> HolidaysWithObservedMondays(List<PublicHoliday> publicHolidays) {
    Set<LocalDate> holidays = publicHolidays.stream()
            .map(PublicHoliday::getHolidayDate)
            .collect(Collectors.toSet());
    // Build a set called 'holidays' from the list of public holidays, 
    // and populate it with the identified holiday dates.

    Set<LocalDate> observedMondays = new java.util.HashSet<>();
    for (LocalDate date : holidays) {
        if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
            LocalDate observedHoliday = date.plusDays(1);
            // Cascade to Tuesday if Monday is already a public holiday
            while (holidays.contains(observedHoliday) || observedMondays.contains(observedHoliday)) {
                observedHoliday = observedHoliday.plusDays(1);
            }
            observedMondays.add(observedHoliday);
        }
    // In the block above, the code will loop through each date in the 'holidays' set,
    // and check if it falls on a Sunday. If it does, the next day (Monday) will 
    // be marked as the observed holiday. Then it checks if observed date is already in
    // the original holiday set, and if so, it pushes the 'observedHoliday' by one more day.
    }
    
    holidays.addAll(observedMondays);
    return holidays;
    }

    //Calculates medical leave days (excludes weekends only).
    public double calculateMedicalLeaveDays(LocalDate startDate, LocalDate endDate) {
        return startDate.datesUntil(endDate.plusDays(1))
                .filter(date -> !isWeekend(date))
                .count();
    }

    //Calculates hospitalisation leave days (excludes weekends only).
    public double calculateHospitalisationLeaveDays(LocalDate startDate, LocalDate endDate) {
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
    public boolean areWorkingDays(LocalDate startDate, LocalDate endDate, List<PublicHoliday> publicHolidays) {
           Set<LocalDate> holidays = HolidaysWithObservedMondays(publicHolidays);
            return !isWeekend(startDate) && !holidays.contains(startDate) &&
                !isWeekend(endDate) && !holidays.contains(endDate);
    }

    /**
     * Calculates compensation leave days from overtime hours.
     * Every 4 hours = 0.5 day compensation; partial hours accrue proportionally
     * (e.g. 1h = 0.125 day, 2h = 0.25 day, 3h = 0.375 day).
     */
    public double calculateCompensationDays(int overtimeHours) {
        return (overtimeHours / 4.0) * 0.5;
    }

    //Calculates compensation leave days (excludes weekends only).
    public double calculateCompensationLeaveDays(LocalDate startDate, LocalDate endDate) {
        return startDate.datesUntil(endDate.plusDays(1))
                .filter(date -> !isWeekend(date))
                .count();
    }
}
