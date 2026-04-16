package com.iss.laps.service;

import com.iss.laps.exception.ResourceNotFoundException;
import com.iss.laps.model.Employee;
import com.iss.laps.model.LeaveEntitlement;
import com.iss.laps.model.LeaveType;
import com.iss.laps.model.Role;
import com.iss.laps.repository.EmployeeRepository;
import com.iss.laps.repository.LeaveEntitlementRepository;
import com.iss.laps.repository.LeaveTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

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
        return employeeRepository.save(employee);
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
            double days;
            if (lt.getName().equalsIgnoreCase("Annual")) {
                days = employee.getDesignation().getAnnualLeaveEntitlement();
            } else if (lt.getName().equalsIgnoreCase("Medical")) {
                days = 60;
            } else {
                days = 0; // Compensation leave starts at 0; earned via claims
            }
            LeaveEntitlement ent = new LeaveEntitlement(employee, lt, year, days);
            leaveEntitlementRepository.save(ent);
        }
    }

    public List<LeaveEntitlement> getEntitlements(Employee employee, int year) {
        return leaveEntitlementRepository.findByEmployeeAndYear(employee, year);
    }

    @Transactional
    public void updateEntitlement(Long entitlementId, double totalDays) {
        LeaveEntitlement ent = leaveEntitlementRepository.findById(entitlementId)
                .orElseThrow(() -> new ResourceNotFoundException("Entitlement not found"));
        ent.setTotalDays(totalDays);
        leaveEntitlementRepository.save(ent);
    }

    public boolean existsByUsername(String username) {
        return employeeRepository.existsByUsername(username);
    }
}
