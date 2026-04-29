package com.example.restaurant.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Корневой объект ответа для диаграммы Ганта.
 * Возвращается эндпоинтом GET /admin/scheduler/api/gantt.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class GanttDataDto {

    /** Дата в формате YYYY-MM-DD */
    private String date;
    /** Начало рабочего дня "HH:mm" */
    private String dayStart;
    /** Конец рабочего дня "HH:mm" */
    private String dayEnd;
    /** Текущее время "HH:mm" (только если isToday = true) */
    private String currentTime;
    /** true — запрошен сегодняшний день (нужно показывать линию "Сейчас") */
    private boolean isToday;

    /** Строки диаграммы — по одной на каждого активного повара */
    private List<GanttCookDto> cooks;
}