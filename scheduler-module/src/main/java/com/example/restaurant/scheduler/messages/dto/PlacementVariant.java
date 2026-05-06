package com.example.restaurant.scheduler.messages.dto;

import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Один вариант размещения задачи в расписании ресурса.
 *
 * CookAgent формирует список таких вариантов и возвращает его TaskAgent-у
 * в сообщении PARAMS_RESPONSE. TaskAgent оценивает все полученные варианты
 * от всех поваров и выбирает лучший по свёртке критериев.
 *
 * Виды вариантов (variantName):
 *   "asap"     — как можно раньше, первый свободный слот начиная с notBefore
 *   "jit"      — just-in-time, задача заканчивается ровно к дедлайну
 *   "conflict" — вытесняющий вариант: нужно удалить менее приоритетную задачу
 */
public class PlacementVariant {

    /**
     * ID агента-ресурса, предложившего этот вариант.
     * Формат: "COOK_7" или "EQUIPMENT_TYPE_OVEN".
     * TaskAgent использует это поле для адресации PLANNING_REQUEST.
     */
    private final String resourceAgentId;

    /**
     * Тип варианта: "asap", "jit" или "conflict".
     * CookAgent указывает тип при создании, TaskAgent учитывает при оценке.
     */
    private final String variantName;

    /** Предлагаемое время начала задачи. */
    private final LocalDateTime startTime;

    /** Предлагаемое время окончания задачи (= startTime + durationMinutes). */
    private final LocalDateTime endTime;

    /**
     * ID задачи-кандидата на вытеснение. Только для variantName="conflict".
     * null для вариантов "asap" и "jit".
     * CookAgent передаёт этот ID чтобы TaskAgent знал, кого придётся потеснить.
     */
    private final Long conflictingTaskId;

    // -------------------------------------------------------------------
    // Оценочные поля — заполняются TaskAgent-ом после получения варианта
    // -------------------------------------------------------------------

    /**
     * Итоговая взвешенная оценка варианта по формуле:
     *   totalScore = 0.5 * urgencyScore + 0.3 * speedScore + 0.2 * loadScore
     * Чем выше — тем лучше. TaskAgent выбирает вариант с максимальным totalScore.
     */
    private double totalScore;

    /**
     * Оценка по критерию срочности: насколько endTime близко к deadline.
     * 1.0 — задача заканчивается ровно к дедлайну (идеально).
     * 0.0 — задача заканчивается сразу после notBefore (есть запас).
     * Отрицательные значения обрезаются до 0 (задача не успевает к дедлайну).
     */
    private double urgencyScore;

    /**
     * Оценка по критерию скорости: насколько рано начинается задача.
     * 1.0 — задача начинается ровно в notBefore.
     * 0.0 — задача начинается как можно позже в окне [notBefore, deadline].
     */
    private double speedScore;

    /**
     * Оценка по критерию нагрузки повара.
     * 1.0 — повар совсем свободен.
     * 0.0 — повар полностью загружен.
     * Помогает равномерно распределять задачи между поварами.
     */
    private double loadScore;

    public PlacementVariant(String resourceAgentId,
                            String variantName,
                            LocalDateTime startTime,
                            LocalDateTime endTime,
                            Long conflictingTaskId) {
        this.resourceAgentId = resourceAgentId;
        this.variantName = variantName;
        this.startTime = startTime;
        this.endTime = endTime;
        this.conflictingTaskId = conflictingTaskId;
    }

    // Геттеры
    public String getResourceAgentId() { return resourceAgentId; }
    public String getVariantName() { return variantName; }
    public LocalDateTime getStartTime() { return startTime; }
    public LocalDateTime getEndTime() { return endTime; }
    public Long getConflictingTaskId() { return conflictingTaskId; }
    public double getTotalScore() { return totalScore; }
    public double getUrgencyScore() { return urgencyScore; }
    public double getSpeedScore() { return speedScore; }
    public double getLoadScore() { return loadScore; }

    // Сеттеры только для оценочных полей — они заполняются TaskAgent-ом
    public void setTotalScore(double totalScore) { this.totalScore = totalScore; }
    public void setUrgencyScore(double urgencyScore) { this.urgencyScore = urgencyScore; }
    public void setSpeedScore(double speedScore) { this.speedScore = speedScore; }
    public void setLoadScore(double loadScore) { this.loadScore = loadScore; }

    @Override
    public String toString() {
        return "PlacementVariant{" +
                "resource='" + resourceAgentId + '\'' +
                ", name='" + variantName + '\'' +
                ", start=" + startTime +
                ", end=" + endTime +
                ", score=" + totalScore +
                '}';
    }
}