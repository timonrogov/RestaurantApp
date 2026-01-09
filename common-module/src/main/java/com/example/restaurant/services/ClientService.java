package com.example.restaurant.services;

import com.example.restaurant.enums.AccountStatus;
import com.example.restaurant.enums.Role;
import com.example.restaurant.models.Account;
import com.example.restaurant.models.Client;
import com.example.restaurant.repositories.ClientRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Optional;

@Service
public class ClientService {
    private final ClientRepository clientRepository;
    private final AccountService accountService;

    @Autowired
    public ClientService(ClientRepository clientRepository,
                         AccountService accountService) {
        this.clientRepository = clientRepository;
        this.accountService = accountService;
    }

    public Optional<Client> getClientByEmail(String email) {
        return Optional.ofNullable(clientRepository.findByEmail(email));
    }

    @Transactional
    public Client createClient(Client client) {
        // Проверяем, существует ли клиент с таким email
        if (clientRepository.existsByEmail(client.getEmail())) {
            throw new RuntimeException("Email уже зарегистрирован");
        }

        // Проверка username
        Account account = client.getAccount();
        if (accountService.existsByUsername(account.getUsername())) {
            throw new RuntimeException("Пользователь с таким именем уже существует");
        }

        accountService.createAccount(account); // Сохраняем аккаунт
        client.setAccount(account);
        return clientRepository.save(client);
    }

    public void deleteClient(Long id) {
        clientRepository.deleteById(id);
    }

    public Optional<Client> getClientByUsername(String username) {
        return clientRepository.findByAccountUsername(username);
    }


    /**
     * Регистрирует нового клиента, создавая для него Аккаунт и профиль Клиента.
     */
    @Transactional
    public void registerClient(String username, String password, String email,
                               String fullName, String phone, String gender,
                               LocalDate dateOfBirth) {

        // 1. Проверки (Валидация бизнес-логики)
        if (clientRepository.existsByEmail(email)) {
            throw new RuntimeException("Email уже зарегистрирован");
        }
        if (accountService.existsByUsername(username)) {
            throw new RuntimeException("Пользователь с таким логином уже существует");
        }

        // 2. Создание Аккаунта
        Account account = new Account();
        account.setUsername(username);
        account.setPassword(password); // Хэширование произойдет внутри accountService.createAccount

        account.setRole(Role.CLIENT);
        account.setStatus(AccountStatus.ACTIVE);

        // Сохраняем аккаунт (здесь же будет хэширование пароля)
        Account savedAccount = accountService.createAccount(account);

        // 3. Создание Клиента
        Client client = new Client();
        client.setAccount(savedAccount);
        client.setEmail(email);
        client.setFullName(fullName);
        client.setPhone(phone);
        client.setGender(gender);
        client.setDateOfBirth(dateOfBirth);

        // 4. Сохранение Клиента
        clientRepository.save(client);
    }
}