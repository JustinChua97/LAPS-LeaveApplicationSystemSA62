package com.iss.laps.taskscheduler;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import com.iss.laps.model.LeaveApplication;
import com.iss.laps.model.LeaveStatus;
import com.iss.laps.repository.LeaveApplicationRepository;
import com.iss.laps.service.ReminderEmailService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;


@Service
@RequiredArgsConstructor
@Slf4j
public class LeaveReminderScheduler {

    private final LeaveApplicationRepository leaveApplicationRepository;
    private final ReminderEmailService emailService;

    //@Scheduled(cron = "0 * * * * *")// Simulating test conditions to test whether scheduler works and email can be received. 1 minute per task.
    @Scheduled(cron = "0 0 9 * * *") // every day at 9 AM Once it works, change to this for production conditions to trigger email reminders.
    public void sendReminders() {
        LocalDate today = LocalDate.now();
        // Find applications that have been applied, but have not been approved or rejected
        List<LeaveApplication> pendingApps = leaveApplicationRepository.findByStatusIn(
                List.of(LeaveStatus.APPLIED, LeaveStatus.UPDATED));

        for (LeaveApplication app : pendingApps) {
            if (app.getEmployee() == null || app.getEmployee().getManager() == null) {
                log.warn("Skipping leave reminder because no manager is assigned for leave application {}", app.getId());
                continue;
            }

            long daysUntilStart = ChronoUnit.DAYS.between(today, app.getStartDate());

            if (daysUntilStart == 7 || daysUntilStart == 4 || daysUntilStart == 1) {
                try {
                    emailService.sendManagerReminder(app, daysUntilStart);
                } catch (RuntimeException e) {
                    log.warn("Failed to queue reminder email for leave application {} ({})",
                            app.getId(), e.getClass().getSimpleName());
                }
            }
        }
    }
}
