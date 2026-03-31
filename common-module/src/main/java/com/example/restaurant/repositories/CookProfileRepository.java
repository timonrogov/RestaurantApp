package com.example.restaurant.repositories;

import com.example.restaurant.enums.CookSpecialization;
import com.example.restaurant.models.CookProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CookProfileRepository extends JpaRepository<CookProfile, Long> {

    /**
     * Найти профиль повара по ID сотрудника.
     * Используется, например, когда администратор переключает доступность сотрудника.
     */
    Optional<CookProfile> findByEmployeeId(Long employeeId);

    /**
     * Найти всех активных поваров с нужной специализацией.
     * Вызывается планировщиком при поиске ресурсов для задачи.
     */
    List<CookProfile> findBySpecializationAndIsActiveTrue(CookSpecialization specialization);

    /**
     * Найти всех активных поваров любой специализации.
     * Используется при инициализации планировщика.
     */
    List<CookProfile> findByIsActiveTrue();

    /**
     * Проверить, существует ли уже профиль для данного сотрудника.
     * Нужно при создании профиля, чтобы не создавать дубликаты.
     */
    boolean existsByEmployeeId(Long employeeId);
}