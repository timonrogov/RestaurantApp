package com.example.restaurant.controllers;

import com.example.restaurant.models.Client;
import com.example.restaurant.services.ClientService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.security.Principal;

@Controller
@RequestMapping("/account")
public class AccountWebController {

    private final ClientService clientService;

    @Autowired
    public AccountWebController(ClientService clientService) {
        this.clientService = clientService;
    }


    @GetMapping
    public String viewAccount(Model model, Principal principal) {

        if (principal == null) {
            return "redirect:/login";
        }

        Client client = clientService.getClientByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Аккаунт не найден"));

        model.addAttribute("client", client);
        return "account";
    }

    @PostMapping("/logout")
    public String logout() {
        // Spring Security автоматически обрабатывает выход
        return "redirect:/login";
    }
}
