package com.example.restaurant.repositories;

import com.example.restaurant.models.Client;
import com.example.restaurant.models.RestaurantReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RestaurantReviewRepository extends JpaRepository<RestaurantReview, Long> {
    Optional<RestaurantReview> findByClient(Client client);

    // Сортировка по убыванию даты
    List<RestaurantReview> findAllByOrderByReviewTimeDesc();

    @Query("SELECT AVG(r.rating) FROM RestaurantReview r")
    Double findAverageRating();

    @Query("SELECT COUNT(r) FROM RestaurantReview r")
    Long countReviews();
}