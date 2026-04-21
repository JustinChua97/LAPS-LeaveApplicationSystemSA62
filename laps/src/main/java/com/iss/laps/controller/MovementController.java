package com.iss.laps.controller;

import com.iss.laps.model.LeaveApplication;
import com.iss.laps.service.LeaveService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDate;
import java.util.List;

@Controller
@RequestMapping("/movement")
@RequiredArgsConstructor
public class MovementController {

    private final LeaveService leaveService;

    @GetMapping
    public String movementRegister(@RequestParam(required = false) Integer year,
                                    @RequestParam(required = false) Integer month,
                                    Model model,
                                    RedirectAttributes redirectAttributes) {
        LocalDate now = LocalDate.now();
        int selectedYear = now.getYear();
        int selectedMonth = now.getMonthValue();

        // Add default attributes first
        model.addAttribute("monthNames", new String[]{
                "", "January", "February", "March", "April", "May", "June",
                "July", "August", "September", "October", "November", "December"
        });

        if (year != null) {
        if (year < 2020 || year > 2035) {
                model.addAttribute("error", "Year must be between 2020 and 2035");
                model.addAttribute("selectedYear", selectedYear);
                model.addAttribute("selectedMonth", selectedMonth);
                model.addAttribute("leaves", List.of());
                redirectAttributes.addFlashAttribute("error", "Year must be between 2020 and 2035");
                return "redirect:/movement?year=" + selectedYear + "&month=" + selectedMonth;
        }
        selectedYear = year;
        }

        if (month != null) {
        if (month < 1 || month > 12) {
                model.addAttribute("error", "Month must be between 1 and 12");
                model.addAttribute("selectedYear", selectedYear);
                model.addAttribute("selectedMonth", selectedMonth);
                model.addAttribute("leaves", List.of());
                redirectAttributes.addFlashAttribute("error", "Month must be between 1 and 12");
                return "redirect:/movement?year=" + selectedYear + "&month=" + selectedMonth;
        }
        selectedMonth = month;
        }

        List<LeaveApplication> leaves = leaveService.getApprovedLeaveInMonth(selectedYear, selectedMonth);

        model.addAttribute("leaves", leaves);
        model.addAttribute("selectedYear", selectedYear);
        model.addAttribute("selectedMonth", selectedMonth);
        model.addAttribute("months", List.of(
                new int[]{1, 0}, new int[]{2, 0}, new int[]{3, 0}, new int[]{4, 0},
                new int[]{5, 0}, new int[]{6, 0}, new int[]{7, 0}, new int[]{8, 0},
                new int[]{9, 0}, new int[]{10, 0}, new int[]{11, 0}, new int[]{12, 0}
        ));

        return "common/movement-register";
    }
}
