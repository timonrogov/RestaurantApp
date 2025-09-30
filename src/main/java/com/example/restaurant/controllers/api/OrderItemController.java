package com.example.restaurant.controllers.api;

import com.example.restaurant.models.Client;
import com.example.restaurant.models.Order;
import com.example.restaurant.models.OrderItem;
import com.example.restaurant.services.ClientService;
import com.example.restaurant.services.OrderItemService;
import com.example.restaurant.services.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/order-items")
public class OrderItemController {
    private final OrderItemService orderItemService;
    private final ClientService clientService;
    private final OrderService orderService;

    @Autowired
    public OrderItemController(OrderItemService orderItemService, ClientService clientService, OrderService orderService) {
        this.orderItemService = orderItemService;
        this.clientService = clientService;
        this.orderService = orderService;
    }

    @GetMapping("/order/{orderId}")
    public List<OrderItem> getOrderItemsByOrderId(@PathVariable Long orderId) {
        return orderItemService.getOrderItemsByOrderId(orderId);
    }

    @PostMapping
    public OrderItem createOrderItem(@RequestBody OrderItem orderItem) {
        return orderItemService.createOrderItem(orderItem);
    }

    @PostMapping("/order-items")
    public String addOrderItem(@RequestParam Long dishId, @RequestParam int quantity,
                               @RequestParam String comment, Principal principal) {
        // Получаем текущего клиента из базы данных
        String username = principal.getName();
        Client client = (Client) clientService.getClientByUsername(username).orElseThrow();

        // Создаем или получаем текущий заказ клиента
        Order currentOrder = orderService.getCurrentOrderForClient(client);

        // Добавляем блюдо в заказ
        orderService.addDishToOrder(currentOrder, dishId, quantity, comment);

        return "redirect:/view-order";
    }

    @DeleteMapping("/{id}")
    public void deleteOrderItem(@PathVariable Long id) {
        orderItemService.deleteOrderItem(id);
    }
}