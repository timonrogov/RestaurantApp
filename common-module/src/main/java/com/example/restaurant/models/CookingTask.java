package com.example.restaurant.models;

import com.example.restaurant.enums.CookingTaskStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

/**
 * Конкретная задача по приготовлению — «живой» экземпляр шаблона.
 *
 * Создаётся планировщиком в момент, когда заказ переходит в статус COOKING.
 * По одной задаче на каждый шаблон (CookingTaskTemplate) каждого блюда в заказе.
 *
 * Жизненный цикл:
 *   1. Создаётся со статусом PENDING
 *   2. После успешных переговоров агентов → PLANNED (заполняются assignedCook, planned*)
 *   3. Когда повар нажал «Начать» на KDS → IN_PROGRESS (заполняется actualStartTime)
 *   4. Когда повар нажал «Готово» → DONE (заполняется actualEndTime)
 *
 * Если заказ отменяется → CANCELLED.
 * Если планировщик не смог найти ресурс → FAILED.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cooking_task")
public class CookingTask {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Позиция заказа, для которой создана эта задача.
     * Через orderItem → dish можно получить блюдо, а через orderItem → order — заказ.
     */
    @ManyToOne
    @JoinColumn(name = "order_item_id", nullable = false)
    private OrderItem orderItem;

    /**
     * Шаблон, по которому создана эта задача.
     * Содержит информацию о длительности, специализации, типе оборудования.
     */
    @ManyToOne
    @JoinColumn(name = "template_id", nullable = false)
    private CookingTaskTemplate template;

    /**
     * Текущий статус задачи.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CookingTaskStatus status = CookingTaskStatus.PENDING;

    /**
     * Назначенный повар.
     * null до момента планирования (статус PENDING).
     * Заполняется планировщиком при переводе в PLANNED.
     */
    @ManyToOne
    @JoinColumn(name = "assigned_cook_id")
    private CookProfile assignedCook;

    /**
     * Тип оборудования, на котором выполняется задача.
     * Хранится строковый код типа (например, "OVEN", "GRILL"), а не ссылка
     * на конкретную единицу оборудования, потому что агент оборудования
     * работает на уровне типа, управляя суммарной ёмкостью всех единиц этого типа.
     * null, если задача не требует оборудования.
     */
    @Column(name = "assigned_equipment_type")
    private String assignedEquipmentType;

    /**
     * Плановое время начала выполнения задачи.
     * Вычисляется планировщиком, null до планирования.
     */
    @Column(name = "planned_start_time")
    private LocalDateTime plannedStartTime;

    /**
     * Изначально запланированное время начала.
     * Фиксируется один раз при самом первом переводе в статус PLANNED.
     * Служит эталоном (baseline) для вычисления общих задержек по задаче.
     */
    @Column(name = "initial_planned_start_time")
    private LocalDateTime initialPlannedStartTime;

    /**
     * Плановое время окончания выполнения задачи.
     * Вычисляется планировщиком, null до планирования.
     */
    @Column(name = "planned_end_time")
    private LocalDateTime plannedEndTime;

    /**
     * Изначально запланированное время окончания.
     * Служит эталоном для вычисления задержек в процессе выполнения (IN_PROGRESS).
     */
    @Column(name = "initial_planned_end_time")
    private LocalDateTime initialPlannedEndTime;

    /**
     * Флаг локальной просрочки (по вине самого повара).
     * Устанавливается в true автоматическим планировщиком, когда он сдвигает задачу
     * из-за того, что повар вовремя не нажал "Начать" или "Готово".
     */
    @Column(name = "is_local_overdue", nullable = false, columnDefinition = "boolean default false")
    private boolean localOverdue = false;

    /**
     * Фактическое время начала.
     * Заполняется когда повар нажимает «Начать» на KDS-экране.
     */
    @Column(name = "actual_start_time")
    private LocalDateTime actualStartTime;

    /**
     * Фактическое время окончания.
     * Заполняется когда повар нажимает «Готово» на KDS-экране.
     */
    @Column(name = "actual_end_time")
    private LocalDateTime actualEndTime;

    /**
     * Счётчик перепланирований.
     * Увеличивается каждый раз, когда задача вытесняется из расписания.
     * Планировщик не допускает вытеснения если счётчик >= MAX_REPLAN (=3).
     */
    @Column(name = "replan_count", nullable = false)
    private int replanCount = 0;

    /**
     * Причина задержки, введённая поваром при нажатии кнопки «Задержка» на KDS.
     * null если задержки не было.
     */
    @Column(name = "delay_reason")
    private String delayReason;

    // -----------------------------------------------------------------------
    // Вспомогательные методы для удобства
    // -----------------------------------------------------------------------

    /**
     * Возвращает true, если задача просрочена:
     * плановое время окончания уже прошло, а задача ещё не DONE.
     */
    public boolean isOverdue() {
        return plannedEndTime != null
                && LocalDateTime.now().isAfter(plannedEndTime)
                && status != CookingTaskStatus.DONE
                && status != CookingTaskStatus.CANCELLED;
    }

    /**
     * Возвращает минуты, оставшиеся до планового окончания.
     * Отрицательное значение означает задержку.
     * null если задача ещё не запланирована.
     */
    public Long getMinutesLeft() {
        if (plannedEndTime == null) return null;
        return java.time.temporal.ChronoUnit.MINUTES.between(LocalDateTime.now(), plannedEndTime);
    }
}