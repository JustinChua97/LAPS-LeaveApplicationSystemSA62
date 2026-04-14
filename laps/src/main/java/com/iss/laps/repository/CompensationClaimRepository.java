package com.iss.laps.repository;

import com.iss.laps.model.CompensationClaim;
import com.iss.laps.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface CompensationClaimRepository extends JpaRepository<CompensationClaim, Long> {

    List<CompensationClaim> findByEmployeeOrderByClaimedDateDesc(Employee employee);

    @Query("SELECT cc FROM CompensationClaim cc WHERE cc.employee.manager = :manager " +
           "AND cc.status = 'PENDING' ORDER BY cc.claimedDate DESC")
    List<CompensationClaim> findPendingByManager(@Param("manager") Employee manager);

    @Query("SELECT cc FROM CompensationClaim cc WHERE cc.employee.manager = :manager " +
           "ORDER BY cc.claimedDate DESC")
    List<CompensationClaim> findAllByManager(@Param("manager") Employee manager);

    @Query("SELECT COALESCE(SUM(cc.compensationDays), 0) FROM CompensationClaim cc " +
           "WHERE cc.employee = :employee AND cc.status = 'APPROVED'")
    double sumApprovedCompDaysByEmployee(@Param("employee") Employee employee);

    /**
     * Sum overtime hours for an employee within a calendar month (by overtimeDate).
     * Excludes REJECTED claims — PENDING counts toward the cap to prevent double-claiming.
     * Used to enforce the MOM 72-hour monthly overtime limit (issue #19).
     */
    @Query("SELECT COALESCE(SUM(cc.overtimeHours), 0) FROM CompensationClaim cc " +
           "WHERE cc.employee = :employee " +
           "AND cc.overtimeDate >= :startOfMonth " +
           "AND cc.overtimeDate <= :endOfMonth " +
           "AND cc.status <> 'REJECTED'")
    int sumOvertimeHoursByEmployeeAndMonth(
            @Param("employee") Employee employee,
            @Param("startOfMonth") LocalDate startOfMonth,
            @Param("endOfMonth") LocalDate endOfMonth);
}
