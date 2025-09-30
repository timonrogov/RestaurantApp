package com.example.restaurant.controllers;

import com.example.restaurant.models.Client;
import com.example.restaurant.models.Order;
import com.example.restaurant.models.OrderItem;
import com.example.restaurant.services.ClientService;
import com.example.restaurant.services.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;

import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/orders")
public class OrderHistoryController {
    @Autowired
    private OrderService orderService;
    @Autowired
    ClientService clientService;

    @GetMapping("/history")
    public String viewOrdersByStatus(Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        Client client = clientService.getClientByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Client not found"));

        List<Order> orders = orderService.getOrdersByClientAndStatus(client, "Готовится");
        model.addAttribute("orders", orders);
        return "orders";
    }

    @GetMapping("/details/{orderId}")
    public String viewOrderDetails(@PathVariable Long orderId, Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        Client client = clientService.getClientByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Клиент не найден"));

        // Используем метод сервиса, который возвращает Order, а не Optional
        Order order = orderService.getOrderById(orderId);

        // Проверяем, принадлежит ли заказ текущему клиенту
        if (!order.getClient().equals(client)) {
            throw new RuntimeException("Доступ запрещен");
        }

        List<OrderItem> orderItems = orderService.getOrderItemsByOrderId(order.getId());
        double totalPrice = orderItems.stream()
                .mapToDouble(item -> item.getQuantity() * item.getDish().getPrice())
                .sum();
        double totalDiscounted = orderItems.stream()
                .mapToDouble(item -> {
                    BigDecimal discount = item.getAppliedDiscount() != null ? item.getAppliedDiscount() : BigDecimal.ZERO;
                    double price = item.getQuantity() * item.getDish().getPrice();
                    return price * (1 - discount.doubleValue());
                })
                .sum();

        model.addAttribute("order", order);
        model.addAttribute("orderItems", orderItems);
        model.addAttribute("totalPrice", totalPrice);
        model.addAttribute("totalDiscounted", totalDiscounted);
        return "order-details";
    }
}
