package com.example.restaurant.repositories;

import com.example.restaurant.enums.AccountStatus;
import com.example.restaurant.enums.Role;
import com.example.restaurant.models.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
    // Найти сотрудника по Email
    Optional<Employee> findByEmail(String email);

    // Найти сотрудника по Username аккаунта (для профиля)
    Optional<Employee> findByAccountUsername(String username);

    // Найти сотрудников по Роли и Статусу (например, всех активных Поваров)
    List<Employee> findByAccountRoleAndAccountStatus(Role role, AccountStatus status);

    // Найти сотрудников только по Статусу (например, всех Заблокированных)
    List<Employee> findByAccountStatus(AccountStatus status);
}