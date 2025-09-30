package com.example.restaurant.services;

import com.example.restaurant.models.Account;
import com.example.restaurant.models.AccountStatus;
import com.example.restaurant.models.AccountType;
import com.example.restaurant.repositories.AccountRepository;
import com.example.restaurant.repositories.AccountStatusRepository;
import com.example.restaurant.repositories.AccountTypeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class AccountService {
    private final AccountRepository accountRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private AccountTypeRepository accountTypeRepository; // Добавьте репозиторий

    @Autowired
    private AccountStatusRepository accountStatusRepository; // Добавьте репозиторий

    @Autowired
    public AccountService(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    public Optional<Account> getAccountByUsername(String username) {
        return accountRepository.findByUsername(username);
    }

    public Account createAccount(Account account) {
        account.setPassword(passwordEncoder.encode(account.getPassword()));
        return accountRepository.save(account);
    }

    public void deleteAccount(Long id) {
        accountRepository.deleteById(id);
    }

    // Получение типа аккаунта по ID
    public AccountType getAccountTypeById(Long id) {
        return accountTypeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account type not found"));
    }

    // Получение статуса аккаунта по ID
    public AccountStatus getAccountStatusById(Long id) {
        return accountStatusRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Account status not found"));
    }

    boolean existsByUsername(String username){
        return accountRepository.existsByUsername(username);
    }
}