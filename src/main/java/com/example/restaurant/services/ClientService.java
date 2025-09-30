package com.example.restaurant.services;

import com.example.restaurant.models.Account;
import com.example.restaurant.models.Client;
import com.example.restaurant.repositories.ClientRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class ClientService {
    private final ClientRepository clientRepository;
    private final AccountService accountService;

    @Autowired
    public ClientService(ClientRepository clientRepository, AccountService accountService) {
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
}