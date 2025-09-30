package com.example.restaurant.controllers;

import com.example.restaurant.models.Client;
import com.example.restaurant.models.Order;
import com.example.restaurant.models.OrderStatus;
import com.example.restaurant.services.OrderService;
import com.example.restaurant.services.OrderStatusService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/orders")
@PreAuthorize("hasRole('СОТРУДНИК')")
public class AdminOrderController {

    private final OrderService orderService;
    private final OrderStatusService orderStatusService;

    @Autowired
    public AdminOrderController(OrderService orderService, OrderStatusService orderStatusService) {
        this.orderService = orderService;
        this.orderStatusService = orderStatusService;
    }

    @GetMapping
    public String getOrders(@RequestParam(required = false) String status, Model model) {
        // Преобразуем пустую строку в null
        String statusFilter = (status != null && status.isEmpty()) ? null : status;

        List<OrderStatus> allStatuses = orderStatusService.getAllOrderStatuses().stream()
                .filter(s -> !s.getName().equals("Сборка"))
                .collect(Collectors.toList());

        model.addAttribute("orders", orderService.getOrdersForAdminPanel(statusFilter));
        model.addAttribute("statuses", allStatuses);
        model.addAttribute("selectedStatus", status);
        return "admin-orders";
    }

    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id, @RequestParam String newStatus) {
        orderService.changeOrderStatus(id, newStatus);
        return "redirect:/admin/orders";
    }

    @GetMapping("/{orderId}")
    public String getOrderDetails(@PathVariable Long orderId, Model model) {
        Order order = orderService.getOrderById(orderId);
        Client client = order.getClient();

        model.addAttribute("order", order);
        model.addAttribute("client", client);
        model.addAttribute("orderItems", order.getOrderItems());
        model.addAttribute("currentStatus", order.getStatus().getName());

        return "admin-order-details";
    }
}