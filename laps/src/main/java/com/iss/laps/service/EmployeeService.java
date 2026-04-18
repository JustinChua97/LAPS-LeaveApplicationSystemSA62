package com.iss.laps.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iss.laps.exception.LeaveApplicationException;
import com.iss.laps.exception.ResourceNotFoundException;
import com.iss.laps.model.Employee;
import com.iss.laps.model.LeaveEntitlement;
import com.iss.laps.model.LeaveType;
import com.iss.laps.model.LeaveTypeDefault;
import com.iss.laps.model.Role;
import com.iss.laps.repository.EmployeeRepository;
import com.iss.laps.repository.LeaveEntitlementRepository;
import com.iss.laps.repository.LeaveTypeRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class EmployeeService {

    private final EmployeeRepository employeeRepository;
    private final LeaveEntitlementRepository leaveEntitlementRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final PasswordEncoder passwordEncoder;

    public Optional<Employee> findByUsername(String username) {
        return employeeRepository.findByUsername(username);
    }

    public Employee findById(Long id) {
        return employeeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Employee not found: " + id));
    }

    public List<Employee> findAll() {
        return employeeRepository.findAllActive();
    }

    public List<Employee> findAllIncludingInactive() {
        return employeeRepository.findAll();
    }

    public List<Employee> findSubordinates(Employee manager) {
        return employeeRepository.findByManager(manager);
    }

    public List<Employee> findByRole(Role role) {
        return employeeRepository.findByRole(role);
    }

    @Transactional
    public Employee createEmployee(Employee employee) {
        employee.setPassword(passwordEncoder.encode(employee.getPassword()));
        Employee saved = employeeRepository.save(employee);
        // Auto-create leave entitlements for current year
        initLeaveEntitlements(saved, LocalDate.now().getYear());
        return saved;
    }

    @Transactional
    public Employee updateEmployee(Employee employee) {
        return employeeRepository.save(employee); //To update employee details - EXCEPT designation
    }

    @Transactional
    public Employee updateEmployeeDesignation(Employee employee) {
        //To recalculate annual leave entitlement for current year if employee designation has changed.
        Employee existing = findById(employee.getId());
        boolean designationChanged = existing.getDesignation() != employee.getDesignation();

        Employee saved = employeeRepository.save(employee);

        if (designationChanged) {
            recalculateAnnualEntitlementForYear(saved, LocalDate.now().getYear());
        }

    return saved; 
    }

    @Transactional
    public void updatePassword(Employee employee, String newPassword) {
        employee.setPassword(passwordEncoder.encode(newPassword));
        employeeRepository.save(employee);
    }

    @Transactional
    public void deactivateEmployee(Long id) {
        Employee employee = findById(id);
        employee.setActive(false);
        employeeRepository.save(employee);
    }

    @Transactional
    public void deleteEmployee(Long id) {
        employeeRepository.deleteById(id);
    }

    private void initLeaveEntitlements(Employee employee, int year) {
        List<LeaveType> leaveTypes = leaveTypeRepository.findByActive(true);
        for (LeaveType lt : leaveTypes) {
            double days = 0.0;
            if (lt.getDefaultType() != null) {
                switch (lt.getDefaultType()) {
                    case ANNUAL:
                        days = employee.getDesignation().getAnnualLeaveEntitlement();
                        break;
                    case MEDICAL:
                        days = 14;
                        break;
                    case HOSPITALISATION:
                        days = 46;
                        break;
                    case COMPENSATION:
                        days = 0;
                        break;
                    default:
                        break;
            }

            LeaveEntitlement ent = new LeaveEntitlement(employee, lt, year, days);
            leaveEntitlementRepository.save(ent);
            }
        }
    }

    public List<LeaveEntitlement> getEntitlements(Employee employee, int year) {
        return leaveEntitlementRepository.findByEmployeeAndYear(employee, year);
    }

    @Transactional
    public void updateEntitlement(Long entitlementId, double totalDays) {
        if (totalDays < 0) {
            throw new LeaveApplicationException("Total entitlement cannot be negative.");
        }

        if (totalDays > 365) {
            throw new LeaveApplicationException("Total entitlement cannot exceed 365 days.");
        }

        LeaveEntitlement entitlement = leaveEntitlementRepository.findById(entitlementId)
            .orElseThrow(() -> new ResourceNotFoundException("Entitlement not found"));

        if (totalDays < entitlement.getUsedDays()) {
            throw new LeaveApplicationException(
                "Total entitlement cannot be less than used days (" + entitlement.getUsedDays() + ").");
        }

        double maxAllowed = getMaxEntitlementFor(entitlement.getEmployee(), entitlement.getLeaveType());
        if (totalDays > maxAllowed) {
            throw new LeaveApplicationException(
                "Total entitlement exceeds the allowed cap of " + maxAllowed + " days for this leave type.");
        }

        entitlement.setTotalDays(totalDays);
        leaveEntitlementRepository.save(entitlement);
    }

    public boolean existsByUsername(String username) {
        return employeeRepository.existsByUsername(username);
    }

//Helper Methods
    private double getMaxEntitlementFor(Employee employee, LeaveType leaveType) {
        LeaveTypeDefault type = leaveType.getDefaultType();

        if (type == null) {
            return 365;
        }

        switch (type) {
            case ANNUAL:
                return employee.getDesignation().getAnnualLeaveEntitlement();
            case MEDICAL:
                return 14;
            case HOSPITALISATION:
                return 46;
            case COMPENSATION:
                return 108;
            default:
                return 365; 
                // Currently the maximum entitlement is set as 365 days temporarily. 
                // This is to accomodate any future enhancements in LAPS where new leave types may be granted - e.g. Maternity leave, No-Pay Leave (NPL) etc.
        }
    }

    private void recalculateAnnualEntitlementForYear(Employee employee, int year) {
        leaveTypeRepository.findByDefaultType(LeaveTypeDefault.ANNUAL)
            .flatMap(annualType -> leaveEntitlementRepository.findByEmployeeAndLeaveTypeAndYear(employee, annualType, year))
            .ifPresent(entitlement -> {
                double annualCap = employee.getDesignation().getAnnualLeaveEntitlement();
                double adjustedTotal = Math.max(annualCap, entitlement.getUsedDays());
                entitlement.setTotalDays(adjustedTotal);
                leaveEntitlementRepository.save(entitlement);
                });
    }
}
