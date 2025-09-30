package com.example.restaurant.controllers.api;

import com.example.restaurant.models.AccountStatus;
import com.example.restaurant.services.AccountStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/account-statuses")
public class AccountStatusController {
    private final AccountStatusService accountStatusService;

    @Autowired
    public AccountStatusController(AccountStatusService accountStatusService) {
        this.accountStatusService = accountStatusService;
    }

    @GetMapping
    public List<AccountStatus> getAllAccountStatuses() {
        return accountStatusService.getAllAccountStatuses();
    }

    @PostMapping
    public AccountStatus createAccountStatus(@RequestBody AccountStatus accountStatus) {
        return accountStatusService.createAccountStatus(accountStatus);
    }

    @DeleteMapping("/{id}")
    public void deleteAccountStatus(@PathVariable Long id) {
        accountStatusService.deleteAccountStatus(id);
    }
}