package com.iss.laps;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.iss.laps.exception.LeaveApplicationException;
import com.iss.laps.model.CompensationClaim;
import com.iss.laps.model.Designation;
import com.iss.laps.model.Employee;
import com.iss.laps.model.LeaveApplication;
import com.iss.laps.model.LeaveEntitlement;
import com.iss.laps.model.LeaveStatus;
import com.iss.laps.model.LeaveType;
import com.iss.laps.model.LeaveTypeDefault;
import com.iss.laps.repository.CompensationClaimRepository;
import com.iss.laps.repository.EmployeeRepository;
import com.iss.laps.repository.LeaveApplicationRepository;
import com.iss.laps.repository.LeaveEntitlementRepository;
import com.iss.laps.repository.LeaveTypeRepository;
import com.iss.laps.repository.PublicHolidayRepository;
import com.iss.laps.service.EmailService;
import com.iss.laps.service.EmployeeService;
import com.iss.laps.service.LeaveService;
import com.iss.laps.util.LeaveCalculator;

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
    @Mock EmployeeRepository employeeRepository;

    @InjectMocks
    LeaveService leaveService;
    EmployeeService employeeService;

    private Employee employee;
    private Employee manager;
    private LeaveType annualLeaveType;
    private LeaveType customLeaveType;
    private LeaveType medicalType;
    private LeaveType compensationType;
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

        annualLeaveType = new LeaveType();
        annualLeaveType.setName("Annual");
        annualLeaveType.setDescription("Annual leave");
        annualLeaveType.setMaxDaysPerYear(21);
        annualLeaveType.setHalfDayAllowed(true);
        annualLeaveType.setDefaultType(LeaveTypeDefault.ANNUAL);
        annualLeaveType.setId(1L);

        medicalType = new LeaveType();
        medicalType.setId(2L);
        medicalType.setName("Medical");
        medicalType.setDefaultType(LeaveTypeDefault.MEDICAL);

        compensationType = new LeaveType();
        compensationType.setId(3L);
        compensationType.setName("Compensation");
        compensationType.setDefaultType(LeaveTypeDefault.COMPENSATION);

        customLeaveType = new LeaveType();
        customLeaveType.setId(99L);
        customLeaveType.setName("Family Care Leave");
        customLeaveType.setDescription("Future leave type - To allow staff time off for family emergencies");
        customLeaveType.setMaxDaysPerYear(5);
        customLeaveType.setHalfDayAllowed(true);
        customLeaveType.setDefaultType(null); // Marks this as a new custom leave type
        customLeaveType.setActive(true);

        sampleApplication = new LeaveApplication();
        sampleApplication.setLeaveType(annualLeaveType);
        sampleApplication.setStartDate(LocalDate.of(2026, 4, 7));
        sampleApplication.setEndDate(LocalDate.of(2026, 4, 9));
        sampleApplication.setReason("Annual family trip");
        sampleApplication.setHalfDay(false);
    }

    @Test
    @DisplayName("Issue 44: Leave entitlement cannot be negative")
    void updateEntitlement_negativeTotal_throwsException() {
        assertThatThrownBy(() -> employeeService.updateEntitlement(1L, -0.5))
                .isInstanceOf(LeaveApplicationException.class)
                .hasMessageContaining("Leave entitlement cannot be negative");
    }

    @Test
    @DisplayName("Issue 44: Leave entitlement cannot exceed 365 days")
    void updateEntitlement_above365_throwsException() {
        assertThatThrownBy(() -> employeeService.updateEntitlement(1L, 366))
                .isInstanceOf(LeaveApplicationException.class)
                .hasMessageContaining("Leave entitlement cannot exceed 365 days");
    }

    @Test
    @DisplayName("Issue 44: Employee has consumed more leave (usedDays) than the new total being proposed(totalDays)")
    void updateEntitlement_belowUsedDays_throwsException() {
        LeaveEntitlement entitlement = new LeaveEntitlement(employee, annualLeaveType, 2026, 14);
        entitlement.setId(100L);
        entitlement.setUsedDays(5.0);

        when(leaveEntitlementRepo.findById(100L)).thenReturn(Optional.of(entitlement));
        assertThatThrownBy(() -> employeeService.updateEntitlement(100L, 4.5))
                .isInstanceOf(LeaveApplicationException.class)
                .hasMessageContaining("New leave entitlement update cannot be less than the number of days consumed by employee");
    }

    @Test
    @DisplayName("Issue 44: Employee has consumed less leave (usedDays) than the new total being proposed(totalDays)")
    void updateEntitlement_validTotal_saves() {
        LeaveEntitlement entitlement = new LeaveEntitlement(employee, medicalType, 2026, 14);
        entitlement.setId(103L);
        entitlement.setUsedDays(5.0);

        when(leaveEntitlementRepo.findById(103L)).thenReturn(Optional.of(entitlement));

        employeeService.updateEntitlement(103L, 20);

        assertThat(entitlement.getTotalDays()).isEqualTo(20);
        verify(leaveEntitlementRepo).save(entitlement);
    }

    @Test
    @DisplayName("Apply leave succeeds when sufficient entitlement exists")
    void applyLeave_withSufficientBalance_savesApplication() {
        LeaveEntitlement entitlement = new LeaveEntitlement(employee, annualLeaveType, 2026, 14);
        when(leaveTypeRepo.findById(1L)).thenReturn(Optional.of(annualLeaveType));
        when(publicHolidayRepo.findByYear(anyInt())).thenReturn(List.of());
        when(leaveEntitlementRepo.findByEmployeeAndLeaveTypeAndYear(any(), any(), anyInt()))
                .thenReturn(Optional.of(entitlement));
        when(leaveAppRepo.sumUsedDaysByEmployeeAndLeaveTypeAndYear(any(), anyLong(), anyInt(), isNull()))
                .thenReturn(0.0);
        when(leaveCalculator.areWorkingDays(any(), any(), any())).thenReturn(true);
        when(leaveCalculator.calculateAnnualLeaveDays(any(), any(), any())).thenReturn(3.0);
        when(leaveAppRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));
        doNothing().when(emailService).sendLeaveApplicationNotification(any(), eq(EmailService.NotificationType.APPLICATION));

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
        doNothing().when(emailService).sendLeaveApplicationNotification(any(), eq(EmailService.NotificationType.APPROVAL));

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

    // =========== COMPENSATION CLAIM — overtime cap + monthly/annual limit (issue #19) ===========

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
    @DisplayName("claimCompensation: minimum 1 overtime hour is accepted")
    void claimCompensation_overtimeHoursAtMin_succeeds() {
        CompensationClaim claim = new CompensationClaim();
        claim.setOvertimeDate(LocalDate.of(2026, 4, 10));
        claim.setOvertimeHours(1);
        when(compClaimRepo.sumOvertimeHoursByEmployeeAndMonth(any(), any(LocalDate.class), any(LocalDate.class)))
                .thenReturn(0);
        when(leaveCalculator.calculateCompensationDays(1)).thenReturn(0.125);
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

    @Test
    @DisplayName("Issue 20: Total compensation leave in one year cannot exceed 108 days")
    void updateEntitlement_compensationAbove108_throwsException() {
        LeaveEntitlement entitlement = new LeaveEntitlement(employee, compensationType, 2026, 10);
        entitlement.setId(101L);
        entitlement.setUsedDays(2.0);

        when(leaveEntitlementRepo.findById(101L)).thenReturn(Optional.of(entitlement));

        assertThatThrownBy(() -> employeeService.updateEntitlement(101L, 109))
                .isInstanceOf(LeaveApplicationException.class)
                .hasMessageContaining("Total compensation leave cannot exceed 108 days in one year.");
    }

        // =========== Leave Application Tests — For Enum Default Types and Custom Types (issue #17) ===========
    
    /*  
    Note (17 Apr) - Custom Leave Types are not yet implemented. 
    So this test validates that custom leave types will be rejected in the apply and update flows.
    */ 

    @Test
    @DisplayName("Apply leave with custom type (null defaultType) throws LeaveApplicationException")
    void applyLeave_customLeaveType_throwsException() {
        sampleApplication.setLeaveType(customLeaveType);
        when(leaveTypeRepo.findById(99L)).thenReturn(Optional.of(customLeaveType));

        // Act & Assert
        assertThatThrownBy(() -> leaveService.applyLeave(sampleApplication, employee))
                .isInstanceOf(LeaveApplicationException.class)
                .hasMessageContaining("Selected leave type is not supported for leave applications.");
    }

    @Test
    @DisplayName("Update leave with custom type (null defaultType) throws LeaveApplicationException")
    void updateLeave_customLeaveType_throwsException() {
        // Arrange: Existing leave with annual type, but trying to update to custom type
        sampleApplication.setId(10L);
        sampleApplication.setEmployee(employee);
        sampleApplication.setStatus(LeaveStatus.APPLIED);
        
        LeaveApplication updateRequest = new LeaveApplication();
        updateRequest.setLeaveType(customLeaveType);
        updateRequest.setStartDate(LocalDate.of(2026, 5, 1));
        updateRequest.setEndDate(LocalDate.of(2026, 5, 3));
        updateRequest.setReason("Reason has been updated.");
        
        when(leaveAppRepo.findById(10L)).thenReturn(Optional.of(sampleApplication));
        when(leaveTypeRepo.findById(99L)).thenReturn(Optional.of(customLeaveType));

        // Act & Assert
        assertThatThrownBy(() -> leaveService.updateLeave(10L, updateRequest, employee))
                .isInstanceOf(LeaveApplicationException.class)
                .hasMessageContaining("Selected leave type is not supported for leave applications.");
    }

    @Test
    @DisplayName("getDefaultActiveLeaveTypes returns only enum-backed types")
    void getDefaultActiveLeaveTypes_filtersOutCustomTypes() {
        // Arrange: Mix of default and custom leave types
        LeaveType annualType = new LeaveType();
        annualType.setId(1L);
        annualType.setName("Annual");
        annualType.setDefaultType(LeaveTypeDefault.ANNUAL);
        
        LeaveType customType = new LeaveType();
        customType.setId(99L);
        customType.setName("Custom");
        customType.setDefaultType(null);
        
        when(leaveTypeRepo.findByActive(true)).thenReturn(List.of(annualType, customType));

        // Act
        List<LeaveType> result = leaveService.getDefaultActiveLeaveTypes();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(1L);
        assertThat(result.get(0).getDefaultType()).isNotNull();
    }

    @Test
    @DisplayName("getCustomActiveLeaveTypes returns only non-enum types")
    void getCustomActiveLeaveTypes_filtersOutDefaultTypes() {
        // Arrange: Mix of default and custom leave types
        LeaveType annualType = new LeaveType();
        annualType.setId(1L);
        annualType.setName("Annual");
        annualType.setDefaultType(LeaveTypeDefault.ANNUAL);
        
        LeaveType customType = new LeaveType();
        customType.setId(99L);
        customType.setName("Custom");
        customType.setDefaultType(null);
        
        when(leaveTypeRepo.findByActive(true)).thenReturn(List.of(annualType, customType));

        // Act
        List<LeaveType> result = leaveService.getCustomActiveLeaveTypes();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getId()).isEqualTo(99L);
        assertThat(result.get(0).getDefaultType()).isNull();
    }

    @Test
    @DisplayName("Annual Leave application with >14 days duration is rejected.")
    void applyOverlimitLeave() {
        LeaveApplication app = new LeaveApplication();
        app.setLeaveType(annualLeaveType);
        app.setStartDate(LocalDate.of(2026, 1, 1));
        app.setEndDate(LocalDate.of(2026, 1, 15)); // Total of 15 calendar days
        app.setReason("Long leave request");

        when(leaveTypeRepo.findById(1L)).thenReturn(Optional.of(annualLeaveType));

        assertThatThrownBy(() -> leaveService.applyLeave(app, employee))
                .isInstanceOf(LeaveApplicationException.class)
                .hasMessageContaining("Duration of Annual Leave application exceeds the maximum limit of 14 consecutive calendar days. Please seek Department Head approval for extended absence.");
    }

    @Test
    @DisplayName("Issue 20: Editing Annual Leave does not exceed assigned limit based on employee's designation")
    void updateEntitlement_annualAboveDesignationCap_throwsException() {
        employee.setDesignation(Designation.ADMINISTRATIVE);
        LeaveEntitlement entitlement = new LeaveEntitlement(employee, annualLeaveType, 2026, 14);
        entitlement.setId(102L);
        entitlement.setUsedDays(3.0);

        when(leaveEntitlementRepo.findById(102L)).thenReturn(Optional.of(entitlement));

        assertThatThrownBy(() -> employeeService.updateEntitlement(102L, 15))
                .isInstanceOf(LeaveApplicationException.class)
                .hasMessageContaining("This employee's designation has an annual leave cap of 14 days.");
    }
    
    @Test
    @DisplayName("Issue 20: A change in employee's designation recalculates annual leave entitlement")
    void updateEmployee_designationChanged_recalculatesAnnualEntitlement() {
        Employee existing = new Employee();
        existing.setId(10L);
        existing.setDesignation(Designation.ADMINISTRATIVE);

        Employee updated = new Employee();
        updated.setId(10L);
        updated.setDesignation(Designation.SENIOR_PROFESSIONAL);

        int currentYear = LocalDate.now().getYear();

        LeaveEntitlement annualEntitlement = new LeaveEntitlement(updated, annualLeaveType, currentYear, 14);
        annualEntitlement.setId(500L);
        annualEntitlement.setUsedDays(6.0);

        when(employeeRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(employeeRepository.save(existing)).thenReturn(existing);
        when(leaveTypeRepo.findByDefaultType(LeaveTypeDefault.ANNUAL)).thenReturn(Optional.of(annualLeaveType));
        when(leaveEntitlementRepo.findByEmployeeAndLeaveTypeAndYear(existing, annualLeaveType, currentYear))
        .thenReturn(Optional.of(annualEntitlement));

        Employee result = employeeService.updateEmployeeDesignation(updated.getId(), updated.getDesignation());

        assertThat(result.getDesignation()).isEqualTo(Designation.SENIOR_PROFESSIONAL);
        assertThat(annualEntitlement.getTotalDays()).isEqualTo(21);
        verify(leaveEntitlementRepo).save(annualEntitlement);
    }

    @Test
    @DisplayName("Issue 20: An employee's annual leave entitlement does not recalculate when designation does not change")
    void updateEmployee_designationUnchanged_noRecalculation() {
        Employee existing = new Employee();
        existing.setId(10L);
        existing.setDesignation(Designation.PROFESSIONAL);

        Employee updated = new Employee();
        updated.setId(10L);
        updated.setDesignation(Designation.PROFESSIONAL);

        when(employeeRepository.findById(10L)).thenReturn(Optional.of(existing));
        when(employeeRepository.save(existing)).thenReturn(existing);  

        employeeService.updateEmployeeDesignation(updated.getId(), updated.getDesignation());

        verify(leaveTypeRepo, never()).findByDefaultType(any());
    }

    @Test
    void applyLeave_medicalLeave_savesApplication() {
    sampleApplication.setLeaveType(medicalType);
    sampleApplication.setStartDate(LocalDate.of(2026, 3, 2));
    sampleApplication.setEndDate(LocalDate.of(2026, 3, 4));

    when(leaveTypeRepo.findById(2L)).thenReturn(Optional.of(medicalType));
    when(leaveAppRepo.sumUsedDaysByEmployeeAndLeaveTypeAndYear(any(), eq(2L), anyInt(), isNull()))
        .thenReturn(0.0);
    when(leaveCalculator.calculateMedicalLeaveDays(any(), any())).thenReturn(3.0);
    when(leaveAppRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    LeaveApplication result = leaveService.applyLeave(sampleApplication, employee);

    assertThat(result.getStatus()).isEqualTo(LeaveStatus.APPLIED);
    assertThat(result.getDuration()).isEqualTo(3.0);
    }

    @Test
    void applyLeave_medicalLeave_overLimit_throwsException() {
    sampleApplication.setLeaveType(medicalType);
    sampleApplication.setStartDate(LocalDate.of(2026, 1, 1));
    sampleApplication.setEndDate(LocalDate.of(2026, 1, 20));

    when(leaveTypeRepo.findById(2L)).thenReturn(Optional.of(medicalType));
    when(leaveAppRepo.sumUsedDaysByEmployeeAndLeaveTypeAndYear(any(), eq(2L), anyInt(), isNull()))
        .thenReturn(0.0);

    assertThatThrownBy(() -> leaveService.applyLeave(sampleApplication, employee))
        .isInstanceOf(LeaveApplicationException.class);
    }

    @Test
    void applyLeave_hospitalisationLeave_savesApplication() {
    LeaveType hospitalisationType = new LeaveType();
    hospitalisationType.setId(4L);
    hospitalisationType.setName("Hospitalisation");
    hospitalisationType.setDefaultType(LeaveTypeDefault.HOSPITALISATION);

    sampleApplication.setLeaveType(hospitalisationType);

    when(leaveTypeRepo.findById(4L)).thenReturn(Optional.of(hospitalisationType));
    when(leaveAppRepo.sumUsedDaysByEmployeeAndLeaveTypeAndYear(any(), eq(4L), anyInt(), isNull()))
        .thenReturn(0.0);
    when(leaveCalculator.calculateHospitalisationLeaveDays(any(), any())).thenReturn(2.0);
    when(leaveAppRepo.save(any())).thenAnswer(inv -> inv.getArgument(0));

    LeaveApplication result = leaveService.applyLeave(sampleApplication, employee);

    assertThat(result.getStatus()).isEqualTo(LeaveStatus.APPLIED);
    assertThat(result.getDuration()).isEqualTo(2.0);
    }

    @Test
    void applyLeave_compensationLeave_insufficientBalance_throwsException() {
    sampleApplication.setLeaveType(compensationType);

    when(leaveTypeRepo.findById(3L)).thenReturn(Optional.of(compensationType));
    when(compClaimRepo.sumApprovedCompDaysByEmployee(employee)).thenReturn(0.0);
    when(leaveAppRepo.sumUsedDaysByEmployeeAndLeaveTypeAndYear(any(), eq(3L), anyInt(), isNull()))
        .thenReturn(0.0);
    when(leaveCalculator.calculateCompensationLeaveDays(any(), any())).thenReturn(1.0);

    assertThatThrownBy(() -> leaveService.applyLeave(sampleApplication, employee))
        .isInstanceOf(LeaveApplicationException.class);
    }

}
