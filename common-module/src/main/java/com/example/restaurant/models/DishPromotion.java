package com.example.restaurant.models;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "dish_promotion")
public class DishPromotion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "dish_id", nullable = false)
    private Dish dish;

    @ManyToOne
    @JoinColumn(name = "promotion_time_slot_id", nullable = false)
    private PromotionTimeSlot promotionTimeSlot;

    @Column(name = "discount", nullable = false)
    private double discount;

    @Column(name = "min_quantity", nullable = false)
    private Integer minQuantity;
}