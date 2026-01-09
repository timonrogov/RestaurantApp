package com.example.restaurant.enums;

public enum AccountStatus {
    ACTIVE("Активный"),
    BANNED("Заблокирован"),
    DELETED("Удален");

    private final String displayName;

    AccountStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}