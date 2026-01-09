package com.example.restaurant.controllers;

import com.example.restaurant.enums.Role;
import com.example.restaurant.models.Account;
import com.example.restaurant.models.Client;
import com.example.restaurant.services.AccountService;
import com.example.restaurant.services.ClientService;
import com.example.restaurant.services.EmployeeService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
public class AuthController {

    private final AccountService accountService;
    private final ClientService clientService;
    private final EmployeeService employeeService;

    @Autowired
    public AuthController(AccountService accountService,
                          ClientService clientService,
                          EmployeeService employeeService) {
        this.accountService = accountService;
        this.clientService = clientService;
        this.employeeService = employeeService;
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public String registerForm() {
        return "register";
    }


    @PostMapping("/register")
    @PreAuthorize("hasRole('ADMIN')")
    public String registerUser(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String fullName,
            @RequestParam String email,
            @RequestParam(required = false) String phone,
            @RequestParam String gender,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateOfBirth,
            @RequestParam Role role, // <--- Получаем роль из формы
            Model model) {

        try {
            // Передаем роль в сервис
            employeeService.registerEmployee(username, password, fullName, email, phone, gender, dateOfBirth, role);

            return "redirect:/admin/orders";

        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage());
            return "register";
        }
    }
}
