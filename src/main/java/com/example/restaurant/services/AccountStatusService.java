package com.example.restaurant.services;

import com.example.restaurant.models.AccountStatus;
import com.example.restaurant.repositories.AccountStatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class AccountStatusService {
    private final AccountStatusRepository accountStatusRepository;

    @Autowired
    public AccountStatusService(AccountStatusRepository accountStatusRepository) {
        this.accountStatusRepository = accountStatusRepository;
    }

    public List<AccountStatus> getAllAccountStatuses() {
        return accountStatusRepository.findAll();
    }

    public AccountStatus createAccountStatus(AccountStatus accountStatus) {
        return accountStatusRepository.save(accountStatus);
    }

    public void deleteAccountStatus(Long id) {
        accountStatusRepository.deleteById(id);
    }
}