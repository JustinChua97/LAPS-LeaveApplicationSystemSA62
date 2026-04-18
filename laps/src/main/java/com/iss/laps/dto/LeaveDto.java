package com.iss.laps.dto;

import com.iss.laps.model.LeaveApplication;
import com.iss.laps.model.LeaveStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record LeaveDto(
        Long id,
        Long leaveTypeId,
        String leaveType,
        LocalDate startDate,
        LocalDate endDate,
        double duration,
        String reason,
        String workDissemination,
        String contactDetails,
        LeaveStatus status,
        String managerComment,
        LocalDateTime appliedDate,
        LocalDateTime updatedDate,
        boolean halfDay,
        String halfDayType
) {
    public static LeaveDto from(LeaveApplication app) {
        return new LeaveDto(
                app.getId(),
                app.getLeaveType().getId(),
                app.getLeaveType().getName(),
                app.getStartDate(),
                app.getEndDate(),
                app.getDuration(),
                app.getReason(),
                app.getWorkDissemination(),
                app.getContactDetails(),
                app.getStatus(),
                app.getManagerComment(),
                app.getAppliedDate(),
                app.getUpdatedDate(),
                app.isHalfDay(),
                app.getHalfDayType()
        );
    }
}
