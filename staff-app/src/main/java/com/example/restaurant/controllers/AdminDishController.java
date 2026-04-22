package com.example.restaurant.controllers;

import com.example.restaurant.models.Dish;
import com.example.restaurant.services.DishCategoryService;
import com.example.restaurant.services.DishService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.io.IOException;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/dishes")
@PreAuthorize("hasRole('ADMIN')")
public class AdminDishController {

    private final DishService dishService;
    private final DishCategoryService dishCategoryService; // Новая зависимость

    @Autowired
    public AdminDishController(DishService dishService, DishCategoryService dishCategoryService) {
        this.dishService = dishService;
        this.dishCategoryService = dishCategoryService;
    }

    // --- 1. Список блюд (существующий метод) ---
    @GetMapping
    public String manageDishes(Model model) {
        List<Dish> dishes = dishService.getAllDishes()
                .stream()
                .sorted(Comparator
                        .comparing(Dish::isAvailable).reversed()
                        .thenComparing(d -> d.getCategory().getId())
                )
                .collect(Collectors.toList());

        model.addAttribute("dishes", dishes);
        return "admin-dishes";
    }

    // --- 2. Форма добавления нового блюда ---
    @GetMapping("/new")
    public String newDishForm(Model model) {
        model.addAttribute("dish", new Dish()); // Пустой объект для формы
        model.addAttribute("categories", dishCategoryService.getAllCategories()); // Список категорий
        return "admin-dish-form"; // Этот шаблон создадим на следующем этапе
    }

    // --- 3. Форма редактирования существующего блюда ---
    @GetMapping("/edit/{id}")
    public String editDishForm(@PathVariable Long id, Model model, RedirectAttributes redirectAttributes) {
        Dish dish = dishService.getDishById(id);
        if (dish == null) {
            redirectAttributes.addFlashAttribute("error", "Блюдо не найдено");
            return "redirect:/admin/dishes";
        }

        model.addAttribute("dish", dish);
        model.addAttribute("categories", dishCategoryService.getAllCategories());
        return "admin-dish-form"; // Используем тот же шаблон, что и для создания
    }

    // --- 4. Сохранение (Создание или Обновление) ---
    @PostMapping("/save")
    public String saveDish(@ModelAttribute Dish dish,
                           @RequestParam("imageFile") MultipartFile imageFile,
                           @RequestParam("categoryId") Long categoryId,
                           RedirectAttributes redirectAttributes) {
        try {
            // Вызываем наш обновленный сервис, который умеет работать с файлами
            dishService.saveDish(dish, imageFile, categoryId);
            redirectAttributes.addFlashAttribute("success", "Блюдо успешно сохранено");
        } catch (IOException e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка при загрузке изображения: " + e.getMessage());
            return "redirect:/admin/dishes/new"; // Или назад на редактирование
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка сохранения: " + e.getMessage());
            return "redirect:/admin/dishes";
        }

        return "redirect:/admin/dishes";
    }

    // --- 5. Удаление блюда ---
    @PostMapping("/delete/{id}")
    public String deleteDish(@PathVariable Long id, RedirectAttributes redirectAttributes) {
        try {
            dishService.deleteDish(id);
            redirectAttributes.addFlashAttribute("success", "Блюдо удалено");
        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка удаления: " + e.getMessage());
        }
        return "redirect:/admin/dishes";
    }

    // --- 6. Переключение доступности (существующий метод) ---
    @PostMapping("/toggle/{id}")
    public String toggleAvailability(@PathVariable Long id) {
        dishService.toggleAvailability(id);
        return "redirect:/admin/dishes";
    }

    /*@GetMapping("/{dishId}/templates")
    public String dishTemplates(@PathVariable Long dishId) {
        return "redirect:/admin/dishes/" + dishId + "/templates";
    }*/
}