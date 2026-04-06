package com.example.restaurant.scheduler.dispatcher;

import com.example.restaurant.scheduler.agents.BaseAgent;
import com.example.restaurant.scheduler.messages.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Optional;
import java.util.Queue;

/**
 * Шина сообщений — центральный маршрутизатор всех переговоров между агентами.
 *
 * Работает как однопоточная очередь: агенты не вызывают друг друга напрямую,
 * а кладут сообщения в очередь через метод deliver(). Очередь обрабатывается
 * вызовом processAll() из SchedulerService — один за другим, без параллелизма.
 *
 * Это намеренное архитектурное решение: однопоточность упрощает отладку,
 * исключает гонки данных и полностью соответствует концепции из методички
 * (акторная модель без многопоточности).
 *
 * Жизненный цикл сообщения:
 *   1. Агент A вызывает send() → сообщение попадает в очередь
 *   2. SchedulerService вызывает processAll()
 *   3. MessageBus берёт сообщение из головы очереди
 *   4. Находит агента-получателя по recipientId
 *   5. Вызывает agent.handleMessage(message)
 *   6. Агент-получатель может в ответ положить новые сообщения в очередь
 *   7. Цикл продолжается пока очередь не опустеет
 *
 * Важно: processAll() — реентерабельный по природе очереди. Если во время
 * обработки сообщения агент добавляет новые — они встают в хвост и тоже
 * будут обработаны в этом же вызове processAll().
 */
@Component
public class MessageBus {

    private static final Logger log = LoggerFactory.getLogger(MessageBus.class);

    /** Реестр всех зарегистрированных агентов: agentId → агент. */
    private final Map<String, BaseAgent> agents = new HashMap<>();

    /** Очередь сообщений, ожидающих доставки. */
    private final Queue<DeliveryItem> queue = new LinkedList<>();

    // -----------------------------------------------------------------------
    // Регистрация агентов
    // -----------------------------------------------------------------------

    /**
     * Зарегистрировать агента в шине.
     * После регистрации агент может получать сообщения и сам отправлять сообщения.
     * Также инжектирует ссылку на шину в агента (агент сам не хранит шину в конструкторе).
     *
     * @param agent агент для регистрации
     */
    public void register(BaseAgent agent) {
        agent.setMessageBus(this);
        agents.put(agent.getAgentId(), agent);
        log.debug("Зарегистрирован агент: {}", agent.getAgentId());
    }

    /**
     * Снять агента с регистрации.
     * Вызывается когда повар стал недоступен или заказ завершён —
     * чтобы не хранить мёртвые агенты в памяти.
     *
     * @param agentId ID агента для удаления
     */
    public void unregister(String agentId) {
        BaseAgent removed = agents.remove(agentId);
        if (removed != null) {
            log.debug("Снят с регистрации агент: {}", agentId);
        } else {
            log.warn("Попытка снять с регистрации несуществующего агента: {}", agentId);
        }
    }

    /**
     * Получить агента по ID.
     * Используется для отладки, тестов и прямого обращения к SceneAgent
     * из DispatcherAgent при инициализации.
     *
     * @param agentId ID агента
     * @return Optional с агентом или пустой если не найден
     */
    public Optional<BaseAgent> getAgent(String agentId) {
        return Optional.ofNullable(agents.get(agentId));
    }

    // -----------------------------------------------------------------------
    // Доставка сообщений
    // -----------------------------------------------------------------------

    /**
     * Поставить сообщение в очередь доставки.
     * Агенты вызывают этот метод через BaseAgent.send() — никогда напрямую.
     *
     * @param recipientId ID агента-получателя
     * @param message     сообщение для доставки
     */
    public void deliver(String recipientId, Message message) {
        queue.add(new DeliveryItem(recipientId, message));
        log.trace("В очередь добавлено: {} → {}, тип={}", message.getSenderId(), recipientId, message.getType());
    }

    /**
     * Обработать все сообщения в очереди.
     *
     * Вызывается из SchedulerService после каждого внешнего события
     * (новый заказ, повар недоступен и т.д.). Метод работает пока очередь
     * не опустеет — то есть обрабатывает и все «вторичные» сообщения,
     * порождённые агентами во время обработки «первичных».
     *
     * Защита от бесконечного цикла: если очередь не пустеет за MAX_ITERATIONS
     * итераций — принудительно прерывается с предупреждением в лог.
     * Это сигнал о баге в логике агентов (цикл вытеснений).
     */
    public void processAll() {
        final int MAX_ITERATIONS = 10_000;
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

    /**
     * Текущий размер очереди. Используется для мониторинга и тестов.
     */
    public int getQueueSize() {
        return queue.size();
    }

    /**
     * Число зарегистрированных агентов. Используется для тестов.
     */
    public int getAgentCount() {
        return agents.size();
    }

    // -----------------------------------------------------------------------
    // Вложенный класс: единица очереди
    // -----------------------------------------------------------------------

    /**
     * Единица очереди: сообщение + адрес получателя.
     * Приватный класс — снаружи не используется.
     */
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