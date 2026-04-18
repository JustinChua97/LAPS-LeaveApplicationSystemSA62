package com.iss.laps.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "leave_types")
@Getter @Setter @NoArgsConstructor
public class LeaveType {

    @Enumerated(EnumType.STRING) 
    @Column(name = "default_type", unique = true, updatable = false, nullable = true)
    private LeaveTypeDefault defaultType;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank
    @Column(unique = true, nullable = false)
    private String name;

    @Column
    private String description;

    @Column(nullable = false)
    private int maxDaysPerYear;

    @Column(nullable = false)
    private boolean halfDayAllowed;

    @Column(nullable = false)
    private boolean active = true;

        public void defaultLeaveType(String name, String description, int maxDaysPerYear, boolean halfDayAllowed, LeaveTypeDefault defaultType) {
        this.name = name;
        this.description = description;
        this.maxDaysPerYear = maxDaysPerYear;
        this.halfDayAllowed = halfDayAllowed;
        this.defaultType = defaultType;
    }

    public boolean isDefault() {
    return this.defaultType != null;
    }
}
