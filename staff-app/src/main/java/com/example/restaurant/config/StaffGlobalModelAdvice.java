package com.example.restaurant.config;

import com.example.restaurant.models.Employee;
import com.example.restaurant.repositories.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.security.Principal;

/**
 * Глобальный ControllerAdvice для staff-app.
 *
 * Добавляет в модель каждого контроллера атрибут {@code currentEmployeeFullName},
 * чтобы шапка могла отображать ФИО сотрудника вместо логина.
 */
@ControllerAdvice
@RequiredArgsConstructor
public class StaffGlobalModelAdvice {

    private final EmployeeRepository employeeRepository;

    /**
     * Возвращает ФИО текущего сотрудника.
     * Если сотрудник не найден (например, на странице входа) — возвращает null.
     */
    @ModelAttribute("currentEmployeeFullName")
    public String currentEmployeeFullName(Principal principal) {
        if (principal == null) return null;
        return employeeRepository.findByAccountUsername(principal.getName())
                .map(Employee::getFullName)
                .orElse(null);
    }
}