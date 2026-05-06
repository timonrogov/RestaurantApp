package com.example.restaurant.scheduler.messages.dto;

import lombok.ToString;
import java.time.LocalDateTime;

/**
 * Тело сообщения CANCEL_AND_REPLAN.
 * Отправляется OrderAgent → TaskAgent.
 *
 * Содержит обновленные временные рамки для задачи, которые TaskAgent
 * должен использовать при новом цикле торгов.
 */
@ToString
public class CancelAndReplanBody {

    private final long taskId;

    /** Новое самое раннее время начала. */
    private final LocalDateTime newNotBefore;

    /** Новое идеальное время завершения (JIT). */
    private final LocalDateTime newTargetEndTime;

    /** Новый дедлайн. */
    private final LocalDateTime newDeadline;

    public CancelAndReplanBody(long taskId,
                               LocalDateTime newNotBefore,
                               LocalDateTime newTargetEndTime,
                               LocalDateTime newDeadline) {
        this.taskId = taskId;
        this.newNotBefore = newNotBefore;
        this.newTargetEndTime = newTargetEndTime;
        this.newDeadline = newDeadline;
    }

    public long getTaskId() { return taskId; }
    public LocalDateTime getNewNotBefore() { return newNotBefore; }
    public LocalDateTime getNewTargetEndTime() { return newTargetEndTime; }
    public LocalDateTime getNewDeadline() { return newDeadline; }
}