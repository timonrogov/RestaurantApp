package com.example.restaurant.repositories;

import com.example.restaurant.models.OrderItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {
    List<OrderItem> findByOrderId(Long orderId);

    @Query("SELECT oi FROM OrderItem oi " +
            "WHERE oi.order.id =: orderId " +
            "ORDER BY oi.dish.name ASC")
    List<OrderItem> findSortedByDishName(Long orderId);

    Optional<OrderItem> findByOrderIdAndDishId(Long orderId, Long dishId);

    Optional<OrderItem> findByOrderIdAndDishIdAndComment(
            Long orderId,
            Long dishId,
            String comment
    );

    void deleteByOrderId(Long orderId);
}
