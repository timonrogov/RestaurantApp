package com.example.restaurant.repositories;

import com.example.restaurant.models.Day;
import com.example.restaurant.models.OrderTimeSlot;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderTimeSlotRepository extends JpaRepository<OrderTimeSlot, Long> {
    List<OrderTimeSlot> findByDayWorkDate(LocalDate workDate);
}
