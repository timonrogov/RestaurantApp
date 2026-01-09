package com.example.restaurant.enums;

public enum OrderStatus {
    ASSEMBLY("Сборка"),
    COOKING("Готовится"),
    READY("Готов"),
    SERVED("Подан"),
    CANCELED("Отменен");

    private final String displayName;

    OrderStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}