package com.example.restaurant.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
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
    private BigDecimal discount;

    @Column(name = "min_quantity", nullable = false)
    private Integer minQuantity;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Dish getDish() {
        return dish;
    }

    public void setDish(Dish dish) {
        this.dish = dish;
    }

    public PromotionTimeSlot getPromotionTimeSlot() {
        return promotionTimeSlot;
    }

    public void setPromotionTimeSlot(PromotionTimeSlot promotionTimeSlot) {
        this.promotionTimeSlot = promotionTimeSlot;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public Integer getMinQuantity() {
        return minQuantity;
    }

    public void setMinQuantity(Integer minQuantity) {
        this.minQuantity = minQuantity;
    }

    public void setDiscountPercentage(String percentage) {
        this.discount = new BigDecimal(percentage).divide(BigDecimal.valueOf(100));
    }
}