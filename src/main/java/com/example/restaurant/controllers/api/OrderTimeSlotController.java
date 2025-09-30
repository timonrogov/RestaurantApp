package com.example.restaurant.controllers.api;

import com.example.restaurant.models.OrderTimeSlot;
import com.example.restaurant.services.OrderTimeSlotService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/time-slots")
public class OrderTimeSlotController {
    private final OrderTimeSlotService orderTimeSlotService;

    @Autowired
    public OrderTimeSlotController(OrderTimeSlotService orderTimeSlotService) {
        this.orderTimeSlotService = orderTimeSlotService;
    }

    @GetMapping("/date/{date}")
    public List<OrderTimeSlot> getTimeSlotsByDate(@PathVariable LocalDate date) {
        return orderTimeSlotService.getTimeSlotsByDate(date);
    }

    @PostMapping
    public OrderTimeSlot createTimeSlot(@RequestBody OrderTimeSlot timeSlot) {
        return orderTimeSlotService.createTimeSlot(timeSlot);
    }

    @DeleteMapping("/{id}")
    public void deleteTimeSlot(@PathVariable Long id) {
        orderTimeSlotService.deleteTimeSlot(id);
    }
}
