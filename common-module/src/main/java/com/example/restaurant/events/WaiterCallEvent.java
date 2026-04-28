package com.example.restaurant.events;

import com.example.restaurant.enums.CallStatus;
import org.springframework.context.ApplicationEvent;

/**
 * Spring-событие: изменилось состояние вызова официанта.
 *
 * Публикуется WaiterCallService при создании и закрытии вызова.
 * Слушатели (StaffWebSocketEventListener) пушат обновления в браузер через WebSocket.
 */
public class WaiterCallEvent extends ApplicationEvent {

    public enum Type { CREATED, RESOLVED }

    private final Type type;
    private final Long callId;
    private final String tableNumber;
    private final long activeCallsCount;

    public WaiterCallEvent(Object source, Type type, Long callId,
                           String tableNumber, long activeCallsCount) {
        super(source);
        this.type = type;
        this.callId = callId;
        this.tableNumber = tableNumber;
        this.activeCallsCount = activeCallsCount;
    }

    public Type getType()              { return type; }
    public Long getCallId()            { return callId; }
    public String getTableNumber()     { return tableNumber; }
    public long getActiveCallsCount()  { return activeCallsCount; }
}