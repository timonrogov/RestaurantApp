package com.example.restaurant.services;

import com.example.restaurant.models.DishReview;
import com.example.restaurant.repositories.DishReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DishReviewService {
    private final DishReviewRepository dishReviewRepository;

    @Autowired
    public DishReviewService(DishReviewRepository dishReviewRepository) {
        this.dishReviewRepository = dishReviewRepository;
    }

    public List<DishReview> getReviewsByDishId(Long dishId) {
        return dishReviewRepository.findByDishId(dishId);
    }

    public DishReview createDishReview(DishReview dishReview) {
        return dishReviewRepository.save(dishReview);
    }

    public void deleteDishReview(Long id) {
        dishReviewRepository.deleteById(id);
    }

    public Map<String, Object> getDishReviewStats(Long dishId) {
        List<DishReview> reviews = dishReviewRepository.findByDishId(dishId);

        // Всегда возвращаем объект, даже если отзывов нет
        if (reviews.isEmpty()) {
            return Map.of(
                    "averageRating", 0.0,
                    "reviewCount", 0
            );
        }

        double average = reviews.stream()
                .mapToInt(DishReview::getRating)
                .average()
                .orElse(0.0);

        average = Math.round(average * 10) / 10.0;

        return Map.of(
                "averageRating", average,
                "reviewCount", reviews.size()
        );
    }

    public boolean hasUserReviewedDish(Long clientId, Long dishId) {
        return dishReviewRepository.existsByClientAndDish(clientId, dishId);
    }

    public Optional<DishReview> getReviewByClientAndDish(Long clientId, Long dishId) {
        return dishReviewRepository.findByClientIdAndDishId(clientId, dishId);
    }
}