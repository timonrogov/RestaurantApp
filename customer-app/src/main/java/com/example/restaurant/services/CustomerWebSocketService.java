package com.example.restaurant.services;

import com.example.restaurant.enums.OrderStatus;
import com.example.restaurant.models.Order;
import com.example.restaurant.repositories.OrderRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис WebSocket-уведомлений для клиентского приложения.
 *
 * Проблема: staff-app и customer-app — разные JVM-процессы.
 * Когда администратор меняет статус заказа в staff-app,
 * customer-app об этом не знает через Spring-события.
 *
 * Решение: каждые 5 секунд сервер (в контексте customer-app) проверяет
 * статусы активных заказов в БД. Если статус изменился по сравнению
 * с кешем — мгновенно пушит обновление подписанным клиентам.
 *
 * Это убирает N HTTP-запросов от N браузеров → 1 запрос к БД каждые 5 сек.
 */
@Service
@RequiredArgsConstructor
public class CustomerWebSocketService {

    private static final Logger log = LoggerFactory.getLogger(CustomerWebSocketService.class);

    private final SimpMessagingTemplate messagingTemplate;
    private final OrderRepository orderRepository;

    /**
     * Кеш: orderId → последний известный статус.
     * Позволяет обнаруживать изменения без хранения истории.
     */
    private final ConcurrentHashMap<Long, OrderStatus> lastKnownStatuses = new ConcurrentHashMap<>();

    /**
     * Каждые 5 секунд: проверить активные заказы и запушить изменения.
     * "Активные" = не SERVED и не CANCELED (ещё могут меняться).
     */
    @Scheduled(fixedRate = 5000)
    public void pushActiveOrderUpdates() {
        try {
            // 1. Берем ID всех заказов, статус которых мы помним с прошлой проверки
            Set<Long> idsToCheck = new HashSet<>(lastKnownStatuses.keySet());

            // 2. Берем все заказы, которые сейчас активны, и тоже добавляем их ID
            List<Order> activeOrders = orderRepository.findByStatusNotIn(
                    List.of(OrderStatus.SERVED, OrderStatus.CANCELED));

            for (Order o : activeOrders) {
                idsToCheck.add(o.getId());
            }

            if (idsToCheck.isEmpty()) return;

            // 3. Вытаскиваем все нужные заказы из БД одним запросом
            List<Order> ordersToCheck = orderRepository.findAllById(idsToCheck);

            for (Order order : ordersToCheck) {
                OrderStatus currentStatus = order.getStatus();
                OrderStatus previousStatus = lastKnownStatuses.put(order.getId(), currentStatus);

                // Если статус изменился — пушим обновление клиенту
                if (previousStatus != null && currentStatus != previousStatus) {
                    pushOrderStatusUpdate(order);
                }
            }

            // 4. И ТОЛЬКО ТЕПЕРЬ очищаем кеш от завершенных заказов
            lastKnownStatuses.keySet().removeIf(id -> {
                OrderStatus status = lastKnownStatuses.get(id);
                return status == OrderStatus.SERVED || status == OrderStatus.CANCELED;
            });

        } catch (Exception e) {
            log.warn("Ошибка при проверке статусов заказов для WebSocket: {}", e.getMessage());
        }
    }

    /**
     * Отправить обновление статуса конкретного заказа.
     * Топик: /topic/order.{orderId} — подписывается клиент, следящий за своим заказом.
     */
    public void pushOrderStatusUpdate(Order order) {
        messagingTemplate.convertAndSend(
                "/topic/order." + order.getId(),
                Map.of(
                        "orderId",       order.getId(),
                        "status",        order.getStatus().name(),
                        "statusDisplay", order.getStatus().getDisplayName()
                )
        );
        log.debug("WS push: orderId={}, status={}", order.getId(), order.getStatus());
    }

    /** Удалить из кеша заказы, которых больше нет среди активных. */
    private void cleanupCache(List<Order> activeOrders) {
        var activeIds = activeOrders.stream().map(Order::getId).collect(java.util.stream.Collectors.toSet());
        lastKnownStatuses.keySet().removeIf(id -> !activeIds.contains(id));
    }
}