package com.example.restaurant.controllers;

import com.example.restaurant.models.Client;
import com.example.restaurant.models.Order;
import com.example.restaurant.models.OrderItem;
import com.example.restaurant.services.ClientService;
import com.example.restaurant.services.OrderItemService;
import com.example.restaurant.services.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;

@Controller
@RequestMapping("/orders")
public class OrderWebController {
    @Autowired
    private OrderService orderService;
    @Autowired
    private ClientService clientService;
    @Autowired
    private OrderItemService orderItemService;

    @PostMapping("/add-item")
    @ResponseBody
    public ResponseEntity<String> addOrderItem(
            @RequestParam Long dishId,
            @RequestParam int quantity,
            @RequestParam(required = false) String comment,
            Principal principal) {
        try {
            if (principal == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                        .body("Ошибка: пользователь не авторизован");
            }

            Client client = clientService.getClientByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Клиент не найден"));

            Order currentOrder = orderService.getCurrentOrderForClient(client);
            orderService.addDishToOrder(currentOrder, dishId, quantity, comment);

            return ResponseEntity.ok("Блюдо добавлено");
        } catch (Exception e) {
            System.out.println("Ошибка добавления блюда" + e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка: " + e.getMessage());
        }
    }

    @GetMapping("/view")
    public String viewOrder(Model model, Principal principal) {
        if (principal == null) {
            return "redirect:/login";
        }

        Client client = clientService.getClientByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Client not found"));

        Order currentOrder = orderService.getCurrentOrderForClient(client);
        List<OrderItem> orderItems = orderService.getOrderItemsByOrderId(currentOrder.getId());

        // Расчет суммы с учетом скидок
        double totalPrice = orderItems.stream()
                .mapToDouble(item -> item.getDish().getPrice() * item.getQuantity())
                .sum();

        double totalDiscounted = orderService.calculateTotalWithDiscount(currentOrder);

        model.addAttribute("orderItems", orderItems);
        model.addAttribute("totalPrice", totalPrice);
        model.addAttribute("totalDiscounted", totalDiscounted); // Передаем сумму со скидкой
        model.addAttribute("order", currentOrder);
        return "view-order";
    }

    @PostMapping("/confirm")
    @ResponseBody
    public ResponseEntity<String> confirmOrder(
            @RequestParam String tableNumber,
            Principal principal) {
        try {
            Client client = clientService.getClientByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Клиент не найден"));

            Order currentOrder = orderService.getCurrentOrderForClient(client);
            currentOrder.setTableNumber(tableNumber); // Устанавливаем номер столика

            orderService.updateOrderStatus(currentOrder.getId(), "Готовится");
            return ResponseEntity.ok("Заказ подтвержден");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Ошибка: " + e.getMessage());
        }
    }

    @GetMapping
    public String viewOrders(
            @RequestParam(required = false) String statusFilter,
            Model model,
            Principal principal) {

        Client client = clientService.getClientByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Клиент не найден"));

        List<Order> orders;
        if (statusFilter != null && !statusFilter.isEmpty()) {
            orders = orderService.getOrdersByClientAndStatus(client, statusFilter);
        } else {
            orders = orderService.getOrdersByClient(client);
        }

        model.addAttribute("orders", orders);
        model.addAttribute("statusFilter", statusFilter); // Передаем выбранный статус
        return "orders";
    }

    @PostMapping("/{orderId}/cancel")
    public ResponseEntity<String> cancelOrder(@PathVariable Long orderId, Principal principal) {
        try {
            Client client = clientService.getClientByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Клиент не найден"));

            Order order = orderService.getOrderById(orderId);

            if (!order.getClient().equals(client)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Доступ запрещен");
            }

            orderService.cancelOrder(orderId);
            return ResponseEntity.ok("Заказ отменен");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/remove-item/{itemId}")
    public ResponseEntity<String> removeOrderItem(
            @PathVariable Long itemId,
            Principal principal
    ) {
        try {
            Client client = clientService.getClientByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Клиент не найден"));

            orderItemService.removeOrderItem(itemId, client);
            return ResponseEntity.ok("Позиция удалена");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }
}