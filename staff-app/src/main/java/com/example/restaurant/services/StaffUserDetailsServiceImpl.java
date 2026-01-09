package com.example.restaurant.services;

import com.example.restaurant.enums.Role;
import com.example.restaurant.models.Account;
import com.example.restaurant.repositories.AccountRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class StaffUserDetailsServiceImpl implements UserDetailsService {

    private final AccountRepository accountRepository;

    public StaffUserDetailsServiceImpl(AccountRepository accountRepository) {
        this.accountRepository = accountRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        Account account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new UsernameNotFoundException("Пользователь не найден: " + username));

        // ИСПРАВЛЕНИЕ:
        // Раньше мы проверяли на конкретную роль "СОТРУДНИК".
        // Теперь мы просто проверяем, что это НЕ "КЛИЕНТ".
        // Если это CLIENT, то ему запрещено входить в приложение для персонала.
        if (account.getRole() == Role.CLIENT) {
            throw new UsernameNotFoundException("Доступ запрещен: клиенты не имеют доступа к панели сотрудников.");
        }

        return account;
    }
}
