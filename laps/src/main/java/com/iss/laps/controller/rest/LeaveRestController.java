package com.iss.laps.controller.rest;

import com.iss.laps.dto.EntitlementDto;
import com.iss.laps.dto.LeaveDto;
import com.iss.laps.model.LeaveApplication;
import com.iss.laps.service.EmployeeService;
import com.iss.laps.service.LeaveService;
import com.iss.laps.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class LeaveRestController {

    private final LeaveService leaveService;
    private final EmployeeService employeeService;
    private final SecurityUtils securityUtils;

    // returns name + designation for the Angular dashboard header
    @GetMapping("/me")
    public ResponseEntity<Map<String, String>> getMe() {
        var employee = securityUtils.getCurrentEmployee();
        return ResponseEntity.ok(Map.of(
                "fullName",    employee.getName(),
                "designation", employee.getDesignation() != null ? employee.getDesignation().name() : ""
        ));
    }

    /**
     * GET /api/v1/leaves/my - Get current user's leave history
     */
    @GetMapping("/leaves/my")
    public ResponseEntity<List<LeaveDto>> getMyLeaves() {
        var employee = securityUtils.getCurrentEmployee();
        return ResponseEntity.ok(leaveService.getMyLeaveHistory(employee).stream()
                .map(LeaveDto::from)
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
    public ResponseEntity<List<EntitlementDto>> getMyEntitlements() {
        var employee = securityUtils.getCurrentEmployee();
        int year = LocalDate.now().getYear();
        return ResponseEntity.ok(employeeService.getEntitlements(employee, year).stream()
                .map(EntitlementDto::from)
                .toList());
    }

    /**
     * GET /api/v1/movement?year=&month= - Get movement register data
     */
    @GetMapping("/movement")
    public ResponseEntity<Map<String, Object>> getMovement(
        @RequestParam(defaultValue = "0") int year,
        @RequestParam(defaultValue = "0") int month) {
    
    // Validate year and month parameters (issue #41)
    LocalDate now = LocalDate.now();
    int y = year == 0 ? now.getYear() : year;
    int m = month == 0 ? now.getMonthValue() : month;
    
    // Validate year range (2020-2035)
    if (y < 2020 || y > 2035) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", "Year must be between 2020 and 2035");
        return ResponseEntity.badRequest().body(error);
    }
    
    // Validate month range (1-12)
    if (m < 1 || m > 12) {
        Map<String, Object> error = new HashMap<>();
        error.put("error", "Month must be between 1 and 12");
        return ResponseEntity.badRequest().body(error);
    }

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
     * GET /api/v1/leave-types - Get all active leave types
     */
    @GetMapping("/leave-types")
    public ResponseEntity<List<?>> getLeaveTypes() {
        return ResponseEntity.ok(leaveService.getActiveLeaveTypes().stream().map(lt -> {
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

}
