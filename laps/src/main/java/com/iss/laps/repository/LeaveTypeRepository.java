package com.iss.laps.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.iss.laps.model.LeaveType;
import com.iss.laps.model.LeaveTypeDefault;

@Repository
public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

    Optional<LeaveType> findByNameIgnoreCase(String name);

    List<LeaveType> findByActive(boolean active);

    Optional<LeaveType> findByDefaultType(LeaveTypeDefault defaultType);

}