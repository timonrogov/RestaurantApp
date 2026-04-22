package com.example.restaurant.controllers;

import com.example.restaurant.enums.CookSpecialization;
import com.example.restaurant.enums.Role;
import com.example.restaurant.models.CookProfile;
import com.example.restaurant.models.Employee;
import com.example.restaurant.repositories.CookProfileRepository;
import com.example.restaurant.repositories.EmployeeRepository;
import com.example.restaurant.scheduler.service.SchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/cooks")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CookProfileController {

    private final CookProfileRepository cookProfileRepository;
    private final EmployeeRepository employeeRepository;
    private final SchedulerService schedulerService;

    @GetMapping
    public String listCooks(Model model) {
        // Все сотрудники с ролью COOK
        List<Employee> cooks = employeeRepository
                .findByAccountRoleAndAccountStatus(Role.COOK,
                        com.example.restaurant.enums.AccountStatus.ACTIVE);
        List<CookProfile> profiles = cookProfileRepository.findAll();
        model.addAttribute("cooks", cooks);
        model.addAttribute("profiles", profiles);
        model.addAttribute("specializations", CookSpecialization.values());
        return "admin-cooks";
    }

    /** Создать или обновить профиль повара. */
    @PostMapping("/{employeeId}/profile")
    public String saveProfile(@PathVariable Long employeeId,
                              @RequestParam CookSpecialization specialization,
                              RedirectAttributes redirectAttributes) {
        Employee employee = employeeRepository.findById(employeeId)
                .orElseThrow(() -> new RuntimeException("Сотрудник не найден"));

        CookProfile profile = cookProfileRepository.findByEmployeeId(employeeId)
                .orElse(new CookProfile());
        profile.setEmployee(employee);
        profile.setSpecialization(specialization);
        profile.setActive(true);
        cookProfileRepository.save(profile);
        schedulerService.addOrUpdateCook(profile);

        redirectAttributes.addFlashAttribute("success",
                "Профиль повара «" + employee.getFullName() + "» сохранён");
        return "redirect:/admin/cooks";
    }

    /** Переключить доступность повара (недоступен / доступен). */
    @PostMapping("/{cookId}/toggle")
    public String toggleCook(@PathVariable Long cookId,
                             RedirectAttributes redirectAttributes) {
        CookProfile profile = cookProfileRepository.findById(cookId)
                .orElseThrow(() -> new RuntimeException("Профиль не найден"));

        boolean wasActive = profile.isActive();
        profile.setActive(!wasActive);
        cookProfileRepository.save(profile);

        if (wasActive) {
            schedulerService.markCookUnavailable(cookId);
            redirectAttributes.addFlashAttribute("success",
                    "Повар помечен как недоступный. Задачи перепланированы.");
        } else {
            schedulerService.markCookAvailable(cookId);
            redirectAttributes.addFlashAttribute("success",
                    "Повар снова доступен для планирования.");
        }

        return "redirect:/admin/cooks";
    }
}