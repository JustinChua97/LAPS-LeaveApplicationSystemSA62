# LAPS Codebase Onboarding Guide

**Last Updated:** 18 April 2026  
**Document Level:** Comprehensive Technical Onboarding  
**Audience:** New developers joining the LAPS team

---

## Table of Contents

1. [Project Overview](#project-overview)
2. [Architecture & Layers](#architecture--layers)
3. [Domain Models](#domain-models)
4. [Business Logic Flows](#business-logic-flows)
5. [Service Layer](#service-layer)
6. [Controller Layer](#controller-layer)
7. [Security Architecture](#security-architecture)
8. [Repository Layer & Queries](#repository-layer--queries)
9. [Utility Functions](#utility-functions)
10. [Key Workflows & Entry Points](#key-workflows--entry-points)
11. [Assumptions & Known Gaps](#assumptions--known-gaps)

---

## Project Overview

**LAPS** (Leave Application Processing System) is an enterprise web application for managing employee leave, built for ISS NUS as a coursework project (SA62).

### Core Features

- **Leave Management**: Employees apply for leave, managers approve/reject, admins manage leave types and public holidays
- **Entitlement Tracking**: Per-employee, per-leave-type, per-year balance management
- **Compensation Claims**: Employees claim overtime hours (converts to compensation leave days at 4h = 0.5 day)
- **Movement Register**: Public-facing view of approved leaves for attendance reconciliation
- **Role-Based Access**: Admin → Manager → Employee hierarchy with differential permissions
- **REST API**: Stateless JWT-authenticated API for external consumers (issue #6)
- **Email Notifications**: Async email alerts for applications, approvals, rejections

### Tech Stack

| Component | Technology |
| --- | --- |
| Framework | Spring Boot 3.2.3 |
| Language | Java 17 |
| Database | PostgreSQL 14+ |
| ORM | Spring Data JPA + Hibernate |
| Security | Spring Security 6 + JWT (JJWT 0.12.6) |
| View Layer | Thymeleaf 3 (server-side rendering) |
| Build | Maven 3.9+ |
| UI | Bootstrap 5.3.2 (WebJars) |
| Testing | JUnit 5 + MockMvc + H2 |

---

## Architecture & Layers

LAPS follows a **3-layer Model-View-Controller (MVC) architecture** with clear separation of concerns:

```
┌─────────────────────────────────────────────────┐
│          PRESENTATION LAYER                     │
│  • Thymeleaf Templates (HTML/Bootstrap)         │
│  • REST Controllers (@RestController)           │
│  • Web Controllers (@Controller)                │
│  • Global Exception Handler                     │
└──────────────┬──────────────────────────────────┘
               │ HTTP Requests
┌──────────────▼──────────────────────────────────┐
│          SECURITY LAYER                         │
│  • Spring Security 6                            │
│  • JWT Authentication Filter                    │
│  • Two-chain configuration (API + Web)          │
│  • Role hierarchy: ADMIN > MANAGER > EMPLOYEE   │
└──────────────┬──────────────────────────────────┘
               │ Authenticated requests
┌──────────────▼──────────────────────────────────┐
│          SERVICE LAYER                          │
│  • LeaveService (core business logic)           │
│  • EmployeeService (employee operations)        │
│  • AdminService (system administration)         │
│  • EmailService (async notifications)           │
│  • CustomUserDetailsService (authentication)    │
│  • JwtService (token generation/validation)     │
│  • @Transactional guards consistency            │
└──────────────┬──────────────────────────────────┘
               │ Domain objects
┌──────────────▼──────────────────────────────────┐
│          REPOSITORY LAYER                       │
│  • Spring Data JPA repositories                 │
│  • Custom JPQL queries (parameterized)          │
│  • No raw SQL or HQL                            │
│  • Authorization checks in query WHERE clauses  │
└──────────────┬──────────────────────────────────┘
               │ ORM → SQL
┌──────────────▼──────────────────────────────────┐
│          DATABASE LAYER                         │
│  • PostgreSQL 14+ relational schema             │
│  • Seed data: test accounts, leave types, PH    │
└─────────────────────────────────────────────────┘
```

### Design Principles

1. **Single Responsibility**: Each layer has one clear job (presentation, security, business, data)
2. **Dependency Injection**: Spring manages all object creation and wiring
3. **Transactional Consistency**: @Transactional guards atomicity of multi-step operations
4. **Async Communication**: Email sends never block the main transaction
5. **Layered Security**: Authorization checked at service layer AND Spring Security
6. **Fail-Fast Validation**: Reject invalid input at controller boundary; don't sanitize

---

## Domain Models

All domain models live in `laps/src/main/java/com/iss/laps/model/` and are JPA entities mapped to PostgreSQL tables.

### 1. **Employee** (`Employee.java`)

Represents a user in the system.

```java
@Entity
@Table(name = "employees")
public class Employee {
    @Id
    private Long id;
    @Column(unique = true)
    private String username;              // Login identifier
    private String password;              // BCrypt-hashed
    private String name;                  // Display name
    @Email
    private String email;                 // Contact email
    @Enumerated(EnumType.STRING)
    private Role role;                    // ADMIN, MANAGER, EMPLOYEE
    @Enumerated(EnumType.STRING)
    private Designation designation;      // ADMINISTRATIVE, PROFESSIONAL, SENIOR_PROFESSIONAL
    private boolean active;               // Soft-delete flag
    
    @ManyToOne(fetch = FetchType.LAZY)
    private Employee manager;             // Reports to (null if no manager)
    
    @OneToMany(mappedBy = "manager")
    private List<Employee> subordinates;  // Direct reports
    
    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL)
    private List<LeaveApplication> leaveApplications;
    
    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL)
    private List<LeaveEntitlement> leaveEntitlements;
    
    @OneToMany(mappedBy = "employee", cascade = CascadeType.ALL)
    private List<CompensationClaim> compensationClaims;
}
```

**Key Relationships:**
- Self-referencing: manager-subordinate (hierarchical org structure)
- Has-many: leave applications, entitlements, compensation claims
- Used for: authentication, authorization, role-based filtering

---

### 2. **LeaveApplication** (`LeaveApplication.java`)

Represents a single leave application with state machine.

```java
@Entity
@Table(name = "leave_applications")
public class LeaveApplication {
    @Id
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    private Employee employee;
    
    @ManyToOne(fetch = FetchType.EAGER)
    private LeaveType leaveType;         // Annual, Medical, Hospitalisation, Compensation
    
    private LocalDate startDate;         // First day of leave
    private LocalDate endDate;           // Last day of leave (inclusive)
    private double duration;             // Calculated in days (or 0.5 increments for compensation)
    
    private String reason;               // Why taking leave
    private String workDissemination;    // Handover plan
    private String contactDetails;       // During-leave contact info
    
    @Enumerated(EnumType.STRING)
    private LeaveStatus status;          // APPLIED, UPDATED, APPROVED, REJECTED, CANCELLED, DELETED
    
    private String managerComment;       // Approval/rejection reason
    
    private LocalDateTime appliedDate;   // Created timestamp
    private LocalDateTime updatedDate;   // Last modified timestamp
    
    // Compensation leave half-day fields
    private boolean halfDay;             // For compensation leave only
    private String halfDayType;          // "AM" or "PM"
    
    // State machine methods:
    public boolean isEditable();         // APPLIED or UPDATED only
    public boolean isCancellable();      // APPROVED only
    public boolean isDeletable();        // APPLIED, UPDATED, or REJECTED
}
```

**State Machine:**
```
[APPLIED] ─(update)─> [UPDATED]
    │                     │
    └─────────────────────┘
                │
        (manager review)
                ├─(approve)─> [APPROVED] ─(cancel)─> [CANCELLED]
                │
                └─(reject)──> [REJECTED]

Employee can delete: APPLIED, UPDATED, REJECTED
System deletes to DELETED (soft delete, not hard delete)
```

---

### 3. **LeaveType** (`LeaveType.java`)

Defines the categories of leave available (Annual, Medical, etc.).

```java
@Entity
@Table(name = "leave_types")
public class LeaveType {
    @Id
    private Long id;
    
    private String name;                 // "Annual Leave", "Medical Leave", etc.
    private String description;
    private int maxDaysPerYear;          // Entitlement per year
    private boolean halfDayAllowed;      // Can take 0.5 day increments?
    private boolean active;              // Can be applied for?
    
    @Enumerated(EnumType.STRING)
    private LeaveTypeDefault defaultType; // ANNUAL, MEDICAL, HOSPITALISATION, COMPENSATION (enum)
}
```

**Seeded Leave Types:**
- **Annual Leave**: max 15 days/year, full days only, distinct calculation rules (see LeaveCalculator)
- **Medical Leave**: max 14 days/year, full days only, used by doctor's prescription
- **Hospitalisation Leave**: max 60 days/year, full days only
- **Compensation Leave**: max 10 days/year (pseudo), half days allowed, created from compensation claims

---

### 4. **LeaveEntitlement** (`LeaveEntitlement.java`)

Tracks balance per employee-leave-type-year.

```java
@Entity
@Table(name = "leave_entitlements",
       uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "leave_type_id", "year"}))
public class LeaveEntitlement {
    @Id
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    private Employee employee;
    
    @ManyToOne(fetch = FetchType.EAGER)
    private LeaveType leaveType;
    
    private int year;                    // Calendar year
    private double totalDays;            // Starting entitlement
    private double usedDays;             // Approved + applied days
    
    public double getRemainingDays() {
        return totalDays - usedDays;
    }
}
```

**Key Point:** The unique constraint ensures only one entitlement record per (employee, leave type, year). Balance is tracked continuously; when a leave is approved, `usedDays` is incremented. When cancelled, it's decremented (restoration).

---

### 5. **LeaveStatus** (`LeaveStatus.java`)

```java
public enum LeaveStatus {
    APPLIED,    // Initial state: awaiting manager review
    UPDATED,    // Employee updated after initial rejection
    APPROVED,   // Manager approved; entitlements deducted
    REJECTED,   // Manager rejected
    CANCELLED,  // Employee cancelled approved leave
    DELETED     // Soft-deleted (for cleanup)
}
```

---

### 6. **Role** (`Role.java`)

```java
public enum Role {
    ROLE_ADMIN,     // Full system access
    ROLE_MANAGER,   // Can view/approve subordinates' leave
    ROLE_EMPLOYEE   // Can view own leave, apply, and claim compensation
}
```

---

### 7. **Designation** (`Designation.java`)

Used to categorize employees for reporting.

```java
public enum Designation {
    ADMINISTRATIVE,
    PROFESSIONAL,
    SENIOR_PROFESSIONAL
}
```

---

### 8. **CompensationClaim** (`CompensationClaim.java`)

Represents an overtime claim that converts to leave days.

```java
@Entity
@Table(name = "compensation_claims")
public class CompensationClaim {
    @Id
    private Long id;
    
    @ManyToOne(fetch = FetchType.LAZY)
    private Employee employee;
    
    private LocalDate overtimeDate;     // Date of overtime work
    
    @Min(1) @Max(4)
    private int overtimeHours;          // 1–4 hours only (validates per MOM rules)
    
    private double compensationDays;    // Calculated: overtimeHours / 4.0 * 0.5
                                       // E.g., 4h = 0.5 days, 2h = 0.25 days
    
    @Enumerated(EnumType.STRING)
    private ClaimStatus status;         // PENDING, APPROVED, REJECTED
    
    private String managerComment;      // Approval/rejection reason
    private LocalDateTime claimedDate;  // When claimed
    private LocalDateTime processedDate;// When manager processed
    private String reason;              // Why overtime
    
    public enum ClaimStatus {
        PENDING, APPROVED, REJECTED
    }
}
```

**Conversion Logic:** Every 4 hours of overtime = 0.5 compensation leave day (MOM rule).  
**Monthly Cap:** Manager can approve max 72 hours of overtime per employee per calendar month (issue #19).

---

### 9. **PublicHoliday** (`PublicHoliday.java`)

Seeded list of Singapore public holidays used in leave calculations.

```java
@Entity
@Table(name = "public_holidays")
public class PublicHoliday {
    @Id
    private Long id;
    
    private LocalDate holidayDate;      // Date of the holiday
    private String name;                // E.g., "New Year's Day"
    private String description;
}
```

**Special Logic:** If a holiday falls on Sunday, the following Monday is marked as observed (cascading rule in LeaveCalculator for repeated holidays).

---

## Business Logic Flows

This section traces the primary business processes through all layers (Controller → Service → Repository → Database).

### Flow 1: Employee Submits a Leave Application

**Actors:** Employee, System  
**Entry Point:** Employee clicks "Apply for Leave" button

#### Step-by-Step Execution

**1. Controller Layer** (`EmployeeController.applyLeave()`)
```java
@PostMapping("/leaves/apply")
public String applyLeave(@Valid @ModelAttribute LeaveApplication application,
                         BindingResult result, Model model, RedirectAttributes redirectAttrs) {
    Employee employee = securityUtils.getCurrentEmployee();
    
    // Validation happens here (first pass via @Valid annotations)
    if (result.hasErrors()) {
        return "employee/leave-apply";  // Re-render form with errors
    }
    
    try {
        leaveService.applyLeave(application, employee);  // Delegate to service
        redirectAttrs.addFlashAttribute("success", "Leave application submitted successfully.");
        return "redirect:/employee/leaves";
    } catch (LeaveApplicationException e) {
        model.addAttribute("error", e.getMessage());
        return "employee/leave-apply";
    }
}
```

**What Happens:**
- `@Valid` triggers Bean Validation on `LeaveApplication` (checks @NotNull, @NotBlank on required fields)
- `BindingResult` captures validation errors
- If errors exist, form re-renders with error messages
- If valid, delegates to `LeaveService.applyLeave()`

**2. Service Layer** (`LeaveService.applyLeave()`)
```java
@Transactional
public LeaveApplication applyLeave(LeaveApplication application, Employee employee) {
    // Set defaults
    application.setEmployee(employee);
    application.setStatus(LeaveStatus.APPLIED);
    application.setLeaveType(resolveLeaveType(application));  // Resolve by ID
    
    // SECOND validation pass (business rules)
    validateLeaveApplication(application, employee, null);
    
    // Calculate duration (business rule)
    double duration = calculateDuration(application);
    application.setDuration(duration);
    
    // Persist (repository)
    LeaveApplication saved = leaveAppRepo.save(application);
    
    // Notify manager asynchronously (non-blocking)
    if (employee.getManager() != null) {
        initEmailAssociations(saved);
        emailService.sendLeaveApplicationNotification(saved, NotificationType.APPLICATION);
    }
    
    return saved;
}
```

**What Happens:**
- Sets employee context and initial status
- **Second validation pass** via `validateLeaveApplication()` checks:
  - Start date ≥ today
  - End date ≥ start date
  - Leave type is active
  - Reason and contact details not blank
  - Overlapping applications don't exist
  - Sufficient entitlement balance (estimated)
- Duration is calculated via `calculateDuration()` which uses `LeaveCalculator` (see next section)
- Transaction boundary ensures atomicity
- Saved to repository
- **Async email** queued (doesn't block the transaction)

**3. Repository Layer** (implicit in `save()`)
```java
// LeaveApplicationRepository extends JpaRepository<LeaveApplication, Long>
// JpaRepository.save() delegates to Hibernate:
// INSERT INTO leave_applications (employee_id, leave_type_id, start_date, end_date, 
//                                 duration, reason, status, applied_date, ...)
// VALUES (?, ?, ?, ?, ?, ?, ?, ?, ...)
```

**4. Database Layer**
```sql
-- Hibernate generates:
INSERT INTO leave_applications (employee_id, leave_type_id, start_date, end_date, 
                               duration, reason, status, applied_date, ...)
VALUES (5, 1, '2024-04-20', '2024-04-24', 5.0, 'Annual vacation', 'APPLIED', '2024-04-18 10:30:45', ...);

-- Returns generated ID: 42
```

**5. Email Service** (async, happens after transaction commits)
```java
@Async
public void sendLeaveApplicationNotification(LeaveApplication app, NotificationType type) {
    // Queued on async thread pool; runs independently
    // If email fails, it's logged but doesn't fail the original request
}
```

**Result:** Application created with status=APPLIED, notification sent to manager, employee redirected to leave history.

---

### Flow 2: Manager Approves a Leave Application

**Actors:** Manager, System, Employee (recipient of approval email)  
**Entry Point:** Manager clicks "Approve" button on a pending leave

#### Step-by-Step Execution

**1. Controller Layer** (`ManagerController.approveLeave()`)
```java
@PostMapping("/leaves/{id}/approve")
public String approveLeave(@PathVariable Long id, @RequestParam String comment, 
                          RedirectAttributes redirectAttrs) {
    Employee manager = securityUtils.getCurrentEmployee();
    
    try {
        leaveService.approveLeave(id, comment, manager);  // Delegate
        redirectAttrs.addFlashAttribute("success", "Leave approved.");
        return "redirect:/manager/leaves";
    } catch (LeaveApplicationException e) {
        redirectAttrs.addFlashAttribute("error", e.getMessage());
        return "redirect:/manager/leaves";
    }
}
```

**2. Service Layer** (`LeaveService.approveLeave()`)
```java
@Transactional
public void approveLeave(Long id, String comment, Employee manager) {
    // Fetch from repository
    LeaveApplication application = findById(id);
    
    // AUTHORIZATION CHECK: Verify manager has authority
    if (!isSubordinate(application.getEmployee(), manager)) {
        throw new LeaveApplicationException("Not authorised to approve this application");
    }
    
    // STATE CHECK: Only APPLIED or UPDATED can be approved
    if (application.getStatus() != LeaveStatus.APPLIED && 
        application.getStatus() != LeaveStatus.UPDATED) {
        throw new LeaveApplicationException("Application is not in a pending state");
    }
    
    // Update state
    application.setStatus(LeaveStatus.APPROVED);
    application.setManagerComment(comment);
    leaveAppRepo.save(application);  // Persist state change
    
    // DEDUCT ENTITLEMENT (critical step)
    deductEntitlement(application, application.getEmployee());
    
    // Notify employee asynchronously
    initEmailAssociations(application);
    emailService.sendLeaveApplicationNotification(application, NotificationType.APPROVAL);
}
```

**3. Authorization Check** (`isSubordinate()` method)
```java
private boolean isSubordinate(Employee employee, Employee potentialManager) {
    // Traverse the manager chain upward
    Employee current = employee.getManager();
    while (current != null) {
        if (current.getId().equals(potentialManager.getId())) {
            return true;  // Found manager in chain
        }
        current = current.getManager();  // Move up one level
    }
    return false;  // Not found; not a subordinate
}
```

**Why:** Prevents a manager from approving unrelated employees' leave (security layer redundancy).

**4. Entitlement Deduction** (`deductEntitlement()` method)
```java
@Transactional
private void deductEntitlement(LeaveApplication application, Employee employee) {
    LeaveEntitlement entitlement = leaveEntitlementRepo
        .findByEmployeeAndLeaveTypeAndYear(
            employee, 
            application.getLeaveType(), 
            application.getStartDate().getYear()
        );
    
    if (entitlement == null) {
        // Create missing entitlement (shouldn't happen in normal flow)
        entitlement = new LeaveEntitlement(employee, application.getLeaveType(), 
                                          application.getStartDate().getYear(), 
                                          application.getLeaveType().getMaxDaysPerYear());
        entitlement = leaveEntitlementRepo.save(entitlement);
    }
    
    // Increment used_days
    double newUsedDays = entitlement.getUsedDays() + application.getDuration();
    entitlement.setUsedDays(newUsedDays);
    leaveEntitlementRepo.save(entitlement);
}
```

**Database State After This Step:**
```sql
-- Leave application status updated
UPDATE leave_applications SET status = 'APPROVED', manager_comment = '...' WHERE id = 42;

-- Entitlement balance reduced
UPDATE leave_entitlements SET used_days = used_days + 5.0 
WHERE employee_id = 5 AND leave_type_id = 1 AND year = 2024;
```

**Result:** Application approved, entitlement reduced, approval email sent to employee, manager redirected.

---

### Flow 3: Calculate Leave Duration (Utility)

**Context:** Called by `calculateDuration()` in `applyLeave()` and `updateLeave()`

#### Leave Duration Calculation Rules

The complexity here lies in **different rules for different leave types** (Business Rule Complexity).

**Invocation:**
```java
double duration = calculateDuration(application);

private double calculateDuration(LeaveApplication application) {
    LocalDate start = application.getStartDate();
    LocalDate end = application.getEndDate();
    LeaveType type = application.getLeaveType();
    
    // Fetch public holidays for the year
    List<PublicHoliday> holidays = publicHolidayRepo
        .findByYearOrderByHolidayDate(start.getYear());
    
    double days;
    if (type.getDefaultType() == LeaveTypeDefault.ANNUAL) {
        days = leaveCalculator.calculateAnnualLeaveDays(start, end, holidays);
    } else if (type.getDefaultType() == LeaveTypeDefault.MEDICAL) {
        days = leaveCalculator.calculateMedicalLeaveDays(start, end);
    } else if (type.getDefaultType() == LeaveTypeDefault.HOSPITALISATION) {
        days = leaveCalculator.calculateHospitalisationLeaveDays(start, end);
    } else if (type.getDefaultType() == LeaveTypeDefault.COMPENSATION) {
        days = calculateCompensationLeaveDuration(application);
    } else {
        days = 1.0;  // Default fallback
    }
    
    return days;
}
```

**Annual Leave Calculation Logic** (complex due to MOM rules):
```
IF calendar days between start and end <= 14:
    Exclude weekends AND public holidays
    Count only working days
ELSE:
    Count all calendar days

Example:
  Mon 2024-04-01 to Fri 2024-04-05 (5 calendar days) = 5 working days ✓
  Mon 2024-04-08 to Sun 2024-04-21 (14 calendar days, no weekends) = 10 working days ✓
  Mon 2024-04-01 to Wed 2024-04-17 (17 calendar days) = 17 days (all counted) ✓
```

**See:** [LeaveCalculator Utility](#utility-functions) for detailed implementation.

**Medical / Hospitalisation Leave:**
- Exclude weekends only (public holidays DO count as leave days used)
- Simpler rule for medical situations

**Compensation Leave:**
- 0.5 days per half-day (AM or PM only)
- Validates against monthly cap via manager

---

### Flow 4: Compensation Claim Submission & Approval

**Actors:** Employee (claimant), Manager (approver)  
**Entry Point:** Employee submits overtime claim

#### Employee Submits Claim

**1. Controller** (`EmployeeController.submitCompensationClaim()`)
```java
@PostMapping("/compensation/claim")
public String submitClaim(@Valid @ModelAttribute CompensationClaim claim, 
                         BindingResult result, RedirectAttributes redirectAttrs) {
    Employee employee = securityUtils.getCurrentEmployee();
    
    if (result.hasErrors()) {
        return "employee/compensation-claim";
    }
    
    try {
        leaveService.submitCompensationClaim(claim, employee);
        redirectAttrs.addFlashAttribute("success", "Claim submitted.");
        return "redirect:/employee/compensation";
    } catch (LeaveApplicationException e) {
        redirectAttrs.addFlashAttribute("error", e.getMessage());
        return "redirect:/employee/compensation";
    }
}
```

**2. Service Layer** (`LeaveService.submitCompensationClaim()`)
```java
@Transactional
public CompensationClaim submitCompensationClaim(CompensationClaim claim, Employee employee) {
    claim.setEmployee(employee);
    claim.setStatus(ClaimStatus.PENDING);
    
    // Validate hours (1–4) - @Min @Max on model
    // Compensation days auto-calculated in @PrePersist:
    // compensationDays = (overtimeHours / 4.0) * 0.5
    
    CompensationClaim saved = compClaimRepo.save(claim);
    
    // Notify manager
    if (employee.getManager() != null) {
        emailService.sendCompensationClaimNotification(saved);
    }
    
    return saved;
}
```

#### Manager Approves Claim

**3. Manager Review** (`LeaveService.approveCompensationClaim()`)
```java
@Transactional
public void approveCompensationClaim(Long claimId, String comment, Employee manager) {
    CompensationClaim claim = compClaimRepo.findById(claimId)
        .orElseThrow(() -> new ResourceNotFoundException("Claim not found"));
    
    // Authorization
    if (!isSubordinate(claim.getEmployee(), manager)) {
        throw new LeaveApplicationException("Not authorised");
    }
    
    if (claim.getStatus() != ClaimStatus.PENDING) {
        throw new LeaveApplicationException("Claim not in pending state");
    }
    
    // CHECK MONTHLY CAP (issue #19)
    LocalDate claimDate = claim.getOvertimeDate();
    int year = claimDate.getYear();
    int month = claimDate.getMonthValue();
    
    double monthlyApprovedHours = compClaimRepo
        .sumApprovedOvertimeHoursForMonthByEmployee(
            claim.getEmployee(), year, month);
    
    if (monthlyApprovedHours + claim.getOvertimeHours() > 72) {
        throw new LeaveApplicationException(
            "Monthly overtime cap exceeded (max 72 hours). " +
            "Already approved: " + monthlyApprovedHours + " hours");
    }
    
    // Approve
    claim.setStatus(ClaimStatus.APPROVED);
    claim.setManagerComment(comment);
    claim.setProcessedDate(LocalDateTime.now());
    compClaimRepo.save(claim);
    
    // CREATE COMPENSATION LEAVE ENTITLEMENT
    // When approved, convert overtime hours to leave days
    createCompensationLeaveFromClaim(claim);
    
    emailService.sendCompensationClaimNotification(claim);
}
```

**4. Create Compensation Leave** (`createCompensationLeaveFromClaim()`)
```java
private void createCompensationLeaveFromClaim(CompensationClaim claim) {
    LeaveType compensationType = leaveTypeRepo
        .findByDefaultType(LeaveTypeDefault.COMPENSATION);
    
    LeaveEntitlement entitlement = leaveEntitlementRepo
        .findByEmployeeAndLeaveTypeAndYear(
            claim.getEmployee(), 
            compensationType, 
            claim.getOvertimeDate().getYear());
    
    if (entitlement == null) {
        entitlement = new LeaveEntitlement(
            claim.getEmployee(), 
            compensationType, 
            claim.getOvertimeDate().getYear(), 
            10.0);  // Max 10 days compensation per year
    }
    
    // Add compensation days to entitlement
    entitlement.setTotalDays(entitlement.getTotalDays() + claim.getCompensationDays());
    leaveEntitlementRepo.save(entitlement);
}
```

**Result:** Compensation days credited to employee's account, can now be used to apply for compensation leave.

---

## Service Layer

The Service layer contains all business logic and is where most of the "brain" of the application lives. All services are marked with `@Service` and critical write operations are wrapped in `@Transactional`.

### LeaveService (`laps/src/main/java/com/iss/laps/service/LeaveService.java`)

**Purpose:** Core leave management business logic

**Key Methods:**

| Method | Purpose | Transactional | Authorization |
| --- | --- | --- | --- |
| `applyLeave(app, emp)` | Employee submits new leave | YES | Employee must be the applicant |
| `updateLeave(id, app, emp)` | Employee edits pending leave | YES | Employee must own the leave |
| `deleteLeave(id, emp)` | Employee deletes pending leave | YES | Employee must own the leave |
| `cancelLeave(id, emp)` | Employee cancels approved leave | YES | Employee must own; must be APPROVED |
| `approveLeave(id, comment, mgr)` | Manager approves leave | YES | Manager must be supervisor; deducts entitlement |
| `rejectLeave(id, comment, mgr)` | Manager rejects leave | YES | Manager must be supervisor; comment mandatory |
| `findById(id)` | Get leave by ID | NO | Public; called by others |
| `findByIdAndEmployee(id, emp)` | Get leave owned by employee | NO | Validates ownership |
| `getMyLeaveHistory(emp)` | Employee views own history | NO | Current year only |
| `getMyLeaveHistoryPaged(emp, page)` | Paginated history | NO | Supports pagination (issue #45) |
| `getPendingApplicationsForManager(mgr)` | Pending leaves for approval | NO | Filters by manager's subordinates |
| `getSubordinateLeaveHistory(mgr, year)` | Manager views subordinate history | NO | Year-scoped |
| `getSubordinateLeaveDuringPeriod(mgr, start, end)` | Conflict checking for managers | NO | Used in approval UX |
| `getDefaultActiveLeaveTypes()` | Available leave types | NO | For dropdowns |
| `submitCompensationClaim(claim, emp)` | Employee submits overtime claim | YES | Notifies manager |
| `approveCompensationClaim(id, comment, mgr)` | Manager approves overtime → creates leave days | YES | Validates monthly cap (72h) |
| `rejectCompensationClaim(id, comment, mgr)` | Manager rejects claim | YES | Comment mandatory |
| `getPendingCompClaimsForManager(mgr)` | Claims awaiting manager action | NO | Subordinate filter |

**Private Helper Methods:**

| Method | Purpose |
| --- | --- |
| `validateLeaveApplication(app, emp, excludeId)` | Second validation pass: dates, availability, overlaps, balance |
| `calculateDuration(app)` | Calls LeaveCalculator for business-rule duration |
| `deductEntitlement(app, emp)` | Decrements leave balance when approved |
| `restoreEntitlement(app, emp)` | Increments leave balance when cancelled |
| `isSubordinate(emp, potentialMgr)` | Traverses manager chain for authorization |
| `resolveLeaveType(app)` | Fetches LeaveType by ID from app object |
| `initEmailAssociations(app)` | Pre-loads lazy-loaded associations for email service |
| `createCompensationLeaveFromClaim(claim)` | Creates entitlement from approved claim |

---

### EmployeeService (`laps/src/main/java/com/iss/laps/service/EmployeeService.java`)

**Purpose:** Employee-related operations (CRUD, entitlements)

**Key Methods:**

| Method | Purpose |
| --- | --- |
| `createEmployee(emp)` | Admin creates new employee, hashes password |
| `updateEmployee(id, updates, admin)` | Admin updates employee record |
| `deactivateEmployee(id, admin)` | Admin soft-deletes employee (sets active=false) |
| `getEmployeeById(id)` | Fetch employee (used by various flows) |
| `getAllEmployees()` | Admin lists all employees |
| `getActiveEmployees()` | Lists only active=true employees |
| `getSubordinates(mgr)` | Manager views direct reports |
| `getEntitlements(emp, year)` | List leave balances for year |
| `getEntitlement(emp, type, year)` | Single balance lookup |
| `createEntitlement(emp, type, year, days)` | Admin creates entitlement record |
| `resetAnnualEntitlements(year)` | Batch operation: reset all Annual leave balances annually (run ~Jan 1) |

---

### AdminService (`laps/src/main/java/com/iss/laps/service/AdminService.java`)

**Purpose:** System administration: leave types, public holidays, reporting

**Key Methods:**

| Method | Purpose |
| --- | --- |
| `createLeaveType(type)` | Add new leave category |
| `updateLeaveType(id, updates)` | Modify leave type rules |
| `deactivateLeaveType(id)` | Disable leave type (can't apply anymore) |
| `getAllLeaveTypes()` | List all types (active + inactive) |
| `getActiveLeaveTypes()` | Dropdown: only available types |
| `createPublicHoliday(ph)` | Add holiday to calendar |
| `deletePublicHoliday(id)` | Remove holiday |
| `getAllPublicHolidays()` | List all holidays |
| `getPublicHolidaysByYear(year)` | Holidays for specific year |
| `generateLeaveReport(start, end, type)` | Export CSV of approved leaves |
| `generateMovementRegister(start, end)` | Public-facing: who's on leave when |

---

### EmailService (`laps/src/main/java/com/iss/laps/service/EmailService.java`)

**Purpose:** Async notification delivery (never blocks main transaction)

**Key Methods:**

| Method | Purpose | Async |
| --- | --- | --- |
| `sendLeaveApplicationNotification(app, type)` | Application/approval/rejection email | YES (@Async) |
| `sendCompensationClaimNotification(claim)` | Claim submission/approval/rejection email | YES (@Async) |

**Why Async?** If email service fails (network issue, SMTP down), the main transaction still completes. Email failures are logged but don't cascade to the user. See [AsyncConfig](#asyncconfig).

---

### CustomUserDetailsService (`laps/src/main/java/com/iss/laps/service/CustomUserDetailsService.java`)

**Purpose:** Bridge between Spring Security and LAPS Employee model

**Key Methods:**

| Method | Purpose |
| --- | --- |
| `loadUserByUsername(username)` | Spring Security calls this during login to fetch user + authorities |

**Workflow:**
1. User submits login form
2. Spring Security `AuthenticationManager` calls this service
3. We query `EmployeeRepository.findByUsername(username)`
4. Return Spring `UserDetails` with roles loaded from DB
5. Spring compares submitted password (BCrypt) with stored hash
6. If match, user is authenticated

---

### JwtService (`laps/src/main/java/com/iss/laps/security/JwtService.java`)

**Purpose:** JWT token generation and validation (stateless API authentication)

**Key Methods:**

| Method | Purpose |
| --- | --- |
| `generateToken(userDetails)` | Create signed JWT with 15-min expiry |
| `validateToken(token)` | Verify signature + expiry (returns boolean, never throws) |
| `extractUsername(token)` | Extract subject (username) from token |

**Algorithm:** HS256 (HMAC-SHA256, symmetric key)  
**Claims Encoded:** `sub` (username), `iat` (issued-at), `exp` (expiration), `roles` (Spring authority strings)  
**No PII:** Email, password, name never included in token

**See:** [JWT Authentication Deep Dive](#jwt-authentication-deep-dive) for full explanation.

---

## Controller Layer

Controllers handle HTTP requests, delegate to services, and format responses. They enforce the first line of validation and authorization.

### Pattern

- **Web Controllers** (`@Controller`): Return Thymeleaf templates + redirects
- **REST Controllers** (`@RestController`): Return JSON + HTTP status codes
- **Shared Features:**
  - Input validation via `@Valid` + `BindingResult` (or they'd throw `MethodArgumentNotValidException`)
  - Authorization via `securityUtils.getCurrentEmployee()` to check role/ownership
  - Exception handling via `GlobalExceptionHandler`

### Web Controllers

#### AuthController (`laps/src/main/java/com/iss/laps/controller/AuthController.java`)

**Purpose:** Form-based login/logout for web UI

| Endpoint | Method | Purpose |
| --- | --- | --- |
| `/login` | GET | Render login form |
| `/login` | POST | Authenticate user (Spring Security handles this) |
| `/logout` | GET | Terminate session |

**Flow:**
1. User GETs `/login` → renders `auth/login.html`
2. User POSTs username+password → Spring Security intercepts
3. `CustomUserDetailsService.loadUserByUsername()` called
4. Password compared (BCrypt)
5. If valid → `SecurityContext` populated, redirected to role dashboard
6. If invalid → redirected to `/login?error=true`

---

#### EmployeeController (`laps/src/main/java/com/iss/laps/controller/EmployeeController.java`)

**Purpose:** Employee self-service: apply leave, view balance, claim compensation

| Endpoint | Method | Purpose | Service Call |
| --- | --- | --- | --- |
| `/employee/dashboard` | GET | Home screen with leave summary | `employeeService.getEntitlements()`, `leaveService.getMyLeaveHistory()` |
| `/employee/leaves/apply` | GET | Render apply form | `leaveService.getDefaultActiveLeaveTypes()` |
| `/employee/leaves/apply` | POST | Submit application | `leaveService.applyLeave()` |
| `/employee/leaves` | GET | Paginated history | `leaveService.getMyLeaveHistoryPaged()` |
| `/employee/leaves/{id}` | GET | View single leave detail | `leaveService.findByIdAndEmployee()` |
| `/employee/leaves/{id}/edit` | GET | Edit form (only if APPLIED/UPDATED) | `leaveService.findByIdAndEmployee()` |
| `/employee/leaves/{id}/edit` | POST | Update application | `leaveService.updateLeave()` |
| `/employee/leaves/{id}/delete` | POST | Soft-delete (APPLIED/UPDATED/REJECTED only) | `leaveService.deleteLeave()` |
| `/employee/leaves/{id}/cancel` | POST | Cancel approved leave, restore balance | `leaveService.cancelLeave()` |
| `/employee/compensation` | GET | Compensation balance + claim history | `leaveService.getPendingCompClaimsForManager()` |
| `/employee/compensation/claim` | GET | Render claim form | – |
| `/employee/compensation/claim` | POST | Submit overtime claim | `leaveService.submitCompensationClaim()` |

**Key Security:** All endpoints check `@PreAuthorize("hasRole('EMPLOYEE')")` or equivalent; `securityUtils.getCurrentEmployee()` ensures data ownership.

---

#### ManagerController (`laps/src/main/java/com/iss/laps/controller/ManagerController.java`)

**Purpose:** Manager approvals, subordinate management

| Endpoint | Method | Purpose | Service Call |
| --- | --- | --- | --- |
| `/manager/dashboard` | GET | Pending actions summary | `leaveService.getPendingApplicationsForManager()`, `leaveService.getPendingCompClaimsForManager()` |
| `/manager/leaves` | GET | List pending subordinate applications | `leaveService.getPendingApplicationsForManager()` |
| `/manager/leaves/{id}` | GET | Detailed view + conflict checking | `leaveService.findById()`, `leaveService.getSubordinateLeaveDuringPeriod()` |
| `/manager/leaves/{id}/approve` | POST | Approve application | `leaveService.approveLeave()` |
| `/manager/leaves/{id}/reject` | POST | Reject with comment (mandatory) | `leaveService.rejectLeave()` |
| `/manager/subordinates` | GET | List direct reports | `employeeService.getSubordinates()` |
| `/manager/subordinates/{empId}/leaves` | GET | Historical view of subordinate leaves | `leaveService.getSubordinateLeaveHistoryPaged()` |
| `/manager/compensation` | GET | Pending compensation claims | `leaveService.getPendingCompClaimsForManager()` |
| `/manager/compensation/{id}/approve` | POST | Approve claim, credit leave days | `leaveService.approveCompensationClaim()` |
| `/manager/compensation/{id}/reject` | POST | Reject claim | `leaveService.rejectCompensationClaim()` |

**Key Security:** `isSubordinate(app.getEmployee(), currentManager)` enforced in service layer; managers can only approve their direct reports.

---

#### AdminController (`laps/src/main/java/com/iss/laps/controller/AdminController.java`)

**Purpose:** System administration

| Endpoint | Method | Purpose | Service Call |
| --- | --- | --- | --- |
| `/admin/dashboard` | GET | System overview | Various |
| `/admin/employees` | GET | List all employees | `employeeService.getAllEmployees()` |
| `/admin/employees/create` | GET/POST | Create new employee | `employeeService.createEmployee()` |
| `/admin/employees/{id}/edit` | GET/POST | Edit employee | `employeeService.updateEmployee()` |
| `/admin/employees/{id}/deactivate` | POST | Disable account | `employeeService.deactivateEmployee()` |
| `/admin/leave-types` | GET | Manage leave categories | `adminService.getAllLeaveTypes()` |
| `/admin/leave-types/create` | GET/POST | Add leave type | `adminService.createLeaveType()` |
| `/admin/leave-types/{id}/edit` | GET/POST | Edit leave type | `adminService.updateLeaveType()` |
| `/admin/leave-types/{id}/deactivate` | POST | Disable leave type | `adminService.deactivateLeaveType()` |
| `/admin/public-holidays` | GET | Manage holidays | `adminService.getAllPublicHolidays()` |
| `/admin/public-holidays/create` | GET/POST | Add holiday | `adminService.createPublicHoliday()` |
| `/admin/public-holidays/{id}/delete` | POST | Delete holiday | `adminService.deletePublicHoliday()` |
| `/admin/reports` | GET | Generate leave reports | `adminService.generateLeaveReport()` |
| `/admin/reports/download` | GET | Download CSV | `adminService.generateLeaveReport()` |

---

#### MovementController (`laps/src/main/java/com/iss/laps/controller/MovementController.java`)

**Purpose:** Public-facing leave movement register (no auth required for viewing)

| Endpoint | Method | Purpose |
| --- | --- | --- |
| `/movement/register` | GET | Search approved leaves by period, department |
| `/movement/register` | GET (filter) | Date range + designation filter |

**No Auth:** This is a read-only, aggregated view of who's on leave (for attendance/scheduling).

---

### REST Controllers

#### JwtAuthController (`laps/src/main/java/com/iss/laps/controller/rest/JwtAuthController.java`)

**Purpose:** Stateless API authentication via JWT

| Endpoint | Method | Purpose | Request | Response |
| --- | --- | --- | --- | --- |
| `/api/v1/auth/token` | POST | Issue JWT token | `{"username":"emp.tan","password":"password"}` | `{"token":"eyJhbGc..."}` |

**Security:**
- No authentication required (public endpoint, as per `SecurityConfig.apiFilterChain()`)
- `@Valid` validates request body (username/password constraints)
- Generic 401 on auth failure (no username enumeration)
- No PII in response

**Workflow:**
1. Client POSTs username+password
2. `AuthenticationManager.authenticate()` called
3. `CustomUserDetailsService.loadUserByUsername()` fetches employee + roles
4. BCrypt comparison of password
5. If valid: `JwtService.generateToken()` creates HS256 token
6. Token returned; client stores and uses for subsequent API calls
7. Token includes: username, roles, 15-min expiry

**Usage:**
```bash
# Get token
curl -X POST http://localhost:8080/api/v1/auth/token \
  -H "Content-Type: application/json" \
  -d '{"username":"emp.tan","password":"password"}'
# Response: {"token":"eyJhbGc..."}

# Use token in subsequent request
curl -X GET http://localhost:8080/api/v1/employee/leaves \
  -H "Authorization: Bearer eyJhbGc..."
```

---

#### LeaveRestController (`laps/src/main/java/com/iss/laps/controller/rest/LeaveRestController.java`)

**Purpose:** JSON API for leaves (read-only in current implementation)

| Endpoint | Method | Purpose | Authorization |
| --- | --- | --- | --- |
| `/api/v1/employee/leaves` | GET | My leaves (paginated) | Bearer token + ROLE_EMPLOYEE |
| `/api/v1/employee/leaves/{id}` | GET | Single leave detail | Bearer token + ownership |
| `/api/v1/manager/subordinates/{empId}/leaves` | GET | Subordinate history | Bearer token + ROLE_MANAGER + supervision |
| `/api/v1/movement/register` | GET | Leave register (public aggregation) | No auth |
| `/api/v1/admin/leave-types` | GET | All leave types | Bearer token + ROLE_ADMIN |

**Response Format:**
```json
{
  "id": 42,
  "employeeName": "Tan",
  "leaveType": "Annual Leave",
  "startDate": "2024-04-20",
  "endDate": "2024-04-24",
  "duration": 5.0,
  "status": "APPROVED"
}
```

---

### GlobalExceptionHandler (`laps/src/main/java/com/iss/laps/controller/GlobalExceptionHandler.java`)

**Purpose:** Centralized exception → response mapping

| Exception | HTTP Status | Response | Template |
| --- | --- | --- | --- |
| `ResourceNotFoundException` | 404 | Error message | `error/404.html` |
| `LeaveApplicationException` | 400 | Error message | `error/error.html` |
| Generic `Exception` | 500 | Generic message (doesn't expose details) | `error/error.html` |

**Why Centralized?**
- Avoids duplicated error handling in every controller
- Ensures consistent error responses
- Logs all errors for debugging
- Never exposes stack traces or `.getMessage()` to UI (security)

---

## Security Architecture

LAPS implements a **two-tier security model**: session-based for web UI + stateless JWT for REST API.

### SecurityConfig Overview

Located in `laps/src/main/java/com/iss/laps/config/SecurityConfig.java`

```java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity  // Enables @PreAuthorize, @PostAuthorize annotations
public class SecurityConfig {
    
    // Two SecurityFilterChain beans (order matters!)
    
    @Bean @Order(1)
    SecurityFilterChain apiFilterChain(HttpSecurity http) {
        // Applies to /api/** requests
        // - STATELESS session (no HttpSession)
        // - CSRF disabled (safe: no cookies involved)
        // - JwtAuthenticationFilter validates Bearer tokens
        // - No redirect to /login; returns 401 JSON
    }
    
    @Bean @Order(2)
    SecurityFilterChain webFilterChain(HttpSecurity http) {
        // Applies to /** (catch-all)
        // - Traditional form login
        // - HttpSession-based auth
        // - CSRF enabled
        // - Redirect to /login on 401
    }
}
```

**Order Matters:** `@Order(1)` is checked FIRST. If request matches `/api/**`, it uses `apiFilterChain` and never reaches `webFilterChain`.

---

### Authentication Flow: Web UI (Session-Based)

```
User Browser
    │
    ├─ GET /login ─────────────────────> AuthController.login() [GET]
    │                                    Renders auth/login.html form
    │
    ├─ POST /login (username=emp.tan&password=xxx) ──> Spring Security Filter
    │                                                   CustomUserDetailsService
    │                                                   .loadUserByUsername("emp.tan")
    │                                                   ↓ finds Employee
    │                                                   ↓ checks password (BCrypt)
    │                                                   ↓ loads authorities: [ROLE_EMPLOYEE]
    │                                                   ↓ match!
    │
    └─ 302 Redirect /employee/dashboard ────────> EmployeeController.dashboard()
                                                   SecurityContext populated
                                                   HttpSession created
                                                   Subsequent requests use session
```

**Session Lifetime:** Default ~30 minutes (configurable in `application.properties`)

---

### Authentication Flow: REST API (Stateless JWT)

```
Client (e.g., mobile app, curl, JavaScript)
    │
    ├─ POST /api/v1/auth/token {"username":"emp.tan","password":"xxx"}
    │                           ↓
    │                           JwtAuthController
    │                           AuthenticationManager.authenticate()
    │                           CustomUserDetailsService.loadUserByUsername()
    │                           ↓ finds Employee, loads roles
    │                           ↓ password check (BCrypt) → success
    │                           JwtService.generateToken(userDetails)
    │                           ↓ creates HS256 signed token, 15-min expiry
    │
    └─ 200 OK {"token":"eyJhbGc.eyJzdWI..."}
    
    Stores token locally

    ├─ GET /api/v1/employee/leaves
    │   Header: Authorization: Bearer eyJhbGc.eyJzdWI...
    │
    └─> JwtAuthenticationFilter (OncePerRequestFilter)
        Extract token from header
        JwtService.validateToken(token)  ← verifies HS256 signature + expiry
        ├─ Valid: extract username, load employee from DB, set SecurityContext
        │         proceed to LeaveRestController
        └─ Invalid: return 401 JSON {"error":"Unauthorized"}
```

**Token Lifetime:** 15 minutes (configurable via `JwtConfig`)

---

### Authorization: Role Hierarchy

LAPS enforces a role hierarchy:

```
ROLE_ADMIN (top)
    ↓
ROLE_MANAGER
    ↓
ROLE_EMPLOYEE (bottom)
```

**Spring Security Hierarchy:** An admin can access anything a manager can access, and a manager can access employee-level endpoints.

**Path-Based Rules** (in `SecurityConfig`):
```java
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/admin/**").hasRole("ADMIN")
    .requestMatchers("/manager/**").hasAnyRole("MANAGER", "ADMIN")
    .requestMatchers("/employee/**").hasAnyRole("EMPLOYEE", "MANAGER", "ADMIN")
    ...
)
```

**Method-Based Rules** (using `@PreAuthorize`):
```java
@PreAuthorize("hasRole('MANAGER') or hasRole('ADMIN')")
public String approveLeavePage(...) { ... }
```

---

### Authorization: Data Ownership & Manager-Subordinate Checks

**Problem:** Path-based rules only check role, not data ownership. A manager could hack the URL to access another manager's subordinates.

**Solution:** Service layer enforces business rules via `isSubordinate()` method.

**Example: Manager Approval**
```java
@Transactional
public void approveLeave(Long id, String comment, Employee manager) {
    LeaveApplication application = findById(id);
    
    // Check: is the employee a subordinate of this manager?
    if (!isSubordinate(application.getEmployee(), manager)) {
        throw new LeaveApplicationException("Not authorised to approve this application");
    }
    
    // ... proceed with approval
}

private boolean isSubordinate(Employee employee, Employee potentialManager) {
    Employee current = employee.getManager();
    while (current != null) {
        if (current.getId().equals(potentialManager.getId())) {
            return true;  // Found manager in chain
        }
        current = current.getManager();  // Walk up hierarchy
    }
    return false;
}
```

**Why Walk the Chain?** Handles multi-level hierarchies (grandmanager can't directly approve, but both manager and grandmanager can).

---

### Password Security

**Storage:** BCrypt (adaptive hashing, work factor configurable)
```java
@Bean
public PasswordEncoder passwordEncoder() {
    return new BCryptPasswordEncoder();  // Default work factor: 10
}
```

**Hashing on Create:**
```java
public void createEmployee(Employee emp) {
    emp.setPassword(passwordEncoder.encode(emp.getPassword()));
    // Original plaintext never stored
}
```

**Verification on Login:**
```java
// Spring Security does this automatically:
// BCryptPasswordEncoder.matches(submittedPassword, storedHash) → true/false
```

---

### CSRF Protection

**Web UI:** Enabled by default (issue #9 requires CSRF remain enabled)
```html
<!-- Thymeleaf form automatically includes CSRF token -->
<form method="post" th:action="@{/employee/leaves/apply}">
    <input type="hidden" name="_csrf" th:value="${_csrf.token}" />
    <!-- fields -->
    <button>Submit</button>
</form>
```

**REST API (/api/\*\*):** CSRF disabled (safe because no cookies used for API auth)
```java
@Bean
@Order(1)
SecurityFilterChain apiFilterChain(HttpSecurity http) {
    http.csrf(AbstractHttpConfigurer::disable);  // CSRF not applicable for stateless API
}
```

---

### JWT Token Structure

Example decoded token:
```json
{
  "sub": "emp.tan",
  "iat": 1713429600,     // issued-at (Unix timestamp)
  "exp": 1713430500,     // expiration (15 minutes later)
  "roles": ["ROLE_EMPLOYEE"]
}
```

**Why No PII?** If token is intercepted or leaked, attacker doesn't get password, email, name.  
**Roles Always from DB:** Even if token is tampered with, roles are re-fetched from `CustomUserDetailsService.loadUserByUsername()` during request.

---

### JwtAuthenticationFilter

Custom `OncePerRequestFilter` that runs BEFORE Spring's standard auth filter on `/api/**`:

```java
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    
    @Override
    protected void doFilterInternal(HttpServletRequest request, 
                                   HttpServletResponse response, 
                                   FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = extractTokenFromHeader(request);
            if (token != null && jwtService.validateToken(token)) {
                String username = jwtService.extractUsername(token);
                UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                
                // Create authentication token
                UsernamePasswordAuthenticationToken auth = 
                    new UsernamePasswordAuthenticationToken(
                        userDetails, null, userDetails.getAuthorities());
                
                // Set in SecurityContext for this request
                SecurityContextHolder.getContext().setAuthentication(auth);
            }
        } catch (Exception ex) {
            // Silently fail; let request proceed to GlobalExceptionHandler or 401 endpoint
        }
        filterChain.doFilter(request, response);
    }
}
```

**Why `OncePerRequestFilter`?** Ensures this filter runs exactly once per request (prevents double-execution if filters chain).

---

## Repository Layer & Queries

All data access goes through Spring Data JPA repositories. NO raw SQL, NO string concatenation. All custom queries use parameterized JPQL.

Located in `laps/src/main/java/com/iss/laps/repository/`

### LeaveApplicationRepository

```java
@Repository
public interface LeaveApplicationRepository extends JpaRepository<LeaveApplication, Long> {
    
    // Derived query (Spring generates SQL)
    Page<LeaveApplication> findByEmployeeAndStatusNotOrderByAppliedDateDesc(
            Employee employee, LeaveStatus status, Pageable pageable);
    
    // Custom JPQL query with parameters
    @Query("SELECT la FROM LeaveApplication la WHERE la.employee = :employee " +
           "AND YEAR(la.startDate) = :year AND la.status != 'DELETED' " +
           "ORDER BY la.appliedDate DESC")
    List<LeaveApplication> findByEmployeeAndYear(@Param("employee") Employee employee,
                                                  @Param("year") int year);
    
    // Manager-scoped: pending leaves for manager's subordinates
    @Query("SELECT la FROM LeaveApplication la WHERE la.employee.manager = :manager " +
           "AND la.status IN ('APPLIED', 'UPDATED') ORDER BY la.appliedDate DESC")
    List<LeaveApplication> findPendingByManager(@Param("manager") Employee manager);
    
    // For conflict detection and movement register
    @Query("SELECT la FROM LeaveApplication la WHERE la.status = 'APPROVED' " +
           "AND la.startDate <= :endDate AND la.endDate >= :startDate")
    List<LeaveApplication> findApprovedLeaveDuringPeriod(@Param("startDate") LocalDate startDate,
                                                          @Param("endDate") LocalDate endDate);
    
    // Entitlement deduction: sum used days
    @Query("SELECT COALESCE(SUM(la.duration), 0) FROM LeaveApplication la " +
           "WHERE la.employee = :employee AND la.leaveType.id = :leaveTypeId " +
           "AND YEAR(la.startDate) = :year AND la.status IN ('APPLIED', 'UPDATED', 'APPROVED') " +
           "AND (:excludeId IS NULL OR la.id != :excludeId)")
    double sumUsedDaysByEmployeeAndLeaveTypeAndYear(@Param("employee") Employee employee,
                                                    @Param("leaveTypeId") Long leaveTypeId,
                                                    @Param("year") int year,
                                                    @Param("excludeId") Long excludeId);
    
    // Reporting
    @Query("SELECT la FROM LeaveApplication la WHERE la.status = 'APPROVED' " +
           "AND la.startDate >= :startDate AND la.endDate <= :endDate " +
           "ORDER BY la.employee.name, la.startDate")
    List<LeaveApplication> findApprovedLeaveInRange(@Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate);
}
```

**Key Query Patterns:**

1. **Parameterized (@Param):** Never concatenate user input
2. **Status Checks in Query:** Filters `WHERE la.status != 'DELETED'` ensure soft-deletes work
3. **Manager Authorization in Query:** `WHERE la.employee.manager = :manager` restricts to subordinates
4. **Date Ranges:** `startDate <= :endDate AND endDate >= :startDate` (overlap detection)
5. **Aggregation:** `SUM(la.duration)` for balance calculations

---

### EmployeeRepository

```java
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    
    Optional<Employee> findByUsername(String username);  // For authentication
    
    List<Employee> findByActive(boolean active);  // Filter active/inactive
    
    List<Employee> findByManager(Employee manager);  // Direct reports
    
    @Query("SELECT DISTINCT e FROM Employee e LEFT JOIN e.subordinates s " +
           "WHERE e.manager IS NULL")
    List<Employee> findTopLevelManagers();  // For org structure
}
```

---

### LeaveEntitlementRepository

```java
@Repository
public interface LeaveEntitlementRepository extends JpaRepository<LeaveEntitlement, Long> {
    
    Optional<LeaveEntitlement> findByEmployeeAndLeaveTypeAndYear(
            Employee employee, LeaveType leaveType, int year);
    
    List<LeaveEntitlement> findByEmployee(Employee employee);
    
    List<LeaveEntitlement> findByEmployeeAndYear(Employee employee, int year);
}
```

---

### CompensationClaimRepository

```java
@Repository
public interface CompensationClaimRepository extends JpaRepository<CompensationClaim, Long> {
    
    List<CompensationClaim> findByEmployee(Employee employee);
    
    @Query("SELECT cc FROM CompensationClaim cc WHERE cc.employee.manager = :manager " +
           "AND cc.status = 'PENDING' ORDER BY cc.claimedDate DESC")
    List<CompensationClaim> findPendingByManager(@Param("manager") Employee manager);
    
    // Monthly cap validation (72-hour limit)
    @Query("SELECT COALESCE(SUM(cc.overtimeHours), 0) FROM CompensationClaim cc " +
           "WHERE cc.employee = :employee AND cc.status = 'APPROVED' " +
           "AND YEAR(cc.overtimeDate) = :year AND MONTH(cc.overtimeDate) = :month")
    int sumApprovedOvertimeHoursForMonthByEmployee(@Param("employee") Employee employee,
                                                    @Param("year") int year,
                                                    @Param("month") int month);
}
```

---

### Other Repositories

**LeaveTypeRepository**
```java
Optional<LeaveType> findByName(String name);
List<LeaveType> findByActive(boolean active);
Optional<LeaveType> findByDefaultType(LeaveTypeDefault defaultType);
```

**PublicHolidayRepository**
```java
List<PublicHoliday> findByYear(int year);  // For calculations
List<PublicHoliday> findByYearOrderByHolidayDate(int year);
```

---

## Utility Functions

### LeaveCalculator (`laps/src/main/java/com/iss/laps/util/LeaveCalculator.java`)

**Purpose:** Complex leave day calculations with business rules.

**Key Methods:**

#### 1. `calculateAnnualLeaveDays(start, end, publicHolidays)`

**Business Rule:** Annual leave uses complex rules:
- **≤14 calendar days:** Exclude weekends AND public holidays (strict)
- **>14 calendar days:** Count all calendar days (generous, no exclusions)

**Why?** MOM rules: short leaves are precious (don't count weekends/holidays); long leaves shouldn't penalize for spanning holidays.

**Algorithm:**
```java
long calendarDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;

if (calendarDays <= 14) {
    Set<LocalDate> holidays = HolidaysWithObservedMondays(publicHolidays);
    
    return startDate.datesUntil(endDate.plusDays(1))
            .filter(date -> !isWeekend(date) && !holidays.contains(date))
            .count();  // Count only working days
}
// Count all calendar days
return calendarDays;
```

**Example:**
- 20 Apr (Mon) to 24 Apr (Wed) = 5 calendar days, no holidays/weekends → **5 days**
- 01 Apr to 15 Apr (includes 14 days, with 1 weekend) = 14 calendar days → all counted → **14 days**
- 01 Apr to 17 Apr = 17 calendar days → all counted (no exclusions) → **17 days**

---

#### 2. `calculateMedicalLeaveDays(start, end)`

**Business Rule:** Exclude weekends only (public holidays DO count as used days).

**Algorithm:**
```java
return startDate.datesUntil(endDate.plusDays(1))
        .filter(date -> !isWeekend(date))
        .count();
```

**Rationale:** Medical situations require leave; public holidays don't reduce medical entitlement need.

---

#### 3. `calculateHospitalisationLeaveDays(start, end)`

Same as medical: exclude weekends, count holidays.

---

#### 4. `HolidaysWithObservedMondays(publicHolidays)`

**Business Rule:** If a holiday falls on Sunday, the following Monday is marked as observed (unless already a holiday).

**Algorithm:**
```java
Set<LocalDate> holidays = publicHolidays.stream()
        .map(PublicHoliday::getHolidayDate)
        .collect(Collectors.toSet());

Set<LocalDate> observedMondays = new HashSet<>();
for (LocalDate date : holidays) {
    if (date.getDayOfWeek() == DayOfWeek.SUNDAY) {
        LocalDate observedHoliday = date.plusDays(1);  // Next day (Monday)
        
        // Cascade: if Monday is already a holiday, use Tuesday, etc.
        while (holidays.contains(observedHoliday) || observedMondays.contains(observedHoliday)) {
            observedHoliday = observedHoliday.plusDays(1);
        }
        observedMondays.add(observedHoliday);
    }
}

holidays.addAll(observedMondays);
return holidays;
```

**Example:** Chinese New Year Year 1 (假1) is Sun 11 Feb 2024 → observed on Mon 12 Feb 2024 (included in calculations).

---

#### 5. `isWeekend(date)`

```java
public boolean isWeekend(LocalDate date) {
    DayOfWeek day = date.getDayOfWeek();
    return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
}
```

---

#### 6. `isPublicHoliday(date, publicHolidays)`

```java
public boolean isPublicHoliday(LocalDate date, List<PublicHoliday> publicHolidays) {
    return publicHolidays.stream()
            .anyMatch(ph -> ph.getHolidayDate().equals(date));
}
```

---

#### 7. `isWorkingDay(date, publicHolidays)`

```java
public boolean isWorkingDay(LocalDate date, List<PublicHoliday> publicHolidays) {
    return !isWeekend(date) && !isPublicHoliday(date, publicHolidays);
}
```

---

### SecurityUtils (`laps/src/main/java/com/iss/laps/util/SecurityUtils.java`)

**Purpose:** Retrieve currently authenticated employee from Spring Security context.

```java
@Component
public class SecurityUtils {
    
    public Employee getCurrentEmployee() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        
        if (auth == null || !auth.isAuthenticated()) {
            throw new LeaveApplicationException("User not authenticated");
        }
        
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) principal;
            return employeeRepository.findByUsername(userDetails.getUsername())
                    .orElseThrow(() -> new ResourceNotFoundException(
                        "Employee not found: " + userDetails.getUsername()));
        }
        
        throw new LeaveApplicationException("Unable to retrieve user");
    }
}
```

**Usage:** Called in every controller to populate `Employee employee = securityUtils.getCurrentEmployee();`

---

## Key Workflows & Entry Points

This section provides step-by-step walkthroughs for the most common user journeys.

### Workflow 1: New Employee Joins, Gets Entitlements

**Actor:** Admin  
**Trigger:** New hire on-boarding

**Steps:**

1. **Admin navigates to** `/admin/employees`
2. **Admin clicks** "Create New Employee"
3. **Form rendered** with fields: username, password, name, email, role, designation
4. **Admin fills in**, e.g., "emp.zhou" / "temp123" / "Zhou Wei" / "zhou@company.com" / "ROLE_EMPLOYEE" / "PROFESSIONAL"
5. **Form submits** to `AdminController.createEmployee()`
6. **Validation passes** (@Valid checks)
7. **Service called** `employeeService.createEmployee(emp)`
8. **Password hashed** via BCryptPasswordEncoder
9. **Employee saved** to `employees` table
10. **Redirect to** `/admin/employees`

**Next: Assign Entitlements**

11. **Admin navigates to** `/admin/employees/{id}/entitlements`
12. **Form shows** all leave types with input fields for max days
13. **Admin fills in** Annual=15, Medical=14, Hospitalisation=60 for year 2024
14. **Form submits** to `AdminController.createEntitlements()`
15. **Service called** `employeeService.createEntitlement()` for each type
16. **Entitlements saved** to `leave_entitlements` table
17. **New employee is ready** to apply for leave

---

### Workflow 2: Employee Applies for Annual Leave

**Actor:** Employee  
**Trigger:** Employee needs time off

**Steps:**

1. **Employee logs in** to `/login`, enters username/password
2. **Spring Security** authenticates via `CustomUserDetailsService`
3. **Session created**, redirected to `/employee/dashboard`
4. **Employee navigates to** `/employee/leaves/apply`
5. **Form rendered** with:
   - Leave type dropdown (populated from `leaveService.getDefaultActiveLeaveTypes()`)
   - Start date picker
   - End date picker
   - Reason textarea
   - Work dissemination textarea
   - Contact details textarea
6. **Employee fills in:**
   - Leave type: "Annual Leave"
   - Start: 2024-04-20 (Saturday)
   - End: 2024-04-24 (Wednesday)
   - Reason: "Annual vacation"
   - etc.
7. **Form submits** to `EmployeeController.applyLeave()`
8. **First validation** via `@Valid`: checks @NotNull, @NotBlank
9. **No errors**, delegates to `leaveService.applyLeave(application, employee)`
10. **Second validation** in service:
    - Start ≥ today? ✓
    - End ≥ start? ✓
    - Leave type active? ✓
    - Overlapping applications? Query DB ✓
    - Sufficient balance? Estimate from entitlement ✓
11. **Calculate duration** via `calculateDuration()`:
    - Leave type is Annual
    - 2024-04-20 to 2024-04-24 = 5 calendar days
    - Includes weekend (22-23 Apr)?
    - No public holidays
    - <= 14 days → count working days only
    - Mon, Tue, Wed, Thu = 4 working days (Saturday not counted) = **4 days**
12. **Save to DB:**
    ```sql
    INSERT INTO leave_applications (..., duration=4.0, status='APPLIED', ...) VALUES (...)
    ```
13. **Async email sent** (queued) to employee's manager
14. **Employee redirected** to `/employee/leaves` (history page)
15. **Flash message** "Leave application submitted successfully"

---

### Workflow 3: Manager Reviews and Approves Leave

**Actor:** Manager  
**Trigger:** Receives email notification

**Steps:**

1. **Manager receives email:** "New leave application from Tan (4 days, 20-24 Apr)"
2. **Manager logs in** to `/login`
3. **Redirected to** `/manager/dashboard`
4. **Dashboard shows** "5 pending applications"
5. **Manager clicks** "View Pending Leaves"
6. **Navigates to** `/manager/leaves`
7. **Page displays** grouped list of subordinates' pending applications
8. **Manager clicks** on Tan's application (ID 42)
9. **Navigates to** `/manager/leaves/42`
10. **Detailed view shows:**
    - Employee: Tan
    - Dates: 20-24 Apr (4 working days)
    - Reason: "Annual vacation"
    - Current balance: 15 days (only 4 used after approval, 11 remaining)
    - **Conflict check:** "Other subordinates on leave same period: None"
11. **Manager clicks** "Approve" button
12. **Modal appears** with comment field
13. **Manager enters** "Approved - enjoy your vacation!"
14. **Submits** to `ManagerController.approveLeave(id=42, comment=...)`
15. **Spring Security checks** user is authenticated as MANAGER
16. **Service called** `leaveService.approveLeave(42, comment, manager)`
17. **Authorization check:** `isSubordinate(tan, manager)` → traverses manager chain → true ✓
18. **State check:** status is APPLIED ✓
19. **Update status** to APPROVED
20. **Deduct entitlement** in service:
    ```sql
    UPDATE leave_entitlements SET used_days = used_days + 4.0 
    WHERE employee_id=5 AND leave_type_id=1 AND year=2024
    ```
21. **Async email sent** to Tan: "Leave application approved"
22. **Manager redirected** to `/manager/leaves`
23. **Application removed** from pending list

---

### Workflow 4: Employee Claims Overtime → Converted to Compensation Leave

**Actor:** Employee  
**Trigger:** Worked 4 hours overtime

**Steps:**

1. **Employee navigates to** `/employee/compensation`
2. **Page shows** compensation balance (0 days initially)
3. **Employee clicks** "Submit Overtime Claim"
4. **Form rendered** with:
   - Overtime date (calendar picker)
   - Overtime hours (1–4 dropdown, validates @Min @Max)
   - Reason textarea
5. **Employee fills in:**
   - Date: 2024-04-18
   - Hours: 4
   - Reason: "Project deadline"
6. **Form submits** to `EmployeeController.submitCompensationClaim()`
7. **Validation passes**
8. **Service called** `leaveService.submitCompensationClaim(claim, employee)`
9. **Compensation days auto-calculated** in `@PrePersist`: 4h / 4 * 0.5 = 0.5 days
10. **Claim saved** to `compensation_claims` table, status=PENDING
11. **Async email sent** to manager
12. **Redirected to** `/employee/compensation`

**Manager Reviews Claim**

13. **Manager receives email:** "New compensation claim from Tan: 4 hours"
14. **Manager logs in** to `/manager/compensation`
15. **Page shows** "1 pending claim"
16. **Manager clicks** on claim
17. **Detail view shows:**
    - Employee: Tan
    - Date: 2024-04-18
    - Hours: 4 (converts to 0.5 compensation days)
    - Reason: "Project deadline"
    - **Monthly cap check:** Already approved this month: 0 hours → adding 4 hours = 4/72 ✓
18. **Manager clicks** "Approve"
19. **Modal with comment**
20. **Submits** to `ManagerController.approveCompensationClaim(id, comment)`
21. **Service called** `leaveService.approveCompensationClaim(id, comment, manager)`
22. **Validation:**
    - Manager is supervisor ✓
    - Claim is PENDING ✓
    - Monthly cap: 0 + 4 < 72 ✓
23. **Update claim** status=APPROVED, processedDate=now
24. **Create/update compensation leave entitlement:**
    ```sql
    UPDATE leave_entitlements SET total_days = total_days + 0.5 
    WHERE employee_id=5 AND leave_type_id=4 (compensation) AND year=2024
    ```
    (If no entitlement exists, create one with max=10, then add 0.5)
25. **Async email sent** to employee
26. **Manager redirected** to `/manager/compensation`

**Employee Uses Compensation Leave**

27. **Employee navigates to** `/employee/leaves/apply`
28. **Leaves from dropdown:** "Compensation Leave" (now available, balance 0.5 days)
29. **Employee selects** leave type, date (e.g., 2024-05-06, half day AM)
30. **Form validation:** half day allowed for Compensation? Yes ✓
31. **Duration calculated** for half day: 0.5
32. **Application submitted**, goes through same approval flow

---

## Assumptions & Known Gaps

### Assumptions Made During Onboarding

1. **Database Always Available:** No connection pooling exhaustion handling; assumes DB is up.
2. **Email Service Non-Critical:** Async email failures are logged but don't fail the main transaction.
3. **Single-Manager Hierarchy:** Assumes employees have at most one direct manager (no matrix reporting).
4. **Password Never Changes After Creation:** No password reset UI implemented (only reset possible via admin).
5. **No Concurrent Approval:** Assumes two managers won't approve the same leave simultaneously (no optimistic lock).
6. **Public Holidays Seeded Manually:** No automatic holiday calendar sync (MOM, external source).
7. **No Audit Trail:** Who approved what, when? Not logged (could reconstruct from status changes, but no dedicated audit table).
8. **Time Zone Assumption:** All timestamps in system timezone (not user-specific).
9. **Entitlements Reset Annually:** Assumes `EmployeeService.resetAnnualEntitlements()` is called ~Jan 1 (not automated).
10. **No Proxy Authorization:** Managers can't submit leave on behalf of subordinates.

---

### Known Gaps & Limitations

#### High Priority

1. **Password Reset:** Users who forget password cannot self-reset. Only admin can change.
   - **Impact:** Account lockout without admin intervention
   - **Recommendation:** Implement self-service password reset via email

2. **Concurrent Approval Handling:** No optimistic locking on LeaveApplication.
   - **Impact:** If two managers review same leave simultaneously, second approval overwrites (race condition)
   - **Recommendation:** Add `@Version` field to LeaveApplication, enable optimistic locking

3. **No Entitlement Carryover Logic:** Unused days disappear at year end.
   - **Impact:** Unfair if employee has legitimate reasons for not using leave
   - **Recommendation:** Implement carryover with expiry date (e.g., +3 months into next year)

#### Medium Priority

4. **Limited Leave Duration Flexibility:** Only full days or half days; no hourly leaves.
   - **Impact:** Inflexible for short absences
   - **Recommendation:** Add hourly leave support (extend domain model)

5. **No Delegation:** Manager can't delegate approval authority to another manager during absence.
   - **Impact:** Pending leaves pile up if manager on leave
   - **Recommendation:** Implement temporary delegation flow

6. **Email Timeout Handling:** @Async doesn't retry on failure.
   - **Impact:** Lost notifications if SMTP temporarily down
   - **Recommendation:** Add retry logic with exponential backoff (Spring Retry)

7. **No Bulk Operations:** Can't approve multiple leaves at once.
   - **Impact:** Manager must click approve 10 times for 10 leaves
   - **Recommendation:** Add checkbox bulk approve UI

#### Low Priority

8. **Limited Reporting:** Only CSV export of approved leaves.
   - **Impact:** Can't drill into entitlement usage by type, or forecast future balance
   - **Recommendation:** Add dashboard analytics (charts, summary stats)

9. **No Soft-Delete Recovery:** Deleted applications can't be restored.
   - **Impact:** Accidental deletion is permanent
   - **Recommendation:** Add admin-only "undelete" feature

10. **Mobile UI Non-Responsive:** Bootstrap UI not tested on mobile devices.
    - **Impact:** Poor experience on small screens
    - **Recommendation:** Test and fix mobile responsiveness

---

### Testing Gaps

1. **Limited Integration Tests:** Most tests are unit (mocks). Need more end-to-end flows tested.
2. **No Load Testing:** No verification of performance under 100+ concurrent users.
3. **No Security Penetration Testing:** Haven't verified CSRF, XSS, SQL injection protections.
4. **No Accessibility Testing:** WCAG compliance not verified.

---

### Known Limitations of Current Implementation

1. **No API Versioning:** `/api/v1` suggests versioning, but no `/api/v2` path exists; breaking changes would break all clients.
2. **No Rate Limiting:** No protection against brute-force password guessing or DOS.
3. **No Request Logging:** Audit trail of "who accessed what when" not available.
4. **No Multi-Tenancy:** All data in single database; system can't be deployed as SaaS.

---

## Conclusion

LAPS is a well-structured, security-conscious leave management system built on Spring Boot 3 + Spring Security. The 3-layer architecture (Controller → Service → Repository) is clean and maintainable. Key strengths:

- ✅ **Strong Authorization:** Manager-subordinate checks baked into queries
- ✅ **Async Email:** Non-blocking notifications
- ✅ **JWT Support:** Stateless REST API for external clients
- ✅ **Complex Business Rules:** Annual leave calculation handles MOM edge cases
- ✅ **Transaction Safety:** @Transactional guards consistency
- ✅ **Input Validation:** Two-pass (Bean Validation + service layer)

Key areas for improvement:

- 🔧 **Password Reset:** Self-service mechanism needed
- 🔧 **Concurrent Approval:** Add optimistic locking
- 🔧 **Entitlement Carryover:** Handle year-end unused days

New developers should start by exploring the **Domain Models** section to understand the data structure, then study the **Business Logic Flows** to trace how data moves through the system. The **Service Layer** reference documents where to add new business logic, and **Security Architecture** explains how to enforce authorization.

---

**Document Created:** 18 April 2026  
**Last Reviewed:** 18 April 2026  
**Next Review:** Recommended after major feature additions

