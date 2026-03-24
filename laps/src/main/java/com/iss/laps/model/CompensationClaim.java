package com.iss.laps.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "compensation_claims")
@Getter @Setter @NoArgsConstructor
public class CompensationClaim {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(nullable = false)
    @NotNull
    private LocalDate overtimeDate;

    @Column(nullable = false)
    @Min(4)
    private int overtimeHours; // every 4 hours = 0.5 day

    // Computed: overtimeHours / 4 * 0.5
    @Column(nullable = false)
    private double compensationDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ClaimStatus status;

    @Column
    private String managerComment;

    @Column(nullable = false)
    private LocalDateTime claimedDate;

    @Column
    private LocalDateTime processedDate;

    @Column
    private String reason;

    @PrePersist
    protected void onCreate() {
        this.claimedDate = LocalDateTime.now();
        if (this.status == null) {
            this.status = ClaimStatus.PENDING;
        }
        // Calculate compensation days: every 4 hours = 0.5 day
        this.compensationDays = (overtimeHours / 4) * 0.5;
    }

    public enum ClaimStatus {
        PENDING, APPROVED, REJECTED
    }
}
