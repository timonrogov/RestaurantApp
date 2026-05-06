package com.example.restaurant.scheduler.messages.dto;

import lombok.ToString;
import java.time.LocalDateTime;

/**
 * Тело сообщения INIT для TaskAgent.
 * Передает динамические временные рамки, вычисленные OrderAgent-ом.
 */
@ToString
public class InitTaskPayload {
    private final LocalDateTime notBefore;
    private final LocalDateTime targetEndTime;
    private final LocalDateTime deadline;

    public InitTaskPayload(LocalDateTime notBefore, LocalDateTime targetEndTime, LocalDateTime deadline) {
        this.notBefore = notBefore;
        this.targetEndTime = targetEndTime;
        this.deadline = deadline;
    }

    public LocalDateTime getNotBefore() { return notBefore; }
    public LocalDateTime getTargetEndTime() { return targetEndTime; }
    public LocalDateTime getDeadline() { return deadline; }
}