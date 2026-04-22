package com.example.restaurant.controllers;

import com.example.restaurant.enums.CookingTaskStatus;
import com.example.restaurant.models.CookProfile;
import com.example.restaurant.models.CookingTask;
import com.example.restaurant.models.Equipment;
import com.example.restaurant.repositories.CookProfileRepository;
import com.example.restaurant.repositories.CookingTaskRepository;
import com.example.restaurant.repositories.EquipmentRepository;
import com.example.restaurant.scheduler.service.SchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/scheduler")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SchedulerAdminController {

    private final SchedulerService schedulerService;
    private final CookingTaskRepository taskRepository;
    private final CookProfileRepository cookProfileRepository;
    private final EquipmentRepository equipmentRepository;

    @GetMapping
    public String schedulerDashboard(Model model) {
        List<CookingTask> activeTasks = schedulerService.getAllActiveTasksSummary();

        // Группировка задач по заказу
        Map<Long, List<CookingTask>> tasksByOrder = activeTasks.stream()
                .collect(Collectors.groupingBy(t -> t.getOrderItem().getOrder().getId()));

        // Просроченные задачи
        List<CookingTask> overdueTasks = activeTasks.stream()
                .filter(t -> t.getPlannedEndTime() != null
                        && t.getPlannedEndTime().isBefore(LocalDateTime.now())
                        && t.getStatus() != CookingTaskStatus.DONE)
                .toList();

        // Загруженность поваров
        List<CookProfile> activeCooks = cookProfileRepository.findByIsActiveTrue();
        Map<Long, Long> taskCountByCook = activeTasks.stream()
                .filter(t -> t.getAssignedCook() != null)
                .collect(Collectors.groupingBy(t -> t.getAssignedCook().getId(),
                        Collectors.counting()));

        model.addAttribute("activeTasks",    activeTasks);
        model.addAttribute("tasksByOrder",   tasksByOrder);
        model.addAttribute("overdueTasks",   overdueTasks);
        model.addAttribute("activeCooks",    activeCooks);
        model.addAttribute("taskCountByCook", taskCountByCook);
        model.addAttribute("equipment",      equipmentRepository.findAll());
        return "admin-scheduler";
    }

    /** Экстренное: пометить повара недоступным. */
    @PostMapping("/cook/{cookId}/unavailable")
    public String cookUnavailable(@PathVariable Long cookId,
                                  RedirectAttributes redirectAttributes) {
        CookProfile profile = cookProfileRepository.findById(cookId)
                .orElseThrow(() -> new RuntimeException("Повар не найден"));
        profile.setActive(false);
        cookProfileRepository.save(profile);
        schedulerService.markCookUnavailable(cookId);
        redirectAttributes.addFlashAttribute("success",
                "Повар помечен недоступным, задачи перепланируются.");
        return "redirect:/admin/scheduler";
    }

    /** Экстренное: пометить оборудование сломанным. */
    @PostMapping("/equipment/{equipmentId}/broken")
    public String equipmentBroken(@PathVariable Long equipmentId,
                                  RedirectAttributes redirectAttributes) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new RuntimeException("Оборудование не найдено"));
        equipment.setActive(false);
        equipmentRepository.save(equipment);
        schedulerService.markEquipmentBroken(equipmentId);
        redirectAttributes.addFlashAttribute("success",
                "Оборудование помечено как неисправное, задачи перепланируются.");
        return "redirect:/admin/scheduler";
    }
}