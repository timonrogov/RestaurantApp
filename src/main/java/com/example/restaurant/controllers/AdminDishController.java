package com.example.restaurant.controllers;

import com.example.restaurant.models.Dish;
import com.example.restaurant.services.DishService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/dishes")
@PreAuthorize("hasRole('СОТРУДНИК')")
public class AdminDishController {

    private final DishService dishService;

    @Autowired
    public AdminDishController(DishService dishService) {
        this.dishService = dishService;
    }

    @GetMapping
    public String manageDishes(Model model) {
        List<Dish> dishes = dishService.getAllDishes()
                .stream()
                .sorted(Comparator
                        .comparing(Dish::isAvailable).reversed()  // Первичная сортировка по доступности
                        .thenComparing(d -> d.getCategory().getId())  // Вторичная сортировка по ID категории
                )
                .collect(Collectors.toList());

        model.addAttribute("dishes", dishes);
        return "admin-dishes";
    }

    @PostMapping("/toggle/{id}")
    public String toggleAvailability(@PathVariable Long id) {
        dishService.toggleAvailability(id);
        return "redirect:/admin/dishes";
    }
}