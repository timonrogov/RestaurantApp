package com.example.restaurant.services;

import com.example.restaurant.models.Day;
import com.example.restaurant.models.OrderTimeSlot;
import com.example.restaurant.repositories.OrderTimeSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class OrderTimeSlotService {
    private final OrderTimeSlotRepository orderTimeSlotRepository;

    @Autowired
    public OrderTimeSlotService(OrderTimeSlotRepository orderTimeSlotRepository) {
        this.orderTimeSlotRepository = orderTimeSlotRepository;
    }

    public List<OrderTimeSlot> getTimeSlotsByDate(LocalDate date) {
        return orderTimeSlotRepository.findByDayWorkDate(date);
    }

    public OrderTimeSlot createTimeSlot(OrderTimeSlot timeSlot) {
        return orderTimeSlotRepository.save(timeSlot);
    }

    public void deleteTimeSlot(Long id) {
        orderTimeSlotRepository.deleteById(id);
    }

}
