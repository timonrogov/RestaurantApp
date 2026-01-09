package com.example.restaurant.controllers;

import com.example.restaurant.enums.OrderStatus;
import com.example.restaurant.models.Client;
import com.example.restaurant.models.Order;
import com.example.restaurant.services.OrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/orders")
@PreAuthorize("hasAnyRole('ADMIN', 'COOK', 'WAITER')")
public class AdminOrderController {

    private final OrderService orderService;

    @Autowired
    public AdminOrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @GetMapping
    public String getOrders(@RequestParam(required = false) String status, Model model) {

        // 1. Получаем список статусов для фильтра (исключая "Сборку")
        // Используем Stream API прямо по значениям Enum
        List<OrderStatus> allStatuses = Arrays.stream(OrderStatus.values())
                .filter(s -> s != OrderStatus.ASSEMBLY)
                .collect(Collectors.toList());

        // 2. Конвертируем пришедшую строку (status) в Enum для передачи в сервис
        // Если строка пустая или null, передаем null (значит "все статусы")
        OrderStatus statusFilter = null;
        if (status != null && !status.isEmpty()) {
            try {
                statusFilter = OrderStatus.valueOf(status);
            } catch (IllegalArgumentException e) {
                // Если пришел некорректный статус, игнорируем фильтр
            }
        }

        // 3. Вызываем сервис (убедись, что в OrderService метод принимает OrderStatus!)
        model.addAttribute("orders", orderService.getOrdersForAdminPanel(statusFilter));

        model.addAttribute("statuses", allStatuses);
        model.addAttribute("selectedStatus", status); // Возвращаем строку, чтобы в <select> выбрать нужное значение

        return "admin-orders";
    }

    @PostMapping("/{id}/status")
    public String updateStatus(@PathVariable Long id, @RequestParam String newStatus) {

        // 1. Получаем информацию о текущем пользователе и его ролях
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Collection<? extends GrantedAuthority> authorities = auth.getAuthorities();

        // Определяем флаги ролей для удобства
        boolean isAdmin = authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_ADMIN"));
        boolean isCook = authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_COOK"));
        boolean isWaiter = authorities.stream().anyMatch(a -> a.getAuthority().equals("ROLE_WAITER"));

        // 2. Проверяем права на конкретный переход статуса
        switch (newStatus) {
            case "READY":
                // В статус "Готов" могут переводить только ПОВАР или АДМИН
                if (!isCook && !isAdmin) {
                    throw new AccessDeniedException("Только повар может отметить готовность заказа");
                }
                break;

            case "SERVED":
                // В статус "Подан" могут переводить только ОФИЦИАНТ или АДМИН
                if (!isWaiter && !isAdmin) {
                    throw new AccessDeniedException("Только официант может подать заказ");
                }
                break;

            case "CANCELED":
                // Отменять может только АДМИН
                if (!isAdmin) {
                    throw new AccessDeniedException("Только администратор может отменять заказы");
                }
                break;

            default:
                // Любые другие статусы (на всякий случай) - только админ
                if (!isAdmin) {
                    throw new AccessDeniedException("Недостаточно прав для выполнения операции");
                }
        }

        // 3. Если проверки пройдены, выполняем действие
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
        model.addAttribute("currentStatus", order.getStatus());

        return "admin-order-details";
    }
}