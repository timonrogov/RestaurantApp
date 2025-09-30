package com.example.restaurant.controllers.api;

import com.example.restaurant.models.RestaurantReview;
import com.example.restaurant.services.RestaurantReviewService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/restaurant-reviews")
public class RestaurantReviewController {
    private final RestaurantReviewService restaurantReviewService;

    @Autowired
    public RestaurantReviewController(RestaurantReviewService restaurantReviewService) {
        this.restaurantReviewService = restaurantReviewService;
    }

    @GetMapping
    public List<RestaurantReview> getAllRestaurantReviews() {
        return restaurantReviewService.getAllRestaurantReviews();
    }

    @PostMapping
    public RestaurantReview createRestaurantReview(@RequestBody RestaurantReview restaurantReview) {
        return restaurantReviewService.createRestaurantReview(restaurantReview);
    }

    @DeleteMapping("/{id}")
    public void deleteRestaurantReview(@PathVariable Long id) {
        restaurantReviewService.deleteRestaurantReview(id);
    }
}