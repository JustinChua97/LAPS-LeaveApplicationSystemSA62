package com.iss.laps;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.support.RedirectAttributesModelMap;

import com.iss.laps.controller.AdminController;
import com.iss.laps.exception.LeaveApplicationException;
import com.iss.laps.model.LeaveType;
import com.iss.laps.service.AdminService;
import com.iss.laps.service.EmployeeService;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminController Unit Tests")
class AdminControllerTest {

    @Mock
    private AdminService adminService;

    @InjectMocks
    private AdminController adminController;

    @Mock
    private EmployeeService employeeService;

    @Test
    @DisplayName("Edit leave type submission surfaces validation errors as flash messages")
    void updateLeaveType_catchesServiceErrorAndRedirectsWithError() {
        LeaveType submitted = new LeaveType();
        submitted.setName("Medical");
        submitted.setMaxDaysPerYear(20);
        submitted.setHalfDayAllowed(true);
        submitted.setActive(true);

        when(adminService.saveLeaveType(submitted))
                .thenThrow(new com.iss.laps.exception.LeaveApplicationException(
                        "Cannot modify the maximum number of days for a default leave type"));

        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        String viewName = adminController.updateLeaveType(1L, submitted, redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/leave-types");
        assertThat(redirectAttributes.getFlashAttributes().get("error"))
            .isEqualTo("Cannot modify the maximum number of days for a default leave type");
        assertThat(submitted.getId()).isEqualTo(1L);
        verify(adminService).saveLeaveType(submitted);
    }

    @Test
    @DisplayName("Edit leave type submission allows rename while preserving id and policy values")
    void renameDefaultLeaveType() {
        LeaveType submitted = new LeaveType();
        submitted.setId(12L);
        submitted.setName("Outpatient Sick Leave");
        submitted.setMaxDaysPerYear(14);
        submitted.setHalfDayAllowed(true);
        submitted.setActive(false);

        LeaveType saved = new LeaveType();
        saved.setId(12L);
        saved.setName("Medical Leave");
        saved.setMaxDaysPerYear(14);
        saved.setHalfDayAllowed(true);
        saved.setActive(false);

        when(adminService.saveLeaveType(submitted)).thenReturn(saved);

        RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
        String viewName = adminController.updateLeaveType(12L, submitted, redirectAttributes);

        assertThat(viewName).isEqualTo("redirect:/admin/leave-types");
        assertThat(redirectAttributes.getFlashAttributes().get("success"))
            .isEqualTo("Leave type updated.");
        assertThat(submitted.getId()).isEqualTo(12L);
        assertThat(submitted.getName()).isEqualTo("Outpatient Sick Leave");
        assertThat(submitted.getMaxDaysPerYear()).isEqualTo(14);
        assertThat(submitted.isHalfDayAllowed()).isTrue();
        assertThat(submitted.isActive()).isFalse();
        verify(adminService).saveLeaveType(submitted);
    }

    @Test
    void updateEntitlement_catchesServiceErrorAndRedirectsWithError() {
    doThrow(new LeaveApplicationException("Total entitlement cannot exceed 365 days."))
        .when(employeeService)
        .updateEntitlement(101L, 20);

    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
    String viewName = adminController.updateEntitlement(101L, 20, 2L, redirectAttributes);

    assertThat(viewName).isEqualTo("redirect:/admin/employees/2/entitlements");
    assertThat(redirectAttributes.getFlashAttributes().get("error"))
        .isEqualTo("Total entitlement cannot exceed 365 days.");
    }

    @Test
    void updateEntitlement_successRedirectsWithSuccessFlash() {
    RedirectAttributesModelMap redirectAttributes = new RedirectAttributesModelMap();
    String viewName = adminController.updateEntitlement(101L, 20, 2L, redirectAttributes);

    assertThat(viewName).isEqualTo("redirect:/admin/employees/2/entitlements");
    assertThat(redirectAttributes.getFlashAttributes().get("success"))
        .isEqualTo("Entitlement updated.");
    verify(employeeService).updateEntitlement(101L, 20);
    }


}