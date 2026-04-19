package com.iss.laps;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;

import com.iss.laps.exception.LeaveApplicationException;
import com.iss.laps.model.LeaveType;
import com.iss.laps.model.LeaveTypeDefault;
import com.iss.laps.repository.LeaveTypeRepository;
import com.iss.laps.repository.PublicHolidayRepository;
import com.iss.laps.service.AdminService;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminService Unit Tests")
class AdminServiceTest {

    @Mock
    private LeaveTypeRepository leaveTypeRepo;

    @Mock
    private PublicHolidayRepository publicHolidayRepo;

    @InjectMocks
    private AdminService adminService;

    @Test
    @DisplayName("Default leave types can be activated/deactivated")
    void defaultLeaveType_allowsActivationChanges() {
        LeaveType existing = new LeaveType();
        existing.setId(1L);
        existing.setName("Medical");
        existing.setDefaultType(LeaveTypeDefault.MEDICAL);
        existing.setMaxDaysPerYear(14);
        existing.setHalfDayAllowed(false);
        existing.setActive(true);

        LeaveType updated = new LeaveType();
        updated.setId(1L);
        updated.setName("Medical");
        updated.setMaxDaysPerYear(14);
        updated.setHalfDayAllowed(true);
        updated.setActive(false);

        when(leaveTypeRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(leaveTypeRepo.save(updated)).thenReturn(updated);

        LeaveType result = adminService.saveLeaveType(updated);

        assertThat(result.isHalfDayAllowed()).isTrue();
        assertThat(result.isActive()).isFalse();
        verify(leaveTypeRepo).save(updated);
    }

    @Test
    @DisplayName("Default leave types allows changes to half-day policy")
    void defaultLeaveType_allowsHalfDay() {
        LeaveType existing = new LeaveType();
        existing.setId(1L);
        existing.setName("Medical");
        existing.setDefaultType(LeaveTypeDefault.MEDICAL);
        existing.setMaxDaysPerYear(14);
        existing.setHalfDayAllowed(false);
        existing.setActive(true);

        LeaveType updated = new LeaveType();
        updated.setId(1L);
        updated.setName("Medical");
        updated.setMaxDaysPerYear(14);
        updated.setHalfDayAllowed(true);
        updated.setActive(true);

        when(leaveTypeRepo.findById(1L)).thenReturn(Optional.of(existing));
        when(leaveTypeRepo.save(updated)).thenReturn(updated);

        LeaveType result = adminService.saveLeaveType(updated);

        assertThat(result.isHalfDayAllowed()).isTrue();
        assertThat(result.isActive()).isTrue();
        verify(leaveTypeRepo).save(updated);
    }

    @Test
    @DisplayName("Default leave types reject max days changes")
    void defaultLeaveType_rejectsMaxDaysChange() {
        LeaveType existing = new LeaveType();
        existing.setId(2L);
        existing.setName("Hospitalisation");
        existing.setDefaultType(LeaveTypeDefault.HOSPITALISATION);
        existing.setMaxDaysPerYear(46);
        existing.setHalfDayAllowed(false);
        existing.setActive(true);

        LeaveType updated = new LeaveType();
        updated.setId(2L);
        updated.setName("Hospitalisation");
        updated.setMaxDaysPerYear(50);
        updated.setHalfDayAllowed(false);
        updated.setActive(true);

        when(leaveTypeRepo.findById(2L)).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> adminService.saveLeaveType(updated))
                .isInstanceOf(LeaveApplicationException.class)
                .hasMessageContaining("Cannot modify the maximum number of days for a default leave type");
    }

    @Test
    @DisplayName("Custom leave types allows all leave type policy changes")
    void customeLeaveType_allowsAllPolicyChanges() {
        LeaveType existing = new LeaveType();
        existing.setId(3L);
        existing.setName("Family Care Leave");
        existing.setDefaultType(null);
        existing.setMaxDaysPerYear(5);
        existing.setHalfDayAllowed(false);
        existing.setActive(false);

        LeaveType updated = new LeaveType();
        updated.setId(3L);
        updated.setName("Family Care Leave");
        updated.setMaxDaysPerYear(8);
        updated.setHalfDayAllowed(true);
        updated.setActive(true);

        when(leaveTypeRepo.findById(3L)).thenReturn(Optional.of(existing));
        when(leaveTypeRepo.save(updated)).thenReturn(updated);

        LeaveType result = adminService.saveLeaveType(updated);

        assertThat(result.getMaxDaysPerYear()).isEqualTo(8);
        assertThat(result.isHalfDayAllowed()).isTrue();
        assertThat(result.isActive()).isTrue();
        verify(leaveTypeRepo).save(updated);
    }
}
