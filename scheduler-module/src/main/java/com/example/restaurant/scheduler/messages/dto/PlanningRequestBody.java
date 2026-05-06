package com.example.restaurant.scheduler.messages.dto;

import lombok.ToString;

/**
 * Тело сообщения PLANNING_REQUEST.
 * Отправляется TaskAgent → CookAgent (или EquipmentTypeAgent).
 *
 * TaskAgent выбрал лучший вариант и теперь просит конкретный ресурс
 * официально зарезервировать временной слот.
 *
 * Ресурс обязан ещё раз проверить, не занят ли слот (гонки нет в однопоточной
 * модели, но проверка нужна для варианта "conflict" — пока шли переговоры
 * другой TaskAgent мог занять того же кандидата на вытеснение).
 */
@ToString
public class PlanningRequestBody {

    /** ID задачи, для которой резервируется слот. */
    private final long taskId;

    /**
     * Выбранный вариант размещения.
     * Ресурс смотрит на variant.variantName:
     *   "asap" / "jit" — просто добавить слот
     *   "conflict"     — удалить conflictingTaskId из расписания, добавить новый
     */
    private final PlacementVariant chosenVariant;

    /**
     * ID заказа, которому принадлежит задача.
     * Нужен CookAgent-у при создании ScheduleSlot — orderId хранится в слоте
     * для проверки вытеснения в будущих переговорах (своих вытеснять нельзя).
     */
    private final long orderId;

    public PlanningRequestBody(long taskId, PlacementVariant chosenVariant, long orderId) {
        this.taskId = taskId;
        this.chosenVariant = chosenVariant;
        this.orderId = orderId;
    }

    public long getTaskId() { return taskId; }
    public PlacementVariant getChosenVariant() { return chosenVariant; }
    public long getOrderId() { return orderId; }
}