package com.iss.laps.controller;

import com.iss.laps.model.*;
import com.iss.laps.service.AdminService;
import com.iss.laps.service.EmployeeService;
import com.iss.laps.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
public class AdminController {

    private final EmployeeService employeeService;
    private final AdminService adminService;
    private final SecurityUtils securityUtils;
    private final PasswordEncoder passwordEncoder;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        model.addAttribute("totalEmployees", employeeService.findAll().size());
        model.addAttribute("totalLeaveTypes", adminService.getAllLeaveTypes().size());
        model.addAttribute("totalHolidays", adminService.getHolidaysByYear(LocalDate.now().getYear()).size());
        return "admin/dashboard";
    }

    // =========== EMPLOYEE MANAGEMENT ===========

    @GetMapping("/employees")
    public String listEmployees(Model model) {
        model.addAttribute("employees", employeeService.findAllIncludingInactive());
        return "admin/employee";
    }

    @GetMapping("/employees/new")
    public String newEmployeeForm(Model model) {
        model.addAttribute("employee", new Employee());
        model.addAttribute("roles", Arrays.asList(Role.values()));
        model.addAttribute("designations", Arrays.asList(Designation.values()));
        model.addAttribute("managers", employeeService.findByRole(Role.ROLE_MANAGER));
        return "admin/employee-form";
    }

    @PostMapping("/employees/new")
    public String createEmployee(@Valid @ModelAttribute("employee") Employee employee,
                                  BindingResult result,
                                  Model model,
                                  RedirectAttributes redirectAttrs) {
        if (result.hasErrors()) {
            model.addAttribute("roles", Arrays.asList(Role.values()));
            model.addAttribute("designations", Arrays.asList(Designation.values()));
            model.addAttribute("managers", employeeService.findByRole(Role.ROLE_MANAGER));
            return "admin/employee-form";
        }

        if (employeeService.existsByUsername(employee.getUsername())) {
            model.addAttribute("error", "Username already exists");
            model.addAttribute("roles", Arrays.asList(Role.values()));
            model.addAttribute("designations", Arrays.asList(Designation.values()));
            model.addAttribute("managers", employeeService.findByRole(Role.ROLE_MANAGER));
            return "admin/employee-form";
        }

        employeeService.createEmployee(employee);
        redirectAttrs.addFlashAttribute("success", "Employee created successfully.");
        return "redirect:/admin/employees";
    }

    @GetMapping("/employees/{id}/edit")
    public String editEmployeeForm(@PathVariable Long id, Model model) {
        Employee employee = employeeService.findById(id);
        model.addAttribute("employee", employee);
        model.addAttribute("roles", Arrays.asList(Role.values()));
        model.addAttribute("designations", Arrays.asList(Designation.values()));
        model.addAttribute("managers", employeeService.findByRole(Role.ROLE_MANAGER));
        return "admin/employee-edit";
    }

    @PostMapping("/employees/{id}/edit")
    public String updateEmployee(@PathVariable Long id,
                                  @Valid @ModelAttribute("employee") Employee updated,
                                  BindingResult result,
                                  Model model,
                                  RedirectAttributes redirectAttrs) {
        if (result.hasErrors()) {
            model.addAttribute("roles", Arrays.asList(Role.values()));
            model.addAttribute("designations", Arrays.asList(Designation.values()));
            model.addAttribute("managers", employeeService.findByRole(Role.ROLE_MANAGER));
            return "admin/employee-edit";
        }

        Employee existing = employeeService.findById(id);
        existing.setName(updated.getName());
        existing.setEmail(updated.getEmail());
        existing.setRole(updated.getRole());
        existing.setDesignation(updated.getDesignation());
        existing.setManager(updated.getManager());
        existing.setActive(updated.isActive());

        employeeService.updateEmployee(existing);
        redirectAttrs.addFlashAttribute("success", "Employee updated successfully.");
        return "redirect:/admin/employees";
    }

    @PostMapping("/employees/{id}/deactivate")
    public String deactivateEmployee(@PathVariable Long id, RedirectAttributes redirectAttrs) {
        employeeService.deactivateEmployee(id);
        redirectAttrs.addFlashAttribute("success", "Employee deactivated.");
        return "redirect:/admin/employees";
    }

    @PostMapping("/employees/{id}/delete")
    public String deleteEmployee(@PathVariable Long id, RedirectAttributes redirectAttrs) {
        try {
            employeeService.deleteEmployee(id);
            redirectAttrs.addFlashAttribute("success", "Employee deleted.");
        } catch (Exception e) {
            redirectAttrs.addFlashAttribute("error", "Cannot delete: " + e.getMessage());
        }
        return "redirect:/admin/employees";
    }

    // =========== LEAVE ENTITLEMENT ===========

    @GetMapping("/employees/{id}/entitlements")
    public String viewEntitlements(@PathVariable Long id, Model model) {
        Employee employee = employeeService.findById(id);
        List<LeaveEntitlement> entitlements = employeeService.getEntitlements(employee, LocalDate.now().getYear());
        model.addAttribute("employee", employee);
        model.addAttribute("entitlements", entitlements);
        return "admin/entitlements";
    }

    @PostMapping("/entitlements/{id}/update")
    public String updateEntitlement(@PathVariable Long id,
                                     @RequestParam double totalDays,
                                     @RequestParam Long employeeId,
                                     RedirectAttributes redirectAttrs) {
        employeeService.updateEntitlement(id, totalDays);
        redirectAttrs.addFlashAttribute("success", "Entitlement updated.");
        return "redirect:/admin/employees/" + employeeId + "/entitlements";
    }

    // =========== LEAVE TYPES ===========

    @GetMapping("/leave-types")
    public String listLeaveTypes(Model model) {
        model.addAttribute("leaveTypes", adminService.getAllLeaveTypes());
        return "admin/leave-types";
    }

    @GetMapping("/leave-types/new")
    public String newLeaveTypeForm(Model model) {
        model.addAttribute("leaveType", new LeaveType());
        return "admin/leave-type-form";
    }

    @PostMapping("/leave-types/new")
    public String createLeaveType(@ModelAttribute("leaveType") LeaveType leaveType,
                                   RedirectAttributes redirectAttrs) {
        adminService.saveLeaveType(leaveType);
        redirectAttrs.addFlashAttribute("success", "Leave type created.");
        return "redirect:/admin/leave-types";
    }

    @GetMapping("/leave-types/{id}/edit")
    public String editLeaveTypeForm(@PathVariable Long id, Model model) {
        model.addAttribute("leaveType", adminService.findLeaveTypeById(id));
        return "admin/leave-type-form";
    }

    @PostMapping("/leave-types/{id}/edit")
    public String updateLeaveType(@PathVariable Long id,
                                   @ModelAttribute("leaveType") LeaveType leaveType,
                                   RedirectAttributes redirectAttrs) {
        leaveType.setId(id);
        adminService.saveLeaveType(leaveType);
        redirectAttrs.addFlashAttribute("success", "Leave type updated.");
        return "redirect:/admin/leave-types";
    }

    @PostMapping("/leave-types/{id}/delete")
    public String deleteLeaveType(@PathVariable Long id, RedirectAttributes redirectAttrs) {
        adminService.deleteLeaveType(id);
        redirectAttrs.addFlashAttribute("success", "Leave type deleted.");
        return "redirect:/admin/leave-types";
    }

    // =========== PUBLIC HOLIDAYS ===========

    @GetMapping("/holidays")
    public String listHolidays(@RequestParam(defaultValue = "#{T(java.time.LocalDate).now().year}") int year,
                                Model model) {
        model.addAttribute("holidays", adminService.getHolidaysByYear(year));
        model.addAttribute("year", year);
        return "admin/holidays";
    }

    @GetMapping("/holidays/new")
    public String newHolidayForm(Model model) {
        model.addAttribute("holiday", new PublicHoliday());
        return "admin/holiday-form";
    }

    @PostMapping("/holidays/new")
    public String createHoliday(@ModelAttribute("holiday") PublicHoliday holiday,
                                 RedirectAttributes redirectAttrs) {
        if (adminService.isHolidayDateTaken(holiday.getHolidayDate())) {
            redirectAttrs.addFlashAttribute("error", "A holiday already exists for this date.");
            return "redirect:/admin/holidays/new";
        }
        holiday.setYear(holiday.getHolidayDate().getYear());
        adminService.saveHoliday(holiday);
        redirectAttrs.addFlashAttribute("success", "Public holiday added.");
        return "redirect:/admin/holidays";
    }

    @PostMapping("/holidays/{id}/delete")
    public String deleteHoliday(@PathVariable Long id, RedirectAttributes redirectAttrs) {
        adminService.deleteHoliday(id);
        redirectAttrs.addFlashAttribute("success", "Holiday deleted.");
        return "redirect:/admin/holidays";
    }
}
