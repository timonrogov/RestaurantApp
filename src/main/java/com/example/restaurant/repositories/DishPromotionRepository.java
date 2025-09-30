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
}