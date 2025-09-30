package com.example.restaurant.controllers;

import com.example.restaurant.models.Client;
import com.example.restaurant.services.ClientService;
import com.example.restaurant.services.RestaurantReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

import java.security.Principal;

@Controller
@RequestMapping("/restaurant-reviews")
public class RestaurantReviewWebController {
    private final RestaurantReviewService reviewService;
    private final ClientService clientService;

    @Autowired
    public RestaurantReviewWebController(RestaurantReviewService reviewService, ClientService clientService) {
        this.reviewService = reviewService;
        this.clientService = clientService;
    }

    @GetMapping
    public String getReviews(Model model) {
        model.addAttribute("averageRating", reviewService.getAverageRating());
        model.addAttribute("reviewsCount", reviewService.getReviewsCount());
        model.addAttribute("reviews", reviewService.getAllReviews());
        return "restaurant-reviews";
    }

    @PostMapping
    public String addReview(
            @RequestParam Integer rating,
            @RequestParam(required = false) String reviewText,
            Principal principal,
            RedirectAttributes redirectAttributes
    ) {
        if (rating == null) {
            redirectAttributes.addFlashAttribute("error", "Пожалуйста, выберите оценку");
            return "redirect:/restaurant-reviews";
        }

        Client client = clientService.getClientByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Клиент не найден"));

        reviewService.saveOrUpdateReview(client, rating, reviewText);
        return "redirect:/restaurant-reviews";
    }
}
