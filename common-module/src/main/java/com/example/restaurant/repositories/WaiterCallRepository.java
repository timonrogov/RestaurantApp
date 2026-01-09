package com.example.restaurant.repositories;

import com.example.restaurant.enums.CallStatus;
import com.example.restaurant.models.Client;
import com.example.restaurant.models.WaiterCall;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface WaiterCallRepository extends JpaRepository<WaiterCall, Long> {
    // Найти все активные вызовы, сначала старые (чтобы быстрее обслужить)
    List<WaiterCall> findByStatusOrderByCallTimeAsc(CallStatus status);

    // Подсчитать количество активных (для бейджика)
    Long countByStatus(CallStatus status);

    // Проверка на дубликат: тот же стол, статус Активен
    boolean existsByTableNumberAndStatus(String tableNumber, CallStatus status);
}