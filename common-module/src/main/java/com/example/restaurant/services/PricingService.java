package com.example.restaurant.services;

import com.example.restaurant.models.Dish;
import com.example.restaurant.models.DishPromotion;
import com.example.restaurant.models.Order;
import com.example.restaurant.models.OrderItem;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class PricingService {

    private final DishPromotionService dishPromotionService;
    private final PromotionTimeSlotService promotionTimeSlotService;


    /**
     * Рассчитывает общую стоимость заказа без учета скидок.
     * @param order Заказ для расчета.
     * @return Общая стоимость (double).
     */
    public double calculateTotal(Order order) {
        if (order.getOrderItems() == null) {
            return 0.0;
        }
        return order.getOrderItems().stream()
                .mapToDouble(item -> item.getDish().getPrice() * item.getQuantity())
                .sum();
    }


    /**
     * Рассчитывает общую стоимость заказа с учетом всех активных скидок.
     * @param order Заказ для расчета.
     * @return Итоговая стоимость со скидками (double).
     */
    public double calculateTotalWithDiscount(Order order) {
        if (order.getOrderItems() == null) {
            return 0.0;
        }

        double totalWithDiscount = 0.0;
        LocalDate currentDate = LocalDate.now();
        LocalTime currentTime = LocalTime.now();

        for (OrderItem item : order.getOrderItems()) {
            Dish dish = item.getDish();
            int quantity = item.getQuantity();

            // Ищем активную акцию для данного блюда
            Optional<DishPromotion> activePromotionOpt = dishPromotionService.findActivePromotionForDish(dish.getId(), currentDate, currentTime);

            if (activePromotionOpt.isPresent()) {
                // Если акция найдена, применяем скидку
                DishPromotion promotion = activePromotionOpt.get();
                double discountedPrice = dish.getPrice() * (1 - promotion.getDiscount());
                totalWithDiscount += discountedPrice * quantity;
            } else {
                // Если акции нет, используем базовую цену
                totalWithDiscount += dish.getPrice() * quantity;
            }
        }
        return totalWithDiscount;
    }
}