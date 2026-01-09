package com.example.restaurant.controllers;

import com.example.restaurant.models.Day;
import com.example.restaurant.models.DishPromotion;
import com.example.restaurant.models.PromotionTimeSlot;
import com.example.restaurant.services.DayService;
import com.example.restaurant.services.DishPromotionService;
import com.example.restaurant.services.DishService;
import com.example.restaurant.services.PromotionTimeSlotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;

@Controller
@RequestMapping("/admin/promotions")
@PreAuthorize("hasRole('ADMIN')")
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
            // timeSlot можно убрать из @ModelAttribute, так как его поля приходят отдельными параметрами
            // Но можно и оставить, если это не мешает.
            // Главное - принять discountPercentage отдельно!
            @RequestParam("discountPercentage") int discountPercentage,

            @RequestParam("startDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam("endDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @RequestParam("startTime") LocalTime startTime,
            @RequestParam("endTime") LocalTime endTime,

            BindingResult result,
            Model model,
            RedirectAttributes redirectAttributes) {

        try {
            // Вызываем сервис, передавая ему процент скидки как число (например, 20)
            dishPromotionService.createPromotionWithSlot(
                    promotion,
                    discountPercentage,
                    startDate, endDate, startTime, endTime
            );

            redirectAttributes.addFlashAttribute("success", "Акция успешно создана!");

        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/promotions/new";
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка сервера: " + e.getMessage());
            return "redirect:/admin/promotions/new";
        }

        return "redirect:/admin/promotions";
    }
}