package com.example.restaurant.scheduler.messages.dto;

import lombok.ToString;

import java.time.LocalDateTime;

/**
 * Тело сообщения EQUIPMENT_RESPONSE.
 * Отправляется EquipmentTypeAgent → TaskAgent.
 *
 * Если available=true — оборудование данного типа имеет свободную ёмкость
 * в запрошенный интервал. TaskAgent может включать это в выбранный вариант.
 *
 * Если available=false — всё оборудование данного типа занято.
 * TaskAgent должен попробовать следующий вариант повара (может, другое время).
 */
@ToString
public class EquipmentResponseBody {

    private final boolean available;

    /** ID задачи (зеркало из запроса). */
    private final long taskId;

    /**
     * Тип оборудования (зеркало из запроса).
     * Нужен TaskAgent-у чтобы понять, для какого запроса пришёл ответ.
     */
    private final String equipmentType;

    /**
     * Подтверждённое время начала.
     * Обычно совпадает с desiredStart. null если available=false.
     */
    private final LocalDateTime confirmedStart;

    /**
     * Подтверждённое время окончания. null если available=false.
     */
    private final LocalDateTime confirmedEnd;

    public EquipmentResponseBody(boolean available,
                                 long taskId,
                                 String equipmentType,
                                 LocalDateTime confirmedStart,
                                 LocalDateTime confirmedEnd) {
        this.available = available;
        this.taskId = taskId;
        this.equipmentType = equipmentType;
        this.confirmedStart = confirmedStart;
        this.confirmedEnd = confirmedEnd;
    }

    /** Фабричный метод для удобного создания ответа о недоступности. */
    public static EquipmentResponseBody unavailable(long taskId, String equipmentType) {
        return new EquipmentResponseBody(false, taskId, equipmentType, null, null);
    }

    public boolean isAvailable() { return available; }
    public long getTaskId() { return taskId; }
    public String getEquipmentType() { return equipmentType; }
    public LocalDateTime getConfirmedStart() { return confirmedStart; }
    public LocalDateTime getConfirmedEnd() { return confirmedEnd; }
}