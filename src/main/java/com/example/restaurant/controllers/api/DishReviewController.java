package com.example.restaurant.controllers.api;

import com.example.restaurant.models.DishReview;
import com.example.restaurant.services.DishReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dish-reviews")
public class DishReviewController {
    private final DishReviewService dishReviewService;

    @Autowired
    public DishReviewController(DishReviewService dishReviewService) {
        this.dishReviewService = dishReviewService;
    }

    @GetMapping("/dish/{dishId}")
    public List<DishReview> getReviewsByDishId(@PathVariable Long dishId) {
        return dishReviewService.getReviewsByDishId(dishId);
    }

    @PostMapping
    public DishReview createDishReview(@RequestBody DishReview dishReview) {
        return dishReviewService.createDishReview(dishReview);
    }

    @DeleteMapping("/{id}")
    public void deleteDishReview(@PathVariable Long id) {
        dishReviewService.deleteDishReview(id);
    }
}