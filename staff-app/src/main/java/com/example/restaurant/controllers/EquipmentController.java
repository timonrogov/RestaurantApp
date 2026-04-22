package com.example.restaurant.controllers;

import com.example.restaurant.models.Equipment;
import com.example.restaurant.repositories.EquipmentRepository;
import com.example.restaurant.scheduler.service.SchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
@RequestMapping("/admin/equipment")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class EquipmentController {

    private final EquipmentRepository equipmentRepository;
    private final SchedulerService schedulerService;

    @GetMapping
    public String listEquipment(Model model) {
        model.addAttribute("equipmentList", equipmentRepository.findAll());
        model.addAttribute("newEquipment", new Equipment());
        return "admin-equipment";
    }

    @PostMapping
    public String saveEquipment(@RequestParam String name,
                                @RequestParam String equipmentType,
                                @RequestParam int maxParallelTasks,
                                RedirectAttributes redirectAttributes) {
        Equipment e = new Equipment();
        e.setName(name);
        e.setEquipmentType(equipmentType.trim().toUpperCase());
        e.setMaxParallelTasks(maxParallelTasks);
        e.setActive(true);
        equipmentRepository.save(e);
        schedulerService.addEquipment(e);

        redirectAttributes.addFlashAttribute("success", "Оборудование «" + name + "» добавлено");
        return "redirect:/admin/equipment";
    }

    /** Включить/выключить единицу оборудования и уведомить планировщик. */
    @PostMapping("/{id}/toggle")
    public String toggleEquipment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        Equipment equipment = equipmentRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Оборудование не найдено"));

        boolean wasActive = equipment.isActive();
        equipment.setActive(!wasActive);
        equipmentRepository.save(equipment);

        // Уведомляем планировщик об изменении
        if (wasActive) {
            schedulerService.markEquipmentBroken(id);
            redirectAttributes.addFlashAttribute("success",
                    "«" + equipment.getName() + "» помечено как неисправное. Задачи перепланированы.");
        } else {
            schedulerService.markEquipmentFixed(id);
            redirectAttributes.addFlashAttribute("success",
                    "«" + equipment.getName() + "» снова активно.");
        }

        return "redirect:/admin/equipment";
    }

    @PostMapping("/{id}/delete")
    public String deleteEquipment(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        equipmentRepository.deleteById(id);
        redirectAttributes.addFlashAttribute("success", "Оборудование удалено");
        return "redirect:/admin/equipment";
    }
}