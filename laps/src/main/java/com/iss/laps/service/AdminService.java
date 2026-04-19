package com.iss.laps.service;
import com.iss.laps.dto.HolidaySyncResult;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iss.laps.exception.LeaveApplicationException;
import com.iss.laps.exception.ResourceNotFoundException;
import com.iss.laps.model.Employee;
import com.iss.laps.model.LeaveEntitlement;
import com.iss.laps.model.LeaveType;
import com.iss.laps.model.PublicHoliday;
import com.iss.laps.model.Role;
import com.iss.laps.repository.EmployeeRepository;
import com.iss.laps.repository.LeaveEntitlementRepository;
import com.iss.laps.repository.LeaveTypeRepository;
import com.iss.laps.repository.PublicHolidayRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final EmployeeRepository employeeRepository;
    private final LeaveEntitlementRepository leaveEntitlementRepository;
    private final LeaveTypeRepository leaveTypeRepo;
    private final PublicHolidayRepository publicHolidayRepo;
    private final PublicHolidayApiService publicHolidayApiService;
    // =========== LEAVE TYPES ===========

    public List<LeaveType> getAllLeaveTypes() {
        return leaveTypeRepo.findAll();
    }

    public LeaveType findLeaveTypeById(Long id) {
        return leaveTypeRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Leave type not found"));
    }

    @Transactional
    public LeaveType saveLeaveType(LeaveType leaveType) {
        if (leaveType.getId() != null) {
            LeaveType existing = findLeaveTypeById(leaveType.getId());
            leaveType.setDefaultType(existing.getDefaultType());
            // Default leave types can change active/half-day policy, but not max days.
            if (existing.getDefaultType() != null) {
                boolean policyChanged = leaveType.getMaxDaysPerYear() != existing.getMaxDaysPerYear();
                if (policyChanged) {
                    throw new LeaveApplicationException("Cannot modify the maximum number of days for a default leave type. Please approach the system administrator.");
                }
            }
        }
        return leaveTypeRepo.save(leaveType);
    }

    @Transactional
    public void deleteLeaveType(Long id) {
        LeaveType leaveType = findLeaveTypeById(id);
        
        if (leaveType.isActive()) {
            throw new LeaveApplicationException("Cannot delete an active leave type. Please deactivate it first.");
        }
        
        if (leaveType.isDefault()) {
            throw new LeaveApplicationException("This leave type cannot be deleted.");
        }
        
        // Delete all entitlements for this leave type first (to avoid foreign key constraint violation)
        leaveEntitlementRepository.deleteByLeaveType(leaveType);
        
        leaveTypeRepo.deleteById(id);
    }

    @Transactional
    public void createLeaveType(LeaveType leaveType) {
    leaveTypeRepo.save(leaveType);
    
    // Auto-create entitlements for all existing non-admin employees
    int currentYear = LocalDate.now().getYear();
    List<Employee> allEmployees = employeeRepository.findAll();
    
    for (Employee emp : allEmployees) {
        // Skip ROLE_ADMIN and employees without designation
        if (emp.getRole() == Role.ROLE_ADMIN || emp.getDesignation() == null) {
            continue;
        }
        
        // Calculate entitled days based on leave type
        double days = 0.0;
        if (leaveType.getDefaultType() != null) {
            switch (leaveType.getDefaultType()) {
                case ANNUAL:
                    days = emp.getDesignation().getAnnualLeaveEntitlement();
                    break;
                case MEDICAL:
                    days = 14;
                    break;
                case HOSPITALISATION:
                    days = 46;
                    break;
                case COMPENSATION:
                    days = 0;
                    break;
                default:
                    break;
            }
        }
        
        // Create entitlement
        LeaveEntitlement ent = new LeaveEntitlement(emp, leaveType, currentYear, days);
        leaveEntitlementRepository.save(ent);
    }
    }

    // =========== PUBLIC HOLIDAYS ===========

    public List<PublicHoliday> getHolidaysByYear(int year) {
        return publicHolidayRepo.findByYear(year);
    }

    public List<PublicHoliday> getAllHolidays() {
        return publicHolidayRepo.findAll();
    }

    @Transactional
    public PublicHoliday saveHoliday(PublicHoliday holiday) {
        return publicHolidayRepo.save(holiday);
    }

    @Transactional
    public void deleteHoliday(Long id) {
        publicHolidayRepo.deleteById(id);
    }

    public PublicHoliday findHolidayById(Long id) {
        return publicHolidayRepo.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Holiday not found"));
    }

    public boolean isHolidayDateTaken(LocalDate date) {
        return publicHolidayRepo.existsByHolidayDate(date);
    }
    @Transactional
    public HolidaySyncResult syncHolidaysFromCsv(int year) {
        if (year < 2020 || year > 2099) {
            throw new IllegalArgumentException("Year must be between 2020 and 2099");
        }

        String apiUrl = "https://data.gov.sg/api/action/datastore_search?resource_id=d_149b61ad0a22f61c09dc80f2df5bbec8&limit=50";
        List<PublicHoliday> fetched = publicHolidayApiService.fetchHolidaysFromApi(apiUrl);

        int added = 0, skipped = 0;
        for (PublicHoliday h : fetched) {
            if (h.getYear() != year) continue;
            if (publicHolidayRepo.existsByHolidayDate(h.getHolidayDate())) {
                skipped++;
            } else {
                publicHolidayRepo.save(h);
                added++;
            }
        }
        return new HolidaySyncResult(added, skipped);
    }
}
