package com.example.restaurant.scheduler.messages.dto;

import java.time.LocalDateTime;

/**
 * Тело сообщения TASK_PLANNED.
 * Отправляется TaskAgent → OrderAgent.
 *
 * Сообщает OrderAgent-у, что задача успешно запланирована.
 * OrderAgent использует confirmedEnd для вычисления latestPlannedEnd курса,
 * которое потом станет notBefore для следующего курса.
 */
public class TaskPlannedBody {

    private final long taskId;

    /**
     * Подтверждённое время окончания задачи.
     * OrderAgent берёт максимум из confirmedEnd всех задач курса.
     */
    private final LocalDateTime confirmedEnd;

    public TaskPlannedBody(long taskId, LocalDateTime confirmedEnd) {
        this.taskId = taskId;
        this.confirmedEnd = confirmedEnd;
    }

    public long getTaskId() { return taskId; }
    public LocalDateTime getConfirmedEnd() { return confirmedEnd; }
}