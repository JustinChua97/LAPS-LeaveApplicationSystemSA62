package com.iss.laps.service;

import com.iss.laps.exception.ResourceNotFoundException;
import com.iss.laps.model.LeaveType;
import com.iss.laps.model.PublicHoliday;
import com.iss.laps.repository.LeaveTypeRepository;
import com.iss.laps.repository.PublicHolidayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

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
        return leaveTypeRepo.save(leaveType);
    }

    @Transactional
    public void deleteLeaveType(Long id) {
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
