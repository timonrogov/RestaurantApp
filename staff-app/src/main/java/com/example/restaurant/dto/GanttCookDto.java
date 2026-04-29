package com.example.restaurant.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * Данные одного повара для диаграммы Ганта: строка на диаграмме.
 */
@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class GanttCookDto {

    private Long   id;
    /** Полное имя повара */
    private String name;
    /** Отображаемое название специализации */
    private String specialization;

    /** Задачи повара за выбранный день, отсортированные по plannedStart */
    private List<GanttTaskDto> tasks;
}