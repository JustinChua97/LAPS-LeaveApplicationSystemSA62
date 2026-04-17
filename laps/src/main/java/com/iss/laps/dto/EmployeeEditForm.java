package com.iss.laps.dto;

import com.iss.laps.model.Designation;
import com.iss.laps.model.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class EmployeeEditForm {

    @NotBlank
    private String name;

    @Email
    @NotBlank
    private String email;

    private Role role;

    private Designation designation; // nullable — ROLE_ADMIN accounts have no designation

    private boolean active;
}