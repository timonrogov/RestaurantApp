package com.example.restaurant.controllers.api;

import com.example.restaurant.models.OrderStatus;
import com.example.restaurant.services.OrderStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/order-statuses")
public class OrderStatusController {
    private final OrderStatusService orderStatusService;

    @Autowired
    public OrderStatusController(OrderStatusService orderStatusService) {
        this.orderStatusService = orderStatusService;
    }

    @GetMapping
    public List<OrderStatus> getAllOrderStatuses() {
        return orderStatusService.getAllOrderStatuses();
    }

    @PostMapping
    public OrderStatus createOrderStatus(@RequestBody OrderStatus orderStatus) {
        return orderStatusService.createOrderStatus(orderStatus);
    }

    @DeleteMapping("/{id}")
    public void deleteOrderStatus(@PathVariable Long id) {
        orderStatusService.deleteOrderStatus(id);
    }
}