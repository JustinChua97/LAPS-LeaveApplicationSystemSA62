package com.iss.laps.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import com.iss.laps.model.LeaveTypeDefaults;

@Entity
@Table(name = "leave_types")
@Getter @Setter @NoArgsConstructor
public class LeaveType {

    //Insert enum from LeaveTypeDefaults.java file
    @Enumerated(EnumType.STRING) 
        //(EnumType.STRING) tells database to store the literal text instead of a numeric index.
    @Column(name = "DefaultLeaveType", unique = true, updatable = false, nullable = false)
    private LeaveTypeDefaults defaultLeaveType;

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

    //add Enum to constructor
    public LeaveType(String name, String description, int maxDaysPerYear, boolean halfDayAllowed, LeaveTypeDefaults defaultLeaveType) {
        this.name = name;
        this.description = description;
        this.maxDaysPerYear = maxDaysPerYear;
        this.halfDayAllowed = halfDayAllowed;
        this.defaultLeaveType = defaultLeaveType;
    }
}
