package com.example.restaurant.repositories;

import com.example.restaurant.enums.CookingTaskStatus;
import com.example.restaurant.models.CookingTask;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface CookingTaskRepository extends JpaRepository<CookingTask, Long> {

    @Query("SELECT t FROM CookingTask t WHERE t.orderItem.order.id = :orderId")
    List<CookingTask> findByOrderId(@Param("orderId") Long orderId);

    List<CookingTask> findByAssignedCookIdAndStatusIn(
            Long cookId, List<CookingTaskStatus> statuses);

    List<CookingTask> findByStatus(CookingTaskStatus status);

    List<CookingTask> findByStatusIn(List<CookingTaskStatus> statuses);

    List<CookingTask> findByAssignedEquipmentTypeAndStatus(
            String equipmentType, CookingTaskStatus status);

    @Query("SELECT t FROM CookingTask t " +
            "WHERE t.orderItem.order.id = :orderId " +
            "AND t.orderItem.courseNumber = :courseNumber")
    List<CookingTask> findByOrderIdAndCourseNumber(
            @Param("orderId") Long orderId,
            @Param("courseNumber") int courseNumber);

    @Query("""
        SELECT o.id FROM Order o
        WHERE o.status = 'COOKING'
        AND NOT EXISTS (
            SELECT 1 FROM CookingTask ct
            WHERE ct.orderItem.order.id = o.id
        )
    """)
    List<Long> findCookingOrderIdsWithoutTasks();

    /**
     * Задачи IN_PROGRESS, у которых плановое время окончания уже прошло.
     * Используется для автоматического обнаружения задержек выполнения.
     */
    @Query("SELECT t FROM CookingTask t " +
            "WHERE t.status = com.example.restaurant.enums.CookingTaskStatus.IN_PROGRESS " +
            "AND t.plannedEndTime IS NOT NULL " +
            "AND t.plannedEndTime < :now")
    List<CookingTask> findOverdueInProgressTasks(@Param("now") LocalDateTime now);

    /**
     * Задачи PLANNED, у которых плановое время начала уже прошло.
     * Используется для автоматического обнаружения задержек начала.
     */
    @Query("SELECT t FROM CookingTask t " +
            "WHERE t.status = com.example.restaurant.enums.CookingTaskStatus.PLANNED " +
            "AND t.plannedStartTime IS NOT NULL " +
            "AND t.plannedStartTime < :now")
    List<CookingTask> findOverduePlannedTasks(@Param("now") LocalDateTime now);

    /**
     * PLANNED-задачи повара, начинающиеся строго позже указанного времени.
     * Используется для сдвига последующих задач при задержке.
     */
    List<CookingTask> findByAssignedCookIdAndStatusAndPlannedStartTimeGreaterThanEqual(
            Long cookId, CookingTaskStatus status, LocalDateTime time);

    /**
     * PLANNED-задачи заказа в курсах строго позже указанного.
     * Используется для сдвига следующих курсов при задержке.
     */
    @Query("SELECT t FROM CookingTask t " +
            "WHERE t.orderItem.order.id = :orderId " +
            "AND t.orderItem.courseNumber > :courseNumber " +
            "AND t.status = com.example.restaurant.enums.CookingTaskStatus.PLANNED")
    List<CookingTask> findNextCourseTasks(
            @Param("orderId") Long orderId,
            @Param("courseNumber") int courseNumber);

    /**
     * Число задач заказа, ещё не завершённых.
     * Если результат == 0 — все задачи выполнены, можно переводить заказ в READY.
     */
    @Query("SELECT COUNT(t) FROM CookingTask t " +
            "WHERE t.orderItem.order.id = :orderId " +
            "AND t.status NOT IN (" +
            "  com.example.restaurant.enums.CookingTaskStatus.DONE, " +
            "  com.example.restaurant.enums.CookingTaskStatus.FAILED, " +
            "  com.example.restaurant.enums.CookingTaskStatus.CANCELLED" +
            ")")
    long countUnfinishedTasksByOrderId(@Param("orderId") Long orderId);

    /**
     * Выборка задач для диаграммы Ганта за конкретный день.
     *
     * Включает задачи, у которых:
     *   - plannedStartTime попадает в диапазон [dayStart, dayEnd), ИЛИ
     *   - статус IN_PROGRESS и actualStartTime в том же диапазоне
     *     (задача могла начаться раньше планового времени)
     *
     * @param dayStart начало рабочего дня (например 2025-05-16T09:00)
     * @param dayEnd   конец рабочего дня  (например 2025-05-16T22:00)
     */
    @Query("""
        SELECT t FROM CookingTask t
        WHERE t.status != com.example.restaurant.enums.CookingTaskStatus.CANCELLED
          AND (
              (t.plannedStartTime >= :dayStart AND t.plannedStartTime < :dayEnd)
              OR (t.status = com.example.restaurant.enums.CookingTaskStatus.IN_PROGRESS
                  AND t.actualStartTime >= :dayStart AND t.actualStartTime < :dayEnd)
          )
        ORDER BY t.plannedStartTime ASC NULLS LAST
        """)
    List<CookingTask> findForGantt(
            @Param("dayStart") LocalDateTime dayStart,
            @Param("dayEnd")   LocalDateTime dayEnd);

    /**
     * Получить актуальные задачи для KDS:
     * 1. Все задачи в статусах PLANNED и IN_PROGRESS (текущая работа).
     * 2. Задачи в статусе DONE, завершенные не раньше указанного времени (обычно начало дня).
     */
    @Query("""
    SELECT t FROM CookingTask t 
    WHERE t.assignedCook.id = :cookId 
    AND (
        t.status IN (
            com.example.restaurant.enums.CookingTaskStatus.PLANNED, 
            com.example.restaurant.enums.CookingTaskStatus.IN_PROGRESS
        )
        OR (
            t.status = com.example.restaurant.enums.CookingTaskStatus.DONE 
            AND t.actualEndTime >= :since
            AND t.orderItem.order.status != com.example.restaurant.enums.OrderStatus.SERVED
            AND t.orderItem.order.status != com.example.restaurant.enums.OrderStatus.CANCELED
        )
    )
""")
    List<CookingTask> findActiveAndRecentlyDoneTasks(
            @Param("cookId") Long cookId,
            @Param("since") LocalDateTime since);

    /**
     * [Исправление 5] PLANNED-задачи того же курса и заказа, назначенные
     * НА ДРУГИХ поваров (не на того, кто начал досрочно).
     *
     * Используется в DispatcherAgent.shiftSubsequentTasks при отрицательном
     * delayMinutes: когда один повар начал задачу раньше плана, задачи
     * его «соседей» по курсу тоже нужно сдвинуть назад, иначе следующий
     * курс приедет к ним раньше, чем они закончат текущий.
     */
    @Query("SELECT t FROM CookingTask t " +
            "WHERE t.orderItem.order.id = :orderId " +
            "AND t.orderItem.courseNumber = :courseNumber " +
            "AND t.assignedCook.id != :excludeCookId " +
            "AND t.status = com.example.restaurant.enums.CookingTaskStatus.PLANNED")
    List<CookingTask> findSameCourseTasksForOtherCooks(
            @Param("orderId") Long orderId,
            @Param("courseNumber") int courseNumber,
            @Param("excludeCookId") Long excludeCookId);

    /**
     * [Исправление 6] Максимальное plannedEndTime среди задач с указанными ID.
     *
     * Заменяет findAll() в OrderAgent.recalculateLatestPlannedEnd():
     * вместо загрузки всей таблицы выполняем один агрегатный запрос.
     */
    @Query("SELECT MAX(t.plannedEndTime) FROM CookingTask t WHERE t.id IN :ids")
    Optional<LocalDateTime> findMaxPlannedEndTimeByIds(
            @Param("ids") Collection<Long> ids);
}