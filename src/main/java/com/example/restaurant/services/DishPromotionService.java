package com.example.restaurant.services;

import com.example.restaurant.models.Dish;
import com.example.restaurant.models.DishPromotion;
import com.example.restaurant.models.OrderTimeSlot;
import com.example.restaurant.models.PromotionTimeSlot;
import com.example.restaurant.repositories.DishPromotionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DishPromotionService {
    private final DishPromotionRepository dishPromotionRepository;
    private PromotionTimeSlotService promotionTimeSlotService;

    @Autowired
    public DishPromotionService(DishPromotionRepository dishPromotionRepository,
                                PromotionTimeSlotService promotionTimeSlotService) {
        this.dishPromotionRepository = dishPromotionRepository;
        this.promotionTimeSlotService = promotionTimeSlotService;
    }

    public List<DishPromotion> getDishPromotionsByPromotionTimeSlotId(Long promotionTimeSlotId) {
        return dishPromotionRepository.findByPromotionTimeSlotId(promotionTimeSlotId);
    }

    public DishPromotion createDishPromotion(DishPromotion dishPromotion) {
        return dishPromotionRepository.save(dishPromotion);
    }

    public void deleteDishPromotion(Long id) {
        dishPromotionRepository.deleteById(id);
    }

    public List<DishPromotion> getActivePromotions(LocalDate date, LocalTime time) {
        return dishPromotionRepository.findAll().stream()
                .filter(promo -> isPromotionActive(promo.getPromotionTimeSlot(), date, time))
                .collect(Collectors.toList());
    }

    public BigDecimal getDiscountForDish(
            Dish dish,
            int totalQuantity, // Теперь передаем общее количество
            LocalDate currentDate,
            LocalTime currentTime) {

        return dish.getPromotions().stream()
                .filter(promo -> isPromotionActive(promo.getPromotionTimeSlot(), currentDate, currentTime))
                .filter(promo -> totalQuantity >= promo.getMinQuantity())
                .map(DishPromotion::getDiscount)
                .findFirst()
                .orElse(BigDecimal.ZERO);
    }

    private boolean isPromotionActive(
            PromotionTimeSlot promoSlot,
            LocalDate currentDate,
            LocalTime currentTime) {

        return !promoSlot.getStartDate().getWorkDate().isAfter(currentDate) &&
                !promoSlot.getEndDate().getWorkDate().isBefore(currentDate) &&
                !promoSlot.getStartTime().isAfter(currentTime) &&
                !promoSlot.getEndTime().isBefore(currentTime);
    }

    public boolean isMinQuantityMet(Dish dish, int quantity, LocalDate date, LocalTime time) {
        return dish.getPromotions().stream()
                .filter(promo -> isPromotionActive(promo.getPromotionTimeSlot(), date, time))
                .anyMatch(promo -> quantity >= promo.getMinQuantity());
    }

    public int getMinQuantity(Dish dish, LocalDate date, LocalTime time) {
        return dish.getPromotions().stream()
                .filter(promo -> isPromotionActive(promo.getPromotionTimeSlot(), date, time))
                .mapToInt(DishPromotion::getMinQuantity)
                .findFirst()
                .orElse(0);
    }

    public List<DishPromotion> getAllActivePromotions() {
        LocalDate nowDate = LocalDate.now();
        LocalTime nowTime = LocalTime.now();

        return dishPromotionRepository.findAll().stream()
                .filter(promo -> promotionTimeSlotService.isPromotionActive(
                        promo.getPromotionTimeSlot(),
                        nowDate,
                        nowTime))
                .collect(Collectors.toList());
    }

}