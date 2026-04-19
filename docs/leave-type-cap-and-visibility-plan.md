# Leave Type Cap And Visibility Plan

## Summary

Two related gaps exist in the current leave workflow:

1. Custom leave type entitlements are not capped by the value defined on the leave type itself.
2. Employees cannot see custom leave types in the leave application form, so they cannot apply for them.

The fix should treat `LeaveType.maxDaysPerYear` as the source of truth for custom leave type entitlements and expose active custom leave types to employee application flows.

## Current Behavior

### Entitlement cap

`EmployeeService.updateEntitlement(...)` currently uses hard-coded caps for default leave types and falls back to `365` days for custom leave types.

```java
double maxAllowed = getMaxEntitlementFor(entitlement.getEmployee(), entitlement.getLeaveType());
if (totalDays > maxAllowed) {
    throw new LeaveApplicationException(
        "Total entitlement exceeds the allowed cap of " + maxAllowed + " days for this leave type.");
}
```

`AdminService.createLeaveType(...)` also creates custom leave type entitlements with `0` days instead of using the leave type's configured maximum.

```java
double days = 0.0;
if (leaveType.getDefaultType() != null) {
    switch (leaveType.getDefaultType()) {
        case ANNUAL:
            days = emp.getDesignation().getAnnualLeaveEntitlement();
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
}
```

### Employee visibility

The employee leave form and the REST endpoint both filter leave types down to default leave types only.

```java
model.addAttribute("leaveTypes", leaveService.getDefaultActiveLeaveTypes());
```

```java
return ResponseEntity.ok(leaveService.getDefaultActiveLeaveTypes().stream().map(lt -> {
    Map<String, Object> map = new HashMap<>();
    map.put("id", lt.getId());
    map.put("name", lt.getName());
    map.put("description", lt.getDescription());
    map.put("maxDaysPerYear", lt.getMaxDaysPerYear());
    map.put("halfDayAllowed", lt.isHalfDayAllowed());
    return map;
}).toList());
```

`LeaveService.validateLeaveApplication(...)` also rejects custom leave types outright.

```java
LeaveTypeDefault typeLeave = leaveType.getDefaultType();
if (typeLeave == null) {
    throw new LeaveApplicationException("Selected leave type is not supported for leave applications");
}
```

## Proposed Changes

### 1. Make entitlement limits come from the leave type definition

For custom leave types, `maxDaysPerYear` should be the entitlement cap and should be enforced in the service layer.

Recommended direction:

```java
private double getMaxEntitlementFor(Employee employee, LeaveType leaveType) {
    if (leaveType.getDefaultType() == null) {
        return leaveType.getMaxDaysPerYear();
    }

    switch (leaveType.getDefaultType()) {
        case ANNUAL:
            return employee.getDesignation().getAnnualLeaveEntitlement();
        case MEDICAL:
            return 14;
        case HOSPITALISATION:
            return 46;
        case COMPENSATION:
            return 108;
        default:
            return leaveType.getMaxDaysPerYear();
    }
}
```

If the business rule is that entitlement must exactly match the leave type setting, the update path should reject any value that is not equal to `leaveType.getMaxDaysPerYear()` instead of only checking the upper bound.

### 2. Seed custom leave entitlements with the configured maximum

When a custom leave type is created, existing employees should receive entitlements based on `leaveType.maxDaysPerYear`, not `0`.

```java
double days = leaveType.getDefaultType() == null
        ? leaveType.getMaxDaysPerYear()
        : switch (leaveType.getDefaultType()) {
            case ANNUAL -> emp.getDesignation().getAnnualLeaveEntitlement();
            case MEDICAL -> 14;
            case HOSPITALISATION -> 46;
            case COMPENSATION -> 0;
        };
```

This keeps the entitlement record aligned with the leave type definition from the moment the type is assigned.

### 3. Expose active custom leave types to employees

The leave application form and any API used by the employee UI should receive all active leave types, not only default ones.

```java
model.addAttribute("leaveTypes", leaveService.getActiveLeaveTypes());
```

```java
return ResponseEntity.ok(leaveService.getActiveLeaveTypes().stream().map(lt -> {
    Map<String, Object> map = new HashMap<>();
    map.put("id", lt.getId());
    map.put("name", lt.getName());
    map.put("description", lt.getDescription());
    map.put("maxDaysPerYear", lt.getMaxDaysPerYear());
    map.put("halfDayAllowed", lt.isHalfDayAllowed());
    map.put("defaultType", lt.getDefaultType());
    return map;
}).toList());
```

### 4. Allow custom leave types in leave application validation

`LeaveService.validateLeaveApplication(...)` should allow custom leave types to pass validation and apply a generic duration rule.

Recommended direction:

```java
LeaveType leaveType = resolveLeaveType(application);
if (leaveType.getDefaultType() == null) {
    double duration = calculateDuration(application);
    double usedDays = leaveAppRepo.sumUsedDaysByEmployeeAndLeaveTypeAndYear(
            employee, leaveType.getId(), start.getYear(), excludeId);

    if (usedDays + duration > leaveType.getMaxDaysPerYear()) {
        throw new LeaveApplicationException("Insufficient leave balance. Remaining: "
                + (leaveType.getMaxDaysPerYear() - usedDays) + " days");
    }
}
```

Custom leave types will use the same generic working-day duration rule as annual leave:

```java
// For custom leave types, calculate duration using the same working-day rules as annual leave.
double duration = leaveCalculator.calculateAnnualLeaveDays(start, end, holidays);
```

## Files Likely To Change

- [src/main/java/com/iss/laps/service/AdminService.java](src/main/java/com/iss/laps/service/AdminService.java)
- [src/main/java/com/iss/laps/service/EmployeeService.java](src/main/java/com/iss/laps/service/EmployeeService.java)
- [src/main/java/com/iss/laps/service/LeaveService.java](src/main/java/com/iss/laps/service/LeaveService.java)
- [src/main/java/com/iss/laps/controller/EmployeeController.java](src/main/java/com/iss/laps/controller/EmployeeController.java)
- [src/main/java/com/iss/laps/controller/rest/LeaveRestController.java](src/main/java/com/iss/laps/controller/rest/LeaveRestController.java)
- [src/main/resources/templates/employee/leave-apply.html](src/main/resources/templates/employee/leave-apply.html)
- [src/main/resources/templates/employee/leave-edit.html](src/main/resources/templates/employee/leave-edit.html)
- [src/main/resources/templates/admin/entitlements.html](src/main/resources/templates/admin/entitlements.html)
- [src/test/java/com/iss/laps/AdminServiceTest.java](src/test/java/com/iss/laps/AdminServiceTest.java)
- [src/test/java/com/iss/laps/EmployeeServiceTest.java](src/test/java/com/iss/laps/EmployeeServiceTest.java)
- [src/test/java/com/iss/laps/LeaveServiceTest.java](src/test/java/com/iss/laps/LeaveServiceTest.java)
- [src/test/java/com/iss/laps/AdminControllerTest.java](src/test/java/com/iss/laps/AdminControllerTest.java)

## Sequenced Work

1. Update entitlement creation so custom leave types inherit `maxDaysPerYear`.
2. Update entitlement update validation so custom leave types cannot exceed the configured cap.
3. Switch employee leave-form sources from default-only types to all active employee-eligible types.
4. Relax leave application validation so custom leave types are accepted and validated.
5. Update any employee-facing templates or API payloads to show custom leave types in the dropdown.
6. Add or update tests for entitlement caps, dropdown visibility, and custom leave application submission.

## Edge Cases To Cover

- Existing custom entitlements created with `0` days should be corrected or migrated.
- Admins should not be able to create an entitlement higher or lower than the leave type cap.
- Inactive leave types must still be excluded from employee-facing dropdowns.
- If a custom leave type has no clear duration rule, the application flow should fail fast with a clear validation message rather than silently accepting bad data.
- The update form should not let the user bypass the cap with a crafted request.

## Review Checkpoint

No open policy question remains for this plan.

Custom leave types will be validated and counted using the same working-day duration rule as annual leave.
