# LAPS (Leave Application Processing System) - Comprehensive Architecture Analysis

**Last Updated:** 18 April 2026  
**Purpose:** Complete technical onboarding documentation for new developers

---

## Table of Contents

1. [Domain Models & Relationships](#domain-models--relationships)
2. [Core Business Flows](#core-business-flows)
3. [Service Layer Inventory](#service-layer-inventory)
4. [Controller Layer & API Endpoints](#controller-layer--api-endpoints)
5. [Security Architecture](#security-architecture)
6. [Repository Layer (Data Persistence)](#repository-layer-data-persistence)
7. [Utility Functions](#utility-functions)
8. [Configuration & Initialization](#configuration--initialization)

---

## Domain Models & Relationships

### 1. **Employee** (`model/Employee.java`)

**Purpose:** Core user entity representing all system users (Admins, Managers, Employees)

**Key Attributes:**
- `id: Long` - Primary key
- `username: String` - Unique login identifier (NOT NULL)
- `password: String` - BCrypt-encoded password
- `name: String` - Full name
- `email: String` - Contact email (validated)
- `role: Role` - ROLE_EMPLOYEE | ROLE_MANAGER | ROLE_ADMIN (Enum)
- `designation: Designation` - ADMINISTRATIVE (14 days) | PROFESSIONAL (18 days) | SENIOR_PROFESSIONAL (21 days)
- `active: boolean` - Soft-delete flag; only active employees can log in
- `manager: Employee (LAZY)` - Self-referencing foreign key; null for admins/top-level
- `subordinates: List<Employee> (LAZY)` - One-to-many reverse relationship

**Relationships:**
- **Manager (Self-referencing):** `@ManyToOne` → `employees.manager_id`
- **Subordinates:** `@OneToMany` mapped by `manager`
- **Leave Applications:** `@OneToMany` mapped by `employee` (cascade all)
- **Leave Entitlements:** `@OneToMany` mapped by `employee` (cascade all)
- **Compensation Claims:** `@OneToMany` mapped by `employee` (cascade all)

**Database Table:** `employees` (Unique: `username`, FK: `manager_id`)

---

### 2. **LeaveApplication** (`model/LeaveApplication.java`)

**Purpose:** Leave request submitted by an employee and reviewed by their manager

**Key Attributes:**
- `id: Long` - Primary key
- `employee: Employee (LAZY)` - Who applied
- `leaveType: LeaveType (EAGER)` - Which type of leave (Foreign key, nullable: NO)
- `startDate: LocalDate` - First day of leave
- `endDate: LocalDate` - Last day of leave
- `duration: double` - Calculated working days (may include 0.5 for half-day)
- `reason: String` - Why the leave is needed
- `workDissemination: String` - Handover/coverage plan
- `contactDetails: String` - How to reach if urgent
- `status: LeaveStatus (Enum)` - APPLIED | UPDATED | APPROVED | REJECTED | CANCELLED | DELETED
- `managerComment: String` - Approval/rejection note
- `appliedDate: LocalDateTime` - When submitted (@PrePersist)
- `updatedDate: LocalDateTime` - Last modified date (@PreUpdate)
- `halfDay: boolean` - Is this a half-day leave?
- `halfDayType: String` - "AM" or "PM" if half-day

**Status Transitions:**
- Initial: `APPLIED`
- After update: `UPDATED` (while pending)
- After approval: `APPROVED` → may later be `CANCELLED`
- If rejected: `REJECTED` (permanent)
- If employee deletes: `DELETED` (soft-delete)

**State Rules:**
- `isEditable()`: status in [APPLIED, UPDATED] — employee can modify
- `isCancellable()`: status == APPROVED — employee can cancel after approval
- `isDeletable()`: status in [APPLIED, UPDATED, REJECTED] — employee can delete

**Database Table:** `leave_applications` (FK: `employee_id`, `leave_type_id`)

---

### 3. **LeaveType** (`model/LeaveType.java`)

**Purpose:** Configuration for types of leave available (e.g., Annual, Medical, Compensation)

**Key Attributes:**
- `id: Long` - Primary key
- `defaultType: LeaveTypeDefault (Enum, nullable, immutable)` - ANNUAL | MEDICAL | HOSPITALISATION | COMPENSATION
- `name: String` - Display name (unique)
- `description: String` - Purpose/policy
- `maxDaysPerYear: int` - Yearly entitlement cap
- `halfDayAllowed: boolean` - Can this leave be taken as half-day?
- `active: boolean` - Soft-delete; only active types show in UI

**Business Rules:**
- **Default leave types** (marked with `defaultType`) cannot be deleted or have max-days modified
- **Custom leave types** (where `defaultType == null`) can be created/deleted but not used for leave applications yet

**Database Table:** `leave_types` (Unique: `name`, Unique: `default_type` if not null)

---

### 4. **LeaveEntitlement** (`model/LeaveEntitlement.java`)

**Purpose:** Annual allocation of leave for an employee per leave type per year

**Key Attributes:**
- `id: Long` - Primary key
- `employee: Employee (LAZY)` - Who owns this
- `leaveType: LeaveType (EAGER)` - What type
- `year: int` - Which year
- `totalDays: double` - Allocated days (e.g., 14 for annual if PROFESSIONAL)
- `usedDays: double` - Days deducted when leave approved

**Computed Property:**
- `getRemainingDays()`: `totalDays - usedDays`

**Unique Constraint:** `(employee_id, leave_type_id, year)` — one record per employee/type/year

**Initialization:**
- Auto-created on employee creation for current year
- Annual: pulls from `Designation.getAnnualLeaveEntitlement()`
- Medical: 14 days
- Hospitalisation: 46 days
- Compensation: 0 days (earned via overtime claims)

**Database Table:** `leave_entitlements` (Unique constraint: `employee_id`, `leave_type_id`, `year`)

---

### 5. **LeaveTypeDefault** (`model/LeaveTypeDefault.java`)

**Purpose:** Enumeration of system-provided leave types

**Values:**
```
ANNUAL           – Standard annual/vacation leave
MEDICAL          – Sick/medical leave (14 days max)
HOSPITALISATION  – Extended medical leave (46 days max)
COMPENSATION     – Earned from overtime claims
```

---

### 6. **Role** (`model/Role.java`)

**Purpose:** User role hierarchy for access control

**Values:**
```
ROLE_EMPLOYEE    – Can apply/view own leave, view own compensation claims
ROLE_MANAGER     – Can approve/reject subordinate leave and compensation claims
ROLE_ADMIN       – Can manage employees, leave types, public holidays
```

**Hierarchy:** `ROLE_ADMIN > ROLE_MANAGER > ROLE_EMPLOYEE`

---

### 7. **Designation** (`model/Designation.java`)

**Purpose:** Job level tied to annual leave entitlement

**Values & Annual Leave:**
```
ADMINISTRATIVE          → 14 days/year
PROFESSIONAL            → 18 days/year
SENIOR_PROFESSIONAL     → 21 days/year
```

Used when initializing annual leave entitlements on employee creation or when designation changes.

---

### 8. **CompensationClaim** (`model/CompensationClaim.java`)

**Purpose:** Claim overtime worked in order to convert it to compensation leave

**Key Attributes:**
- `id: Long` - Primary key
- `employee: Employee (LAZY)` - Who worked overtime
- `overtimeDate: LocalDate` - When overtime was worked
- `overtimeHours: int` - Hours worked (1–4 per day; enforced max 72/month per MOM)
- `compensationDays: double` - Calculated: `(overtimeHours / 4.0) * 0.5`
  - 1h = 0.125 day, 2h = 0.25 day, 3h = 0.375 day, 4h = 0.5 day
- `status: ClaimStatus (Enum)` - PENDING | APPROVED | REJECTED
- `managerComment: String` - Approval/rejection note
- `claimedDate: LocalDateTime` - When submitted (@PrePersist)
- `processedDate: LocalDateTime` - When manager reviewed
- `reason: String` - Why overtime (optional)

**Validation:**
- Max 4 hours per day claim
- Max 72 hours per calendar month (enforced by repository query)
- Monthly cap includes PENDING claims (to prevent double-claiming)

**Approval Process:**
1. Employee claims overtime hours
2. Manager approves/rejects
3. If approved → compensation entitlement created/updated for current year

**Database Table:** `compensation_claims` (FK: `employee_id`)

---

### 9. **PublicHoliday** (`model/PublicHoliday.java`)

**Purpose:** Track public holidays to exclude from annual leave day calculations

**Key Attributes:**
- `id: Long` - Primary key
- `holidayDate: LocalDate` - The date (unique)
- `description: String` - Holiday name (e.g., "Chinese New Year")
- `year: int` - Year for quick lookup

**Special Handling:**
- If a public holiday falls on Sunday, the following Monday is auto-observed
- If Monday is already a holiday, it cascades to Tuesday, etc.
- LeaveCalculator factors observed holidays into annual leave day counts

**Database Table:** `public_holidays` (Unique: `holiday_date`)

---

### 10. **LeaveStatus** (`model/LeaveStatus.java`)

**Purpose:** State machine for leave applications

**Values:**
```
APPLIED       – Newly submitted by employee
UPDATED       – Re-submitted after manager requested changes (not used in current impl)
APPROVED      – Manager approved (entitlement deducted)
REJECTED      – Manager rejected
CANCELLED     – Employee cancelled after approval (entitlement restored)
DELETED       – Employee deleted while pending
```

---

## Core Business Flows

### Flow 1: Employee Leave Application Submission

**File:** `service/LeaveService.java` → `applyLeave(LeaveApplication, Employee)`

**Steps:**

1. **Input:** Employee submits form with leave details (dates, type, reason)
   - Controller: `EmployeeController.applyLeave()` → POST `/employee/leaves/apply`

2. **Validation Layer (Service):**
   ```
   LeaveService.applyLeave()
   ├─ Set employee & status (APPLIED)
   ├─ Resolve leave type ID → fetch from DB
   ├─ VALIDATE (detailed below)
   │  ├─ Check dates valid & end >= start
   │  ├─ Check start/end are working days (for ANNUAL)
   │  └─ Leave-type specific checks:
   │     ├─ ANNUAL: max 14 consecutive calendar days, check entitlement balance
   │     ├─ MEDICAL: max 14/year total
   │     ├─ HOSPITALISATION: max 46/year total
   │     └─ COMPENSATION: check available earned compensation days
   ├─ Calculate duration (calls LeaveCalculator)
   │  └─ If half-day: 0.5, else calculate working days (exclude weekends & holidays)
   └─ Set duration on application
   
3. **Save & Notify:**
   ```
   LeaveService.applyLeave() [cont'd]
   ├─ Save to DB: LeaveApplicationRepository.save()
   ├─ Fetch manager (Employee.getManager())
   ├─ If manager exists:
   │  └─ Force-load lazy associations (initEmailAssociations)
   │  └─ Async send email: EmailService.sendLeaveApplicationNotification(app, APPLICATION)
   │     └─ Recipient: manager email
   │     └─ Subject: "Leave Application from [Employee Name]"
   └─ Return saved application
   ```

**Database Impact:**
- INSERT into `leave_applications` with status=APPLIED
- NO entitlement deduction yet (only happens on APPROVAL)

**Validation Details (validateLeaveApplication):**
- **Generic checks:** dates not null, end >= start
- **Annual-specific:**
  - Max 14 calendar days consecutive
  - Start & end must be working days
  - Query used days: `leaveAppRepo.sumUsedDaysByEmployeeAndLeaveTypeAndYear(employee, leaveTypeId, year, excludeId=null)`
  - Check: `usedDays + newDuration <= entitlement.totalDays`
- **Medical-specific:**
  - Max 14 days/year total
  - If exceeded: reject with message to apply for hospitalisation
- **Hospitalisation-specific:**
  - Max 46 days/year total
- **Compensation-specific:**
  - Query approved compensation: `compClaimRepo.sumApprovedCompDaysByEmployee(employee)`
  - Check: `usedCompDays + newDuration <= totalEarned`

---

### Flow 2: Manager Leave Approval

**File:** `service/LeaveService.java` → `approveLeave(Long, String, Employee)`

**Steps:**

1. **Fetch application:**
   ```
   LeaveService.approveLeave()
   ├─ Find by ID: leaveAppRepo.findById(id)
   ├─ Verify manager is authorized:
   │  └─ Check: employee.manager.id == manager.id
   └─ Verify status is APPLIED or UPDATED
   ```

2. **Approve:**
   ```
   ├─ Set status = APPROVED
   ├─ Set manager comment
   ├─ Save to DB
   ```

3. **Deduct Entitlement:**
   ```
   LeaveService.deductEntitlement()
   ├─ Find entitlement: leaveEntitlementRepo.findByEmployeeAndLeaveTypeAndYear()
   ├─ Increment usedDays: ent.usedDays += application.duration
   └─ Save
   ```

4. **Notify Employee:**
   ```
   ├─ Force-load lazy associations
   └─ Async send: EmailService.sendLeaveApplicationNotification(app, APPROVAL)
      └─ Recipient: employee email
   ```

**Database Impact:**
- UPDATE `leave_applications` set status=APPROVED, manager_comment=...
- UPDATE `leave_entitlements` set used_days = used_days + duration

**Authorization Check:**
```java
private boolean isSubordinate(Employee employee, Employee manager) {
    return employee.getManager() != null &&
           employee.getManager().getId().equals(manager.getId());
}
```

---

### Flow 3: Manager Leave Rejection

**File:** `service/LeaveService.java` → `rejectLeave(Long, String, Employee)`

**Similar to approval but:**
- Requires non-blank comment (mandatory)
- Sets status = REJECTED
- **Does NOT deduct entitlement**
- Sends REJECTION email notification

---

### Flow 4: Employee Leave Cancellation

**File:** `service/LeaveService.java` → `cancelLeave(Long, Employee)`

**Prerequisites:**
- Only callable if status == APPROVED
- Only employee who submitted can cancel their own

**Steps:**
```
LeaveService.cancelLeave()
├─ Verify status is APPROVED
├─ Set status = CANCELLED
├─ Save
└─ Restore entitlement:
   LeaveService.restoreEntitlement()
   ├─ Find entitlement for leave type & year
   └─ Decrement: ent.usedDays = max(0, usedDays - duration)
```

**Database Impact:**
- UPDATE `leave_applications` set status=CANCELLED
- UPDATE `leave_entitlements` set used_days = max(0, used_days - duration)

---

### Flow 5: Leave Balance Calculation

**File:** `model/LeaveEntitlement.java` (computed property)

**Called By:** Controllers when rendering dashboard, REST APIs

```
remainingDays = totalDays - usedDays
```

**Example:**
- Employee with PROFESSIONAL designation: 18 annual days
- Applied & approved 5 days in January
- Remaining = 18 - 5 = 13 days

---

### Flow 6: Compensation Claim Submission

**File:** `service/LeaveService.java` → `claimCompensation(CompensationClaim, Employee)`

**Steps:**

1. **Validate overtime hours:**
   ```
   if (overtimeHours > 4) throw IllegalArgumentException
   ```

2. **Check monthly cap:**
   ```
   ├─ Get start & end of month containing overtimeDate
   ├─ Query: compClaimRepo.sumOvertimeHoursByEmployeeAndMonth(employee, startOfMonth, endOfMonth)
   │  └─ Includes PENDING & APPROVED (excludes REJECTED)
   └─ If (monthlyHours + newClaim) > 72 throw error (MOM limit)
   ```

3. **Set employee & status:**
   ```
   ├─ Set employee
   ├─ Calculate compensationDays: (overtimeHours / 4.0) * 0.5
   ├─ Set status = PENDING
   └─ Save to DB
   ```

**Database Impact:**
- INSERT into `compensation_claims` with status=PENDING

---

### Flow 7: Compensation Claim Approval → Leave Entitlement Creation

**File:** `service/LeaveService.java` → `approveCompensationClaim(Long, String, Employee)`

**Steps:**

1. **Fetch & authorize:**
   ```
   ├─ Find claim by ID
   └─ Verify manager is subordinate's manager
   ```

2. **Approve:**
   ```
   ├─ Set status = APPROVED
   ├─ Set manager comment
   └─ Save
   ```

3. **Add Compensation Entitlement:**
   ```
   LeaveService.addCompensationEntitlement()
   ├─ Find COMPENSATION leave type
   ├─ Try to find existing: leaveEntitlementRepo.findByEmployeeAndLeaveTypeAndYear(
   │     employee, COMPENSATION_TYPE, currentYear)
   │
   ├─ If exists:
   │  └─ Increment totalDays: += compensationDays
   │
   └─ If not exists:
      └─ Create new LeaveEntitlement(employee, COMP_TYPE, year, compensationDays)
   ```

**Database Impact:**
- UPDATE `compensation_claims` set status=APPROVED
- UPSERT `leave_entitlements` (either INSERT new or UPDATE existing)

---

### Flow 8: Leave Duration Calculation

**File:** `util/LeaveCalculator.java`

Called from `LeaveService.calculateDuration()` (switch by leave type)

**Logic by Leave Type:**

#### **ANNUAL Leave:**
```
calculateAnnualLeaveDays(startDate, endDate, publicHolidays)
├─ Calculate calendar days = end - start + 1
│
├─ If calendar days <= 14:
│  ├─ Build holiday set with observed Mondays (if PH on Sunday)
│  ├─ Filter: all dates from start to end
│  ├─ Exclude: weekends (Sat/Sun) & holidays
│  └─ Count remaining
│
└─ If calendar days > 14:
   └─ Return all calendar days (no exclusions)
```

**Example:**
- Start: Monday 1 Jan 2024, End: Friday 5 Jan 2024
- Calendar days = 5
- Weekdays = 5 (all Mon–Fri)
- If none are public holidays → 5 working days

#### **MEDICAL Leave:**
```
calculateMedicalLeaveDays(startDate, endDate)
├─ Filter: all dates from start to end
├─ Exclude: weekends only
└─ Count remaining (public holidays counted)
```

#### **HOSPITALISATION Leave:**
```
calculateHospitalisationLeaveDays(startDate, endDate)
├─ Same as MEDICAL
├─ Filter: all dates from start to end
├─ Exclude: weekends only
└─ Count remaining (public holidays counted)
```

#### **COMPENSATION Leave:**
```
calculateCompensationLeaveDays(startDate, endDate)
├─ Same as MEDICAL & HOSPITALISATION
├─ Filter: all dates from start to end
├─ Exclude: weekends only
└─ Count remaining (public holidays counted)
```

#### **Half-Day Leave:**
```
if (application.isHalfDay()) {
    return 0.5
}
// else use above calculations
```

---

### Flow 9: Leave Type Transition to Compensation

**Scenario:** Employee works 4 hours of overtime

**Conversion:**
```
CompensationClaim.overtimeHours = 4
@PrePersist: compensationDays = (4 / 4.0) * 0.5 = 0.5 days
```

When manager approves:
```
LeaveEntitlement (COMPENSATION type for current year)
├─ If new: create with totalDays = 0.5
└─ If exists: totalDays += 0.5
```

Employee can then apply for compensation leave with:
```
LeaveApplication
├─ leaveType = COMPENSATION
├─ startDate/endDate = [dates for 0.5 day]
├─ duration = 0.5 (or 1.0 if full day approved)
```

---

## Service Layer Inventory

### **LeaveService** (`service/LeaveService.java`)

**Injects:** LeaveApplicationRepository, LeaveEntitlementRepository, PublicHolidayRepository, LeaveTypeRepository, CompensationClaimRepository, LeaveCalculator, EmailService

**Public Methods (Domain Logic):**

| Method | Signature | Purpose | Transaction |
|--------|-----------|---------|-------------|
| `applyLeave` | `(LeaveApplication, Employee) → LeaveApplication` | Submit new leave request | `@Transactional` |
| `updateLeave` | `(Long, LeaveApplication, Employee) → LeaveApplication` | Modify pending application | `@Transactional` |
| `deleteLeave` | `(Long, Employee) → void` | Soft-delete pending/rejected app | `@Transactional` |
| `cancelLeave` | `(Long, Employee) → void` | Cancel approved leave & restore entitlement | `@Transactional` |
| `approveLeave` | `(Long, String, Employee) → void` | Manager approves + deduct entitlement | `@Transactional` |
| `rejectLeave` | `(Long, String, Employee) → void` | Manager rejects (comment mandatory) | `@Transactional` |
| `claimCompensation` | `(CompensationClaim, Employee) → CompensationClaim` | Submit overtime claim (monthly cap check) | `@Transactional` |
| `approveCompensationClaim` | `(Long, String, Employee) → void` | Manager approves + add entitlement | `@Transactional` |
| `rejectCompensationClaim` | `(Long, String, Employee) → void` | Manager rejects compensation | `@Transactional` |

**Query Methods (Read-Only):**

| Method | Signature | Purpose |
|--------|-----------|---------|
| `findById` | `(Long) → LeaveApplication` | Find application or throw 404 |
| `findByIdAndEmployee` | `(Long, Employee) → LeaveApplication` | Find + verify ownership |
| `getMyLeaveHistory` | `(Employee) → List<LeaveApplication>` | Current year history |
| `getMyLeaveHistoryPaged` | `(Employee, Pageable) → Page<LeaveApplication>` | Paginated history |
| `getPendingApplicationsForManager` | `(Employee) → List<LeaveApplication>` | Subordinate pending apps |
| `getSubordinateLeaveHistory` | `(Employee, int) → List<LeaveApplication>` | By year |
| `getSubordinateLeaveHistoryPaged` | `(Employee, Pageable) → Page<LeaveApplication>` | Paginated |
| `getApprovedLeaveInMonth` | `(int, int) → List<LeaveApplication>` | Movement register |
| `getApprovedLeaveInRange` | `(LocalDate, LocalDate) → List<LeaveApplication>` | Reporting range |
| `getApprovedLeaveByTypeAndRange` | `(Long, LocalDate, LocalDate) → List<LeaveApplication>` | Reporting by type |
| `getSubordinateLeaveDuringPeriod` | `(Employee, LocalDate, LocalDate) → List<LeaveApplication>` | Conflict check |
| `getMyCompensationClaims` | `(Employee) → List<CompensationClaim>` | Employee's claims |
| `getPendingCompClaimsForManager` | `(Employee) → List<CompensationClaim>` | Subordinate pending |
| `getAllCompClaimsForManager` | `(Employee) → List<CompensationClaim>` | Subordinate all |
| `getActiveLeaveTypes` | `() → List<LeaveType>` | For dropdowns |
| `findLeaveTypeByName` | `(String) → Optional<LeaveType>` | Case-insensitive lookup |
| `getDefaultActiveLeaveTypes` | `() → List<LeaveType>` | Only ANNUAL, MEDICAL, etc. |
| `getCustomActiveLeaveTypes` | `() → List<LeaveType>` | Future-proofing for custom types |

**Private Helper Methods:**

| Method | Purpose |
|--------|---------|
| `validateLeaveApplication` | Complex validation with switch-by-type logic |
| `calculateDuration` | Calls LeaveCalculator based on type |
| `deductEntitlement` | Decrement used_days on approval |
| `restoreEntitlement` | Increment used_days on cancellation |
| `addCompensationEntitlement` | Create/update COMPENSATION entitlement |
| `calculateCalendarDaysInclusive` | Helper for date math |
| `findHolidaysAcrossRange` | Fetch holidays spanning years |
| `isSubordinate` | Manager authorization check |
| `initEmailAssociations` | Force-load lazy properties for @Async EmailService |

---

### **EmployeeService** (`service/EmployeeService.java`)

**Injects:** EmployeeRepository, LeaveEntitlementRepository, LeaveTypeRepository, PasswordEncoder

**Public Methods:**

| Method | Signature | Purpose | Transaction |
|--------|-----------|---------|-------------|
| `findByUsername` | `(String) → Optional<Employee>` | Auth lookup | Read-only |
| `findById` | `(Long) → Employee` | Find or throw 404 | Read-only |
| `findAll` | `() → List<Employee>` | Active only | Read-only |
| `findAllIncludingInactive` | `() → List<Employee>` | All employees | Read-only |
| `findSubordinates` | `(Employee) → List<Employee>` | Manager's team | Read-only |
| `findByRole` | `(Role) → List<Employee>` | Filter by role | Read-only |
| `createEmployee` | `(Employee) → Employee` | Create + init entitlements | `@Transactional` |
| `updateEmployeeDetails` | `(Employee) → Employee` | Update name, email, role, manager, active | `@Transactional` |
| `updateEmployeeDesignation` | `(Long, Designation) → Employee` | Change designation + recalc annual | `@Transactional` |
| `updatePassword` | `(Employee, String) → void` | Encode & save new password | `@Transactional` |
| `deactivateEmployee` | `(Long) → void` | Set active=false | `@Transactional` |
| `deleteEmployee` | `(Long) → void` | Hard delete (cascades) | `@Transactional` |
| `getEntitlements` | `(Employee, int) → List<LeaveEntitlement>` | By year | Read-only |
| `updateEntitlement` | `(Long, double) → void` | Admin can adjust totalDays (with caps) | `@Transactional` |
| `existsByUsername` | `(String) → boolean` | Duplicate check | Read-only |

**Private Helpers:**

| Method | Purpose |
|--------|---------|
| `initLeaveEntitlements` | Create initial entitlements for new employee |
| `recalculateAnnualEntitlementForYear` | On designation change, update annual days |
| `getMaxEntitlementFor` | Returns cap based on leave type & designation |

---

### **AdminService** (`service/AdminService.java`)

**Injects:** LeaveTypeRepository, PublicHolidayRepository

**Public Methods (Leave Types):**

| Method | Signature | Purpose | Transaction |
|--------|-----------|---------|-------------|
| `getAllLeaveTypes` | `() → List<LeaveType>` | Include inactive | Read-only |
| `findLeaveTypeById` | `(Long) → LeaveType` | Find or throw 404 | Read-only |
| `saveLeaveType` | `(LeaveType) → LeaveType` | Create/update (blocks max-days change for defaults) | `@Transactional` |
| `deleteLeaveType` | `(Long) → void` | Only if custom (no defaultType) | `@Transactional` |

**Public Methods (Public Holidays):**

| Method | Signature | Purpose | Transaction |
|--------|-----------|---------|-------------|
| `getHolidaysByYear` | `(int) → List<PublicHoliday>` | By year | Read-only |
| `getAllHolidays` | `() → List<PublicHoliday>` | All | Read-only |
| `saveHoliday` | `(PublicHoliday) → PublicHoliday` | Create/update | `@Transactional` |
| `deleteHoliday` | `(Long) → void` | Delete | `@Transactional` |
| `findHolidayById` | `(Long) → PublicHoliday` | Find or throw 404 | Read-only |
| `isHolidayDateTaken` | `(LocalDate) → boolean` | Duplicate check | Read-only |

---

### **CustomUserDetailsService** (`service/CustomUserDetailsService.java`)

**Implements:** `UserDetailsService` (Spring Security)

**Injects:** EmployeeRepository

**Public Methods:**

| Method | Signature | Purpose |
|--------|-----------|---------|
| `loadUserByUsername` | `(String) → UserDetails` | Fetch employee, build UserDetails with roles, throw if inactive |

**Used By:**
- Spring Security authentication flow
- JwtAuthenticationFilter to reload roles from DB (not token)
- JwtAuthController on token issuance

---

### **EmailService** (`service/EmailService.java`)

**Injects:** JavaMailSender, TemplateEngine

**Public Methods:**

| Method | Signature | Purpose |
|--------|-----------|---------|
| `sendLeaveApplicationNotification` | `(LeaveApplication, NotificationType) → void` | **Async** send email |

**Notification Types (Enum):**
- `APPLICATION` → Sent to manager, subject "Leave Application from [Employee]"
- `APPROVAL` → Sent to employee, subject "Your Leave Application has been Approved"
- `REJECTION` → Sent to employee, subject "Your Leave Application has been Rejected"

**Private Helpers:**

| Method | Purpose |
|--------|---------|
| `resolveRecipient` | Determine email recipient based on notification type |
| `buildSubject` | Template subject |
| `buildBody` | Process Thymeleaf template (emails/*.html) with context |
| `sendEmail` | Low-level MIME message send |

**Design Notes:**
- All sends are `@Async` (configured in AsyncConfig)
- Failures are logged (level: WARN) but do not abort main transaction
- Context variables: employee, manager, leaveType, dates, reason, comment, appHost

---

## Controller Layer & API Endpoints

### **Web Controllers** (Thymeleaf-based UI)

#### **AuthController** (`controller/AuthController.java`)

**Endpoints:**

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/login` | Login form (query params: `error`, `logout`) |
| GET | `/admin/login` | Redirect to `/login` |
| GET | `/access-denied` | 403 page |
| GET | `/` | Redirect to `/login` |

**Security:** Public (permitAll)

---

#### **EmployeeController** (`controller/EmployeeController.java`)

**Role:** ROLE_EMPLOYEE (and MANAGER/ADMIN inherit via hierarchy)

**Dashboard:**

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/employee/dashboard` | Summary: entitlements, recent leaves |

**Leave Management:**

| Method | Path | Purpose | Calls |
|--------|------|---------|-------|
| GET | `/employee/leaves/apply` | Show form | `LeaveService.getDefaultActiveLeaveTypes()` |
| POST | `/employee/leaves/apply` | Submit application | `LeaveService.applyLeave()` |
| GET | `/employee/leaves` | Paginated history (params: `page`, `size`) | `LeaveService.getMyLeaveHistoryPaged()` |
| GET | `/employee/leaves/{id}` | View single | `LeaveService.findByIdAndEmployee()` |
| GET | `/employee/leaves/{id}/edit` | Edit form (if editable) | `LeaveService.findByIdAndEmployee()` |
| POST | `/employee/leaves/{id}/edit` | Update | `LeaveService.updateLeave()` |
| POST | `/employee/leaves/{id}/delete` | Soft-delete | `LeaveService.deleteLeave()` |
| POST | `/employee/leaves/{id}/cancel` | Cancel approved | `LeaveService.cancelLeave()` |

**Compensation Claims:**

| Method | Path | Purpose | Calls |
|--------|------|---------|-------|
| GET | `/employee/compensation/claim` | Show form + my claims | `LeaveService.getMyCompensationClaims()` |
| POST | `/employee/compensation/claim` | Submit claim | `LeaveService.claimCompensation()` |

**Validation:** Pagination params validated; page < 0 → 0, size must be 10/20/25

---

#### **ManagerController** (`controller/ManagerController.java`)

**Role:** ROLE_MANAGER (and ADMIN inherit)

**Dashboard:**

| Method | Path | Purpose | Calls |
|--------|------|---------|-------|
| GET | `/manager/dashboard` | Pending apps/claims summary | `LeaveService.getPendingApplicationsForManager()`, `LeaveService.getPendingCompClaimsForManager()` |

**Leave Approval:**

| Method | Path | Purpose | Calls |
|--------|------|---------|-------|
| GET | `/manager/leaves` | Pending apps grouped by employee | `LeaveService.getPendingApplicationsForManager()` |
| GET | `/manager/leaves/{id}` | View + show conflicts | `LeaveService.findById()`, `LeaveService.getSubordinateLeaveDuringPeriod()` |
| POST | `/manager/leaves/{id}/approve` | Approve with optional comment | `LeaveService.approveLeave()` |
| POST | `/manager/leaves/{id}/reject` | Reject with mandatory comment | `LeaveService.rejectLeave()` |

**Subordinate History:**

| Method | Path | Purpose | Calls |
|--------|------|---------|-------|
| GET | `/manager/subordinates/leaves` | Paginated (params: `page`, `size`) | `LeaveService.getSubordinateLeaveHistoryPaged()` |

**Compensation Approval:**

| Method | Path | Purpose | Calls |
|--------|------|---------|-------|
| GET | `/manager/compensation` | Show pending + all claims | `LeaveService.getPendingCompClaimsForManager()`, `LeaveService.getAllCompClaimsForManager()` |
| POST | `/manager/compensation/{id}/approve` | Approve + add entitlement | `LeaveService.approveCompensationClaim()` |
| POST | `/manager/compensation/{id}/reject` | Reject with comment | `LeaveService.rejectCompensationClaim()` |

**Reporting:**

| Method | Path | Purpose | Calls |
|--------|------|---------|-------|
| GET | `/manager/reports` | Report form | `LeaveService.getActiveLeaveTypes()` |
| GET | `/manager/reports/leave` | Generate report (params: `startDate`, `endDate`, `leaveTypeId`) | `LeaveService.getApprovedLeaveInRange()`, `LeaveService.getApprovedLeaveByTypeAndRange()` |

---

#### **AdminController** (`controller/AdminController.java`)

**Role:** ROLE_ADMIN

**Dashboard:**

| Method | Path | Purpose | Calls |
|--------|------|---------|-------|
| GET | `/admin/dashboard` | Stats: total employees, leave types, holidays | `EmployeeService.findAll()`, `AdminService.getAllLeaveTypes()`, `AdminService.getHolidaysByYear()` |

**Employee Management:**

| Method | Path | Purpose | Calls |
|--------|------|---------|-------|
| GET | `/admin/employees` | List all (including inactive) | `EmployeeService.findAllIncludingInactive()` |
| GET | `/admin/employees/new` | Create form | `EmployeeService.findByRole(ROLE_MANAGER)` |
| POST | `/admin/employees/new` | Create employee | `EmployeeService.createEmployee()` |
| GET | `/admin/employees/{id}/edit` | Edit form | `EmployeeService.findById()`, `EmployeeService.findByRole(ROLE_MANAGER)` |
| POST | `/admin/employees/{id}/edit` | Update (including designation change) | `EmployeeService.updateEmployeeDetails()`, `EmployeeService.updateEmployeeDesignation()` |
| POST | `/admin/employees/{id}/deactivate` | Soft-deactivate | `EmployeeService.deactivateEmployee()` |
| POST | `/admin/employees/{id}/delete` | Hard delete | `EmployeeService.deleteEmployee()` |

**Leave Entitlements:**

| Method | Path | Purpose | Calls |
|--------|------|---------|-------|
| GET | `/admin/employees/{id}/entitlements` | View by year | `EmployeeService.getEntitlements()` |
| POST | `/admin/entitlements/{id}/update` | Adjust totalDays | `EmployeeService.updateEntitlement()` |

**Leave Types:**

| Method | Path | Purpose | Calls |
|--------|------|---------|-------|
| GET | `/admin/leave-types` | List all | `AdminService.getAllLeaveTypes()` |
| GET | `/admin/leave-types/new` | Create form | — |
| POST | `/admin/leave-types/new` | Create | `AdminService.saveLeaveType()` |
| GET | `/admin/leave-types/{id}/edit` | Edit form | `AdminService.findLeaveTypeById()` |
| POST | `/admin/leave-types/{id}/edit` | Update | `AdminService.saveLeaveType()` |
| POST | `/admin/leave-types/{id}/delete` | Delete (if custom) | `AdminService.deleteLeaveType()` |

**Public Holidays:**

| Method | Path | Purpose | Calls |
|--------|------|---------|-------|
| GET | `/admin/holidays` | List by year (param: `year`) | `AdminService.getHolidaysByYear()` |
| GET | `/admin/holidays/new` | Create form | — |
| POST | `/admin/holidays/new` | Create (duplicate check) | `AdminService.isHolidayDateTaken()`, `AdminService.saveHoliday()` |
| POST | `/admin/holidays/{id}/delete` | Delete | `AdminService.deleteHoliday()` |

---

#### **MovementController** (`controller/MovementController.java`)

**Role:** Any authenticated user

**Purpose:** View approved leave for all employees (movement register)

| Method | Path | Purpose | Calls |
|--------|------|---------|-------|
| GET | `/movement` | Show by month (params: `year`, `month`) | `LeaveService.getApprovedLeaveInMonth()` |

**Data:** Returns leaves with employee name, type, dates, duration

---

### **REST Controllers** (JSON API)

#### **JwtAuthController** (`controller/rest/JwtAuthController.java`)

**Purpose:** Issue JWT tokens for stateless API authentication

**Endpoints:**

| Method | Path | Purpose | Calls |
|--------|------|---------|-------|
| POST | `/api/v1/auth/token` | Issue JWT (public, no auth required) | `AuthenticationManager.authenticate()`, `JwtService.generateToken()` |

**Request Body (AuthRequest):**
```json
{
  "username": "string (max 50)",
  "password": "string (max 128)"
}
```

**Response (AuthResponse):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 900
}
```

**Error Handling:**
- Invalid credentials → 401 JSON (generic, no username enumeration)
- Validation failure (blank username/password) → 400 JSON

**Security Notes:**
- Server-side validation via `@Valid` on AuthRequest
- Token lifetime: 15 minutes (900 seconds)
- No PII in response

---

#### **LeaveRestController** (`controller/rest/LeaveRestController.java`)

**Purpose:** REST APIs for leave data (read-only for now)

**Requires:** Valid JWT Bearer token

**Endpoints:**

| Method | Path | Purpose | Calls |
|--------|------|---------|-------|
| GET | `/api/v1/leaves/my` | Current user's leave history | `LeaveService.getMyLeaveHistory()` |
| GET | `/api/v1/leaves/{id}` | Specific leave (must own) | `LeaveService.findByIdAndEmployee()` |
| GET | `/api/v1/leaves/entitlements` | Current year entitlements | `EmployeeService.getEntitlements()` |
| GET | `/api/v1/movement?year=&month=` | Movement register (params: `year`, `month`, defaults to current) | `LeaveService.getApprovedLeaveInMonth()` |
| GET | `/api/v1/leave-types` | Active default leave types | `LeaveService.getDefaultActiveLeaveTypes()` |

**Response Format:**

**GET /api/v1/leaves/my:**
```json
[
  {
    "id": 1,
    "leaveTypeId": 1,
    "leaveType": "Annual Leave",
    "startDate": "2024-01-15",
    "endDate": "2024-01-19",
    "duration": 5,
    "reason": "Vacation",
    "workDissemination": "Coverage plan",
    "contactDetails": "+65...",
    "status": "APPROVED",
    "managerComment": null,
    "appliedDate": "2024-01-10T10:30:00",
    "updatedDate": null,
    "halfDay": false,
    "halfDayType": null
  }
]
```

**GET /api/v1/leaves/entitlements:**
```json
[
  {
    "id": 1,
    "leaveTypeId": 1,
    "leaveType": "Annual Leave",
    "year": 2024,
    "totalDays": 18,
    "usedDays": 5,
    "remainingDays": 13
  }
]
```

**GET /api/v1/movement:**
```json
{
  "year": 2024,
  "month": 1,
  "leaves": [
    {
      "id": 1,
      "employee": "John Doe",
      "leaveType": "Annual Leave",
      "startDate": "2024-01-15",
      "endDate": "2024-01-19",
      "duration": 5
    }
  ]
}
```

**GET /api/v1/leave-types:**
```json
[
  {
    "id": 1,
    "name": "Annual Leave",
    "description": "Vacation leave",
    "maxDaysPerYear": 18,
    "halfDayAllowed": true
  }
]
```

---

### **GlobalExceptionHandler** (`controller/GlobalExceptionHandler.java`)

**Purpose:** Centralized exception handling for web controllers (not REST APIs)

**Handlers:**

| Exception Type | Response | Template |
|---|---|---|
| `ResourceNotFoundException` | 404 HTTP status + error message | `error/404` |
| `LeaveApplicationException` | View error page | `error/error` |
| `Exception` (generic) | Generic error message (no stack trace) | `error/error` |

**Logging:** All exceptions logged at ERROR level

**Design:** Does NOT expose stack traces or exception messages to UI

---

## Security Architecture

### **Authentication Flow**

```
┌─────────────────────────────────────────────────────────────┐
│  Web Browser / REST Client                                  │
└────────────────────┬────────────────────────────────────────┘
                     │
      ┌──────────────┴──────────────┐
      │                             │
      ▼                             ▼
 Form Login              REST + JWT (Bearer)
   (Session)              (Stateless)
```

#### **Web UI (Form Login + Session):**

1. **User** enters username/password on `/login`
2. **Spring Security** validates via `AuthenticationManager`
3. **UserDetailsService**: `CustomUserDetailsService.loadUserByUsername(username)`
   - Fetches Employee from DB
   - Checks if active → if not, throws UsernameNotFoundException
   - Builds UserDetails with role from Employee.role
4. **Password verification** via BCryptPasswordEncoder
5. **On success:** HttpSession created, user redirected based on role:
   - ROLE_ADMIN → `/admin/dashboard`
   - ROLE_MANAGER → `/manager/dashboard`
   - ROLE_EMPLOYEE → `/employee/dashboard`

#### **REST API (JWT + Stateless):**

1. **POST /api/v1/auth/token** with `AuthRequest { username, password }`
2. **JwtAuthController** calls `authenticationManager.authenticate()`
3. **UserDetailsService** loads user (same as above)
4. **On success:** `JwtService.generateToken(userDetails)` returns JWT
5. **JWT contains:**
   - `sub`: username
   - `roles`: ["ROLE_MANAGER", "ROLE_ADMIN"] (from authorities)
   - `iat`: issued at
   - `exp`: expiration (now + 15 min)
   - Signed: HS256, secret from `JWT_SECRET` env var
6. **Response:** `{ "accessToken": "...", "tokenType": "Bearer", "expiresIn": 900 }`

#### **Subsequent API Requests:**

1. Client sends: `Authorization: Bearer <JWT>`
2. **JwtAuthenticationFilter** intercepts (OncePerRequestFilter)
3. **Validates token:** `jwtService.validateToken(token)`
   - Verifies HS256 signature
   - Checks expiration
   - Returns false on any failure (no exception thrown)
4. **If valid:** Extract username, reload user via `CustomUserDetailsService`
   - **Roles always fetched from DB** (not trusted from token)
5. **Set SecurityContext:** `UsernamePasswordAuthenticationToken` with authorities
6. **Continue chain**

---

### **Authorization Rules**

**SecurityConfig** defines two filter chains:

#### **API Chain** (order=1, applies to `/api/**`)
```
Session: STATELESS
CSRF: DISABLED (safe; no cookies)
JWT validation: Via JwtAuthenticationFilter

Path Rules:
├─ /api/v1/auth/token → permitAll
├─ /api/admin/** → ROLE_ADMIN
├─ /api/manager/** → ROLE_MANAGER, ROLE_ADMIN
├─ /api/employee/** → ROLE_EMPLOYEE, ROLE_MANAGER, ROLE_ADMIN
├─ /api/movement/** → authenticated
└─ anyRequest → authenticated
```

#### **Web Chain** (order=2, applies to all non-API)
```
Session: NORMAL (HttpSession)
CSRF: ENABLED
Form login: /login → /login
Logout: /logout → /login?logout=true

Path Rules:
├─ /css/**, /js/**, /images/**, /webjars/** → permitAll
├─ /login, /admin/login → permitAll
├─ /admin/** → ROLE_ADMIN
├─ /manager/** → ROLE_MANAGER, ROLE_ADMIN
├─ /employee/** → ROLE_EMPLOYEE, ROLE_MANAGER, ROLE_ADMIN
├─ /movement/** → authenticated
└─ anyRequest → authenticated

Access Denied: → /access-denied
```

---

### **Role Hierarchy**

```
ROLE_ADMIN
  ├─ Can access /admin/**, /api/admin/**
  ├─ Can manage employees, leave types, holidays
  ├─ Inherits ROLE_MANAGER & ROLE_EMPLOYEE
  │
ROLE_MANAGER
  ├─ Can access /manager/**, /api/manager/**
  ├─ Can approve/reject subordinate leave & compensation
  ├─ Inherits ROLE_EMPLOYEE
  │
ROLE_EMPLOYEE
  └─ Can access /employee/**, /api/employee/**
     └─ Can view own leave, apply for leave
```

---

### **Authorization Checks (Service Layer)**

Beyond Spring Security path matchers, business logic validates:

#### **Manager Can Only Approve Subordinates:**
```java
private boolean isSubordinate(Employee employee, Employee manager) {
    return employee.getManager() != null &&
           employee.getManager().getId().equals(manager.getId());
}
```
- Used in: `LeaveService.approveLeave()`, `LeaveService.rejectLeave()`, compensation methods

#### **Employee Can Only Modify Own Leave:**
```java
public LeaveApplication findByIdAndEmployee(Long id, Employee employee) {
    LeaveApplication la = findById(id);
    if (!la.getEmployee().getId().equals(employee.getId())) {
        throw new LeaveApplicationException("Access denied: Not your leave application");
    }
    return la;
}
```

#### **Get Current User:**
```java
// SecurityUtils.getCurrentEmployee()
Authentication auth = SecurityContextHolder.getContext().getAuthentication();
String username = auth.getName();
return employeeService.findByUsername(username)
    .orElseThrow(() -> new RuntimeException("Current user not found"));
```

---

### **Password Security**

- **Storage:** BCrypt (strength=10 by default)
- **Encoding:** `PasswordEncoder` bean (BCryptPasswordEncoder)
- **Never logged:** Credentials never appear in debug logs
- **Environment:** Seed passwords via `app.seed.password` config property (not hardcoded)

---

### **JWT Security Properties**

**From CLAUDE.md & code comments:**

- **Algorithm:** HS256 (pinned via JJWT 0.12.x, rejects alg:none)
- **Secret:** Sourced from `JWT_SECRET` environment variable (never hardcoded), must be ≥256 bits
- **Expiration:** 15 minutes (`app.jwt.expirationMs`)
- **Claims:** `sub` (username), `roles` (from DB), `iat`, `exp`
- **No PII:** Email, password, full name NOT in token (ASVS V2.1.1)
- **Roles Always from DB:** Token roles not trusted; reloaded on each request
- **Error Handling:** Any validation failure → 401 JSON (never stack traces)

**Configuration:** `config/JwtConfig.java`
```properties
app.jwt.secret=${JWT_SECRET}
app.jwt.expirationMs=900000  # 15 minutes
```

---

### **CSRF Protection**

- **Web UI:** Enabled (Spring Security default)
  - All POST/PUT/DELETE require CSRF token
  - Thymeleaf auto-includes `th:action` with token
- **REST API:** Disabled (stateless JWT, no cookies)

---

### **Input Validation (Defense in Depth)**

#### **Controller Boundary:**
- `@Valid` on all request body DTOs & form models
- Example: `@Valid @ModelAttribute("leaveApplication") LeaveApplication`
- Validation annotations: `@NotBlank`, `@NotNull`, `@Size`, `@Email`

#### **Service Layer (Business Logic):**
- `validateLeaveApplication()` — extensive checks per leave type
- `claimCompensation()` — monthly cap enforcement
- Manager authorization checks

#### **Database:**
- Constraints: `NOT NULL`, `UNIQUE`, `Foreign Keys`
- Parameterized queries (JPA, no SQL injection)

---

## Repository Layer (Data Persistence)

### **LeaveApplicationRepository**

**Custom Queries (Spring Data @Query):**

```java
// Find current year history (excluding DELETED)
@Query("SELECT la FROM LeaveApplication la WHERE la.employee = :employee " +
       "AND YEAR(la.startDate) = :year AND la.status != 'DELETED' ORDER BY la.appliedDate DESC")
List<LeaveApplication> findByEmployeeAndYear(Employee, int year)

// Pending for manager's subordinates
@Query("SELECT la FROM LeaveApplication la WHERE la.employee.manager = :manager " +
       "AND la.status IN ('APPLIED', 'UPDATED') ORDER BY la.appliedDate DESC")
List<LeaveApplication> findPendingByManager(Employee manager)

// Subordinate history by year
@Query("SELECT la FROM LeaveApplication la WHERE la.employee.manager = :manager " +
       "AND YEAR(la.startDate) = :year AND la.status != 'DELETED' ORDER BY la.appliedDate DESC")
List<LeaveApplication> findByManagerAndYear(Employee manager, int year)

// Approved leave overlapping date range (for conflicts & movement register)
@Query("SELECT la FROM LeaveApplication la WHERE la.status = 'APPROVED' " +
       "AND la.startDate <= :endDate AND la.endDate >= :startDate")
List<LeaveApplication> findApprovedLeaveDuringPeriod(LocalDate startDate, LocalDate endDate)

// Subordinate approved leave in period
@Query("SELECT la FROM LeaveApplication la WHERE la.employee.manager = :manager " +
       "AND la.status = 'APPROVED' " +
       "AND la.startDate <= :endDate AND la.endDate >= :startDate")
List<LeaveApplication> findSubordinateLeaveDuringPeriod(Employee manager, LocalDate startDate, LocalDate endDate)

// Sum used days for validation
@Query("SELECT COALESCE(SUM(la.duration), 0) FROM LeaveApplication la " +
       "WHERE la.employee = :employee AND la.leaveType.id = :leaveTypeId " +
       "AND YEAR(la.startDate) = :year AND la.status IN ('APPLIED', 'UPDATED', 'APPROVED') " +
       "AND (:excludeId IS NULL OR la.id != :excludeId)")
double sumUsedDaysByEmployeeAndLeaveTypeAndYear(Employee employee, Long leaveTypeId, int year, Long excludeId)

// Reporting: approved leave in range
@Query("SELECT la FROM LeaveApplication la WHERE la.status = 'APPROVED' " +
       "AND la.startDate >= :startDate AND la.endDate <= :endDate ORDER BY la.employee.name, la.startDate")
List<LeaveApplication> findApprovedLeaveInRange(LocalDate startDate, LocalDate endDate)

// Reporting: by leave type
@Query("SELECT la FROM LeaveApplication la WHERE la.status = 'APPROVED' " +
       "AND la.leaveType.id = :leaveTypeId " +
       "AND la.startDate >= :startDate AND la.endDate <= :endDate ORDER BY la.employee.name, la.startDate")
List<LeaveApplication> findApprovedLeaveByTypeAndRange(Long leaveTypeId, LocalDate startDate, LocalDate endDate)
```

**Spring Data Methods:**
```java
Page<LeaveApplication> findByEmployeeAndStatusNotOrderByAppliedDateDesc(Employee, LeaveStatus, Pageable)
Page<LeaveApplication> findByManagerPageable(Employee manager, Pageable) // Custom @Query
```

---

### **EmployeeRepository**

**Methods:**
```java
Optional<Employee> findByUsername(String username)        // Auth lookup
boolean existsByUsername(String username)                  // Duplicate check
List<Employee> findByManager(Employee manager)             // Subordinates
List<Employee> findByManagerId(Long managerId)
List<Employee> findByRole(Role role)
List<Employee> findByActive(boolean active)

@Query("SELECT e FROM Employee e WHERE e.active = true ORDER BY e.name")
List<Employee> findAllActive()
```

---

### **LeaveEntitlementRepository**

**Methods:**
```java
Optional<LeaveEntitlement> findByEmployeeAndLeaveTypeAndYear(Employee, LeaveType, int)
List<LeaveEntitlement> findByEmployeeAndYear(Employee, int year)
List<LeaveEntitlement> findByYear(int year)
```

---

### **CompensationClaimRepository**

**Methods:**
```java
List<CompensationClaim> findByEmployeeOrderByClaimedDateDesc(Employee)

@Query("SELECT cc FROM CompensationClaim cc WHERE cc.employee.manager = :manager " +
       "AND cc.status = 'PENDING' ORDER BY cc.claimedDate DESC")
List<CompensationClaim> findPendingByManager(Employee manager)

@Query("SELECT cc FROM CompensationClaim cc WHERE cc.employee.manager = :manager ORDER BY cc.claimedDate DESC")
List<CompensationClaim> findAllByManager(Employee manager)

// Sum approved compensation days (for entitlement validation)
@Query("SELECT COALESCE(SUM(cc.compensationDays), 0) FROM CompensationClaim cc " +
       "WHERE cc.employee = :employee AND cc.status = 'APPROVED'")
double sumApprovedCompDaysByEmployee(Employee employee)

// Monthly cap check (includes PENDING to prevent double-claiming)
@Query("SELECT COALESCE(SUM(cc.overtimeHours), 0) FROM CompensationClaim cc " +
       "WHERE cc.employee = :employee " +
       "AND cc.overtimeDate >= :startOfMonth AND cc.overtimeDate <= :endOfMonth " +
       "AND cc.status <> 'REJECTED'")
int sumOvertimeHoursByEmployeeAndMonth(Employee employee, LocalDate startOfMonth, LocalDate endOfMonth)
```

---

### **LeaveTypeRepository**

**Methods:**
```java
Optional<LeaveType> findByNameIgnoreCase(String name)
List<LeaveType> findByActive(boolean active)
Optional<LeaveType> findByDefaultType(LeaveTypeDefault defaultType)
```

---

### **PublicHolidayRepository**

**Methods:**
```java
List<PublicHoliday> findByYear(int year)
Optional<PublicHoliday> findByHolidayDate(LocalDate date)
boolean existsByHolidayDate(LocalDate date)
```

---

## Utility Functions

### **LeaveCalculator** (`util/LeaveCalculator.java`)

**Purpose:** Calculate leave days based on type, excluding weekends/holidays as appropriate

**Public Methods:**

#### **calculateAnnualLeaveDays(startDate, endDate, holidays) → double**
```
if (calendar days <= 14):
    exclude weekends & public holidays
    count working days
else:
    return all calendar days (for extended absence > 14 days)
```

#### **calculateMedicalLeaveDays(startDate, endDate) → double**
```
exclude weekends only
count working days (public holidays counted)
```

#### **calculateHospitalisationLeaveDays(startDate, endDate) → double**
```
same as medical: exclude weekends only
```

#### **calculateCompensationLeaveDays(startDate, endDate) → double**
```
same as medical: exclude weekends only
```

#### **calculateCompensationDays(overtimeHours) → double**
```
return (overtimeHours / 4.0) * 0.5
e.g.: 1h → 0.125, 2h → 0.25, 3h → 0.375, 4h → 0.5
```

#### **isWeekend(date) → boolean**
```
return Saturday or Sunday
```

#### **isPublicHoliday(date, holidays) → boolean**
```
check if date in holiday set
```

#### **isWorkingDay(date, holidays) → boolean**
```
return !isWeekend(date) && !isPublicHoliday(date, holidays)
```

#### **areWorkingDays(startDate, endDate, holidays) → boolean**
```
verify both start and end are working days (for annual leave validation)
```

#### **HolidaysWithObservedMondays(holidays) → Set<LocalDate>** (private)
```
For each Sunday holiday:
  Mark the following Monday as observed
  If Monday already taken, cascade to Tuesday, etc.
```

---

### **SecurityUtils** (`util/SecurityUtils.java`)

**Purpose:** Retrieve current authenticated user from SecurityContext

**Public Methods:**

#### **getCurrentEmployee() → Employee**
```
Get SecurityContext authentication
Extract username
Load Employee from DB via EmployeeService
Throw RuntimeException if not found
```

#### **getCurrentUsername() → String**
```
Get SecurityContext authentication
Return username
```

**Used By:** All controllers to identify current user for authorization checks

---

## Configuration & Initialization

### **SecurityConfig** (`config/SecurityConfig.java`)

**Beans:**

| Bean | Type | Purpose |
|------|------|---------|
| `passwordEncoder()` | `PasswordEncoder` | BCryptPasswordEncoder for password encoding/validation |
| `authenticationManager()` | `AuthenticationManager` | Built from `AuthenticationConfiguration` |
| `jwtAuthenticationFilterRegistration()` | `FilterRegistrationBean` | Disable auto-registration (manually added via `addFilterBefore`) |
| `apiFilterChain()` | `SecurityFilterChain` | Stateless JWT chain (order=1) |
| `webFilterChain()` | `SecurityFilterChain` | Session+form login chain (order=2) |

---

### **JwtConfig** (`config/JwtConfig.java`)

**Properties Binding:**
```properties
app.jwt.secret=${JWT_SECRET}          # From environment
app.jwt.expirationMs=900000           # 15 minutes
```

**Beans:** None (purely configuration class with getters/setters)

---

### **AsyncConfig** (`config/AsyncConfig.java`)

**Purpose:** Enable async processing for long-running tasks

**Annotation:** `@EnableAsync`

**Used By:** `EmailService.sendLeaveApplicationNotification()`

---

### **DataInitializer** (`config/DataInitializer.java`)

**Purpose:** Seed test data on application startup

**Implements:** `ApplicationRunner`

**Process:**

1. Check if "admin" user exists
   - If yes: exit (seed only runs once)
   - If no: proceed

2. Fetch leave types from DB (inserted by `data.sql`)
   - ANNUAL, MEDICAL, HOSPITALISATION, COMPENSATION

3. Create test employees:
   - Admin user
   - 2 Managers (Chen, Lim)
   - 6 Employees under managers
   - Some with leave/compensation data

4. Set manager hierarchy:
   - Employees assigned to Chen/Lim

5. Auto-create leave entitlements for all employees (current year)

**Security Note:** Passwords from `app.seed.password` config (never hardcoded)

---

### **Key Application Properties**

```properties
# JWT
app.jwt.secret=${JWT_SECRET}
app.jwt.expirationMs=900000

# Email
spring.mail.host=${MAIL_HOST}
spring.mail.port=${MAIL_PORT}
spring.mail.username=${MAIL_USERNAME}
spring.mail.password=${MAIL_PASSWORD}
spring.mail.properties.mail.smtp.auth=true
spring.mail.properties.mail.smtp.starttls.enable=true

# Database (H2 or actual DB)
spring.datasource.url=...
spring.datasource.username=...
spring.datasource.password=...

# App-specific
app.base-url=localhost
app.seed.password=...

# JPA
spring.jpa.hibernate.ddl-auto=validate  # Or create
spring.jpa.show-sql=false
```

---

## Summary: Key Concepts for Developers

### **Request-Response Cycle Example: Applying Leave**

```
1. Employee fills form: dates, leave type, reason
   ↓
2. POST /employee/leaves/apply → EmployeeController.applyLeave()
   ↓
3. Form validation: @Valid checks field constraints
   ↓
4. LeaveService.applyLeave(application, employee)
   ├─ Validate: dates, working days, entitlement balance
   ├─ Calculate: duration via LeaveCalculator
   └─ Save: leaveAppRepo.save()
   ↓
5. Fetch manager, force-load lazy properties
   ↓
6. EmailService.sendLeaveApplicationNotification() [ASYNC]
   └─ Sends email to manager in background
   ↓
7. Redirect: /employee/leaves (success flash message)
```

### **Database Consistency Guarantees**

- All mutations in LeaveService wrapped in `@Transactional`
- Hibernate manages lazy loading within transaction
- Async email failures do not abort main transaction (logged)
- Unique constraints prevent duplicate entitlements

### **Entry Points for New Features**

1. **New Leave Type:** AdminService.saveLeaveType() + controller
2. **New Validation Rule:** LeaveService.validateLeaveApplication() switch statement
3. **New Report:** Manager/MovementController + LeaveService query method
4. **New Email Template:** EmailService + Thymeleaf HTML file

---

## Conclusion

This architecture follows **3-layer design** (Controller → Service → Repository) with:
- **Security:** Role-based access control (RBAC) + authorization checks
- **Modularity:** Clean separation of concerns
- **Testability:** Dependency injection (Lombok @RequiredArgsConstructor)
- **Maintainability:** Documented complex logic, consistent naming
- **Compliance:** OWASP ASVS principles (input validation, no stack trace leaks, etc.)

Use this document as reference for code reviews, debugging, and new feature development.
