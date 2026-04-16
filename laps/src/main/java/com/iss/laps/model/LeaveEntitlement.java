package com.iss.laps.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "leave_entitlements",
       uniqueConstraints = @UniqueConstraint(columnNames = {"employee_id", "leave_type_id", "year"}))
@Getter @Setter @NoArgsConstructor
public class LeaveEntitlement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(nullable = false)
    private int year;

    @Column(nullable = false)
    private double totalDays;

    @Column(nullable = false)
    private double usedDays;

    public double getRemainingDays() {
        return totalDays - usedDays;
    }

    public LeaveEntitlement(Employee employee, LeaveType leaveType, int year, double totalDays) {
        this.employee = employee;
        this.leaveType = leaveType;
        this.year = year;
        this.totalDays = totalDays;
        this.usedDays = 0;
    }
}
