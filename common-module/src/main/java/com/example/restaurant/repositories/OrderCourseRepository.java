package com.example.restaurant.repositories;

import com.example.restaurant.models.OrderCourse;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderCourseRepository extends JpaRepository<OrderCourse, Long> {

    /**
     * Получить все курсы заказа, отсортированные по номеру.
     * OrderAgent вызывает при инициализации для построения графа зависимостей.
     */
    List<OrderCourse> findByOrderIdOrderByCourseNumberAsc(Long orderId);

    /**
     * Найти конкретный курс заказа по его номеру.
     */
    Optional<OrderCourse> findByOrderIdAndCourseNumber(Long orderId, int courseNumber);
}