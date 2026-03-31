package com.example.restaurant.models;

import jakarta.persistence.*;
import lombok.*;

/**
 * Курс блюд внутри одного заказа.
 *
 * Позволяет разбить заказ на подачи (закуски → основное → десерт)
 * с заданными временными паузами между ними.
 *
 * Пример для заказа из трёх курсов:
 *   courseNumber=1, syncGapMinutes=0   — закуски, подаются сразу
 *   courseNumber=2, syncGapMinutes=15  — основное, через 15 мин после закусок
 *   courseNumber=3, syncGapMinutes=20  — десерт, через 20 мин после основного
 *
 * Планировщик использует syncGapMinutes для вычисления notBefore
 * при запуске планирования следующего курса.
 *
 * Если в заказе все блюда одного курса — создаётся одна запись
 * с courseNumber=1, syncGapMinutes=0.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "order_course")
public class OrderCourse {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /**
     * Заказ, к которому относится этот курс.
     */
    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    /**
     * Номер курса (1 = первый, 2 = второй и т.д.).
     * Курсы планируются строго по возрастанию номера.
     */
    @Column(name = "course_number", nullable = false)
    private int courseNumber;

    /**
     * Пауза в минутах между окончанием предыдущего курса и началом готовки этого.
     * Для первого курса (courseNumber=1) всегда должно быть 0.
     *
     * Пример: если первый курс готов в 14:30, а syncGapMinutes=15,
     * то второй курс начнёт планироваться с notBefore=14:45.
     */
    @Column(name = "sync_gap_minutes", nullable = false)
    private int syncGapMinutes = 0;
}