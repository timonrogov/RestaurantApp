package com.example.restaurant.scheduler.schedule;

import com.example.restaurant.scheduler.messages.dto.PlacementVariant;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Один занятый временной слот в расписании ресурса (повара или оборудования).
 *
 * Слот создаётся когда агент-ресурс получает PLANNING_REQUEST и успешно
 * резервирует время. Удаляется при вытеснении (REMOVE_TASK) или отмене заказа.
 *
 * Поля taskId и orderId — неизменяемые идентификаторы: по ним CookAgent ищет
 * кандидатов на вытеснение и проверяет, не принадлежит ли конфликтующий слот
 * тому же заказу (своих вытеснять нельзя).
 */
public class ScheduleSlot {

    /** ID задачи (CookingTask.id). Уникален — один слот на одну задачу у ресурса. */
    private final long taskId;

    /**
     * ID заказа, которому принадлежит задача.
     * Используется CookAgent при поиске кандидатов на вытеснение:
     * нельзя вытеснять задачи с тем же orderId.
     */
    private final long orderId;

    private LocalDateTime startTime;
    private LocalDateTime endTime;

    /**
     * Вариант размещения, по которому создан этот слот.
     * Хранится для отладки и для откатов: если вытеснение не удалось,
     * CookAgent восстанавливает расписание из сохранённой копии слотов.
     */
    private final PlacementVariant variant;

    public ScheduleSlot(long taskId,
                        long orderId,
                        LocalDateTime startTime,
                        LocalDateTime endTime,
                        PlacementVariant variant) {
        this.taskId = taskId;
        this.orderId = orderId;
        this.startTime = startTime;
        this.endTime = endTime;
        this.variant = variant;
    }

    // -----------------------------------------------------------------------
    // Геттеры
    // -----------------------------------------------------------------------

    public long getTaskId() { return taskId; }
    public long getOrderId() { return orderId; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public PlacementVariant getVariant() { return variant; }

    // -----------------------------------------------------------------------
    // Вспомогательные методы
    // -----------------------------------------------------------------------

    /**
     * Длительность слота в минутах.
     * Используется при расчёте нагрузки ресурса (occupancyRate).
     */
    public long getDurationMinutes() {
        return ChronoUnit.MINUTES.between(startTime, endTime);
    }

    /**
     * Пересекается ли этот слот с интервалом [from, to)?
     *
     * Два интервала [a,b) и [c,d) пересекаются тогда и только тогда,
     * когда a < d && c < b.
     * Слоты, стоящие вплотную (b == c), НЕ считаются пересекающимися —
     * повар может сразу после одной задачи начать следующую.
     *
     * @param from начало проверяемого интервала (включительно)
     * @param to   конец проверяемого интервала (не включая)
     * @return true если пересекается
     */
    public boolean overlapsWith(LocalDateTime from, LocalDateTime to) {
        return startTime.isBefore(to) && from.isBefore(endTime);
    }

    @Override
    public String toString() {
        return "ScheduleSlot{taskId=" + taskId +
                ", orderId=" + orderId +
                ", start=" + startTime +
                ", end=" + endTime + "}";
    }
}