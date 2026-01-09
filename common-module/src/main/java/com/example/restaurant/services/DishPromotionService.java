package com.example.restaurant.services;

import com.example.restaurant.models.*;
import com.example.restaurant.repositories.DishPromotionRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DishPromotionService {
    private final DishPromotionRepository dishPromotionRepository;
    private final PromotionTimeSlotService promotionTimeSlotService;
    private final DayService dayService;

    @Autowired
    public DishPromotionService(DishPromotionRepository dishPromotionRepository,
                                PromotionTimeSlotService promotionTimeSlotService,
                                DayService dayService) {
        this.dishPromotionRepository = dishPromotionRepository;
        this.promotionTimeSlotService = promotionTimeSlotService;
        this.dayService = dayService;
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

    public Double getDiscountForDish(
            Dish dish,
            int totalQuantity, // Теперь передаем общее количество
            LocalDate currentDate,
            LocalTime currentTime) {

        return dish.getPromotions().stream()
                .filter(promo -> isPromotionActive(promo.getPromotionTimeSlot(), currentDate, currentTime))
                .filter(promo -> totalQuantity >= promo.getMinQuantity())
                .map(DishPromotion::getDiscount)
                .findFirst()
                .orElse(0.0);
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


    public Optional<DishPromotion> findActivePromotionForDish(Long dishId, LocalDate currentDate, LocalTime currentTime) {
        return dishPromotionRepository.findActivePromotionForDish(dishId, currentDate, currentTime);
    }


    /**
     * Создает новую акцию вместе с временным слотом.
     *
     * @param promotion Объект акции (должен содержать dish и minQuantity).
     * @param discountPercentage Процент скидки (целое число от 1 до 100), пришедший с формы.
     * @param startDate Дата начала акции.
     * @param endDate Дата окончания акции.
     * @param startTime Время начала действия акции в течение дня.
     * @param endTime Время окончания действия акции в течение дня.
     * @throws IllegalArgumentException Если данные некорректны или дни не найдены в расписании.
     */
    @Transactional
    public void createPromotionWithSlot(DishPromotion promotion,
                                        int discountPercentage,
                                        LocalDate startDate, LocalDate endDate,
                                        LocalTime startTime, LocalTime endTime) {

        // 1. Валидация скидки
        if (discountPercentage <= 0 || discountPercentage > 100) {
            throw new IllegalArgumentException("Скидка должна быть от 1 до 100%");
        }

        // 2. Конвертация процентов в долю (например, 20 -> 0.2) и установка в сущность
        double discountValue = discountPercentage / 100.0;
        promotion.setDiscount(discountValue);

        // 3. Проверка существования дней в расписании ресторана
        // dayService должен уметь искать по дате.
        // Если метод называется findById, используй его. Если getDayByDate - то его.
        // Здесь я использую getDayByDate, так как он был в твоем старом коде DayService.
        Day startDayEntity = dayService.getDayByDate(startDate);
        Day endDayEntity = dayService.getDayByDate(endDate);

        if (startDayEntity == null || endDayEntity == null) {
            throw new IllegalArgumentException("На выбранные даты не запланирована работа ресторана. Сначала добавьте эти дни в расписание.");
        }

        // 4. Создание и сохранение временного слота (PromotionTimeSlot)
        PromotionTimeSlot timeSlot = new PromotionTimeSlot();
        timeSlot.setStartDate(startDayEntity);
        timeSlot.setEndDate(endDayEntity);
        timeSlot.setStartTime(startTime);
        timeSlot.setEndTime(endTime);

        // Сохраняем слот через его сервис
        PromotionTimeSlot savedSlot = promotionTimeSlotService.createPromotionTimeSlot(timeSlot);

        // 5. Связывание слота с акцией и сохранение акции
        promotion.setPromotionTimeSlot(savedSlot);
        dishPromotionRepository.save(promotion);
    }
}