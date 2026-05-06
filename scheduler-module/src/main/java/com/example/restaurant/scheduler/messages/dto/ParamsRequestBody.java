package com.example.restaurant.scheduler.messages.dto;

import com.example.restaurant.enums.CookSpecialization;
import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Тело сообщения PARAMS_REQUEST.
 * Отправляется TaskAgent → CookAgent.
 *
 * Содержит всё необходимое для того, чтобы CookAgent мог
 * найти подходящие варианты размещения в своём расписании.
 */
@ToString
public class ParamsRequestBody {

    /** ID задачи (CookingTask.id). CookAgent вернёт это же значение в ответе. */
    private final long taskId;

    /** Длительность задачи в минутах (берётся из CookingTaskTemplate.durationMinutes). */
    private final int durationMinutes;

    /** Требуемая специализация. CookAgent сверяет со своим профилем. */
    private final CookSpecialization requiredSpecialization;

    /**
     * Самое раннее время начала задачи.
     * Для первого курса = moment планирования.
     * Для N-го курса = конец (N-1)-го курса + syncGapMinutes.
     * Для задач с зависимостью от предыдущего этапа = конец предыдущего этапа.
     */
    private final LocalDateTime notBefore;

    /**
     * Крайний срок: задача должна завершиться не позже этого момента.
     * CookAgent использует дедлайн для построения JIT-варианта и оценки
     * кандидатов на вытеснение.
     */
    private final LocalDateTime deadline;

    /**
     * ID заказа, которому принадлежит задача.
     * CookAgent НЕ должен вытеснять задачи с тем же orderId —
     * нельзя нарушать приоритеты внутри одного заказа.
     */
    private final long orderIdForConflictCheck;

    private final LocalDateTime targetEndTime;

    public ParamsRequestBody(long taskId,
                             int durationMinutes,
                             CookSpecialization requiredSpecialization,
                             LocalDateTime notBefore,
                             LocalDateTime deadline,
                             long orderIdForConflictCheck,
                             LocalDateTime targetEndTime) {
        this.taskId = taskId;
        this.durationMinutes = durationMinutes;
        this.requiredSpecialization = requiredSpecialization;
        this.notBefore = notBefore;
        this.deadline = deadline;
        this.orderIdForConflictCheck = orderIdForConflictCheck;
        this.targetEndTime = targetEndTime;
    }

    public long getTaskId() { return taskId; }
    public int getDurationMinutes() { return durationMinutes; }
    public CookSpecialization getRequiredSpecialization() { return requiredSpecialization; }
    public LocalDateTime getNotBefore() { return notBefore; }
    public LocalDateTime getDeadline() { return deadline; }
    public long getOrderIdForConflictCheck() { return orderIdForConflictCheck; }
    public LocalDateTime getTargetEndTime() { return targetEndTime; }
}