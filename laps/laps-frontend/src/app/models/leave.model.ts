// Matches EntitlementDto.java
export interface EntitlementDto {
  id: number;
  leaveTypeId: number;
  leaveType: string;
  year: number;
  totalDays: number;
  usedDays: number;
  remainingDays: number;
}

// Matches LeaveDto.java
// LocalDate/LocalDateTime arrive as strings ("2025-01-15", ISO-8601)
export interface LeaveDto {
  id: number;
  leaveTypeId: number;
  leaveType: string;
  startDate: string;
  endDate: string;
  duration: number;
  reason: string;
  workDissemination: string | null;
  contactDetails: string | null;
  status: string;
  managerComment: string | null;
  appliedDate: string;
  updatedDate: string | null;
  halfDay: boolean;
  halfDayType: string | null;
}
