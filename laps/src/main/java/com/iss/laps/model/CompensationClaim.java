package com.iss.laps.model;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
    @Min(1)
    @Max(4)
    private int overtimeHours; // 1–4 hours per day; every 4h = 0.5 comp day (closes #19)

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
        // Calculate compensation days proportionally (floating-point: 1h=0.125, 4h=0.5)
        this.compensationDays = (overtimeHours / 4.0) * 0.5;
    }

    public enum ClaimStatus {
        PENDING, APPROVED, REJECTED
    }
}
