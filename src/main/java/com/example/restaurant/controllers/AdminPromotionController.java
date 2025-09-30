package com.example.restaurant.controllers;

import com.example.restaurant.models.Day;
import com.example.restaurant.models.DishPromotion;
import com.example.restaurant.models.PromotionTimeSlot;
import com.example.restaurant.services.DayService;
import com.example.restaurant.services.DishPromotionService;
import com.example.restaurant.services.DishService;
import com.example.restaurant.services.PromotionTimeSlotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;

@Controller
@RequestMapping("/admin/promotions")
public class AdminPromotionController {

    private final DishPromotionService promotionService;
    private final DayService dayService;
    private final PromotionTimeSlotService promotionTimeSlotService;
    private final DishPromotionService dishPromotionService;
    private final DishService dishService;

    @Autowired
    public AdminPromotionController(DishPromotionService promotionService,
                                    DayService dayService,
                                    PromotionTimeSlotService promotionTimeSlotService,
                                    DishPromotionService dishPromotionService,
                                    DishService dishService) {
        this.promotionService = promotionService;
        this.dayService = dayService;
        this.promotionTimeSlotService = promotionTimeSlotService;
        this.dishPromotionService = dishPromotionService;
        this.dishService = dishService;
    }

    @GetMapping
    public String promotionsPage(Model model) {
        model.addAttribute("promotions", promotionService.getAllActivePromotions());
        return "admin-promotions";
    }

    @PostMapping("/delete/{id}")
    public String deletePromotion(@PathVariable Long id) {
        promotionService.deleteDishPromotion(id);
        return "redirect:/admin/promotions";
    }

    @GetMapping("/new")
    public String newPromotionForm(Model model) {
        model.addAttribute("allDishes", dishService.getDishesWithoutActivePromotions());
        model.addAttribute("promotion", new DishPromotion());

        PromotionTimeSlot timeSlot = new PromotionTimeSlot();
        Day defaultDay = new Day();
        defaultDay.setWorkDate(LocalDate.now());
        timeSlot.setStartDate(defaultDay);
        timeSlot.setEndDate(defaultDay);

        model.addAttribute("timeSlot", timeSlot);
        return "new-promotion";
    }

    @PostMapping("/create")
    public String createPromotion(
            @ModelAttribute("promotion") DishPromotion promotion,
            @ModelAttribute("timeSlot") PromotionTimeSlot timeSlot,
            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes) {

        // Проверка существования дней в расписании
        Day startDay = dayService.findById(timeSlot.getStartDate().getWorkDate())
                .orElse(null);
        Day endDay = dayService.findById(timeSlot.getEndDate().getWorkDate())
                .orElse(null);

        if (startDay == null || endDay == null) {
            redirectAttributes.addFlashAttribute("error",
                    "На выбранные даты не запланирована работа ресторана");
            return "redirect:/admin/promotions/new";
        }

        // Проверка скидки
        if (promotion.getDiscount().compareTo(BigDecimal.ZERO) <= 0 ||
                promotion.getDiscount().compareTo(BigDecimal.valueOf(100)) > 0) {
            redirectAttributes.addFlashAttribute("error",
                    "Скидка должна быть от 1 до 100%");
            return "redirect:/admin/promotions/new";
        }

        try {
            // Создаем временной слот
            timeSlot.setStartDate(startDay);
            timeSlot.setEndDate(endDay);
            PromotionTimeSlot savedSlot = promotionTimeSlotService
                    .createPromotionTimeSlot(timeSlot);

            // Создаем акцию
            promotion.setPromotionTimeSlot(savedSlot);
            dishPromotionService.createDishPromotion(promotion);

            redirectAttributes.addFlashAttribute("success",
                    "Акция успешно создана!");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error",
                    "Ошибка при создании акции: " + e.getMessage());
        }

        return "redirect:/admin/promotions";
    }
}