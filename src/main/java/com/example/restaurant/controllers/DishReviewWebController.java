package com.example.restaurant.controllers;

import com.example.restaurant.models.Client;
import com.example.restaurant.models.Dish;
import com.example.restaurant.models.DishReview;
import com.example.restaurant.services.ClientService;
import com.example.restaurant.services.DishReviewService;
import com.example.restaurant.services.DishService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;
import java.time.LocalDateTime;
import java.util.List;

@Controller
@RequestMapping("/dish")
public class DishReviewWebController {
    private final DishService dishService;
    private final DishReviewService dishReviewService;
    private final ClientService clientService;

    @Autowired
    public DishReviewWebController(DishService dishService, DishReviewService dishReviewService, ClientService clientService) {
        this.dishService = dishService;
        this.dishReviewService = dishReviewService;
        this.clientService = clientService;
    }

    @GetMapping("/{dishId}/reviews")
    public String getDishReviews(@PathVariable Long dishId, Model model, Principal principal) {
        Dish dish = dishService.getDishById(dishId);
        model.addAttribute("dish", dish);

        if (principal != null) {
            Client client = clientService.getClientByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Клиент не найден"));
            model.addAttribute("client", client);
        }

        return "dish-reviews";
    }

    @PostMapping("/{id}/reviews/add")
    public String addReview(@PathVariable Long id,
                            @RequestParam("rating") Integer rating,
                            @RequestParam("reviewText") String reviewText,
                            Principal principal,
                            RedirectAttributes redirectAttributes) {
        try {
            // 1. Проверка аутентификации
            if (principal == null) {
                redirectAttributes.addFlashAttribute("error", "Для добавления отзыва необходимо авторизоваться");
                return "redirect:/login";
            }

            // 2. Получение клиента
            Client client = clientService.getClientByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Клиент не найден"));

            // 3. Получение блюда
            Dish dish = dishService.getDishById(id);

            // 4. Проверка существующего отзыва
            if (dishReviewService.hasUserReviewedDish(client.getId(), id)) {
                DishReview review = dishReviewService.getReviewByClientAndDish(client.getId(), id).orElse(new DishReview());
                review.setRating(rating);
                review.setReviewText(reviewText);
                review.setClient(client);
                review.setDish(dish);
                review.setReviewTime(LocalDateTime.now());
                dishReviewService.createDishReview(review);
                return "redirect:/dish/" + id + "/reviews";
            }

            if (rating == null || rating < 1 || rating > 5) {
                redirectAttributes.addFlashAttribute("error", "Некорректная оценка");
                return "redirect:/dish/" + id + "/reviews";
            }

            // 5. Создание и сохранение отзыва
            DishReview review = new DishReview();
            review.setRating(rating);
            review.setReviewText(reviewText);
            review.setClient(client);
            review.setDish(dish);
            review.setReviewTime(LocalDateTime.now());

            dishReviewService.createDishReview(review);

            // 6. Перенаправление с сообщением об успехе
            redirectAttributes.addFlashAttribute("success", "Отзыв успешно добавлен");
            return "redirect:/dish/" + id + "/reviews";

        } catch (Exception e) {
            redirectAttributes.addFlashAttribute("error", "Ошибка: " + e.getMessage());
            return "redirect:/dish/" + id + "/reviews";
        }
    }
}
