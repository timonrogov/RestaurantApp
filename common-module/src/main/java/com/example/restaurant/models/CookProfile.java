package com.example.restaurant.models;

import com.example.restaurant.enums.CookSpecialization;
import jakarta.persistence.*;
import lombok.*;

/**
 * Профиль повара как ресурса для планировщика.
 * Связан с уже существующей сущностью Employee (один к одному).
 *
 * Таблица cook_profile хранит информацию о специализации сотрудника
 * и его текущей доступности для планирования задач.
 *
 * Важно: не каждый Employee является поваром. CookProfile создаётся
 * только для сотрудников с ролью COOK.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "cook_profile")
public class CookProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Связь с сотрудником.
     * У каждого повара ровно один профиль, у каждого профиля ровно один сотрудник.
     */
    @OneToOne
    @JoinColumn(name = "employee_id", nullable = false, unique = true)
    private Employee employee;

    /**
     * Специализация: горячий цех, холодный цех, кондитерский и т.д.
     * Планировщик использует это поле при матчинге задач.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "specialization", nullable = false)
    private CookSpecialization specialization;

    /**
     * Флаг текущей доступности.
     * false — повар заболел, ушёл раньше или временно недоступен.
     * При переключении в false планировщик перераспределяет все задачи этого повара.
     */
    @Column(name = "is_active", nullable = false)
    private boolean isActive = true;
}