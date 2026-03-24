package com.iss.laps.repository;

import com.iss.laps.model.Employee;
import com.iss.laps.model.LeaveEntitlement;
import com.iss.laps.model.LeaveType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LeaveEntitlementRepository extends JpaRepository<LeaveEntitlement, Long> {

    Optional<LeaveEntitlement> findByEmployeeAndLeaveTypeAndYear(
            Employee employee, LeaveType leaveType, int year);

    List<LeaveEntitlement> findByEmployeeAndYear(Employee employee, int year);

    List<LeaveEntitlement> findByYear(int year);
}
