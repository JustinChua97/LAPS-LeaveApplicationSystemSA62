package com.iss.laps.service;

import com.iss.laps.exception.LeaveApplicationException;
import com.iss.laps.exception.ResourceNotFoundException;
import com.iss.laps.model.*;
import com.iss.laps.repository.*;
import com.iss.laps.util.LeaveCalculator;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class LeaveService {

    private final LeaveApplicationRepository leaveAppRepo;
    private final LeaveEntitlementRepository leaveEntitlementRepo;
    private final PublicHolidayRepository publicHolidayRepo;
    private final LeaveTypeRepository leaveTypeRepo;
    private final CompensationClaimRepository compClaimRepo;
    private final LeaveCalculator leaveCalculator;
    private final EmailService emailService;

    // =========== LEAVE APPLICATION ===========

    @Transactional
    public LeaveApplication applyLeave(LeaveApplication application, Employee employee) {
        application.setEmployee(employee);
        application.setStatus(LeaveStatus.APPLIED);

        Long leaveTypeId = application.getLeaveType() != null ? application.getLeaveType().getId() : null;
        if (leaveTypeId == null) {
            throw new LeaveApplicationException("Leave type is required");
        }
        LeaveType leaveType = leaveTypeRepo.findById(leaveTypeId)
                .orElseThrow(() -> new LeaveApplicationException("Invalid leave type selected"));
        application.setLeaveType(leaveType);

        validateLeaveApplication(application, employee, null);

        double duration = calculateDuration(application);
        application.setDuration(duration);

        LeaveApplication saved = leaveAppRepo.save(application);

        // Notify manager
        if (employee.getManager() != null) {
            initEmailAssociations(saved);
            emailService.sendLeaveApplicationNotification(saved, EmailService.NotificationType.APPLICATION);
        }

        return saved;
    }

    @Transactional
    public LeaveApplication updateLeave(Long id, LeaveApplication updated, Employee employee) {
        LeaveApplication existing = findByIdAndEmployee(id, employee);

        if (!existing.isEditable()) {
            throw new LeaveApplicationException(
                    "Leave application cannot be updated in status: " + existing.getStatus());
        }

        existing.setLeaveType(updated.getLeaveType());
        existing.setStartDate(updated.getStartDate());
        existing.setEndDate(updated.getEndDate());
        existing.setReason(updated.getReason());
        existing.setWorkDissemination(updated.getWorkDissemination());
        existing.setContactDetails(updated.getContactDetails());
        existing.setHalfDay(updated.isHalfDay());
        existing.setHalfDayType(updated.getHalfDayType());
        existing.setStatus(LeaveStatus.UPDATED);

        validateLeaveApplication(existing, employee, id);

        double duration = calculateDuration(existing);
        existing.setDuration(duration);

        return leaveAppRepo.save(existing);
    }

    @Transactional
    public void deleteLeave(Long id, Employee employee) {
        LeaveApplication existing = findByIdAndEmployee(id, employee);
        if (!existing.isDeletable()) {
            throw new LeaveApplicationException(
                    "Leave application cannot be deleted in status: " + existing.getStatus());
        }
        existing.setStatus(LeaveStatus.DELETED);
        leaveAppRepo.save(existing);
    }

    @Transactional
    public void cancelLeave(Long id, Employee employee) {
        LeaveApplication existing = findByIdAndEmployee(id, employee);
        if (!existing.isCancellable()) {
            throw new LeaveApplicationException(
                    "Leave can only be cancelled when Approved. Current status: " + existing.getStatus());
        }
        existing.setStatus(LeaveStatus.CANCELLED);
        leaveAppRepo.save(existing);

        // Restore entitlement
        restoreEntitlement(existing, employee);
    }

    @Transactional
    public void approveLeave(Long id, String comment, Employee manager) {
        LeaveApplication application = findById(id);

        if (!isSubordinate(application.getEmployee(), manager)) {
            throw new LeaveApplicationException("Not authorised to approve this application");
        }
        if (application.getStatus() != LeaveStatus.APPLIED && application.getStatus() != LeaveStatus.UPDATED) {
            throw new LeaveApplicationException("Application is not in a pending state");
        }

        application.setStatus(LeaveStatus.APPROVED);
        application.setManagerComment(comment);
        leaveAppRepo.save(application);

        // Update entitlement
        deductEntitlement(application, application.getEmployee());

        initEmailAssociations(application);
        emailService.sendLeaveApplicationNotification(application, EmailService.NotificationType.APPROVAL);
    }

    @Transactional
    public void rejectLeave(Long id, String comment, Employee manager) {
        if (comment == null || comment.isBlank()) {
            throw new LeaveApplicationException("Comment is mandatory when rejecting a leave application");
        }
        LeaveApplication application = findById(id);

        if (!isSubordinate(application.getEmployee(), manager)) {
            throw new LeaveApplicationException("Not authorised to reject this application");
        }
        if (application.getStatus() != LeaveStatus.APPLIED && application.getStatus() != LeaveStatus.UPDATED) {
            throw new LeaveApplicationException("Application is not in a pending state");
        }

        application.setStatus(LeaveStatus.REJECTED);
        application.setManagerComment(comment);
        leaveAppRepo.save(application);

        initEmailAssociations(application);
        emailService.sendLeaveApplicationNotification(application, EmailService.NotificationType.REJECTION);
    }

    // =========== QUERIES ===========

    public LeaveApplication findById(Long id) {
        return leaveAppRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave application not found: " + id));
    }

    public LeaveApplication findByIdAndEmployee(Long id, Employee employee) {
        LeaveApplication la = findById(id);
        if (!la.getEmployee().getId().equals(employee.getId())) {
            throw new LeaveApplicationException("Access denied: Not your leave application");
        }
        return la;
    }

    public List<LeaveApplication> getMyLeaveHistory(Employee employee) {
        return leaveAppRepo.findByEmployeeAndYear(employee, LocalDate.now().getYear());
    }

    public Page<LeaveApplication> getMyLeaveHistoryPaged(Employee employee, Pageable pageable) {
        return leaveAppRepo.findByEmployeeAndStatusNotOrderByAppliedDateDesc(
                employee, LeaveStatus.DELETED, pageable);
    }

    public List<LeaveApplication> getPendingApplicationsForManager(Employee manager) {
        return leaveAppRepo.findPendingByManager(manager);
    }

    public List<LeaveApplication> getSubordinateLeaveHistory(Employee manager, int year) {
        return leaveAppRepo.findByManagerAndYear(manager, year);
    }

    public Page<LeaveApplication> getSubordinateLeaveHistoryPaged(Employee manager, Pageable pageable) {
        return leaveAppRepo.findByManagerPageable(manager, pageable);
    }

    public List<LeaveApplication> getApprovedLeaveInMonth(int year, int month) {
        LocalDate start = LocalDate.of(year, month, 1);
        LocalDate end = start.withDayOfMonth(start.lengthOfMonth());
        return leaveAppRepo.findApprovedLeaveDuringPeriod(start, end);
    }

    public List<LeaveApplication> getApprovedLeaveInRange(LocalDate start, LocalDate end) {
        return leaveAppRepo.findApprovedLeaveInRange(start, end);
    }

    public List<LeaveApplication> getApprovedLeaveByTypeAndRange(Long leaveTypeId, LocalDate start, LocalDate end) {
        return leaveAppRepo.findApprovedLeaveByTypeAndRange(leaveTypeId, start, end);
    }

    public List<LeaveApplication> getSubordinateLeaveDuringPeriod(Employee manager, LocalDate start, LocalDate end) {
        return leaveAppRepo.findSubordinateLeaveDuringPeriod(manager, start, end);
    }
    // =========== HOLIDAY LOOKUP ===========

    public List<PublicHoliday> getPublicHolidaysForYear(int year) {
        return publicHolidayRepo.findByYear(year);
    }

    // =========== COMPENSATION CLAIM ===========

    @Transactional
    public CompensationClaim claimCompensation(CompensationClaim claim, Employee employee) {
        if (claim.getOvertimeHours() > 4) {
            throw new IllegalArgumentException("Overtime hours cannot exceed 4 per day");
        }
        LocalDate overtimeDate = claim.getOvertimeDate();
        LocalDate startOfMonth = overtimeDate.withDayOfMonth(1);
        LocalDate endOfMonth = overtimeDate.withDayOfMonth(overtimeDate.lengthOfMonth());
        int monthlyHours = compClaimRepo.sumOvertimeHoursByEmployeeAndMonth(
                employee, startOfMonth, endOfMonth);
        if (monthlyHours + claim.getOvertimeHours() > 72) {
            throw new IllegalArgumentException(
                    "This claim would exceed the 72 overtime hours allowed per month (MOM limit)");
        }
        claim.setEmployee(employee);
        double compDays = leaveCalculator.calculateCompensationDays(claim.getOvertimeHours());
        claim.setCompensationDays(compDays);
        claim.setStatus(CompensationClaim.ClaimStatus.PENDING);
        return compClaimRepo.save(claim);
    }

    @Transactional
    public void approveCompensationClaim(Long id, String comment, Employee manager) {
        CompensationClaim claim = compClaimRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found"));
        if (!isSubordinate(claim.getEmployee(), manager)) {
            throw new LeaveApplicationException("Not authorised");
        }
        claim.setStatus(CompensationClaim.ClaimStatus.APPROVED);
        claim.setManagerComment(comment);
        compClaimRepo.save(claim);

        // Update compensation leave entitlement
        addCompensationEntitlement(claim.getEmployee(), claim.getCompensationDays());
    }

    @Transactional
    public void rejectCompensationClaim(Long id, String comment, Employee manager) {
        if (comment == null || comment.isBlank()) {
            throw new LeaveApplicationException("Comment is mandatory when rejecting");
        }
        CompensationClaim claim = compClaimRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Claim not found"));
        if (!isSubordinate(claim.getEmployee(), manager)) {
            throw new LeaveApplicationException("Not authorised");
        }
        claim.setStatus(CompensationClaim.ClaimStatus.REJECTED);
        claim.setManagerComment(comment);
        compClaimRepo.save(claim);
    }

    public List<CompensationClaim> getMyCompensationClaims(Employee employee) {
        return compClaimRepo.findByEmployeeOrderByClaimedDateDesc(employee);
    }

    public List<CompensationClaim> getPendingCompClaimsForManager(Employee manager) {
        return compClaimRepo.findPendingByManager(manager);
    }

    public List<CompensationClaim> getAllCompClaimsForManager(Employee manager) {
        return compClaimRepo.findAllByManager(manager);
    }

    // =========== LEAVE TYPES ===========

    public List<LeaveType> getActiveLeaveTypes() {
        return leaveTypeRepo.findByActive(true);
    }

    public Optional<LeaveType> findLeaveTypeByName(String name) {
        return leaveTypeRepo.findByNameIgnoreCase(name);
    }

    // =========== PRIVATE HELPERS ===========

    private void validateLeaveApplication(LeaveApplication application, Employee employee, Long excludeId) {
        LocalDate start = application.getStartDate();
        LocalDate end = application.getEndDate();

        if (start == null || end == null) {
            throw new LeaveApplicationException("Start and end dates are required");
        }
        if (end.isBefore(start)) {
            throw new LeaveApplicationException("End date must be after or equal to start date");
        }

        String leaveTypeName = application.getLeaveType().getName();
        List<PublicHoliday> holidays = publicHolidayRepo.findByYear(start.getYear());

        if (leaveTypeName.equalsIgnoreCase("Annual")) {
            if (!leaveCalculator.areWorkingDays(start, end, holidays)) {
                throw new LeaveApplicationException("Start and end dates must be working days for annual leave");
            }
        }

        // Check entitlement
        double duration = calculateDuration(application);
        LeaveType leaveType = application.getLeaveType();

        Optional<LeaveEntitlement> entOpt = leaveEntitlementRepo.findByEmployeeAndLeaveTypeAndYear(
                employee, leaveType, start.getYear());

        if (leaveTypeName.equalsIgnoreCase("Medical")) {
            // Medical: max 60 days/year
            double usedDays = leaveAppRepo.sumUsedDaysByEmployeeAndLeaveTypeAndYear(
                    employee, leaveType.getId(), start.getYear());
            if (usedDays + duration > 60) {
                throw new LeaveApplicationException(
                        "Medical leave limit exceeded. Remaining: " + (60 - usedDays) + " days");
            }
        } else if (!leaveTypeName.equalsIgnoreCase("Compensation") && entOpt.isPresent()) {
            LeaveEntitlement ent = entOpt.get();
            double usedDays = leaveAppRepo.sumUsedDaysByEmployeeAndLeaveTypeAndYear(
                    employee, leaveType.getId(), start.getYear());
            if (usedDays + duration > ent.getTotalDays()) {
                throw new LeaveApplicationException(
                        "Insufficient leave balance. Remaining: " + (ent.getTotalDays() - usedDays) + " days");
            }
        } else if (leaveTypeName.equalsIgnoreCase("Compensation")) {
            // Check available compensation balance
            double earned = compClaimRepo.sumApprovedCompDaysByEmployee(employee);
            double used = leaveAppRepo.sumUsedDaysByEmployeeAndLeaveTypeAndYear(
                    employee, leaveType.getId(), start.getYear());
            if (used + duration > earned) {
                throw new LeaveApplicationException(
                        "Insufficient compensation leave. Available: " + (earned - used) + " days");
            }
        }
    }

    private double calculateDuration(LeaveApplication application) {
        String typeName = application.getLeaveType().getName();
        LocalDate start = application.getStartDate();
        LocalDate end = application.getEndDate();

        if (application.isHalfDay()) {
            return 0.5;
        }

        List<PublicHoliday> holidays = publicHolidayRepo.findByYear(start.getYear());

        if (typeName.equalsIgnoreCase("Annual")) {
            return leaveCalculator.calculateAnnualLeaveDays(start, end, holidays);
        } else if (typeName.equalsIgnoreCase("Medical")) {
            return leaveCalculator.calculateMedicalLeaveDays(start, end);
        } else {
            // Compensation: full or half day
            return leaveCalculator.calculateMedicalLeaveDays(start, end);
        }
    }

    private void deductEntitlement(LeaveApplication application, Employee employee) {
        LeaveType leaveType = application.getLeaveType();
        leaveEntitlementRepo
                .findByEmployeeAndLeaveTypeAndYear(employee, leaveType, application.getStartDate().getYear())
                .ifPresent(ent -> {
                    ent.setUsedDays(ent.getUsedDays() + application.getDuration());
                    leaveEntitlementRepo.save(ent);
                });
    }

    private void restoreEntitlement(LeaveApplication application, Employee employee) {
        LeaveType leaveType = application.getLeaveType();
        leaveEntitlementRepo
                .findByEmployeeAndLeaveTypeAndYear(employee, leaveType, application.getStartDate().getYear())
                .ifPresent(ent -> {
                    ent.setUsedDays(Math.max(0, ent.getUsedDays() - application.getDuration()));
                    leaveEntitlementRepo.save(ent);
                });
    }

    private void addCompensationEntitlement(Employee employee, double days) {
        leaveTypeRepo.findByNameIgnoreCase("Compensation").ifPresent(lt -> {
            Optional<LeaveEntitlement> entOpt = leaveEntitlementRepo.findByEmployeeAndLeaveTypeAndYear(
                    employee, lt, LocalDate.now().getYear());
            if (entOpt.isPresent()) {
                LeaveEntitlement ent = entOpt.get();
                ent.setTotalDays(ent.getTotalDays() + days);
                leaveEntitlementRepo.save(ent);
            } else {
                LeaveEntitlement ent = new LeaveEntitlement(employee, lt, LocalDate.now().getYear(), days);
                leaveEntitlementRepo.save(ent);
            }
        });
    }

    private boolean isSubordinate(Employee employee, Employee manager) {
        return employee.getManager() != null &&
                employee.getManager().getId().equals(manager.getId());
    }

    /**
     * Force-loads lazy associations needed by EmailService within the current
     * Hibernate session, so the @Async email thread can access them after the
     * session closes (fixes GenericJDBCException: This statement has been closed).
     */
    private void initEmailAssociations(LeaveApplication app) {
        Employee emp = app.getEmployee();
        emp.getName();
        emp.getEmail();
        Employee mgr = emp.getManager();
        if (mgr != null) {
            mgr.getName();
            mgr.getEmail();
        }
        app.getLeaveType().getName();
    }
}
