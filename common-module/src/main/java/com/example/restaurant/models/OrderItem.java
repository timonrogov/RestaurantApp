package com.example.restaurant.models;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "order_item")
public class OrderItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne
    @JoinColumn(name = "dish_id", nullable = false)
    private Dish dish;

    @Column(name = "quantity", nullable = false)
    private Integer quantity = 0;

    @Column(name = "comment")
    private String comment;

    @Column(name = "applied_discount")
    private BigDecimal appliedDiscount = BigDecimal.ZERO;

    /**
     * К какому курсу относится это блюдо в заказе.
     * По умолчанию 1 — все блюда считаются одним курсом, если не указано иное.
     * Официант или система может назначать разные номера курсов при оформлении.
     */
    @Column(name = "course_number", nullable = false)
    private int courseNumber = 1;

    private double totalPrice = 0;
}