package com.iss.laps.config;

import com.iss.laps.model.*;
import com.iss.laps.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Seeds test user accounts and leave entitlements at application startup.
 * Passwords are encoded at runtime — no credentials are stored in source files.
 *
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class DataInitializer implements ApplicationRunner {

    private final EmployeeRepository employeeRepository;
    private final LeaveTypeRepository leaveTypeRepository;
    private final LeaveEntitlementRepository entitlementRepository;
    private final LeaveApplicationRepository leaveApplicationRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${seed.user.password}")
    private String seedPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (employeeRepository.existsByUsername("admin")) {
            log.info("DataInitializer: seed data already present, skipping.");
            return;
        }

        log.info("DataInitializer: seeding test accounts...");

        // Resolve leave types (inserted by data.sql)
        LeaveType annualLeave  = findLeaveType("Annual");
        LeaveType medicalLeave = findLeaveType("Medical");
        LeaveType compLeave    = findLeaveType("Compensation");

        // ---- Admin ----
        Employee admin = save(new Employee(
                "admin", encode(seedPassword),
                "System Administrator", "admin@iss.edu.sg",
                Role.ROLE_ADMIN, Designation.ADMINISTRATIVE));

        // ---- Managers ----
        Employee mgrChen = save(new Employee(
                "mgr.chen", encode(seedPassword),
                "Chen Wei Ming", "chen.weiming@iss.edu.sg",
                Role.ROLE_MANAGER, Designation.PROFESSIONAL));

        Employee mgrLim = save(new Employee(
                "mgr.lim", encode(seedPassword),
                "Lim Siew Bee", "lim.siewbee@iss.edu.sg",
                Role.ROLE_MANAGER, Designation.SENIOR_PROFESSIONAL));

        // ---- Employees under mgrChen ----
        Employee empTan = save(new Employee(
                "emp.tan", encode(seedPassword),
                "Tan Ah Kow", "tan.ahkow@iss.edu.sg",
                Role.ROLE_EMPLOYEE, Designation.ADMINISTRATIVE));
        empTan.setManager(mgrChen);
        employeeRepository.save(empTan);

        Employee empKumar = save(new Employee(
                "emp.kumar", encode(seedPassword),
                "Kumar Rajan", "kumar.rajan@iss.edu.sg",
                Role.ROLE_EMPLOYEE, Designation.PROFESSIONAL));
        empKumar.setManager(mgrChen);
        employeeRepository.save(empKumar);

        // ---- Employees under mgrLim ----
        Employee empAli = save(new Employee(
                "emp.ali", encode(seedPassword),
                "Ali Hassan", "ali.hassan@iss.edu.sg",
                Role.ROLE_EMPLOYEE, Designation.ADMINISTRATIVE));
        empAli.setManager(mgrLim);
        employeeRepository.save(empAli);

        Employee empSarah = save(new Employee(
                "emp.sarah", encode(seedPassword),
                "Sarah Wong", "sarah.wong@iss.edu.sg",
                Role.ROLE_EMPLOYEE, Designation.PROFESSIONAL));
        empSarah.setManager(mgrLim);
        employeeRepository.save(empSarah);

        // ---- Leave Entitlements 2026 ----
        int year = 2026;
        seedEntitlements(empTan,   annualLeave, medicalLeave, compLeave, 14, year);
        seedEntitlements(empKumar, annualLeave, medicalLeave, compLeave, 18, year);
        seedEntitlements(empAli,   annualLeave, medicalLeave, compLeave, 14, year);
        seedEntitlements(empSarah, annualLeave, medicalLeave, compLeave, 18, year);
        seedEntitlements(mgrChen,  annualLeave, medicalLeave, compLeave, 18, year);
        seedEntitlements(mgrLim,   annualLeave, medicalLeave, compLeave, 21, year);
        seedEntitlements(admin,    annualLeave, medicalLeave, compLeave, 14, year);

        // ---- Sample leave applications ----
        seedSampleLeave(empTan, annualLeave, mgrChen);
        seedSampleLeave(empKumar, medicalLeave, mgrChen);

        log.info("DataInitializer: seeding complete.");
    }

    // ---- helpers ----

    private String encode(String raw) {
        return passwordEncoder.encode(raw);
    }

    private Employee save(Employee e) {
        return employeeRepository.save(e);
    }

    private LeaveType findLeaveType(String name) {
        return leaveTypeRepository.findByNameIgnoreCase(name)
                .orElseThrow(() -> new IllegalStateException(
                        "Leave type '" + name + "' not found. Check data.sql."));
    }

    private void seedEntitlements(Employee employee, LeaveType annual, LeaveType medical,
                                   LeaveType comp, double annualDays, int year) {
        saveEntitlement(employee, annual,  year, annualDays);
        saveEntitlement(employee, medical, year, 60);
        saveEntitlement(employee, comp,    year, 0);
    }

    private void saveEntitlement(Employee employee, LeaveType leaveType, int year, double days) {
        Optional<LeaveEntitlement> existing =
                entitlementRepository.findByEmployeeAndLeaveTypeAndYear(employee, leaveType, year);
        if (existing.isEmpty()) {
            entitlementRepository.save(new LeaveEntitlement(employee, leaveType, year, days));
        }
    }

    private void seedSampleLeave(Employee employee, LeaveType leaveType, Employee manager) {
        if (leaveApplicationRepository.findByEmployeeAndYear(employee, 2026).isEmpty()) {
            LeaveApplication la = new LeaveApplication();
            la.setEmployee(employee);
            la.setLeaveType(leaveType);
            la.setStartDate(LocalDate.of(2026, 2, 10));
            la.setEndDate(LocalDate.of(2026, 2, 11));
            la.setDuration(2);
            la.setReason("Sample leave for demonstration");
            la.setStatus(LeaveStatus.APPROVED);
            la.setManagerComment("Approved");
            la.setHalfDay(false);
            leaveApplicationRepository.save(la);
        }
    }
}
