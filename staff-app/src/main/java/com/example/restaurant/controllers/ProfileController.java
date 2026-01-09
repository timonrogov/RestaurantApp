package com.example.restaurant.controllers;

import com.example.restaurant.models.Employee;
import com.example.restaurant.services.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDate;

@Controller
@RequestMapping("/profile")
@PreAuthorize("isAuthenticated()") // Доступ для всех авторизованных сотрудников
public class ProfileController {

    private final EmployeeService employeeService;

    @Autowired
    public ProfileController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public String viewProfile(Model model, Principal principal) {
        // Получаем сотрудника по логину текущего пользователя
        Employee employee = employeeService.getEmployeeByUsername(principal.getName());
        model.addAttribute("employee", employee);
        return "profile";
    }

    @PostMapping("/update")
    public String updateProfile(
            @RequestParam Long id,
            @RequestParam String username,
            @RequestParam String fullName,
            @RequestParam String phone,
            @RequestParam String email,
            @RequestParam String gender,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateOfBirth,
            RedirectAttributes redirectAttributes) {

        try {
            employeeService.updateProfile(id, username, fullName, phone, email, gender, dateOfBirth);
            redirectAttributes.addFlashAttribute("success", "Профиль успешно обновлен. Если вы изменили логин, пожалуйста, перезайдите.");
        } catch (RuntimeException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
        }

        return "redirect:/profile";
    }
}