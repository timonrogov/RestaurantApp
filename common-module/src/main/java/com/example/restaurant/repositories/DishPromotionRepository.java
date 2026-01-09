package com.example.restaurant.repositories;

import com.example.restaurant.models.Dish;
import com.example.restaurant.models.DishPromotion;
import com.example.restaurant.models.OrderTimeSlot;
import com.example.restaurant.models.PromotionTimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface DishPromotionRepository extends JpaRepository<DishPromotion, Long> {
    List<DishPromotion> findByPromotionTimeSlotId(Long promotionTimeSlotId);

    Optional<DishPromotion> findByDishAndPromotionTimeSlot(
            Dish dish,
            PromotionTimeSlot promotionTimeSlot);

    @Query("SELECT dp FROM DishPromotion dp WHERE " +
            ":date BETWEEN dp.promotionTimeSlot.startDate.workDate AND dp.promotionTimeSlot.endDate.workDate " +
            "AND :time BETWEEN dp.promotionTimeSlot.startTime AND dp.promotionTimeSlot.endTime")
    List<DishPromotion> findActivePromotions(
            @Param("date") LocalDate date,
            @Param("time") LocalTime time);


    /**
     * Находит активную акцию для указанного блюда в указанные дату и время.
     * Запрос объединяет DishPromotion -> PromotionTimeSlot -> Day
     * и проверяет все условия: соответствие dishId, нахождение даты в
     * интервале startDate/endDate и времени в интервале startTime/endTime.
     *
     * @param dishId ID блюда.
     * @param currentDate Текущая дата для проверки.
     * @param currentTime Текущее время для проверки.
     * @return Optional с активной акцией, если она найдена.
     */
    @Query("SELECT dp FROM DishPromotion dp " +
            "JOIN dp.promotionTimeSlot ts " +
            "JOIN ts.startDate sd " + // Соединяем с Day для startDate
            "JOIN ts.endDate ed " +   // Соединяем с Day для endDate
            "WHERE dp.dish.id = :dishId " +
            "AND :currentTime BETWEEN ts.startTime AND ts.endTime " +
            "AND :currentDate BETWEEN sd.workDate AND ed.workDate") // Сравниваем даты
    Optional<DishPromotion> findActivePromotionForDish(
            @Param("dishId") Long dishId,
            @Param("currentDate") LocalDate currentDate,
            @Param("currentTime") LocalTime currentTime
    );
}