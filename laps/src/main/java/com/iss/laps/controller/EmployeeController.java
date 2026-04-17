package com.iss.laps.controller;

import com.iss.laps.exception.LeaveApplicationException;
import com.iss.laps.model.*;
import com.iss.laps.service.EmployeeService;
import com.iss.laps.service.LeaveService;
import com.iss.laps.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/employee")
@RequiredArgsConstructor
public class EmployeeController {

    private final LeaveService leaveService;
    private final EmployeeService employeeService;
    private final SecurityUtils securityUtils;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Employee employee = securityUtils.getCurrentEmployee();
        model.addAttribute("employee", employee);

        // Leave summary
        List<LeaveEntitlement> entitlements = employeeService.getEntitlements(employee, LocalDate.now().getYear());
        model.addAttribute("entitlements", entitlements);

        List<LeaveApplication> recentLeaves = leaveService.getMyLeaveHistory(employee);
        model.addAttribute("recentLeaves", recentLeaves.stream().limit(5).toList());

        return "employee/dashboard";
    }

    // =========== LEAVE APPLICATION ===========

    @GetMapping("/leaves/apply")
    public String applyLeaveForm(Model model) {
        model.addAttribute("leaveApplication", new LeaveApplication());
        populateLeaveFormModel(model); // handles leaveTypes, today, publicHolidays
        return "employee/leave-apply";
    }

    @PostMapping("/leaves/apply")
    public String applyLeave(@Valid @ModelAttribute("leaveApplication") LeaveApplication application,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttrs) {
        Employee employee = securityUtils.getCurrentEmployee();

        if (result.hasErrors()) {
            populateLeaveFormModel(model);
            return "employee/leave-apply";
        }

        try {
            leaveService.applyLeave(application, employee);
            redirectAttrs.addFlashAttribute("success", "Leave application submitted successfully.");
            return "redirect:/employee/leaves";
        } catch (LeaveApplicationException e) {
            model.addAttribute("error", e.getMessage());
            populateLeaveFormModel(model);
            return "employee/leave-apply";
        }
    }

    // =========== LEAVE HISTORY ===========

    @GetMapping("/leaves")
    public String leaveHistory(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            Model model) {
        Employee employee = securityUtils.getCurrentEmployee();
        // Validate pagination parameters (issue #45)
        if (page < 0) {
            page = 0;
        }
        if (size != 10 && size != 20 && size != 25) {
            size = 10;
        }
        Pageable pageable = PageRequest.of(page, size);
        Page<LeaveApplication> leavePage = leaveService.getMyLeaveHistoryPaged(employee, pageable);

        model.addAttribute("employee", employee);
        model.addAttribute("leavePage", leavePage);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("pageSizes", List.of(10, 20, 25));
        return "employee/leave-history";
    }

    // =========== VIEW INDIVIDUAL LEAVE ===========

    @GetMapping("/leaves/{id}")
    public String viewLeave(@PathVariable Long id, Model model) {
        Employee employee = securityUtils.getCurrentEmployee();
        LeaveApplication leave = leaveService.findByIdAndEmployee(id, employee);
        model.addAttribute("leave", leave);
        return "employee/leave-detail";
    }

    // =========== UPDATE LEAVE ===========

    @GetMapping("/leaves/{id}/edit")
    public String editLeaveForm(@PathVariable Long id, Model model) {
        Employee employee = securityUtils.getCurrentEmployee();
        LeaveApplication leave = leaveService.findByIdAndEmployee(id, employee);

        if (!leave.isEditable()) {
            return "redirect:/employee/leaves/" + id + "?error=Cannot+edit+this+leave";
        }

        model.addAttribute("leaveApplication", leave);
        populateLeaveFormModel(model);
        return "employee/leave-edit";
    }

    @PostMapping("/leaves/{id}/edit")
    public String updateLeave(@PathVariable Long id,
            @Valid @ModelAttribute("leaveApplication") LeaveApplication updated,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttrs) {
        Employee employee = securityUtils.getCurrentEmployee();

        if (result.hasErrors()) {
            model.addAttribute("leaveTypes", leaveService.getActiveLeaveTypes());
            return "employee/leave-edit";
        }

        try {
            leaveService.updateLeave(id, updated, employee);
            redirectAttrs.addFlashAttribute("success", "Leave application updated successfully.");
            return "redirect:/employee/leaves";
        } catch (LeaveApplicationException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("leaveTypes", leaveService.getActiveLeaveTypes());
            return "employee/leave-edit";
        }
    }

    // =========== DELETE LEAVE ===========

    @PostMapping("/leaves/{id}/delete")
    public String deleteLeave(@PathVariable Long id, RedirectAttributes redirectAttrs) {
        Employee employee = securityUtils.getCurrentEmployee();
        try {
            leaveService.deleteLeave(id, employee);
            redirectAttrs.addFlashAttribute("success", "Leave application deleted.");
        } catch (LeaveApplicationException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/employee/leaves";
    }

    // =========== CANCEL LEAVE ===========

    @PostMapping("/leaves/{id}/cancel")
    public String cancelLeave(@PathVariable Long id, RedirectAttributes redirectAttrs) {
        Employee employee = securityUtils.getCurrentEmployee();
        try {
            leaveService.cancelLeave(id, employee);
            redirectAttrs.addFlashAttribute("success", "Leave application cancelled.");
        } catch (LeaveApplicationException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/employee/leaves";
    }

    // =========== COMPENSATION CLAIM ===========

    @GetMapping("/compensation/claim")
    public String compensationClaimForm(Model model) {
        Employee employee = securityUtils.getCurrentEmployee();
        model.addAttribute("claim", new CompensationClaim());
        model.addAttribute("myClaims", leaveService.getMyCompensationClaims(employee));
        model.addAttribute("today", LocalDate.now());
        return "employee/compensation-claim";
    }

    @PostMapping("/compensation/claim")
    public String submitCompensationClaim(@Valid @ModelAttribute("claim") CompensationClaim claim,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttrs) {
        if (result.hasErrors()) {
            Employee employee = securityUtils.getCurrentEmployee();
            model.addAttribute("myClaims", leaveService.getMyCompensationClaims(employee));
            model.addAttribute("today", LocalDate.now());
            return "employee/compensation-claim";
        }
        Employee employee = securityUtils.getCurrentEmployee();
        try {
            leaveService.claimCompensation(claim, employee);
            redirectAttrs.addFlashAttribute("success", "Compensation claim submitted.");
        } catch (IllegalArgumentException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/employee/compensation/claim";
    }
    // =========== PRIVATE HELPERS ===========

    private void populateLeaveFormModel(Model model) {
        int year = LocalDate.now().getYear();
        model.addAttribute("leaveTypes", leaveService.getActiveLeaveTypes());
        model.addAttribute("today", LocalDate.now());
        model.addAttribute("publicHolidays", leaveService.getPublicHolidaysForYear(year));
    }

}
