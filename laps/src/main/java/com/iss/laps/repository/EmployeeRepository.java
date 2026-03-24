package com.iss.laps.repository;

import com.iss.laps.model.Employee;
import com.iss.laps.model.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    Optional<Employee> findByUsername(String username);

    boolean existsByUsername(String username);

    List<Employee> findByManager(Employee manager);

    List<Employee> findByManagerId(Long managerId);

    List<Employee> findByRole(Role role);

    List<Employee> findByActive(boolean active);

    @Query("SELECT e FROM Employee e WHERE e.active = true ORDER BY e.name")
    List<Employee> findAllActive();
}
