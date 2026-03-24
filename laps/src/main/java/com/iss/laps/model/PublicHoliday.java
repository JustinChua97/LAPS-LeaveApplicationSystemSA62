package com.iss.laps.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Entity
@Table(name = "public_holidays")
@Getter @Setter @NoArgsConstructor
public class PublicHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private LocalDate holidayDate;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private int year;

    public PublicHoliday(LocalDate holidayDate, String description) {
        this.holidayDate = holidayDate;
        this.description = description;
        this.year = holidayDate.getYear();
    }
}
