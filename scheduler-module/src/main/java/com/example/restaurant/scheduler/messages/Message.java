package com.example.restaurant.scheduler.messages;

/**
 * Неизменяемое сообщение между агентами планировщика.
 *
 * Сообщение создаётся один раз и не модифицируется — это важно для
 * корректности однопоточной очереди (MessageBus). Любой агент, получивший
 * сообщение, может безопасно читать его поля.
 *
 * Тело (body) — нетипизированный Object, так как каждый тип сообщения
 * несёт разный payload. Агент приводит body к нужному DTO-классу сам,
 * зная какой тип сообщения он обрабатывает.
 *
 * Пример создания:
 *   Message msg = new Message(MessageType.PARAMS_REQUEST, body, "TASK_42");
 *
 * Пример чтения тела:
 *   ParamsRequestBody req = (ParamsRequestBody) message.getBody();
 */
public class Message {

    private final MessageType type;

    /**
     * Тело сообщения. Конкретный тип зависит от MessageType.
     * Смотри комментарии в MessageType и классы в пакете dto/.
     * Может быть null для сообщений, не требующих дополнительных данных
     * (например, INIT без параметров).
     */
    private final Object body;

    /**
     * Идентификатор агента-отправителя.
     * Формат: "<ТИП>_<ID>", например "TASK_42", "COOK_7", "ORDER_15".
     * Используется получателем для отправки ответного сообщения.
     */
    private final String senderId;

    /**
     * Время создания сообщения в миллисекундах (System.currentTimeMillis()).
     * Полезно для отладки и логирования порядка переговоров.
     */
    private final long timestamp;

    public Message(MessageType type, Object body, String senderId) {
        this.type = type;
        this.body = body;
        this.senderId = senderId;
        this.timestamp = System.currentTimeMillis();
    }

    public MessageType getType() {
        return type;
    }

    public Object getBody() {
        return body;
    }

    public String getSenderId() {
        return senderId;
    }

    public long getTimestamp() {
        return timestamp;
    }

    @Override
    public String toString() {
        return "Message{type=" + type + ", sender='" + senderId + "', ts=" + timestamp + "}";
    }
}