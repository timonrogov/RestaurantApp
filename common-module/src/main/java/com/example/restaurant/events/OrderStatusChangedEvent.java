package com.example.restaurant.events;

import com.example.restaurant.enums.OrderStatus;
import org.springframework.context.ApplicationEvent;

/**
 * Spring-событие: статус заказа изменился.
 *
 * Публикуется OrderService при каждом изменении статуса заказа.
 * Слушатели (например, SchedulerService) реагируют на нужные им статусы.
 *
 * Использование ApplicationEvent вместо прямого вызова SchedulerService
 * позволяет избежать циклической зависимости:
 *   common-module публикует событие, не зная о scheduler-module.
 *   scheduler-module слушает событие, завися только от common-module.
 */
public class OrderStatusChangedEvent extends ApplicationEvent {

    private final Long orderId;
    private final OrderStatus newStatus;
    private final OrderStatus previousStatus;

    public OrderStatusChangedEvent(Object source,
                                   Long orderId,
                                   OrderStatus newStatus,
                                   OrderStatus previousStatus) {
        super(source);
        this.orderId = orderId;
        this.newStatus = newStatus;
        this.previousStatus = previousStatus;
    }

    public Long getOrderId()             { return orderId; }
    public OrderStatus getNewStatus()    { return newStatus; }
    public OrderStatus getPreviousStatus() { return previousStatus; }
}