package com.example.restaurant.events;

import org.springframework.context.ApplicationEvent;

/**
 * Spring-событие: состояние задач планировщика изменилось.
 *
 * Публикуется SchedulerService при любом изменении CookingTask:
 * старт, завершение, задержка, перепланирование.
 *
 * Слушатели (StaffWebSocketEventListener) пушат сигнал обновления
 * на KDS-экраны поваров и на страницу планировщика администратора.
 */
public class CookingTaskUpdatedEvent extends ApplicationEvent {

    /** ID CookProfile повара, чья задача изменилась. Может быть null при перепланировании. */
    private final Long cookProfileId;

    public CookingTaskUpdatedEvent(Object source, Long cookProfileId) {
        super(source);
        this.cookProfileId = cookProfileId;
    }

    public Long getCookProfileId() { return cookProfileId; }
}