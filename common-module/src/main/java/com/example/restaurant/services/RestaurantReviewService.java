package com.example.restaurant.services;

import com.example.restaurant.models.Client;
import com.example.restaurant.models.RestaurantReview;
import com.example.restaurant.repositories.RestaurantReviewRepository;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class RestaurantReviewService {
    private final RestaurantReviewRepository restaurantReviewRepository;

    @Autowired
    public RestaurantReviewService(RestaurantReviewRepository restaurantReviewRepository,
                                   ClientService clientService) {
        this.restaurantReviewRepository = restaurantReviewRepository;
    }

    public List<RestaurantReview> getAllRestaurantReviews() {
        return restaurantReviewRepository.findAllByOrderByReviewTimeDesc();
    }

    public RestaurantReview createRestaurantReview(RestaurantReview restaurantReview) {
        return restaurantReviewRepository.save(restaurantReview);
    }

    public void deleteRestaurantReview(Long id) {
        restaurantReviewRepository.deleteById(id);
    }


    @Transactional
    public void saveOrUpdateReview(Client client, Integer rating, String reviewText) {
        RestaurantReview review = restaurantReviewRepository.findByClient(client)
                .orElse(new RestaurantReview());

        review.setClient(client);
        review.setRating(rating);
        review.setReviewText(reviewText);
        review.setReviewTime(LocalDateTime.now());

        restaurantReviewRepository.save(review);
    }

    public Double getAverageRating() {
        return restaurantReviewRepository.findAverageRating();
    }

    public Long getReviewsCount() {
        return restaurantReviewRepository.countReviews();
    }

    public List<RestaurantReview> getAllReviews() {
        return restaurantReviewRepository.findAll();
    }
}