package com.example.restaurant.repositories;

import com.example.restaurant.enums.CookingTaskStatus;
import com.example.restaurant.models.CookingTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CookingTaskRepository extends JpaRepository<CookingTask, Long> {

    /**
     * Получить все задачи заказа.
     * OrderAgent использует этот метод для отслеживания общего прогресса.
     */
    @Query("SELECT t FROM CookingTask t WHERE t.orderItem.order.id = :orderId")
    List<CookingTask> findByOrderId(@Param("orderId") Long orderId);

    /**
     * Получить задачи конкретного повара в заданных статусах.
     * KDS-контроллер вызывает этот метод для отображения задач на экране.
     *
     * Пример вызова: findByAssignedCookIdAndStatusIn(cookId, List.of(PLANNED, IN_PROGRESS))
     */
    List<CookingTask> findByAssignedCookIdAndStatusIn(
            Long cookId,
            List<CookingTaskStatus> statuses
    );

    /**
     * Получить все задачи с заданным статусом.
     * Используется администратором для сводного экрана планировщика.
     */
    List<CookingTask> findByStatus(CookingTaskStatus status);

    /**
     * Получить задачи, назначенные на конкретный тип оборудования, с заданным статусом.
     */
    List<CookingTask> findByAssignedEquipmentTypeAndStatus(
            String equipmentType,
            CookingTaskStatus status
    );

    /**
     * Получить задачи заказа для конкретного курса.
     * OrderAgent вызывает при контроле завершения курса.
     */
    @Query("SELECT t FROM CookingTask t " +
            "WHERE t.orderItem.order.id = :orderId " +
            "AND t.orderItem.courseNumber = :courseNumber")
    List<CookingTask> findByOrderIdAndCourseNumber(
            @Param("orderId") Long orderId,
            @Param("courseNumber") int courseNumber
    );

    /**
     * Найти ID заказов со статусом COOKING, у которых ещё нет ни одной задачи.
     * Используется планировщиком для обнаружения новых заказов из customer-app.
     */
    @Query("""
                SELECT o.id FROM Order o
                WHERE o.status = 'COOKING'
                AND NOT EXISTS (
                    SELECT 1 FROM CookingTask ct
                    WHERE ct.orderItem.order.id = o.id
                )
            """)
    List<Long> findCookingOrderIdsWithoutTasks();
}