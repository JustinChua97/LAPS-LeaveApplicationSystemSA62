package com.iss.laps.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "leave_types")
@Getter @Setter @NoArgsConstructor
public class LeaveType {

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

    public LeaveType(String name, String description, int maxDaysPerYear, boolean halfDayAllowed) {
        this.name = name;
        this.description = description;
        this.maxDaysPerYear = maxDaysPerYear;
        this.halfDayAllowed = halfDayAllowed;
    }
}
