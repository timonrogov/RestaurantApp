package com.example.restaurant.controllers;

import com.example.restaurant.models.Account;
import com.example.restaurant.models.Client;
import com.example.restaurant.services.AccountService;
import com.example.restaurant.services.ClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;

@Controller
public class AuthController {
    @Autowired
    private AccountService accountService;

    @Autowired
    private ClientService clientService;

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/register")
    public String registerForm() {
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(
            @RequestParam String username,
            @RequestParam String password,
            @RequestParam String email,
            @RequestParam String fullName,
            @RequestParam(required = false) String phone,
            @RequestParam String gender,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateOfBirth,
            Model model) {

        try {
            Account account = new Account();
            account.setUsername(username);
            account.setPassword(password);
            account.setType(accountService.getAccountTypeById(1L));
            account.setStatus(accountService.getAccountStatusById(1L));

            Client client = new Client();
            client.setAccount(account);
            client.setEmail(email);
            client.setFullName(fullName);
            client.setPhone(phone);
            client.setGender(gender);
            client.setDateOfBirth(dateOfBirth);

            clientService.createClient(client);
            return "redirect:/login";
        } catch (RuntimeException e) {
            model.addAttribute("error", e.getMessage()); // Передаем ошибку в шаблон
            return "register"; // Возвращаемся на страницу регистрации
        }
    }
}
