package com.example.restaurant.controllers.api;

import com.example.restaurant.models.PromotionTimeSlot;
import com.example.restaurant.services.PromotionTimeSlotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/promotion-time-slots")
public class PromotionTimeSlotController {
    private final PromotionTimeSlotService promotionTimeSlotService;

    @Autowired
    public PromotionTimeSlotController(PromotionTimeSlotService promotionTimeSlotService) {
        this.promotionTimeSlotService = promotionTimeSlotService;
    }

    @GetMapping
    public List<PromotionTimeSlot> getAllPromotionTimeSlots() {
        return promotionTimeSlotService.getAllPromotionTimeSlots();
    }

    @PostMapping
    public PromotionTimeSlot createPromotionTimeSlot(@RequestBody PromotionTimeSlot promotionTimeSlot) {
        return promotionTimeSlotService.createPromotionTimeSlot(promotionTimeSlot);
    }

    @DeleteMapping("/{id}")
    public void deletePromotionTimeSlot(@PathVariable Long id) {
        promotionTimeSlotService.deletePromotionTimeSlot(id);
    }
}