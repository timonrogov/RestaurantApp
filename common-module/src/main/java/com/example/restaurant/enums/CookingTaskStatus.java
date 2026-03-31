package com.example.restaurant.enums;

/**
 * Статус задачи по приготовлению блюда.
 * Жизненный цикл задачи:
 *   PENDING → PLANNED → IN_PROGRESS → DONE
 *                    ↘ CANCELLED (заказ отменён)
 *                    ↘ FAILED    (не удалось запланировать)
 */
public enum CookingTaskStatus {

    PENDING("Ожидает планирования"),
    PLANNED("Запланирована"),
    IN_PROGRESS("Выполняется"),
    DONE("Готово"),
    CANCELLED("Отменена"),
    FAILED("Не удалось запланировать");

    private final String displayName;

    CookingTaskStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}