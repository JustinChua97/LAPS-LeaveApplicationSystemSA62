package com.iss.laps.repository;

import com.iss.laps.model.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveTypeRepository extends JpaRepository<LeaveType, Long> {

    Optional<LeaveType> findByNameIgnoreCase(String name);

    List<LeaveType> findByActive(boolean active);
}
