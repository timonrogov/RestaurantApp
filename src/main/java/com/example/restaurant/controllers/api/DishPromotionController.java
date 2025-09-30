package com.example.restaurant.controllers.api;

import com.example.restaurant.models.DishPromotion;
import com.example.restaurant.services.DishPromotionService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/dish-promotions")
public class DishPromotionController {
    private final DishPromotionService dishPromotionService;

    @Autowired
    public DishPromotionController(DishPromotionService dishPromotionService) {
        this.dishPromotionService = dishPromotionService;
    }

    @GetMapping("/promotion-time-slot/{promotionTimeSlotId}")
    public List<DishPromotion> getDishPromotionsByPromotionTimeSlotId(@PathVariable Long promotionTimeSlotId) {
        return dishPromotionService.getDishPromotionsByPromotionTimeSlotId(promotionTimeSlotId);
    }

    @PostMapping
    public DishPromotion createDishPromotion(@RequestBody DishPromotion dishPromotion) {
        return dishPromotionService.createDishPromotion(dishPromotion);
    }

    @DeleteMapping("/{id}")
    public void deleteDishPromotion(@PathVariable Long id) {
        dishPromotionService.deleteDishPromotion(id);
    }
}