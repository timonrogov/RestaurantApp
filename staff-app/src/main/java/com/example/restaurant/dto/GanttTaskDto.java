package com.example.restaurant.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Данные одной задачи для диаграммы Ганта.
 *
 * Все времена передаются строками "HH:mm" — удобнее для JS-парсинга
 * без TimeZone-сложностей. isToday определяет, нужно ли рисовать
 * линию текущего времени.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class GanttTaskDto {

    private Long   id;
    private Long   orderId;
    private String tableNumber;

    /** Название блюда */
    private String dishName;
    /** Путь к изображению блюда (готовый URL, например /images/dishes/борщ.jpg) */
    private String dishImagePath;

    /** Название этапа из шаблона (например "Жарка на гриле") */
    private String stepName;
    /** Порядковый номер этапа */
    private int    stepNumber;

    /** Код статуса: PLANNED, IN_PROGRESS, DONE, PENDING, FAILED, CANCELLED */
    private String status;
    /** Отображаемое название статуса */
    private String statusDisplay;

    /** Тип оборудования (GRILL, OVEN, FRYER, STOVE) или null */
    private String equipmentType;

    /** Плановое время начала, формат "HH:mm" */
    private String plannedStart;
    /** Плановое время окончания, формат "HH:mm" */
    private String plannedEnd;
    /** Фактическое время начала, "HH:mm" или null */
    private String actualStart;
    /** Фактическое время окончания, "HH:mm" или null */
    private String actualEnd;

    /** Плановая длительность в минутах (для отображения в попапе) */
    private int    durationMinutes;

    private boolean isOverdue;
    private String  delayReason;
    private int     replanCount;
}