package com.example.restaurant.scheduler.messages.dto;

import java.time.LocalDateTime;

/**
 * Тело сообщения EQUIPMENT_REQUEST.
 * Отправляется TaskAgent → EquipmentTypeAgent.
 *
 * После того как TaskAgent выбрал подходящего повара, он проверяет,
 * доступно ли оборудование нужного типа в тот же временной интервал.
 * Если оборудование недоступно — вариант отбрасывается, TaskAgent берёт следующий.
 *
 * Важно: интервал [desiredStart, desiredEnd) должен совпадать с интервалом
 * выбранного варианта повара — оборудование и повар работают одновременно.
 */
public class EquipmentRequestBody {

    /** ID задачи. */
    private final long taskId;

    /**
     * Тип оборудования, которое нужно проверить.
     * Например: "OVEN", "GRILL", "FRYER".
     * Совпадает с CookingTaskTemplate.requiredEquipmentType.
     */
    private final String requiredEquipmentType;

    /** Желаемое время начала использования оборудования. */
    private final LocalDateTime desiredStart;

    /** Желаемое время окончания использования оборудования. */
    private final LocalDateTime desiredEnd;

    public EquipmentRequestBody(long taskId,
                                String requiredEquipmentType,
                                LocalDateTime desiredStart,
                                LocalDateTime desiredEnd) {
        this.taskId = taskId;
        this.requiredEquipmentType = requiredEquipmentType;
        this.desiredStart = desiredStart;
        this.desiredEnd = desiredEnd;
    }

    public long getTaskId() { return taskId; }
    public String getRequiredEquipmentType() { return requiredEquipmentType; }
    public LocalDateTime getDesiredStart() { return desiredStart; }
    public LocalDateTime getDesiredEnd() { return desiredEnd; }
}