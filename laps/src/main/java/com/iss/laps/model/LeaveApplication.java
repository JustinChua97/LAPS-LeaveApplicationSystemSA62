package com.iss.laps.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "leave_applications")
@Getter @Setter @NoArgsConstructor
public class LeaveApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "leave_type_id", nullable = false)
    @NotNull
    private LeaveType leaveType;

    @Column(nullable = false)
    @NotNull
    private LocalDate startDate;

    @Column(nullable = false)
    @NotNull
    private LocalDate endDate;

    // Duration in days (or 0.5 increments for compensation)
    @Column(nullable = false)
    private double duration;

    @Column(nullable = false)
    @NotBlank
    private String reason;

    @Column
    private String workDissemination;

    @Column
    private String contactDetails;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeaveStatus status;

    @Column
    private String managerComment;

    @Column(nullable = false)
    private LocalDateTime appliedDate;

    @Column
    private LocalDateTime updatedDate;

    // Half day fields for compensation leave
    @Column(nullable = false)
    private boolean halfDay = false;

    @Column
    private String halfDayType; // "AM" or "PM"

    @PrePersist
    protected void onCreate() {
        this.appliedDate = LocalDateTime.now();
        if (this.status == null) {
            this.status = LeaveStatus.APPLIED;
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedDate = LocalDateTime.now();
    }

    public boolean isEditable() {
        return status == LeaveStatus.APPLIED || status == LeaveStatus.UPDATED;
    }

    public boolean isCancellable() {
        return status == LeaveStatus.APPROVED;
    }

    public boolean isDeletable() {
        return status == LeaveStatus.APPLIED || status == LeaveStatus.UPDATED || status == LeaveStatus.REJECTED;
    }
}
