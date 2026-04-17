package com.iss.laps.dto;

import com.iss.laps.model.LeaveEntitlement;

public record EntitlementDto(
        Long id,
        Long leaveTypeId,
        String leaveType,
        int year,
        double totalDays,
        double usedDays,
        double remainingDays
) {
    public static EntitlementDto from(LeaveEntitlement ent) {
        return new EntitlementDto(
                ent.getId(),
                ent.getLeaveType().getId(),
                ent.getLeaveType().getName(),
                ent.getYear(),
                ent.getTotalDays(),
                ent.getUsedDays(),
                ent.getRemainingDays()
        );
    }
}
