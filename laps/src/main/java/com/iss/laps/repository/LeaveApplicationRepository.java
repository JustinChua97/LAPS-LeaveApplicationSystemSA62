package com.iss.laps.repository;

import com.iss.laps.model.Employee;
import com.iss.laps.model.LeaveApplication;
import com.iss.laps.model.LeaveStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LeaveApplicationRepository extends JpaRepository<LeaveApplication, Long> {

    // Employee's own leave history for current year
    @Query("SELECT la FROM LeaveApplication la WHERE la.employee = :employee " +
           "AND YEAR(la.startDate) = :year AND la.status != 'DELETED' ORDER BY la.appliedDate DESC")
    List<LeaveApplication> findByEmployeeAndYear(@Param("employee") Employee employee,
                                                  @Param("year") int year);

    // All leave for an employee (pageable)
    Page<LeaveApplication> findByEmployeeAndStatusNotOrderByAppliedDateDesc(
            Employee employee, LeaveStatus status, Pageable pageable);

    // Pending applications for manager's subordinates
    @Query("SELECT la FROM LeaveApplication la WHERE la.employee.manager = :manager " +
           "AND la.status IN ('APPLIED', 'UPDATED') ORDER BY la.appliedDate DESC")
    List<LeaveApplication> findPendingByManager(@Param("manager") Employee manager);

    // All subordinate leave history
    @Query("SELECT la FROM LeaveApplication la WHERE la.employee.manager = :manager " +
           "AND YEAR(la.startDate) = :year AND la.status != 'DELETED' ORDER BY la.appliedDate DESC")
    List<LeaveApplication> findByManagerAndYear(@Param("manager") Employee manager,
                                                 @Param("year") int year);

    // Pageable subordinate history
    @Query("SELECT la FROM LeaveApplication la WHERE la.employee.manager = :manager " +
           "AND la.status != 'DELETED' ORDER BY la.appliedDate DESC")
    Page<LeaveApplication> findByManagerPageable(@Param("manager") Employee manager, Pageable pageable);

    // Leave during a period (for conflict checking and movement register)
    @Query("SELECT la FROM LeaveApplication la WHERE la.status = 'APPROVED' " +
           "AND la.startDate <= :endDate AND la.endDate >= :startDate")
    List<LeaveApplication> findApprovedLeaveDuringPeriod(@Param("startDate") LocalDate startDate,
                                                          @Param("endDate") LocalDate endDate);

    // Subordinate leave during a period (for manager approval helper)
    @Query("SELECT la FROM LeaveApplication la WHERE la.employee.manager = :manager " +
           "AND la.status = 'APPROVED' " +
           "AND la.startDate <= :endDate AND la.endDate >= :startDate")
    List<LeaveApplication> findSubordinateLeaveDuringPeriod(@Param("manager") Employee manager,
                                                             @Param("startDate") LocalDate startDate,
                                                             @Param("endDate") LocalDate endDate);

    // Count used leave days for entitlement check
    @Query("SELECT COALESCE(SUM(la.duration), 0) FROM LeaveApplication la " +
           "WHERE la.employee = :employee AND la.leaveType.id = :leaveTypeId " +
           "AND YEAR(la.startDate) = :year AND la.status IN ('APPLIED', 'UPDATED', 'APPROVED')")
    double sumUsedDaysByEmployeeAndLeaveTypeAndYear(@Param("employee") Employee employee,
                                                    @Param("leaveTypeId") Long leaveTypeId,
                                                    @Param("year") int year);

    // Reporting: all approved leave in a date range
    @Query("SELECT la FROM LeaveApplication la WHERE la.status = 'APPROVED' " +
           "AND la.startDate >= :startDate AND la.endDate <= :endDate " +
           "ORDER BY la.employee.name, la.startDate")
    List<LeaveApplication> findApprovedLeaveInRange(@Param("startDate") LocalDate startDate,
                                                     @Param("endDate") LocalDate endDate);

    // Reporting: by leave type
    @Query("SELECT la FROM LeaveApplication la WHERE la.status = 'APPROVED' " +
           "AND la.leaveType.id = :leaveTypeId " +
           "AND la.startDate >= :startDate AND la.endDate <= :endDate " +
           "ORDER BY la.employee.name, la.startDate")
    List<LeaveApplication> findApprovedLeaveByTypeAndRange(@Param("leaveTypeId") Long leaveTypeId,
                                                            @Param("startDate") LocalDate startDate,
                                                            @Param("endDate") LocalDate endDate);
}
