package com.example.restaurant.services;

import com.example.restaurant.models.PromotionTimeSlot;
import com.example.restaurant.repositories.PromotionTimeSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class PromotionTimeSlotService {
    private final PromotionTimeSlotRepository promotionTimeSlotRepository;

    @Autowired
    public PromotionTimeSlotService(PromotionTimeSlotRepository promotionTimeSlotRepository) {
        this.promotionTimeSlotRepository = promotionTimeSlotRepository;
    }

    public List<PromotionTimeSlot> getAllPromotionTimeSlots() {
        return promotionTimeSlotRepository.findAll();
    }

    public PromotionTimeSlot createPromotionTimeSlot(PromotionTimeSlot promotionTimeSlot) {
        return promotionTimeSlotRepository.save(promotionTimeSlot);
    }

    public void deletePromotionTimeSlot(Long id) {
        promotionTimeSlotRepository.deleteById(id);
    }

    public Optional<PromotionTimeSlot> findActivePromotion(LocalDate date, LocalTime time) {
        return promotionTimeSlotRepository.findActivePromotion(date, time);
    }

    public boolean isPromotionActive(
            PromotionTimeSlot promoSlot,
            LocalDate currentDate,
            LocalTime currentTime) {

        // Проверка даты
        boolean dateValid = !promoSlot.getStartDate().getWorkDate().isAfter(currentDate) &&
                !promoSlot.getEndDate().getWorkDate().isBefore(currentDate);

        // Проверка времени
        boolean timeValid = !promoSlot.getStartTime().isAfter(currentTime) &&
                !promoSlot.getEndTime().isBefore(currentTime);

        return dateValid && timeValid;
    }
}
