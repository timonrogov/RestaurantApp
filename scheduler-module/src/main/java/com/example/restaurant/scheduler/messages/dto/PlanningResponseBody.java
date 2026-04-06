package com.example.restaurant.scheduler.messages.dto;

import java.time.LocalDateTime;

/**
 * Тело сообщения PLANNING_RESPONSE.
 * Отправляется CookAgent (или EquipmentTypeAgent) → TaskAgent.
 *
 * Если success=true — слот зарезервирован, можно сохранять результат в БД.
 * Если success=false — слот не удалось зарезервировать (занят).
 *   TaskAgent должен попробовать следующий вариант из своего списка.
 */
public class PlanningResponseBody {

    private final boolean success;

    /** ID задачи (зеркало из запроса). */
    private final long taskId;

    /**
     * Подтверждённое время начала.
     * Обычно совпадает с запрошенным, но может незначительно отличаться
     * если ресурс скорректировал слот (например, выровнял по минутам).
     * null если success=false.
     */
    private final LocalDateTime confirmedStart;

    /**
     * Подтверждённое время окончания.
     * null если success=false.
     */
    private final LocalDateTime confirmedEnd;

    public PlanningResponseBody(boolean success,
                                long taskId,
                                LocalDateTime confirmedStart,
                                LocalDateTime confirmedEnd) {
        this.success = success;
        this.taskId = taskId;
        this.confirmedStart = confirmedStart;
        this.confirmedEnd = confirmedEnd;
    }

    /** Фабричный метод для удобного создания ответа об отказе. */
    public static PlanningResponseBody failure(long taskId) {
        return new PlanningResponseBody(false, taskId, null, null);
    }

    public boolean isSuccess() { return success; }
    public long getTaskId() { return taskId; }
    public LocalDateTime getConfirmedStart() { return confirmedStart; }
    public LocalDateTime getConfirmedEnd() { return confirmedEnd; }
}