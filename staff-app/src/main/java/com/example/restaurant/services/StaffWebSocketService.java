package com.example.restaurant.services;

import com.example.restaurant.enums.CallStatus;
import com.example.restaurant.enums.OrderStatus;
import com.example.restaurant.models.Order;
import com.example.restaurant.repositories.OrderRepository;
import com.example.restaurant.repositories.WaiterCallRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Сервис для отправки WebSocket-сообщений подписчикам в staff-app.
 *
 * Использует SimpMessagingTemplate — Spring-абстракцию над STOMP-брокером.
 * Каждый метод соответствует одному топику.
 */
@Service
@RequiredArgsConstructor
public class StaffWebSocketService {

    private final SimpMessagingTemplate messagingTemplate;
    private final WaiterCallRepository waiterCallRepository;
    private final OrderRepository orderRepository;

    // Кеш для отслеживания изменений
    private long lastKnownCallCount = -1;
    private final ConcurrentHashMap<Long, OrderStatus> lastKnownOrderStatuses = new ConcurrentHashMap<>();

    private final com.example.restaurant.repositories.CookingTaskRepository cookingTaskRepository;
    private final ConcurrentHashMap<Long, com.example.restaurant.enums.CookingTaskStatus>
            lastKnownTaskStatuses = new ConcurrentHashMap<>();


    /**
     * Уведомить страницу заказов об изменении статуса заказа.
     *
     * @param orderId     ID заказа
     * @param tableNumber номер стола
     * @param status      новый статус (код, например "COOKING")
     * @param statusDisplay отображаемое название статуса
     */
    public void notifyOrderUpdate(Long orderId, String tableNumber, String status, String statusDisplay) {
        messagingTemplate.convertAndSend("/topic/orders", Map.of(
                "orderId", orderId,
                // Если номер стола вдруг null, передаем прочерк
                "tableNumber", tableNumber != null ? tableNumber : "—",
                "status", status,
                "statusDisplay", statusDisplay
        ));
    }

    /**
     * Уведомить страницу планировщика о необходимости обновить данные.
     */
    public void notifySchedulerUpdate() {
        messagingTemplate.convertAndSend("/topic/scheduler", Map.of(
                "type", "REFRESH"
        ));
    }

    /**
     * Уведомить все KDS-экраны об изменении задач.
     * Каждый повар при получении сам запросит свои задачи через REST API.
     */
    public void notifyKdsUpdate() {
        messagingTemplate.convertAndSend("/topic/kds", Map.of(
                "type", "REFRESH"
        ));
    }

    /**
     * Уведомить страницу вызовов и шапку (бейдж) об изменении вызовов.
     *
     * @param type            тип события: "NEW_CALL" или "CALL_RESOLVED"
     * @param callId          ID вызова
     * @param tableNumber     номер стола
     * @param activeCallsCount текущее количество активных вызовов
     */
    public void notifyCallsUpdate(String type, Long callId, String tableNumber, long activeCallsCount) {
        messagingTemplate.convertAndSend("/topic/calls", Map.of(
                "type", type,
                // Если callId = null, передаем 0L, чтобы Map.of не ругался
                "callId", callId != null ? callId : 0L,
                "tableNumber", tableNumber != null ? tableNumber : "—",
                "activeCallsCount", activeCallsCount
        ));
    }

    @Scheduled(fixedRate = 5000)
    @Transactional(readOnly = true)
    public void pollWaiterCalls() {
        long currentCallCount = waiterCallRepository.countByStatus(CallStatus.ACTIVE);
        if (currentCallCount != lastKnownCallCount) {
            lastKnownCallCount = currentCallCount;
            // Пушим обновление. Если вызовов стало больше, JS сам перезагрузит страницу вызовов
            notifyCallsUpdate("NEW_CALL", null, "Обновление", currentCallCount);
        }
    }

    @Scheduled(fixedRate = 5000)
    @Transactional(readOnly = true)
    public void pollOrdersAndTasks() {
        List<Order> activeOrders = orderRepository.findByStatusNotIn(
                List.of(OrderStatus.ASSEMBLY, OrderStatus.SERVED, OrderStatus.CANCELED)
        );

        boolean ordersChanged = false;
        for (Order order : activeOrders) {
            OrderStatus currentStatus = order.getStatus();
            OrderStatus previousStatus = lastKnownOrderStatuses.put(order.getId(), currentStatus);

            if (previousStatus != null && currentStatus != previousStatus) {
                ordersChanged = true;
                notifyOrderUpdate(order.getId(), order.getTableNumber(), currentStatus.name(), currentStatus.getDisplayName());
            } else if (previousStatus == null) {
                // Если появился новый заказ
                ordersChanged = true;
            }
        }

        if (ordersChanged) {
            notifySchedulerUpdate();
            notifyKdsUpdate(); // Дергаем KDS, потому что могли сгенерироваться новые задачи от планировщика!
        }
    }

    @Scheduled(fixedRate = 5000)
    @Transactional(readOnly = true)
    public void pollTasks() {
        List<com.example.restaurant.models.CookingTask> activeTasks = cookingTaskRepository.findByStatusIn(
                List.of(com.example.restaurant.enums.CookingTaskStatus.PLANNED,
                        com.example.restaurant.enums.CookingTaskStatus.IN_PROGRESS)
        );

        java.util.Set<Long> idsToCheck = new java.util.HashSet<>(lastKnownTaskStatuses.keySet());
        for (com.example.restaurant.models.CookingTask t : activeTasks) {
            idsToCheck.add(t.getId());
        }

        if (idsToCheck.isEmpty()) return;

        List<com.example.restaurant.models.CookingTask> tasksToCheck = cookingTaskRepository.findAllById(idsToCheck);
        boolean changed = false;

        for (com.example.restaurant.models.CookingTask task : tasksToCheck) {
            com.example.restaurant.enums.CookingTaskStatus currentStatus = task.getStatus();
            com.example.restaurant.enums.CookingTaskStatus previousStatus = lastKnownTaskStatuses.put(task.getId(), currentStatus);

            if (previousStatus != null && currentStatus != previousStatus) {
                changed = true;
            } else if (previousStatus == null) {
                changed = true;
            }
        }

        lastKnownTaskStatuses.keySet().removeIf(id -> {
            com.example.restaurant.enums.CookingTaskStatus status = lastKnownTaskStatuses.get(id);
            return status == com.example.restaurant.enums.CookingTaskStatus.DONE ||
                    status == com.example.restaurant.enums.CookingTaskStatus.CANCELLED ||
                    status == com.example.restaurant.enums.CookingTaskStatus.FAILED;
        });

        if (changed) {
            notifySchedulerUpdate();
            notifyKdsUpdate();
        }
    }
}