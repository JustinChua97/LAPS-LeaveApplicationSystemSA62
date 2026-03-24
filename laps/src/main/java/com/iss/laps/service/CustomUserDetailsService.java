package com.iss.laps.service;

import com.iss.laps.model.Employee;
import com.iss.laps.repository.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final EmployeeRepository employeeRepository;

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Employee employee = employeeRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found: " + username));

        if (!employee.isActive()) {
            throw new UsernameNotFoundException("User account is inactive: " + username);
        }

        return User.builder()
                .username(employee.getUsername())
                .password(employee.getPassword())
                .authorities(List.of(new SimpleGrantedAuthority(employee.getRole().name())))
                .build();
    }
}
