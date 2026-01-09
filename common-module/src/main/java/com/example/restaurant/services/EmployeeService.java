package com.example.restaurant.services;

import com.example.restaurant.enums.AccountStatus;
import com.example.restaurant.enums.Role;
import com.example.restaurant.models.Account;
import com.example.restaurant.models.Employee;
import com.example.restaurant.repositories.EmployeeRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
public class EmployeeService {
    private final EmployeeRepository employeeRepository;
    private final AccountService accountService;

    @Autowired
    public EmployeeService(EmployeeRepository employeeRepository,
                           AccountService accountService) {
        this.employeeRepository = employeeRepository;
        this.accountService = accountService;
    }

    public Optional<Employee> getEmployeeByEmail(String email) {
        return employeeRepository.findByEmail(email);
    }

    public Employee createEmployee(Employee employee) {
        return employeeRepository.save(employee);
    }

    public void deleteEmployee(Long id) {
        employeeRepository.deleteById(id);
    }


    /**
     * Регистрирует нового сотрудника с указанной ролью.
     */
    @Transactional
    public void registerEmployee(String username, String password, String fullName, String email,
                                 String phone, String gender, LocalDate dateOfBirth,
                                 Role role) { // <--- Добавили аргумент Role

        // ... проверки email и username ...

        Account account = new Account();
        account.setUsername(username);
        account.setPassword(password);

        // ИСПРАВЛЕНИЕ: Используем переданную роль вместо хардкода
        account.setRole(role);
        account.setStatus(AccountStatus.ACTIVE);

        Account savedAccount = accountService.createAccount(account);

        // 3. Создание Сотрудника
        Employee employee = new Employee();
        employee.setAccount(savedAccount);
        employee.setFullName(fullName);
        employee.setEmail(email);
        employee.setPhone(phone);
        employee.setGender(gender);
        employee.setDateOfBirth(dateOfBirth);

        // 4. Сохранение
        employeeRepository.save(employee);
    }


    // 1. Методы для получения списков

    public List<Employee> getEmployeesByRole(Role role) {
        // Возвращаем только АКТИВНЫХ сотрудников выбранной роли
        return employeeRepository.findByAccountRoleAndAccountStatus(role, AccountStatus.ACTIVE);
    }

    public List<Employee> getBannedEmployees() {
        // Возвращаем всех заблокированных (независимо от роли)
        return employeeRepository.findByAccountStatus(AccountStatus.BANNED);
    }

    // 2. Методы управления статусом

    @Transactional
    public void blockEmployee(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

        // Нельзя заблокировать самого себя или другого админа (опциональная защита)
        if (employee.getAccount().getRole() == Role.ADMIN) {
            // Можно добавить проверку, чтобы админ не мог заблочить админа,
            // но пока оставим простую логику.
        }

        employee.getAccount().setStatus(AccountStatus.BANNED);
        // Достаточно сохранить employee, изменения в account подхватятся (если настроен Cascade),
        // но для надежности сохраним аккаунт через сервис или репозиторий.
        accountService.createAccount(employee.getAccount()); // createAccount делает save
    }

    @Transactional
    public void unblockEmployee(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

        employee.getAccount().setStatus(AccountStatus.ACTIVE);
        accountService.createAccount(employee.getAccount());
    }

    @Transactional
    public void deleteEmployeeFull(Long id) {
        Employee employee = employeeRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

        // Удаляем сотрудника.
        // ВАЖНО: В базе данных должен быть настроен ON DELETE CASCADE для внешнего ключа,
        // либо мы должны сначала удалить сотрудника, а потом аккаунт.
        // Если у вас в Employee стоит @OneToOne(cascade = CascadeType.ALL), то удаление сотрудника удалит и аккаунт.
        employeeRepository.delete(employee);

        // Если каскад не настроен в Java, нужно вручную удалить аккаунт:
        // accountService.deleteAccount(employee.getAccount().getId());
    }

    // 3. Метод для профиля и обновления данных

    public Employee getEmployeeByUsername(String username) {
        return employeeRepository.findByAccountUsername(username)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));
    }

    @Transactional
    public void updateProfile(Long employeeId, String newUsername, String fullName,
                              String phone, String email, String gender, LocalDate dateOfBirth) {

        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));
        Account account = employee.getAccount();

        // Проверка уникальности логина (если он изменился)
        if (!account.getUsername().equals(newUsername) && accountService.existsByUsername(newUsername)) {
            throw new RuntimeException("Этот логин уже занят");
        }

        // Проверка уникальности email (если он изменился)
        // Примечание: findByEmail возвращает Optional в новом коде,
        // но если вы не меняли старый код, там могло быть Employee.
        // Используем безопасную проверку:
        Optional<Employee> existingEmp = employeeRepository.findByEmail(email);
        if (existingEmp.isPresent() && !existingEmp.get().getId().equals(employeeId)) {
            throw new RuntimeException("Этот email уже используется другим сотрудником");
        }

        // Обновление данных
        account.setUsername(newUsername);
        accountService.createAccount(account); // Сохраняем аккаунт

        employee.setFullName(fullName);
        employee.setPhone(phone);
        employee.setEmail(email);
        employee.setGender(gender);
        employee.setDateOfBirth(dateOfBirth);

        employeeRepository.save(employee); // Сохраняем сотрудника
    }
}