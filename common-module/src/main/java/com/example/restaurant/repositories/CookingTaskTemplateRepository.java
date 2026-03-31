package com.example.restaurant.repositories;

import com.example.restaurant.models.CookingTaskTemplate;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CookingTaskTemplateRepository extends JpaRepository<CookingTaskTemplate, Long> {

    /**
     * Получить все этапы приготовления блюда, отсортированные по порядку.
     * OrderAgent вызывает этот метод для каждого блюда в заказе при инициализации.
     */
    List<CookingTaskTemplate> findByDishIdOrderByStepNumberAsc(Long dishId);

    /**
     * Проверить, есть ли хотя бы один шаблон для данного блюда.
     * Удобно при валидации блюда перед добавлением в меню.
     */
    boolean existsByDishId(Long dishId);
}