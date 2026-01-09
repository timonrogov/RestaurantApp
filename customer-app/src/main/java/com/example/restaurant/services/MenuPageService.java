package com.example.restaurant.services;

import com.example.restaurant.models.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Service
@RequiredArgsConstructor
public class MenuPageService {

    private final DishService dishService;
    private final DishCategoryService dishCategoryService;
    private final DishPromotionService dishPromotionService;
    private final DishReviewService dishReviewService;
    private final RestaurantScheduleService restaurantScheduleService;
    private final DayService dayService;
    private final CartService cartService;
    private final PricingService pricingService;

    /**
     * Собирает все необходимые данные для отображения страницы меню.
     * @param principal Текущий пользователь (может быть null).
     * @return Map с атрибутами для модели.
     */
    public Map<String, Object> getMenuPageData(Principal principal,
                                               HttpServletRequest request,
                                               HttpServletResponse response) {
        Map<String, Object> data = new HashMap<>();

        LocalDate currentDate = LocalDate.now();
        LocalTime currentTime = LocalTime.now();

        // 1. Статус ресторана
        Map<String, Object> restaurantStatus = restaurantScheduleService.getRestaurantStatus();
        data.put("restaurantStatus", restaurantStatus);

        // Если ресторан закрыт или еще не открылся
        if (!"open".equals(restaurantStatus.get("status"))) {
            if ("not_opened_yet".equals(restaurantStatus.get("status"))) {
                Day today = dayService.getDayByDate(currentDate);
                if (today != null) {
                    data.put("openingTime", today.getStartTime());
                    data.put("closingTime", today.getEndTime());
                }
            }
            return data; // Возвращаем только статус
        }

        // 2. Данные для открытого ресторана
        data.put("currentDate", currentDate);
        data.put("currentTime", currentTime);

        // Акции
        List<DishPromotion> activePromotions = dishPromotionService.getActivePromotions(currentDate, currentTime);
        data.put("activePromotions", activePromotions);

        // Блюда по категориям
        List<DishCategory> categories = dishCategoryService.getAllCategories();
        Map<DishCategory, List<Dish>> dishesByCategory = new LinkedHashMap<>();

        for (DishCategory category : categories) {
            List<Dish> dishes = dishService.getDishesByCategory(category);

            // Обогащаем каждое блюдо статистикой (рейтинг) и акциями
            dishes.forEach(dish -> {
                // Статистика отзывов
                Map<String, Object> stats = dishReviewService.getDishReviewStats(dish.getId());
                dish.setStats(stats);

                // Активные акции для конкретного блюда
                // (Логика, которая была в контроллере или DishService.getAllAvailableDishes)
                List<DishPromotion> activeDishPromos = dish.getPromotions().stream()
                        .filter(promo -> activePromotions.contains(promo)) // Простая проверка, так как activePromotions уже отфильтрованы
                        .toList();
                dish.setActivePromotions(activeDishPromos);
            });

            dishesByCategory.put(category, dishes);
        }
        data.put("dishesByCategory", dishesByCategory);

        // 3. Данные корзины (если пользователь авторизован)
        if (principal != null) {
            addCartDataToModel(data, principal, request, response);
        } else {
            // Значения по умолчанию для гостя
            data.put("hasItems", false);
            data.put("totalPrice", 0.0);
            data.put("totalDiscounted", 0.0);
            data.put("hasDiscount", false);
        }

        return data;
    }

    // Вспомогательный метод для расчета корзины
    private void addCartDataToModel(Map<String, Object> data,
                                    Principal principal,
                                    HttpServletRequest request,
                                    HttpServletResponse response) {
        try {
            Order currentOrder = cartService.getCurrentCart(principal, request, response);
            if (currentOrder != null && !currentOrder.getOrderItems().isEmpty()) {
                data.put("hasItems", true);

                double totalPrice = pricingService.calculateTotal(currentOrder);
                double totalDiscounted = pricingService.calculateTotalWithDiscount(currentOrder);

                data.put("totalPrice", totalPrice);
                data.put("totalDiscounted", totalDiscounted);
                data.put("hasDiscount", totalDiscounted < totalPrice);
            } else {
                data.put("hasItems", false);
                data.put("totalPrice", 0.0);
                data.put("totalDiscounted", 0.0);
                data.put("hasDiscount", false);
            }
        } catch (Exception e) {
            // Если не удалось получить корзину (например, юзер не найден), считаем ее пустой
            data.put("hasItems", false);
        }
    }
}