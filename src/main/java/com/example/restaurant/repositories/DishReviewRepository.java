package com.example.restaurant.repositories;

import com.example.restaurant.models.DishReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DishReviewRepository extends JpaRepository<DishReview, Long> {
    List<DishReview> findByDishId(Long dishId);

    @Query("SELECT CASE WHEN COUNT(r) > 0 THEN true ELSE false END " +
            "FROM DishReview r " +
            "WHERE r.client.id = :clientId AND r.dish.id = :dishId")
    boolean existsByClientAndDish(@Param("clientId") Long clientId,
                                  @Param("dishId") Long dishId);

    Optional<DishReview> findByClientIdAndDishId(Long clientId, Long dishId);
}