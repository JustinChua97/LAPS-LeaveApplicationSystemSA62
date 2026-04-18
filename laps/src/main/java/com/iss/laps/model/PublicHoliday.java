package com.iss.laps.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "public_holidays")
@Getter 
@Setter 
@NoArgsConstructor
public class PublicHoliday {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotNull(message = "Holiday date is required")
    @Column(nullable = false, unique = true)
    private LocalDate holidayDate;

    @NotBlank(message = "Description is required")
    @Size(max = 100, message = "Description must not exceed 100 characters")
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
