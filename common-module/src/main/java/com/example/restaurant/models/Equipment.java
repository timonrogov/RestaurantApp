package com.example.restaurant.models;

import jakarta.persistence.*;
import lombok.*;

/**
 * Единица кухонного оборудования.
 *
 * Оборудование является вторым типом ресурсов в планировщике (наряду с поварами).
 * Ключевая особенность: одна единица оборудования может одновременно
 * использоваться несколькими задачами (например, в духовке помещается 3 блюда).
 * Это контролируется полем maxParallelTasks.
 *
 * Примеры оборудования:
 *   name="Духовка №1", equipmentType="OVEN",   maxParallelTasks=3
 *   name="Гриль",      equipmentType="GRILL",   maxParallelTasks=2
 *   name="Фритюр №1",  equipmentType="FRYER",   maxParallelTasks=1
 *   name="Плита №2",   equipmentType="STOVE",   maxParallelTasks=4
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "equipment")
public class Equipment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Человекочитаемое название, например «Духовка №1».
     * Отображается на KDS-экране и в интерфейсе администратора.
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Строковый код типа оборудования.
     * Используется планировщиком при матчинге:
     * шаблон задачи указывает requiredEquipmentType,
     * планировщик ищет оборудование с совпадающим типом.
     *
     * Рекомендуемые значения: "OVEN", "FRYER", "STOVE", "GRILL", "STEAMER".
     * Можно расширять без изменения кода — это просто строка.
     */
    @Column(name = "equipment_type", nullable = false)
    private String equipmentType;

    /**
     * Максимальное число задач, выполняемых на этом оборудовании одновременно.
     * Планировщик не назначит больше задач, чем это значение.
     */
    @Column(name = "max_parallel_tasks", nullable = false)
    private int maxParallelTasks = 1;

    /**
     * Флаг работоспособности.
     * false — оборудование сломано. Планировщик перераспределяет задачи.
     */
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}