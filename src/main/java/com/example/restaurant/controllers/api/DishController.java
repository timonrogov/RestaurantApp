package com.example.restaurant.controllers.api;

import com.example.restaurant.models.Dish;
import com.example.restaurant.models.DishPromotion;
import com.example.restaurant.services.DishPromotionService;
import com.example.restaurant.services.DishService;
import com.example.restaurant.services.PromotionTimeSlotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/dishes")
public class DishController {
    private final DishService dishService;
    private final DishPromotionService dishPromotionService;
    private final PromotionTimeSlotService promotionTimeSlotService;

    @Autowired
    public DishController(DishService dishService, DishPromotionService dishPromotionService,
                          PromotionTimeSlotService promotionTimeSlotService) {
        this.dishService = dishService;
        this.dishPromotionService = dishPromotionService;
        this.promotionTimeSlotService = promotionTimeSlotService;
    }

    @GetMapping
    public List<Dish> getAllDishes() {
        return dishService.getAllDishes();
    }

    @GetMapping("/{id}")
    public Dish getDishById(@PathVariable Long id) {
        return dishService.getDishById(id);
    }

    @PostMapping
    public Dish createDish(@RequestBody Dish dish) {
        return dishService.createDish(dish);
    }

    @DeleteMapping("/{id}")
    public void deleteDish(@PathVariable Long id) {
        dishService.deleteDish(id);
    }


    @GetMapping("/{dishId}/check-discount")
    public ResponseEntity<DiscountCheckResponse> checkDiscount(
            @PathVariable Long dishId,
            @RequestParam int quantity,
            @RequestParam String date,
            @RequestParam String time) {

        Dish dish = dishService.getDishById(dishId);

        LocalDate currentDate = LocalDate.parse(date);
        LocalTime currentTime = LocalTime.parse(time);

        // Проверяем все активные акции для блюда
        Optional<DishPromotion> activePromoOpt = dish.getActivePromotions().stream()
                .filter(promo -> promotionTimeSlotService.isPromotionActive(
                        promo.getPromotionTimeSlot(),
                        currentDate,
                        currentTime))
                .findFirst();

        BigDecimal discount = activePromoOpt.map(DishPromotion::getDiscount)
                .orElse(BigDecimal.ZERO);

        boolean quantityMet = activePromoOpt.map(promo -> quantity >= promo.getMinQuantity())
                .orElse(false);

        return ResponseEntity.ok(new DiscountCheckResponse(
                discount.compareTo(BigDecimal.ZERO) > 0,
                quantityMet,
                dish.getPrice(),
                dish.getPrice() * (1 - discount.doubleValue()),
                discount.multiply(BigDecimal.valueOf(100)).intValue(),
                activePromoOpt.map(DishPromotion::getMinQuantity).orElse(0)
        ));
    }

    private record DiscountCheckResponse(
            boolean available,
            boolean quantityMet,
            double originalPrice,
            double discountedPrice,
            int discountPercent,
            int minQuantity
    ) {}
}
