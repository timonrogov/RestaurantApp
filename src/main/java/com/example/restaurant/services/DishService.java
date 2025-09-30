package com.example.restaurant.services;
import com.example.restaurant.models.Day;
import com.example.restaurant.models.Dish;
import com.example.restaurant.models.DishCategory;
import com.example.restaurant.models.DishPromotion;
import com.example.restaurant.repositories.DayRepository;
import com.example.restaurant.repositories.DishRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DishService {
    private final DishRepository dishRepository;
    private final DayRepository dayRepository;
    private final PromotionTimeSlotService promotionTimeSlotService;

    @Autowired
    public DishService(DishRepository dishRepository, DayRepository dayRepository, PromotionTimeSlotService promotionTimeSlotService) {
        this.dishRepository = dishRepository;
        this.dayRepository = dayRepository;
        this.promotionTimeSlotService = promotionTimeSlotService;
    }

    public List<Dish> getAllDishes() {
        return dishRepository.findAll();
    }

    public Dish getDishById(Long id) {
        return dishRepository.findById(id).orElse(null);
    }

    public Dish createDish(Dish dish) {
        return dishRepository.save(dish);
    }

    public void deleteDish(Long id) {
        dishRepository.deleteById(id);
    }

    public List<Dish> getAllDishesWithActivePromotions(LocalDate date, LocalTime time) {
        List<Dish> dishes = dishRepository.findAll();
        dishes.forEach(dish -> {
            List<DishPromotion> activePromos = dish.getPromotions().stream()
                    .filter(promo -> promotionTimeSlotService.isPromotionActive(promo.getPromotionTimeSlot(), date, time))
                    .collect(Collectors.toList());
            dish.setActivePromotions(activePromos);
        });
        return dishes;
    }

    public List<Dish> getDishesByCategory(DishCategory category) {
        return dishRepository.findByCategoryAndIsAvailableTrue(category);
    }

    public List<Dish> getAllAvailableDishes(LocalDate date, LocalTime time) {
        List<Dish> dishes = dishRepository.findByIsAvailableTrue();
        dishes.forEach(dish -> {
            List<DishPromotion> activePromos = dish.getPromotions().stream()
                    .filter(promo -> promotionTimeSlotService.isPromotionActive(promo.getPromotionTimeSlot(), date, time))
                    .collect(Collectors.toList());
            dish.setActivePromotions(activePromos);
        });
        return dishes;
    }

    public void toggleAvailability(Long id) {
        Dish dish = dishRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dish not found"));
        dish.setAvailable(!dish.isAvailable());
        dishRepository.save(dish);
    }

    public List<Dish> getDishesWithoutActivePromotions() {
        LocalDate currentDate = LocalDate.now();
        List<Dish> allDishes = dishRepository.findAll();
        return allDishes.stream()
                .filter(dish -> {
                    List<DishPromotion> promotions = dish.getPromotions();
                    return promotions.stream()
                            .noneMatch(promo -> promo.getPromotionTimeSlot().getEndDate().getWorkDate().isAfter(currentDate));
                })
                .collect(Collectors.toList());
    }

    public Map<String, Object> getRestaurantStatus(LocalDate date, LocalTime time) {
        Optional<Day> dayOpt = dayRepository.findById(date);

        if (dayOpt.isEmpty()) {
            return Map.of(
                    "status", "closed_today",
                    "message", "Извините, сегодня ресторан не работает"
            );
        }

        Day day = dayOpt.get();
        if (time.isBefore(day.getStartTime())) {
            return Map.of(
                    "status", "not_opened_yet",
                    "message", "Ресторан еще закрыт, он откроется в " + day.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                    "openingTime", day.getStartTime()
            );
        }

        if (time.isAfter(day.getEndTime())) {
            return Map.of(
                    "status", "already_closed",
                    "message", "Извините, ресторан уже закрыт"
            );
        }

        return Map.of(
                "status", "open",
                "message", ""
        );
    }
}
