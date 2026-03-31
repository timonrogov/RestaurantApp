package com.example.restaurant.enums;

/**
 * Квалификация (специализация) повара.
 * Определяет, задачи какого типа может выполнять конкретный сотрудник.
 * Используется планировщиком при матчинге задач и поваров.
 */
public enum CookSpecialization {

    HOT_SHOP("Горячий цех"),
    COLD_SHOP("Холодный цех"),
    PASTRY("Кондитерский цех"),
    GRILL("Гриль"),
    UNIVERSAL("Универсальный");

    private final String displayName;

    CookSpecialization(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}