package com.example.restaurant.scheduler.messages.dto;

import lombok.ToString;

/**
 * Тело сообщения TASK_DELAY_EVENT.
 * Отправляется SchedulerService → DispatcherAgent.
 *
 * Повар нажал кнопку «Задержка» на KDS-экране.
 * DispatcherAgent находит соответствующий OrderAgent и пересчитывает
 * notBefore для задач следующих курсов.
 */
@ToString
public class TaskDelayBody {

    private final long taskId;

    /** На сколько минут задерживается задача. */
    private final int delayMinutes;

    /**
     * Причина задержки, введённая поваром (может быть null если не указана).
     * Сохраняется в CookingTask.delayReason.
     */
    private final String reason;

    public TaskDelayBody(long taskId, int delayMinutes, String reason) {
        this.taskId = taskId;
        this.delayMinutes = delayMinutes;
        this.reason = reason;
    }

    public long getTaskId() { return taskId; }
    public int getDelayMinutes() { return delayMinutes; }
    public String getReason() { return reason; }
}