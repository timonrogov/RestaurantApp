package com.example.restaurant.services;

import com.example.restaurant.models.OrderStatus;
import com.example.restaurant.repositories.OrderStatusRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class OrderStatusService {
    private final OrderStatusRepository orderStatusRepository;

    @Autowired
    public OrderStatusService(OrderStatusRepository orderStatusRepository) {
        this.orderStatusRepository = orderStatusRepository;
    }

    public List<OrderStatus> getAllOrderStatuses() {
        return orderStatusRepository.findAll();
    }

    public OrderStatus createOrderStatus(OrderStatus orderStatus) {
        return orderStatusRepository.save(orderStatus);
    }

    public void deleteOrderStatus(Long id) {
        orderStatusRepository.deleteById(id);
    }

    public List<OrderStatus> getFilterableStatuses() {
        return orderStatusRepository.findAll().stream()
                .filter(status -> !status.getName().equals("Сборка"))
                .collect(Collectors.toList());
    }
}