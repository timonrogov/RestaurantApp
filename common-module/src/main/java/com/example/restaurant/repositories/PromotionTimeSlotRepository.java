package com.example.restaurant.repositories;

import com.example.restaurant.models.PromotionTimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;

@Repository
public interface PromotionTimeSlotRepository extends JpaRepository<PromotionTimeSlot, Long> {
    @Query("SELECT pts FROM PromotionTimeSlot pts " +
            "WHERE :currentDate BETWEEN pts.startDate.workDate AND pts.endDate.workDate " +
            "AND :currentTime BETWEEN pts.startTime AND pts.endTime")
    Optional<PromotionTimeSlot> findActivePromotion(
            @Param("currentDate") LocalDate currentDate,
            @Param("currentTime") LocalTime currentTime);
}