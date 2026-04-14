package com.iss.laps;

import com.iss.laps.exception.LeaveApplicationException;
import com.iss.laps.model.*;
import com.iss.laps.repository.*;
import com.iss.laps.service.EmailService;
import com.iss.laps.service.LeaveService;
import com.iss.laps.util.LeaveCalculator;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LeaveService Unit Tests")
class LeaveServiceTest {

    @Mock LeaveApplicationRepository leaveAppRepo;
    @Mock LeaveEntitlementRepository leaveEntitlementRepo;
    @Mock PublicHolidayRepository publicHolidayRepo;
    @Mock LeaveTypeRepository leaveTypeRepo;
    @Mock CompensationClaimRepository compClaimRepo;
    @Mock LeaveCalculator leaveCalculator;
    @Mock EmailService emailService;

    @InjectMocks
    LeaveService leaveService;

    private Employee employee;
    private Employee manager;
    private LeaveType annualLeaveType;
    private LeaveApplication sampleApplication;

    @BeforeEach
    void setUp() {
        manager = new Employee();
        manager.setId(1L);
        manager.setName("Manager Chen");
        manager.setEmail("mgr@iss.edu.sg");

        employee = new Employee();
        employee.setId(2L);
        employee.setName("Tan Ah Kow");
        employee.setEmail("tan@iss.edu.sg");
        employee.setDesignation(Designation.ADMINISTRATIVE);
        employee.setManager(manager);

        annualLeaveType = new LeaveType("Annual", "Annual leave", 21, false);
        annualLeaveType.setId(1L);

        sampleApplication = new LeaveApplication();
        sampleApplication.setLeaveType(annualLeaveType);
        sampleApplication.setStartDate(LocalDate.of(2026, 4, 7));
        sampleApplication.setEndDate(LocalDate.of(2026, 4, 9));
        sampleApplication.setReason("Annual family trip");
        sampleApplication.setHalfDay(false);
    }

    @Test
    @DisplayName("Apply leave succeeds when sufficient entitlement exists")
    void applyLeave_withSufficientBalance_savesApplication() {
        LeaveEntitlement entitlement = new LeaveEntitlement(employee, annualLeaveType, 2026, 14);
        when(leaveTypeRepo.findById(1L)).thenReturn(Optional.of(annualLeaveType));
        when(publicHolidayRepo.findByYear(anyInt())).thenReturn(List.of());
        when(leaveEntitlementRepo.findByEmployeeAndLeaveTypeAndYear(any(), any(), anyInt()))
                .thenReturn(Optional.of(entitlement));
        when(leaveAppRepo.sumUsedDaysByEmployeeAndLeaveTypeAndYear(any(), anyLong(), anyInt()))
                .thenReturn(0.0);
        when(leaveCalculator.areWorkingDays(any(), any(), any())).thenReturn(true);
        when(leaveCalculator.calculateAnnualLeaveDays(any(), any(), any())).thenReturn(3.0);
        when(leaveAppRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(emailService).sendLeaveApplicationNotification(any());

        LeaveApplication result = leaveService.applyLeave(sampleApplication, employee);

        assertThat(result.getStatus()).isEqualTo(LeaveStatus.APPLIED);
        assertThat(result.getDuration()).isEqualTo(3.0);
        assertThat(result.getEmployee()).isEqualTo(employee);
        verify(leaveAppRepo).save(any(LeaveApplication.class));
    }

    @Test
    @DisplayName("Apply leave fails when end date is before start date")
    void applyLeave_endBeforeStart_throwsException() {
        sampleApplication.setStartDate(LocalDate.of(2026, 4, 10));
        sampleApplication.setEndDate(LocalDate.of(2026, 4, 7));
        when(leaveTypeRepo.findById(1L)).thenReturn(Optional.of(annualLeaveType));

        assertThatThrownBy(() -> leaveService.applyLeave(sampleApplication, employee))
                .isInstanceOf(LeaveApplicationException.class)
                .hasMessageContaining("End date must be after");
    }

    @Test
    @DisplayName("Approve leave transitions status to APPROVED")
    void approveLeave_byAuthorisedManager_changesStatusToApproved() {
        sampleApplication.setId(10L);
        sampleApplication.setEmployee(employee);
        sampleApplication.setStatus(LeaveStatus.APPLIED);
        sampleApplication.setDuration(3.0);
        sampleApplication.setStartDate(LocalDate.of(2026, 4, 7));

        when(leaveAppRepo.findById(10L)).thenReturn(Optional.of(sampleApplication));
        when(leaveAppRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(leaveEntitlementRepo.findByEmployeeAndLeaveTypeAndYear(any(), any(), anyInt()))
                .thenReturn(Optional.empty());
        doNothing().when(emailService).sendLeaveApprovalNotification(any());

        leaveService.approveLeave(10L, "Approved, enjoy!", manager);

        assertThat(sampleApplication.getStatus()).isEqualTo(LeaveStatus.APPROVED);
        assertThat(sampleApplication.getManagerComment()).isEqualTo("Approved, enjoy!");
    }

    @Test
    @DisplayName("Reject leave requires a non-blank comment")
    void rejectLeave_withBlankComment_throwsException() {
        assertThatThrownBy(() -> leaveService.rejectLeave(10L, "", manager))
                .isInstanceOf(LeaveApplicationException.class)
                .hasMessageContaining("Comment is mandatory");
    }

    @Test
    @DisplayName("Reject leave by unauthorised manager throws exception")
    void rejectLeave_byWrongManager_throwsException() {
        Employee otherManager = new Employee();
        otherManager.setId(99L);

        sampleApplication.setId(10L);
        sampleApplication.setEmployee(employee);
        sampleApplication.setStatus(LeaveStatus.APPLIED);

        when(leaveAppRepo.findById(10L)).thenReturn(Optional.of(sampleApplication));

        assertThatThrownBy(() -> leaveService.rejectLeave(10L, "No", otherManager))
                .isInstanceOf(LeaveApplicationException.class)
                .hasMessageContaining("Not authorised");
    }

    @Test
    @DisplayName("Cancel leave transitions APPROVED status to CANCELLED")
    void cancelLeave_approvedApplication_changesStatusToCancelled() {
        sampleApplication.setId(10L);
        sampleApplication.setEmployee(employee);
        sampleApplication.setStatus(LeaveStatus.APPROVED);
        sampleApplication.setDuration(3.0);
        sampleApplication.setStartDate(LocalDate.of(2026, 4, 7));

        when(leaveAppRepo.findById(10L)).thenReturn(Optional.of(sampleApplication));
        when(leaveAppRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(leaveEntitlementRepo.findByEmployeeAndLeaveTypeAndYear(any(), any(), anyInt()))
                .thenReturn(Optional.empty());

        leaveService.cancelLeave(10L, employee);

        assertThat(sampleApplication.getStatus()).isEqualTo(LeaveStatus.CANCELLED);
    }

    @Test
    @DisplayName("Delete leave in APPLIED status marks it DELETED")
    void deleteLeave_appliedStatus_marksDeleted() {
        sampleApplication.setId(10L);
        sampleApplication.setEmployee(employee);
        sampleApplication.setStatus(LeaveStatus.APPLIED);

        when(leaveAppRepo.findById(10L)).thenReturn(Optional.of(sampleApplication));
        when(leaveAppRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        leaveService.deleteLeave(10L, employee);

        assertThat(sampleApplication.getStatus()).isEqualTo(LeaveStatus.DELETED);
    }

    @Test
    @DisplayName("Delete APPROVED leave throws exception")
    void deleteLeave_approvedStatus_throwsException() {
        sampleApplication.setId(10L);
        sampleApplication.setEmployee(employee);
        sampleApplication.setStatus(LeaveStatus.APPROVED);

        when(leaveAppRepo.findById(10L)).thenReturn(Optional.of(sampleApplication));

        assertThatThrownBy(() -> leaveService.deleteLeave(10L, employee))
                .isInstanceOf(LeaveApplicationException.class)
                .hasMessageContaining("cannot be deleted");
    }

    // =========== COMPENSATION CLAIM — overtime cap + monthly limit (issue #19) ===========

    @Test
    @DisplayName("claimCompensation: overtime hours above 4 throws IllegalArgumentException")
    void claimCompensation_overtimeHoursExceedsMax_throwsException() {
        CompensationClaim claim = new CompensationClaim();
        claim.setOvertimeDate(LocalDate.of(2026, 4, 10));
        claim.setOvertimeHours(5);

        assertThatThrownBy(() -> leaveService.claimCompensation(claim, employee))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot exceed 4");
    }

    @Test
    @DisplayName("claimCompensation: exactly 4 overtime hours is accepted")
    void claimCompensation_overtimeHoursAtMax_succeeds() {
        CompensationClaim claim = new CompensationClaim();
        claim.setOvertimeDate(LocalDate.of(2026, 4, 10));
        claim.setOvertimeHours(4);
        when(compClaimRepo.sumOvertimeHoursByEmployeeAndMonth(any(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(0);
        when(leaveCalculator.calculateCompensationDays(4)).thenReturn(0.5);
        when(compClaimRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompensationClaim result = leaveService.claimCompensation(claim, employee);

        assertThat(result.getEmployee()).isEqualTo(employee);
        assertThat(result.getStatus()).isEqualTo(CompensationClaim.ClaimStatus.PENDING);
        verify(compClaimRepo).save(any(CompensationClaim.class));
    }

    @Test
    @DisplayName("claimCompensation: minimum 4 overtime hours is accepted")
    void claimCompensation_overtimeHoursAtMin_succeeds() {
        CompensationClaim claim = new CompensationClaim();
        claim.setOvertimeDate(LocalDate.of(2026, 4, 10));
        claim.setOvertimeHours(4);
        when(compClaimRepo.sumOvertimeHoursByEmployeeAndMonth(any(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(0);
        when(leaveCalculator.calculateCompensationDays(4)).thenReturn(0.5);
        when(compClaimRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompensationClaim result = leaveService.claimCompensation(claim, employee);

        assertThat(result.getEmployee()).isEqualTo(employee);
        assertThat(result.getStatus()).isEqualTo(CompensationClaim.ClaimStatus.PENDING);
        verify(compClaimRepo).save(any(CompensationClaim.class));
    }

    @Test
    @DisplayName("claimCompensation: monthly hours + new claim exceeding 72h throws exception")
    void claimCompensation_monthlyCapExceeded_throwsException() {
        CompensationClaim claim = new CompensationClaim();
        claim.setOvertimeDate(LocalDate.of(2026, 4, 20));
        claim.setOvertimeHours(4);
        when(compClaimRepo.sumOvertimeHoursByEmployeeAndMonth(any(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(70); // 70 + 4 = 74 > 72

        assertThatThrownBy(() -> leaveService.claimCompensation(claim, employee))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("72 overtime hours");
    }

    @Test
    @DisplayName("claimCompensation: monthly hours + new claim exactly at 72h succeeds")
    void claimCompensation_monthlyCapAtLimit_succeeds() {
        CompensationClaim claim = new CompensationClaim();
        claim.setOvertimeDate(LocalDate.of(2026, 4, 20));
        claim.setOvertimeHours(4);
        when(compClaimRepo.sumOvertimeHoursByEmployeeAndMonth(any(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(68); // 68 + 4 = 72 = limit
        when(leaveCalculator.calculateCompensationDays(4)).thenReturn(0.5);
        when(compClaimRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompensationClaim result = leaveService.claimCompensation(claim, employee);

        assertThat(result.getEmployee()).isEqualTo(employee);
        verify(compClaimRepo).save(any(CompensationClaim.class));
    }

    @Test
    @DisplayName("claimCompensation: prior month at 72h does not block new month claim")
    void claimCompensation_claimInNewMonth_ignoresPriorMonth() {
        CompensationClaim claim = new CompensationClaim();
        claim.setOvertimeDate(LocalDate.of(2026, 5, 1)); // May — different month
        claim.setOvertimeHours(4);
        // Repository returns 0 for May (prior 72h were in April)
        when(compClaimRepo.sumOvertimeHoursByEmployeeAndMonth(any(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(0);
        when(leaveCalculator.calculateCompensationDays(4)).thenReturn(0.5);
        when(compClaimRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        CompensationClaim result = leaveService.claimCompensation(claim, employee);

        assertThat(result.getEmployee()).isEqualTo(employee);
        verify(compClaimRepo).save(any(CompensationClaim.class));
    }
}
