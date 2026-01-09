package com.example.restaurant.services;

import com.example.restaurant.models.Client;
import com.example.restaurant.models.Dish;
import com.example.restaurant.models.DishReview;
import com.example.restaurant.repositories.DishReviewRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DishReviewService {
    private final DishReviewRepository dishReviewRepository;
    private final ClientService clientService;
    private final DishService dishService;

    @Autowired
    public DishReviewService(DishReviewRepository dishReviewRepository,
                             ClientService clientService,
                             DishService dishService) {
        this.dishReviewRepository = dishReviewRepository;
        this.clientService = clientService;
        this.dishService = dishService;
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


    @Transactional
    public void addOrUpdateReview(Long dishId, String username, Integer rating, String reviewText) {
        // 1. Получаем клиента и блюдо (внутри сервиса!)
        Client client = clientService.getClientByUsername(username)
                .orElseThrow(() -> new RuntimeException("Клиент не найден"));

        Dish dish = dishService.getDishById(dishId); // Предполагаем, что метод выбрасывает исключение или возвращает null
        if (dish == null) throw new RuntimeException("Блюдо не найдено");

        // 2. Ищем существующий отзыв или создаем новый
        DishReview review = dishReviewRepository.findByClientIdAndDishId(client.getId(), dishId)
                .orElse(new DishReview());

        // 3. Обновляем поля
        if (review.getId() == null) { // Если новый
            review.setClient(client);
            review.setDish(dish);
        }
        review.setRating(rating);
        review.setReviewText(reviewText);
        review.setReviewTime(LocalDateTime.now());

        // 4. Сохраняем
        dishReviewRepository.save(review);
    }
}