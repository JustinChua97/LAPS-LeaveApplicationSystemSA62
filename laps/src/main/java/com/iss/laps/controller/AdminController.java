package com.iss.laps.controller;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import com.iss.laps.dto.EmployeeEditForm;
import com.iss.laps.exception.LeaveApplicationException;
import com.iss.laps.model.Designation;
import com.iss.laps.model.Employee;
import com.iss.laps.model.LeaveEntitlement;
import com.iss.laps.model.LeaveType;
import com.iss.laps.model.PublicHoliday;
import com.iss.laps.dto.HolidaySyncResult;
import com.iss.laps.exception.PublicHolidaySyncException;
import com.iss.laps.model.Role;
import com.iss.laps.service.AdminService;
import com.iss.laps.service.EmployeeService;
import com.iss.laps.util.SecurityUtils;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Controller
@RequestMapping("/admin")
@RequiredArgsConstructor
@Slf4j
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

    /**
     * Roles that can be assigned via the employee management UI (ROLE_ADMIN
     * excluded).
     */
    private List<Role> employeeRoles() {
        return Arrays.stream(Role.values())
                .filter(r -> r != Role.ROLE_ADMIN)
                .toList();
    }

    @GetMapping("/employees")
    public String listEmployees(Model model) {
        model.addAttribute("employees", employeeService.findAllIncludingInactive());
        return "admin/employee-list";
    }

    @GetMapping("/employees/new")
    public String newEmployeeForm(Model model) {
        model.addAttribute("employee", new Employee());
        model.addAttribute("roles", employeeRoles());
        model.addAttribute("designations", Arrays.asList(Designation.values()));
        model.addAttribute("managers", employeeService.findByRole(Role.ROLE_MANAGER));
        return "admin/employee-form";
    }

    @PostMapping("/employees/new")
    public String createEmployee(@Valid @ModelAttribute("employee") Employee employee,
            BindingResult result,
            @RequestParam(required = false) Long managerId,
            Model model,
            RedirectAttributes redirectAttrs) {
        if (result.hasErrors()) {
            model.addAttribute("roles", employeeRoles());
            model.addAttribute("designations", Arrays.asList(Designation.values()));
            model.addAttribute("managers", employeeService.findByRole(Role.ROLE_MANAGER));
            return "admin/employee-form";
        }

        if (employeeService.existsByUsername(employee.getUsername())) {
            model.addAttribute("error", "Username already exists");
            model.addAttribute("roles", employeeRoles());
            model.addAttribute("designations", Arrays.asList(Designation.values()));
            model.addAttribute("managers", employeeService.findByRole(Role.ROLE_MANAGER));
            return "admin/employee-form";
        }

        if (managerId != null) {
            employee.setManager(employeeService.findById(managerId));
        }
        employeeService.createEmployee(employee);
        redirectAttrs.addFlashAttribute("success", "Employee created successfully.");
        return "redirect:/admin/employees";
    }

    @GetMapping("/employees/{id}/edit")
    public String editEmployeeForm(@PathVariable Long id, Model model) {
        Employee employee = employeeService.findById(id);
        EmployeeEditForm form = new EmployeeEditForm();
        form.setName(employee.getName());
        form.setEmail(employee.getEmail());
        form.setRole(employee.getRole());
        form.setDesignation(employee.getDesignation());
        form.setActive(employee.isActive());
        model.addAttribute("employee", employee);
        model.addAttribute("employeeForm", form);
        model.addAttribute("roles", employeeRoles());
        model.addAttribute("designations", Arrays.asList(Designation.values()));
        model.addAttribute("managers", employeeService.findByRole(Role.ROLE_MANAGER));
        return "admin/employee-edit";
    }

    @PostMapping("/employees/{id}/edit")
    public String updateEmployee(@PathVariable Long id,
            @Valid @ModelAttribute("employeeForm") EmployeeEditForm form,
            BindingResult result,
            @RequestParam(required = false) Long managerId,
            Model model,
            RedirectAttributes redirectAttrs) {
        if (result.hasErrors()) {
            model.addAttribute("employee", employeeService.findById(id));
            model.addAttribute("roles", employeeRoles());
            model.addAttribute("designations", Arrays.asList(Designation.values()));
            model.addAttribute("managers", employeeService.findByRole(Role.ROLE_MANAGER));
            return "admin/employee-edit";
        }
        try {
            employeeService.updateEmployeeFromForm(id, form, managerId);
            redirectAttrs.addFlashAttribute("success", "Employee updated successfully.");
        } catch (IllegalStateException | IllegalArgumentException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/employees";
    }

    @PostMapping("/employees/{id}/deactivate")
    public String deactivateEmployee(@PathVariable Long id, RedirectAttributes redirectAttrs) {
        try {
            employeeService.deactivateEmployee(id);
            redirectAttrs.addFlashAttribute("success", "Employee deactivated.");
        } catch (IllegalStateException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/admin/employees";
    }

    @PostMapping("/employees/{id}/delete")
    public String deleteEmployee(@PathVariable Long id, RedirectAttributes redirectAttrs) {
        try {
            employeeService.deleteEmployee(id);
            redirectAttrs.addFlashAttribute("success", "Employee deleted.");
        } catch (IllegalStateException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        } catch (Exception e) {
            log.error("Failed to delete employee {}", id, e);
            redirectAttrs.addFlashAttribute("error", "Cannot delete employee.");
        }
        return "redirect:/admin/employees";
    }

    // =========== LEAVE ENTITLEMENT ===========

    @GetMapping("/employees/{id}/entitlements")
    public String viewEntitlements(@PathVariable Long id, Model model, RedirectAttributes redirectAttrs) {
        Employee employee = employeeService.findById(id);
        if (employee.getRole() == Role.ROLE_ADMIN) {
            redirectAttrs.addFlashAttribute("error", "Admin accounts do not have leave entitlements.");
            return "redirect:/admin/employees";
        }
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
        try {
            employeeService.updateEntitlement(id, totalDays);
            redirectAttrs.addFlashAttribute("success", "Entitlement updated.");
        } catch (IllegalStateException | LeaveApplicationException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
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
    public String createLeaveType(@Valid LeaveType leaveType, BindingResult result, RedirectAttributes redirectAttrs) {
        if (result.hasErrors()) {
            return "admin/leave-type-form";
        }

        try {
            adminService.createLeaveType(leaveType); // Use the new method
            redirectAttrs.addFlashAttribute("success", "Leave type created successfully!");
        } catch (LeaveApplicationException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/admin/leave-types";
    }

    @GetMapping("/leave-types/{id}/edit")
    public String editLeaveTypeForm(@PathVariable Long id, Model model) {
        model.addAttribute("leaveType", adminService.findLeaveTypeById(id));
        return "admin/leave-type-form";
    }

    @PostMapping("/leave-types/{id}/edit")
    public String updateLeaveType(@PathVariable Long id,
            @Valid @ModelAttribute("leaveType") LeaveType leaveType,
            BindingResult result,
            RedirectAttributes redirectAttrs) {
        leaveType.setId(id);
        if (result.hasErrors()) {
            return "admin/leave-type-form";
        }
        try {
            adminService.saveLeaveType(leaveType);
            redirectAttrs.addFlashAttribute("success", "Leave type updated.");
        } catch (LeaveApplicationException e) {
            redirectAttrs.addFlashAttribute("error",
                    "Not allowed to edit a leave entitlement to have more than 365 days.");
        }
        return "redirect:/admin/leave-types";
    }

    @PostMapping("/leave-types/{id}/delete")
    public String deleteLeaveType(@PathVariable Long id, RedirectAttributes redirectAttrs) {
        try {
            adminService.deleteLeaveType(id);
            redirectAttrs.addFlashAttribute("success", "Leave type deleted successfully.");
        } catch (LeaveApplicationException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
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
    public String createHoliday(@Valid @ModelAttribute("holiday") PublicHoliday holiday,
            BindingResult result,
            RedirectAttributes redirectAttrs) {
        if (result.hasErrors()) {
            return "admin/holiday-form";
        }
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

    @PostMapping("/holidays/sync")
    public String syncHolidays(@RequestParam int year, RedirectAttributes redirectAttrs) {
        try {
            HolidaySyncResult result = adminService.syncHolidaysFromCsv(year);
            redirectAttrs.addFlashAttribute("success",
                    "Sync complete: " + result.added() + " added, " + result.skipped() + " already exist.");
        } catch (IllegalArgumentException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        } catch (PublicHolidaySyncException e) {
            redirectAttrs.addFlashAttribute("error", "Holiday sync failed. Please try again or add manually.");
        }
        return "redirect:/admin/holidays";
    }
}
