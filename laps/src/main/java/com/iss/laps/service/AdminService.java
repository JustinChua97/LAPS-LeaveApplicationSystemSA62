package com.iss.laps.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.iss.laps.exception.LeaveApplicationException;
import com.iss.laps.exception.ResourceNotFoundException;
import com.iss.laps.model.LeaveType;
import com.iss.laps.model.PublicHoliday;
import com.iss.laps.repository.LeaveTypeRepository;
import com.iss.laps.repository.PublicHolidayRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final LeaveTypeRepository leaveTypeRepo;
    private final PublicHolidayRepository publicHolidayRepo;

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
        LeaveType existingLeaveType = leaveTypeRepo.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("Leave type not found"));
            
        if (existingLeaveType.getDefaultType() != null) {
            throw new LeaveApplicationException("Cannot delete a default leave type");
        }

        leaveTypeRepo.deleteById(id);
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
}
