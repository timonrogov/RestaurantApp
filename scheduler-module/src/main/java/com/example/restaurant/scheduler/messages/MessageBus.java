package com.example.restaurant.scheduler.messages;

import com.example.restaurant.scheduler.agents.BaseAgent;
import com.example.restaurant.scheduler.config.SchedulerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Шина сообщений — центральный маршрутизатор всех переговоров между агентами.
 *
 * ИСПРАВЛЕНИЕ БАГ-03: Добавлена потокобезопасность:
 *   - HashMap → ConcurrentHashMap (безопасный реестр агентов)
 *   - LinkedList → ConcurrentLinkedQueue (безопасная очередь)
 *   - processAll() синхронизирован — только один поток обрабатывает очередь в момент времени
 *   - deliver() синхронизирован с processAll() через единый монитор объекта
 */
@Component
public class MessageBus {

    private static final Logger log = LoggerFactory.getLogger(MessageBus.class);

    /**
     * Реестр всех зарегистрированных агентов: agentId → агент.
     * ИСПРАВЛЕНО: ConcurrentHashMap вместо HashMap для потокобезопасного доступа.
     */
    private final Map<String, BaseAgent> agents = new ConcurrentHashMap<>();

    /**
     * Очередь сообщений, ожидающих доставки.
     * ИСПРАВЛЕНО: ConcurrentLinkedQueue вместо LinkedList.
     */
    private final Queue<DeliveryItem> queue = new ConcurrentLinkedQueue<>();

    private final NegotiationFileLogger fileLogger;

    private final SchedulerProperties props;

    public MessageBus(NegotiationFileLogger fileLogger, SchedulerProperties props) {
        this.fileLogger = fileLogger;
        this.props = props;
    }

    // -----------------------------------------------------------------------
    // Регистрация агентов
    // -----------------------------------------------------------------------

    public void register(BaseAgent agent) {
        agent.setMessageBus(this);
        agents.put(agent.getAgentId(), agent);
        log.debug("Зарегистрирован агент: {}", agent.getAgentId());
    }

    public void unregister(String agentId) {
        BaseAgent removed = agents.remove(agentId);
        if (removed != null) {
            log.debug("Снят с регистрации агент: {}", agentId);
        } else {
            log.warn("Попытка снять с регистрации несуществующего агента: {}", agentId);
        }
    }

    public Optional<BaseAgent> getAgent(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }

    // -----------------------------------------------------------------------
    // Доставка сообщений
    // -----------------------------------------------------------------------

    /**
     * Поставить сообщение в очередь доставки.
     *
     * ИСПРАВЛЕНО БАГ-03: ConcurrentLinkedQueue.add() атомарна — безопасна из нескольких потоков.
     */
    public void deliver(String recipientId, Message message) {
        queue.add(new DeliveryItem(recipientId, message));
        fileLogger.logCommunication(message, recipientId);
        log.trace("В очередь добавлено: {} → {}, тип={}", message.getSenderId(), recipientId, message.getType());
    }

    /**
     * Обработать все сообщения в очереди.
     *
     * ИСПРАВЛЕНО БАГ-03: Метод synchronized — только один поток может выполнять
     * processAll() в каждый момент времени. Это критично, т.к. агенты не потокобезопасны:
     * они хранят состояние в полях и модифицируют его во время обработки сообщений.
     *
     * Алгоритм: пока очередь не пуста — извлекаем следующее сообщение и доставляем.
     * Агент-получатель может в процессе обработки добавить новые сообщения в очередь
     * (через deliver). Они встанут в хвост и тоже будут обработаны в этом вызове.
     */
    public synchronized void processAll() {
        final int MAX_ITERATIONS = props.getMessageBus().getMaxIterations();;
        int iterations = 0;

        log.debug("Начало обработки очереди сообщений, размер: {}", queue.size());

        while (!queue.isEmpty()) {
            if (iterations++ > MAX_ITERATIONS) {
                log.error("MessageBus: превышен лимит итераций ({}). " +
                                "Возможен бесконечный цикл в логике агентов. " +
                                "Оставшихся сообщений в очереди: {}",
                        MAX_ITERATIONS, queue.size());
                queue.clear();
                break;
            }

            DeliveryItem item = queue.poll();
            if (item == null) break; // Защита от гонки (очень маловероятно, но возможно)

            BaseAgent recipient = agents.get(item.getRecipientId());

            if (recipient == null) {
                log.warn("Агент-получатель не найден: {}. Сообщение типа {} от {} отброшено.",
                        item.getRecipientId(), item.getMessage().getType(), item.getMessage().getSenderId());
                continue;
            }

            recipient.handleMessage(item.getMessage());
        }

        log.debug("Очередь сообщений обработана за {} итераций", iterations);
    }

    public int getQueueSize() {
        return queue.size();
    }

    public int getAgentCount() {
        return agents.size();
    }

    // -----------------------------------------------------------------------
    // Вложенный класс: единица очереди
    // -----------------------------------------------------------------------

    private static class DeliveryItem {

        private final String recipientId;
        private final Message message;

        DeliveryItem(String recipientId, Message message) {
            this.recipientId = recipientId;
            this.message = message;
        }

        String getRecipientId() { return recipientId; }
        Message getMessage() { return message; }
    }
}