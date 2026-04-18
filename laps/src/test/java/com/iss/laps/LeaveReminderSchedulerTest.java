package com.iss.laps;

import com.iss.laps.model.Employee;
import com.iss.laps.model.LeaveApplication;
import com.iss.laps.model.LeaveStatus;
import com.iss.laps.repository.LeaveApplicationRepository;
import com.iss.laps.service.ReminderEmailService;
import com.iss.laps.taskscheduler.LeaveReminderScheduler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("LeaveReminderScheduler Unit Tests")
class LeaveReminderSchedulerTest {

    private static final List<LeaveStatus> PENDING_STATUSES = List.of(LeaveStatus.APPLIED, LeaveStatus.UPDATED);

    @Mock
    LeaveApplicationRepository leaveApplicationRepository;

    @Mock
    ReminderEmailService reminderEmailService;

    @InjectMocks
    LeaveReminderScheduler scheduler;

    @Test
    @DisplayName("sendReminders: pending leave starting in 7 days sends email")
    void sendReminders_pendingLeaveSevenDays_sendsEmail() {
        LeaveApplication application = leaveStartingIn(7, true);
        when(leaveApplicationRepository.findByStatusIn(PENDING_STATUSES))
                .thenReturn(List.of(application));

        scheduler.sendReminders();

        verify(leaveApplicationRepository).findByStatusIn(PENDING_STATUSES);
        verify(reminderEmailService).sendManagerReminder(application, 7);
    }

    @Test
    @DisplayName("sendReminders: pending leave starting in 4 days sends email")
    void sendReminders_pendingLeaveFourDays_sendsEmail() {
        LeaveApplication application = leaveStartingIn(4, true);
        when(leaveApplicationRepository.findByStatusIn(PENDING_STATUSES))
                .thenReturn(List.of(application));

        scheduler.sendReminders();

        verify(reminderEmailService).sendManagerReminder(application, 4);
    }

    @Test
    @DisplayName("sendReminders: pending leave starting in 1 day sends email")
    void sendReminders_pendingLeaveOneDay_sendsEmail() {
        LeaveApplication application = leaveStartingIn(1, true);
        when(leaveApplicationRepository.findByStatusIn(PENDING_STATUSES))
                .thenReturn(List.of(application));

        scheduler.sendReminders();

        verify(reminderEmailService).sendManagerReminder(application, 1);
    }

    @Test
    @DisplayName("sendReminders: pending leave outside reminder days skips email")
    void sendReminders_pendingLeaveNonReminderDay_skipsEmail() {
        LeaveApplication application = leaveStartingIn(3, true);
        when(leaveApplicationRepository.findByStatusIn(PENDING_STATUSES))
                .thenReturn(List.of(application));

        scheduler.sendReminders();

        verify(reminderEmailService, never()).sendManagerReminder(application, 3);
    }

    @Test
    @DisplayName("sendReminders: leave without assigned manager skips safely")
    void sendReminders_noManager_skipsSafely() {
        LeaveApplication application = leaveStartingIn(7, false);
        when(leaveApplicationRepository.findByStatusIn(PENDING_STATUSES))
                .thenReturn(List.of(application));

        scheduler.sendReminders();

        verify(reminderEmailService, never()).sendManagerReminder(application, 7);
    }

    @Test
    @DisplayName("sendReminders: email failure for one leave does not stop later reminders")
    void sendReminders_emailFailure_continuesProcessing() {
        LeaveApplication firstApplication = leaveStartingIn(7, true);
        firstApplication.setId(10L);
        LeaveApplication secondApplication = leaveStartingIn(7, true);
        secondApplication.setId(11L);
        when(leaveApplicationRepository.findByStatusIn(PENDING_STATUSES))
                .thenReturn(List.of(firstApplication, secondApplication));
        doThrow(new RuntimeException("SMTP unavailable"))
                .when(reminderEmailService).sendManagerReminder(firstApplication, 7);

        scheduler.sendReminders();

        verify(reminderEmailService).sendManagerReminder(firstApplication, 7);
        verify(reminderEmailService).sendManagerReminder(secondApplication, 7);
    }

    private LeaveApplication leaveStartingIn(long daysUntilStart, boolean withManager) {
        Employee employee = new Employee();
        employee.setId(2L);
        employee.setName("Tan Ah Kow");

        if (withManager) {
            Employee manager = new Employee();
            manager.setId(1L);
            manager.setName("Manager Chen");
            manager.setEmail("manager@example.com");
            employee.setManager(manager);
        }

        LeaveApplication application = new LeaveApplication();
        application.setId(10L);
        application.setEmployee(employee);
        application.setStatus(LeaveStatus.APPLIED);
        application.setStartDate(LocalDate.now().plusDays(daysUntilStart));
        return application;
    }
}
