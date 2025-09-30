package com.example.restaurant.controllers.api;

import com.example.restaurant.models.AccountType;
import com.example.restaurant.services.AccountTypeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/account-types")
public class AccountTypeController {
    private final AccountTypeService accountTypeService;

    @Autowired
    public AccountTypeController(AccountTypeService accountTypeService) {
        this.accountTypeService = accountTypeService;
    }

    @GetMapping
    public List<AccountType> getAllAccountTypes() {
        return accountTypeService.getAllAccountTypes();
    }

    @PostMapping
    public AccountType createAccountType(@RequestBody AccountType accountType) {
        return accountTypeService.createAccountType(accountType);
    }

    @DeleteMapping("/{id}")
    public void deleteAccountType(@PathVariable Long id) {
        accountTypeService.deleteAccountType(id);
    }
}