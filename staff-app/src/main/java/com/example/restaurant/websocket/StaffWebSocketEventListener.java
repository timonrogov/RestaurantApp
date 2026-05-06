package com.example.restaurant.websocket;

import com.example.restaurant.events.CookingTaskUpdatedEvent;
import com.example.restaurant.events.OrderStatusChangedEvent;
import com.example.restaurant.events.WaiterCallEvent;
import com.example.restaurant.repositories.OrderRepository;
import com.example.restaurant.services.StaffWebSocketService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Слушатель Spring-событий в staff-app.
 *
 * Связывает бизнес-события (изменение заказа, задачи, вызова)
 * с WebSocket-уведомлениями для браузеров персонала.
 */
@Component
@RequiredArgsConstructor
public class StaffWebSocketEventListener {

    private final StaffWebSocketService wsService;
    private final OrderRepository orderRepository;

    // ИСПОЛЬЗУЕМ @TransactionalEventListener вместо @EventListener
    // Сигнал уйдет в сокеты ТОЛЬКО после успешного COMMIT в базу данных
    /**
     * Заказ изменил статус → обновить страницу заказов и планировщик.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        // Подгружаем данные заказа для отправки в браузер
        orderRepository.findById(event.getOrderId()).ifPresent(order -> {
            wsService.notifyOrderUpdate(
                    order.getId(),
                    order.getTableNumber() != null ? order.getTableNumber() : "—",
                    order.getStatus().name(),
                    order.getStatus().getDisplayName()
            );
        });

        // Планировщик тоже нужно обновить (новый заказ или отмена влияют на задачи)
        wsService.notifySchedulerUpdate();

        // При подаче заказа — очищаем его задачи с KDS
        if (event.getNewStatus() == com.example.restaurant.enums.OrderStatus.SERVED) {
            wsService.notifyKdsOrderServed(event.getOrderId());
        }
    }

    /**
     * Задача повара изменила статус → обновить KDS и планировщик.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onCookingTaskUpdated(CookingTaskUpdatedEvent event) {
        wsService.notifyKdsUpdate();       // Все KDS-экраны обновятся
        wsService.notifySchedulerUpdate(); // Планировщик обновит статистику
    }

    /**
     * Вызов официанта создан или закрыт → обновить страницу вызовов и бейдж в шапке.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onWaiterCall(WaiterCallEvent event) {
        String type = event.getType() == WaiterCallEvent.Type.CREATED ? "NEW_CALL" : "CALL_RESOLVED";
        wsService.notifyCallsUpdate(
                type,
                event.getCallId(),
                event.getTableNumber(),
                event.getActiveCallsCount()
        );
    }
}