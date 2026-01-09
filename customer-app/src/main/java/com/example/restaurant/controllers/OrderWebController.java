package com.example.restaurant.controllers;

import com.example.restaurant.enums.OrderStatus;
import com.example.restaurant.models.Client;
import com.example.restaurant.models.Dish;
import com.example.restaurant.models.Order;
import com.example.restaurant.models.OrderItem;
import com.example.restaurant.services.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/orders")
public class OrderWebController {

    private final OrderService orderService;
    private final ClientService clientService;
    private final OrderItemService orderItemService;
    private final CartService cartService;
    private final PricingService pricingService;
    private final DishService dishService;

    @Autowired
    public OrderWebController(OrderService orderService,
                              ClientService clientService,
                              OrderItemService orderItemService,
                              CartService cartService,
                              PricingService pricingService,
                              DishService dishService) {
        this.orderService = orderService;
        this.clientService = clientService;
        this.orderItemService = orderItemService;
        this.cartService = cartService;
        this.pricingService = pricingService;
        this.dishService = dishService;
    }


    @PostMapping("/add-item") // Будет доступен по /orders/add-item
    @ResponseBody
    public ResponseEntity<Map<String, Object>> addOrderItem(
            @RequestParam Long dishId,
            @RequestParam int quantity,
            @RequestParam(required = false) String comment,
            Principal principal,
            HttpServletRequest httpServletRequest,
            HttpServletResponse httpServletResponse) {

        Map<String, Object> response = new HashMap<>();

        try {
            // Запрет совершать заказ неавторизованному пользователю
            /*if (principal == null) {
                response.put("success", false);
                response.put("error", "Пользователь не авторизован");
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(response);
            }*/

            // Проверка доступности (логику можно вынести в CartService, но пока оставим тут)
            Dish dish = dishService.getDishById(dishId);
            if (!dish.isAvailable()) {
                response.put("success", false);
                response.put("error", "Блюдо недоступно для заказа");
                return ResponseEntity.badRequest().body(response);
            }

            // Добавление
            cartService.addItemToCart(dishId, quantity, comment, principal, httpServletRequest, httpServletResponse);

            // Пересчет для ответа
            Order currentOrder = cartService.getCurrentCart(principal, httpServletRequest, httpServletResponse);
            double total = pricingService.calculateTotal(currentOrder);
            double totalDiscounted = pricingService.calculateTotalWithDiscount(currentOrder);

            response.put("success", true);
            response.put("totalPrice", total);
            response.put("totalDiscounted", totalDiscounted);
            response.put("hasItems", true);
            response.put("hasDiscount", totalDiscounted < total);

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
        }
    }

    @GetMapping("/view")
    public String viewOrder(Model model,
                            Principal principal,
                            HttpServletRequest httpServletRequest,
                            HttpServletResponse httpServletResponse) {
        /*if (principal == null) {
            return "redirect:/login";
        }*/

        /*Client client = clientService.getClientByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Client not found"));*/

        Order currentOrder = cartService.getCurrentCart(principal, httpServletRequest, httpServletResponse);
        List<OrderItem> orderItems = orderService.getOrderItemsByOrderId(currentOrder.getId());

        // Расчет суммы с учетом скидок
        double totalPrice = orderItems.stream()
                .mapToDouble(item -> item.getDish().getPrice() * item.getQuantity())
                .sum();

        double totalDiscounted = pricingService.calculateTotalWithDiscount(currentOrder);

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
            Principal principal,
            HttpServletRequest request,
            HttpServletResponse response) {
        try {

            // Вызываем обновленный метод сервиса
            orderService.confirmOrder(principal, tableNumber, request, response);

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

        // ЛОГИКА ФИЛЬТРАЦИИ
        if ("ACTIVE".equals(statusFilter)) {
            // 1. Если запросили "ACTIVE" — берем список статусов
            orders = orderService.getOrdersByClientAndStatuses(client,
                    List.of(OrderStatus.COOKING, OrderStatus.READY));
        }
        else if (statusFilter != null && !statusFilter.isEmpty()) {
            // 2. Если другой конкретный статус (SERVED, CANCELED)
            try {
                OrderStatus status = OrderStatus.valueOf(statusFilter);
                orders = orderService.getOrdersByClientAndStatus(client, status);
            } catch (IllegalArgumentException e) {
                orders = orderService.getOrderHistory(client);
                statusFilter = null;
            }
        } else {
            // 3. Если фильтра нет — показываем историю (всё кроме корзины)
            orders = orderService.getOrderHistory(client);
        }

        model.addAttribute("orders", orders);
        model.addAttribute("statusFilter", statusFilter);
        // allStatuses нам здесь особо не нужен, так как мы используем кнопки, а не select

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

            cartService.removeOrderItem(itemId);
            return ResponseEntity.ok("Позиция удалена");
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }


    @GetMapping("/details/{orderId}")
    public String viewOrderDetails(@PathVariable Long orderId, Model model, Principal principal) {
        // Проверку на principal можно убрать, если мы разрешаем гостям смотреть свои заказы
        // Но тогда нужно проверять sessionToken.
        // Для простоты пока оставим требование авторизации для просмотра деталей,
        // либо доработаем CartService для проверки прав доступа к чужим заказам.

        if (principal == null) {
            return "redirect:/login";
        }

        Client client = clientService.getClientByUsername(principal.getName())
                .orElseThrow(() -> new RuntimeException("Клиент не найден"));

        Order order = orderService.getOrderById(orderId);

        // Проверка прав
        if (!order.getClient().getId().equals(client.getId())) {
            throw new RuntimeException("Доступ запрещен"); // Или вернуть 403
        }

        List<OrderItem> orderItems = orderService.getOrderItemsByOrderId(order.getId());
        double totalPrice = pricingService.calculateTotal(order);
        double totalDiscounted = pricingService.calculateTotalWithDiscount(order);

        model.addAttribute("order", order);
        model.addAttribute("orderItems", orderItems);
        model.addAttribute("totalPrice", totalPrice);
        model.addAttribute("totalDiscounted", totalDiscounted);

        return "order-details";
    }
}