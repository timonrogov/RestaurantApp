package com.example.restaurant.repositories;

import com.example.restaurant.models.Dish;
import com.example.restaurant.models.DishCategory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;

public interface DishRepository extends JpaRepository<Dish, Long> {
    List<Dish> findByCategory(DishCategory category);
    List<Dish> findByIsAvailableTrue();
    List<Dish> findByCategoryAndIsAvailableTrue(DishCategory category);

}
