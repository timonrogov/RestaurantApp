package com.example.restaurant.enums;

public enum Role {
    CLIENT("КЛИЕНТ"),

    // Новые роли сотрудников
    ADMIN("АДМИНИСТРАТОР"), // Управляет всем (меню, сотрудники, статистика)
    WAITER("ОФИЦИАНТ"),     // Работает с заказами в зале, статусы "Подан"
    COOK("ПОВАР");          // Работает с KDS, статусы "Готовится" -> "Готово"

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
