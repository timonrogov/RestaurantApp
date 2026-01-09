package com.example.restaurant.controllers;

import com.example.restaurant.enums.Role;
import com.example.restaurant.models.Employee;
import com.example.restaurant.services.EmployeeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.Collections;
import java.util.List;

@Controller
@RequestMapping("/admin/employees")
@PreAuthorize("hasRole('ADMIN')") // Доступ только для Админа
public class AdminEmployeeController {

    private final EmployeeService employeeService;

    @Autowired
    public AdminEmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    @GetMapping
    public String getEmployees(@RequestParam(required = false, defaultValue = "ADMIN") String tab,
                               Model model) {
        List<Employee> employees;

        // Логика переключения вкладок
        if ("BANNED".equals(tab)) {
            // Если выбрана вкладка "Заблокированные"
            employees = employeeService.getBannedEmployees();
        } else {
            // Пытаемся найти по Роли (ADMIN, COOK, WAITER)
            try {
                Role role = Role.valueOf(tab);
                employees = employeeService.getEmployeesByRole(role);
            } catch (IllegalArgumentException e) {
                // Если передали что-то странное в URL, возвращаем пустой список или дефолтный
                employees = Collections.emptyList();
            }
        }

        model.addAttribute("employees", employees);
        model.addAttribute("currentTab", tab); // Чтобы подсветить активную кнопку в HTML

        // Передаем все роли для генерации кнопок (кроме CLIENT)
        model.addAttribute("roles", Role.values());

        return "admin-employees";
    }

    @PostMapping("/{id}/block")
    public String blockEmployee(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            employeeService.blockEmployee(id);
            redirectAttributes.addFlashAttribute("success", "Сотрудник успешно заблокирован");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка: " + e.getMessage());
        }
        // Возвращаемся на ту же страницу (можно добавить сохранение текущего таба, если нужно)
        return "redirect:/admin/employees";
    }

    @PostMapping("/{id}/unblock")
    public String unblockEmployee(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            employeeService.unblockEmployee(id);
            redirectAttributes.addFlashAttribute("success", "Сотрудник разблокирован");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка: " + e.getMessage());
        }
        return "redirect:/admin/employees?tab=BANNED"; // Возвращаемся в список заблокированных
    }

    @PostMapping("/{id}/delete")
    public String deleteEmployee(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            employeeService.deleteEmployeeFull(id);
            redirectAttributes.addFlashAttribute("success", "Сотрудник удален");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка: " + e.getMessage());
        }
        return "redirect:/admin/employees";
    }
}