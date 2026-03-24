package com.iss.laps.controller;

import com.iss.laps.exception.LeaveApplicationException;
import com.iss.laps.model.*;
import com.iss.laps.service.EmployeeService;
import com.iss.laps.service.LeaveService;
import com.iss.laps.util.SecurityUtils;
import com.opencsv.CSVWriter;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.io.StringWriter;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/manager")
@RequiredArgsConstructor
public class ManagerController {

    private final LeaveService leaveService;
    private final EmployeeService employeeService;
    private final SecurityUtils securityUtils;

    @GetMapping("/dashboard")
    public String dashboard(Model model) {
        Employee manager = securityUtils.getCurrentEmployee();
        model.addAttribute("manager", manager);

        List<LeaveApplication> pendingLeaves = leaveService.getPendingApplicationsForManager(manager);
        model.addAttribute("pendingCount", pendingLeaves.size());
        model.addAttribute("pendingLeaves", pendingLeaves.stream().limit(5).toList());

        List<CompensationClaim> pendingClaims = leaveService.getPendingCompClaimsForManager(manager);
        model.addAttribute("pendingClaimsCount", pendingClaims.size());

        return "manager/dashboard";
    }

    // =========== VIEW APPLICATIONS FOR APPROVAL ===========

    @GetMapping("/leaves")
    public String viewPendingLeaves(Model model) {
        Employee manager = securityUtils.getCurrentEmployee();
        List<LeaveApplication> pendingLeaves = leaveService.getPendingApplicationsForManager(manager);

        // Group by employee
        Map<String, List<LeaveApplication>> grouped = pendingLeaves.stream()
                .collect(Collectors.groupingBy(la -> la.getEmployee().getName()));

        model.addAttribute("grouped", grouped);
        model.addAttribute("manager", manager);
        return "manager/leave-pending";
    }

    @GetMapping("/leaves/{id}")
    public String viewLeaveDetail(@PathVariable Long id, Model model) {
        Employee manager = securityUtils.getCurrentEmployee();
        LeaveApplication application = leaveService.findById(id);

        // Show other subordinates' leave during the same period
        List<LeaveApplication> conflicting = leaveService.getSubordinateLeaveDuringPeriod(
                manager, application.getStartDate(), application.getEndDate());

        model.addAttribute("leave", application);
        model.addAttribute("conflicting", conflicting);
        return "manager/leave-detail";
    }

    @PostMapping("/leaves/{id}/approve")
    public String approveLeave(@PathVariable Long id,
                                @RequestParam(required = false) String comment,
                                RedirectAttributes redirectAttrs) {
        Employee manager = securityUtils.getCurrentEmployee();
        try {
            leaveService.approveLeave(id, comment, manager);
            redirectAttrs.addFlashAttribute("success", "Leave application approved.");
        } catch (LeaveApplicationException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/manager/leaves";
    }

    @PostMapping("/leaves/{id}/reject")
    public String rejectLeave(@PathVariable Long id,
                               @RequestParam String comment,
                               RedirectAttributes redirectAttrs) {
        Employee manager = securityUtils.getCurrentEmployee();
        try {
            leaveService.rejectLeave(id, comment, manager);
            redirectAttrs.addFlashAttribute("success", "Leave application rejected.");
        } catch (LeaveApplicationException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/manager/leaves";
    }

    // =========== SUBORDINATE LEAVE HISTORY ===========

    @GetMapping("/subordinates/leaves")
    public String subordinateLeaveHistory(@RequestParam(defaultValue = "0") int page,
                                           @RequestParam(defaultValue = "10") int size,
                                           Model model) {
        Employee manager = securityUtils.getCurrentEmployee();
        Pageable pageable = PageRequest.of(page, size);
        Page<LeaveApplication> leavePage = leaveService.getSubordinateLeaveHistoryPaged(manager, pageable);

        model.addAttribute("leavePage", leavePage);
        model.addAttribute("currentPage", page);
        model.addAttribute("pageSize", size);
        model.addAttribute("pageSizes", List.of(10, 20, 25));
        return "manager/subordinate-history";
    }

    // =========== COMPENSATION CLAIMS ===========

    @GetMapping("/compensation")
    public String viewCompensationClaims(Model model) {
        Employee manager = securityUtils.getCurrentEmployee();
        model.addAttribute("pendingClaims", leaveService.getPendingCompClaimsForManager(manager));
        model.addAttribute("allClaims", leaveService.getAllCompClaimsForManager(manager));
        return "manager/compensation-claims";
    }

    @PostMapping("/compensation/{id}/approve")
    public String approveCompClaim(@PathVariable Long id,
                                    @RequestParam(required = false) String comment,
                                    RedirectAttributes redirectAttrs) {
        Employee manager = securityUtils.getCurrentEmployee();
        try {
            leaveService.approveCompensationClaim(id, comment, manager);
            redirectAttrs.addFlashAttribute("success", "Compensation claim approved.");
        } catch (LeaveApplicationException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/manager/compensation";
    }

    @PostMapping("/compensation/{id}/reject")
    public String rejectCompClaim(@PathVariable Long id,
                                   @RequestParam String comment,
                                   RedirectAttributes redirectAttrs) {
        Employee manager = securityUtils.getCurrentEmployee();
        try {
            leaveService.rejectCompensationClaim(id, comment, manager);
            redirectAttrs.addFlashAttribute("success", "Compensation claim rejected.");
        } catch (LeaveApplicationException e) {
            redirectAttrs.addFlashAttribute("error", e.getMessage());
        }
        return "redirect:/manager/compensation";
    }

    // =========== REPORTING ===========

    @GetMapping("/reports")
    public String reportsPage(Model model) {
        model.addAttribute("leaveTypes", leaveService.getActiveLeaveTypes());
        model.addAttribute("today", LocalDate.now());
        return "manager/reports";
    }

    @GetMapping("/reports/leave")
    public String leaveReport(@RequestParam LocalDate startDate,
                               @RequestParam LocalDate endDate,
                               @RequestParam(required = false) Long leaveTypeId,
                               Model model) {
        List<LeaveApplication> results;
        if (leaveTypeId != null) {
            results = leaveService.getApprovedLeaveByTypeAndRange(leaveTypeId, startDate, endDate);
        } else {
            results = leaveService.getApprovedLeaveInRange(startDate, endDate);
        }

        model.addAttribute("results", results);
        model.addAttribute("startDate", startDate);
        model.addAttribute("endDate", endDate);
        model.addAttribute("leaveTypes", leaveService.getActiveLeaveTypes());
        model.addAttribute("selectedLeaveTypeId", leaveTypeId);
        return "manager/report-result";
    }

    @GetMapping("/reports/leave/export")
    public ResponseEntity<byte[]> exportLeaveReport(@RequestParam LocalDate startDate,
                                                     @RequestParam LocalDate endDate,
                                                     @RequestParam(required = false) Long leaveTypeId) throws IOException {
        List<LeaveApplication> results;
        if (leaveTypeId != null) {
            results = leaveService.getApprovedLeaveByTypeAndRange(leaveTypeId, startDate, endDate);
        } else {
            results = leaveService.getApprovedLeaveInRange(startDate, endDate);
        }

        StringWriter sw = new StringWriter();
        try (CSVWriter writer = new CSVWriter(sw)) {
            writer.writeNext(new String[]{"Employee", "Leave Type", "Start Date", "End Date", "Duration", "Reason", "Status"});
            for (LeaveApplication la : results) {
                writer.writeNext(new String[]{
                        la.getEmployee().getName(),
                        la.getLeaveType().getName(),
                        la.getStartDate().toString(),
                        la.getEndDate().toString(),
                        String.valueOf(la.getDuration()),
                        la.getReason(),
                        la.getStatus().name()
                });
            }
        }

        byte[] csvBytes = sw.toString().getBytes();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=leave-report.csv")
                .contentType(MediaType.parseMediaType("text/csv"))
                .body(csvBytes);
    }
}
