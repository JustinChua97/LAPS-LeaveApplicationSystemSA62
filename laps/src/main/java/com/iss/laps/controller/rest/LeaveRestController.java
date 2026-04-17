package com.iss.laps.controller.rest;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.iss.laps.model.LeaveApplication;
import com.iss.laps.model.LeaveEntitlement;
import com.iss.laps.service.EmployeeService;
import com.iss.laps.service.LeaveService;
import com.iss.laps.util.SecurityUtils;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LeaveRestController {

    private final LeaveService leaveService;
    private final EmployeeService employeeService;
    private final SecurityUtils securityUtils;

    /**
     * GET /api/v1/leaves/my - Get current user's leave history
     */
    @GetMapping("/leaves/my")
    public ResponseEntity<List<Map<String, Object>>> getMyLeaves() {
        var employee = securityUtils.getCurrentEmployee();
        return ResponseEntity.ok(leaveService.getMyLeaveHistory(employee).stream()
                .map(this::toLeaveResponse)
                .toList());
    }

    /**
     * GET /api/v1/leaves/{id} - Get specific leave application
     */
    @GetMapping("/leaves/{id}")
    public ResponseEntity<Map<String, Object>> getLeave(@PathVariable Long id) {
        var employee = securityUtils.getCurrentEmployee();
        return ResponseEntity.ok(toLeaveResponse(leaveService.findByIdAndEmployee(id, employee)));
    }

    /**
     * GET /api/v1/leaves/entitlements - Get current user's entitlements
     */
    @GetMapping("/leaves/entitlements")
    public ResponseEntity<List<Map<String, Object>>> getMyEntitlements() {
        var employee = securityUtils.getCurrentEmployee();
        int year = LocalDate.now().getYear();
        return ResponseEntity.ok(employeeService.getEntitlements(employee, year).stream()
                .map(this::toEntitlementResponse)
                .toList());
    }

    /**
     * GET /api/v1/movement?year=&month= - Get movement register data
     */
    @GetMapping("/movement")
    public ResponseEntity<Map<String, Object>> getMovement(
            @RequestParam(defaultValue = "0") int year,
            @RequestParam(defaultValue = "0") int month) {
        LocalDate now = LocalDate.now();
        int y = year == 0 ? now.getYear() : year;
        int m = month == 0 ? now.getMonthValue() : month;

        List<LeaveApplication> leaves = leaveService.getApprovedLeaveInMonth(y, m);

        Map<String, Object> result = new HashMap<>();
        result.put("year", y);
        result.put("month", m);
        result.put("leaves", leaves.stream().map(la -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", la.getId());
            map.put("employee", la.getEmployee().getName());
            map.put("leaveType", la.getLeaveType().getName());
            map.put("startDate", la.getStartDate().toString());
            map.put("endDate", la.getEndDate().toString());
            map.put("duration", la.getDuration());
            return map;
        }).toList());

        return ResponseEntity.ok(result);
    }

    /**
     * GET /api/v1/leave-types - Get all default active leave types
     */
    @GetMapping("/leave-types")
    public ResponseEntity<List<?>> getLeaveTypes() {
        return ResponseEntity.ok(leaveService.getDefaultActiveLeaveTypes().stream().map(lt -> {
            Map<String, Object> map = new HashMap<>();
            map.put("id", lt.getId());
            map.put("name", lt.getName());
            map.put("description", lt.getDescription());
            map.put("maxDaysPerYear", lt.getMaxDaysPerYear());
            map.put("halfDayAllowed", lt.isHalfDayAllowed());
            return map;
        }).toList());
    }

    private Map<String, Object> toLeaveResponse(LeaveApplication leaveApplication) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", leaveApplication.getId());
        map.put("leaveTypeId", leaveApplication.getLeaveType().getId());
        map.put("leaveType", leaveApplication.getLeaveType().getName());
        map.put("startDate", leaveApplication.getStartDate().toString());
        map.put("endDate", leaveApplication.getEndDate().toString());
        map.put("duration", leaveApplication.getDuration());
        map.put("reason", leaveApplication.getReason());
        map.put("workDissemination", leaveApplication.getWorkDissemination());
        map.put("contactDetails", leaveApplication.getContactDetails());
        map.put("status", leaveApplication.getStatus().name());
        map.put("managerComment", leaveApplication.getManagerComment());
        map.put("appliedDate", leaveApplication.getAppliedDate().toString());
        map.put("updatedDate", leaveApplication.getUpdatedDate() == null
                ? null
                : leaveApplication.getUpdatedDate().toString());
        map.put("halfDay", leaveApplication.isHalfDay());
        map.put("halfDayType", leaveApplication.getHalfDayType());
        return map;
    }

    private Map<String, Object> toEntitlementResponse(LeaveEntitlement entitlement) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("id", entitlement.getId());
        map.put("leaveTypeId", entitlement.getLeaveType().getId());
        map.put("leaveType", entitlement.getLeaveType().getName());
        map.put("year", entitlement.getYear());
        map.put("totalDays", entitlement.getTotalDays());
        map.put("usedDays", entitlement.getUsedDays());
        map.put("remainingDays", entitlement.getRemainingDays());
        return map;
    }
}
