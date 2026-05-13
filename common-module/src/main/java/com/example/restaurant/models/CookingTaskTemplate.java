package com.example.restaurant.models;

import com.example.restaurant.enums.CookSpecialization;
import jakarta.persistence.*;
import lombok.*;

/**
 * Шаблон одного этапа приготовления блюда.
 *
 * Каждое блюдо (Dish) имеет один или несколько шаблонов — по одному на каждый этап.
 * Шаблоны задаются администратором при настройке меню.
 *
 * Пример для блюда «Стейк рибай»:
 *   stepNumber=1, stepName="Подготовка мяса",  duration=5,  specialization=HOT_SHOP, equipment=null
 *   stepNumber=2, stepName="Жарка на гриле",   duration=15, specialization=GRILL,    equipment="GRILL"
 *   stepNumber=3, stepName="Отдых мяса",        duration=5,  specialization=HOT_SHOP, equipment=null
 *
 * При поступлении заказа планировщик создаёт по одному CookingTask на каждый шаблон.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cooking_task_template")
public class CookingTaskTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Блюдо, к которому относится этот шаблон.
     * У одного блюда может быть несколько шаблонов (несколько этапов).
     */
    @ManyToOne
    @JoinColumn(name = "dish_id", nullable = false)
    private Dish dish;

    /**
     * Порядковый номер этапа в рамках одного блюда.
     * Этапы выполняются последовательно: шаг 1 → шаг 2 → шаг 3.
     * Планировщик создаёт TaskAgent для шага N только после завершения шага N-1.
     */
    @Column(name = "step_number", nullable = false)
    private int stepNumber;

    /**
     * Название этапа для отображения на KDS.
     * Например: «Нарезка», «Жарка», «Сборка», «Подача».
     */
    @Column(name = "step_name", nullable = false)
    private String stepName;

    /**
     * Плановая длительность этапа в минутах.
     * Используется планировщиком для расчёта временных слотов.
     */
    @Column(name = "duration_minutes", nullable = false)
    private int durationMinutes;

    /**
     * Требуемая специализация повара для выполнения этого этапа.
     * Планировщик будет искать только поваров с совпадающей специализацией
     * (или UNIVERSAL — такой повар подходит для любого этапа).
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "required_specialization", nullable = false)
    private CookSpecialization requiredSpecialization;

    /**
     * Тип оборудования, необходимого для этого этапа.
     * null — оборудование не требуется (например, нарезка на разделочной доске).
     * Если задано — планировщик ищет свободное оборудование нужного типа.
     */
    @Column(name = "required_equipment_type")
    private String requiredEquipmentType;

    /**
     * Может ли этот этап выполняться параллельно с другими этапами того же блюда.
     * Зарезервировано для будущего развития системы.
     * Сейчас все этапы считаются последовательными.
     */
    @Column(name = "can_be_parallel", nullable = false)
    private boolean canBeParallel = false;

    /**
     * Количество порций, которые повар готовит за один «заход» на данном этапе.
     *
     * Используется OrderAgent при разбивке OrderItem на CookingTask-партии:
     *   batchCount = ceil(orderItem.quantity / portionsPerSlot)
     *
     * Примеры:
     *   portionsPerSlot = 1 → каждая порция — отдельная задача (дефолт)
     *   portionsPerSlot = 4 → за один раз на гриле жарится до 4 стейков
     *
     * Каждая задача-партия занимает ровно 1 слот capacity оборудования
     * и не зависит от того, сколько порций в ней — duration одинаков.
     */
    @Column(name = "portions_per_slot", nullable = false, columnDefinition = "integer default 1")
    private int portionsPerSlot = 1;
}