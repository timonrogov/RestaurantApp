package com.example.restaurant.enums;

public enum CallStatus {
    ACTIVE("Активен"),
    CLOSED("Закрыт");

    private final String displayName;

    CallStatus(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}