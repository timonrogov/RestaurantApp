package com.example.restaurant.services;

import com.example.restaurant.enums.Role;
import com.example.restaurant.models.Account;
import com.example.restaurant.repositories.AccountRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class CustomerUserDetailsServiceImpl implements UserDetailsService {

    private final AccountRepository accountRepository;

    @Autowired
    public CustomerUserDetailsServiceImpl(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден: " + username));

        // КЛЮЧЕВАЯ ПРОВЕРКА: Если тип аккаунта - НЕ "КЛИЕНТ", выбрасываем исключение.
        // Для клиентского приложения сотрудник - это такой же "ненайденный" пользователь.
        if (!Role.CLIENT.equals(account.getRole())) {
            throw new UsernameNotFoundException("Доступ для данной роли запрещен: " + username);
        }

        return account;
    }
}
