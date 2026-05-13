package com.example.restaurant.controllers;

import com.example.restaurant.enums.CookSpecialization;
import com.example.restaurant.models.CookingTaskTemplate;
import com.example.restaurant.models.Dish;
import com.example.restaurant.repositories.CookingTaskTemplateRepository;
import com.example.restaurant.services.DishService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.util.List;

@Controller
@RequestMapping("/admin/dishes/{dishId}/templates")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class CookingTemplateController {

    private final CookingTaskTemplateRepository templateRepository;
    private final DishService dishService;

    /** Список всех шаблонов этапов для конкретного блюда. */
    @GetMapping
    public String listTemplates(@PathVariable Long dishId, Model model) {
        Dish dish = dishService.getDishById(dishId);
        if (dish == null) return "redirect:/admin/dishes";

        List<CookingTaskTemplate> templates =
                templateRepository.findByDishIdOrderByStepNumberAsc(dishId);

        model.addAttribute("dish", dish);
        model.addAttribute("templates", templates);
        model.addAttribute("specializations", CookSpecialization.values());
        return "admin-templates";
    }

    /** Сохранить новый шаблон этапа. */
    @PostMapping
    public String saveTemplate(@PathVariable Long dishId,
                               @RequestParam int stepNumber,
                               @RequestParam String stepName,
                               @RequestParam int durationMinutes,
                               @RequestParam CookSpecialization requiredSpecialization,
                               @RequestParam(required = false) String requiredEquipmentType,
                               @RequestParam(defaultValue = "1") int portionsPerSlot,  // НОВЫЙ ПАРАМЕТР
                               RedirectAttributes redirectAttributes) {
        Dish dish = dishService.getDishById(dishId);
        if (dish == null) return "redirect:/admin/dishes";

        CookingTaskTemplate template = new CookingTaskTemplate();
        template.setDish(dish);
        template.setStepNumber(stepNumber);
        template.setStepName(stepName);
        template.setDurationMinutes(durationMinutes);
        template.setRequiredSpecialization(requiredSpecialization);
        template.setRequiredEquipmentType(
                (requiredEquipmentType != null && !requiredEquipmentType.isBlank())
                        ? requiredEquipmentType.trim().toUpperCase()
                        : null
        );
        template.setPortionsPerSlot(Math.max(1, portionsPerSlot));  // НОВАЯ СТРОКА (защита от 0 и отрицательных)

        templateRepository.save(template);

        redirectAttributes.addFlashAttribute("success", "Этап «" + stepName + "» добавлен");
        return "redirect:/admin/dishes/" + dishId + "/templates";
    }

    /** Удалить шаблон этапа. */
    @PostMapping("/{templateId}/delete")
    public String deleteTemplate(@PathVariable Long dishId,
                                 @PathVariable Long templateId,
                                 RedirectAttributes redirectAttributes) {
        templateRepository.deleteById(templateId);
        redirectAttributes.addFlashAttribute("success", "Этап удалён");
        return "redirect:/admin/dishes/" + dishId + "/templates";
    }
}