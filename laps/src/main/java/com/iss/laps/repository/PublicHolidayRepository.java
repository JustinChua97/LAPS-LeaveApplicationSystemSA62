package com.iss.laps.repository;

import com.iss.laps.model.PublicHoliday;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface PublicHolidayRepository extends JpaRepository<PublicHoliday, Long> {

    List<PublicHoliday> findByYear(int year);

    Optional<PublicHoliday> findByHolidayDate(LocalDate date);

    boolean existsByHolidayDate(LocalDate date);
}
