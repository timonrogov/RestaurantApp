package com.example.restaurant.scheduler.agents;

import com.example.restaurant.scheduler.dispatcher.MessageBus;
import com.example.restaurant.scheduler.messages.Message;
import com.example.restaurant.scheduler.messages.MessageType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Базовый класс для всех агентов планировщика.
 *
 * Все конкретные агенты (CookAgent, TaskAgent, OrderAgent и т.д.)
 * наследуются от этого класса и реализуют только один метод — dispatch().
 * Всё остальное (логирование, защита от исключений, отправка сообщений)
 * берёт на себя этот базовый класс.
 *
 * Соглашение об именовании agentId:
 *   CookAgent          → "COOK_{cookProfile.id}"           например "COOK_3"
 *   TaskAgent          → "TASK_{cookingTask.id}"           например "TASK_42"
 *   OrderAgent         → "ORDER_{order.id}"                например "ORDER_15"
 *   EquipmentTypeAgent → "EQUIPMENT_TYPE_{equipmentType}"  например "EQUIPMENT_TYPE_OVEN"
 *   SceneAgent         → "SCENE"                           (единственный)
 *   DispatcherAgent    → "DISPATCHER"                      (единственный)
 *
 * Пример наследования:
 * <pre>
 *   public class CookAgent extends BaseAgent {
 *       public CookAgent(CookProfile profile) {
 *           super("COOK_" + profile.getId());
 *           this.profile = profile;
 *       }
 *
 *       {@literal @}Override
 *       protected void dispatch(Message message) {
 *           switch (message.getType()) {
 *               case PARAMS_REQUEST   -> handleParamsRequest(message);
 *               case PLANNING_REQUEST -> handlePlanningRequest(message);
 *               default -> log.warn("{}: неизвестный тип сообщения {}", agentId, message.getType());
 *           }
 *       }
 *   }
 * </pre>
 */
public abstract class BaseAgent {

    protected final Logger log = LoggerFactory.getLogger(getClass());

    /**
     * Уникальный идентификатор агента.
     * Используется как ключ в реестре MessageBus и как senderId в Message.
     * Формат: "<ТИП>_<ID>" или просто "<ТИП>" для одиночных агентов.
     */
    protected final String agentId;

    /**
     * Ссылка на шину сообщений.
     * Не передаётся в конструктор — инжектируется через setMessageBus()
     * в момент регистрации агента в MessageBus.
     * Это намеренно: агент создаётся до регистрации, и на этапе конструктора
     * шина ещё не известна.
     */
    protected MessageBus messageBus;

    /**
     * Конструктор базового агента.
     *
     * @param agentId уникальный идентификатор, например "COOK_7" или "TASK_42"
     */
    protected BaseAgent(String agentId) {
        this.agentId = agentId;
    }

    // -----------------------------------------------------------------------
    // Публичный API
    // -----------------------------------------------------------------------

    /**
     * Точка входа для получения сообщений. Вызывается MessageBus.
     *
     * Оборачивает dispatch() в try-catch, чтобы исключение в одном агенте
     * не прерывало обработку всей очереди сообщений. Ошибка логируется
     * и обработка продолжается со следующего сообщения.
     *
     * Метод final — наследники не могут переопределить логику обёртки.
     * Вся бизнес-логика должна быть в dispatch().
     *
     * @param message входящее сообщение
     */
    public final void handleMessage(Message message) {
        log.debug("{} получил сообщение типа {} от {}",
                agentId, message.getType(), message.getSenderId());
        try {
            dispatch(message);
        } catch (Exception e) {
            log.error("{}: ошибка при обработке сообщения {} от {}: {}",
                    agentId, message.getType(), message.getSenderId(), e.getMessage(), e);
        }
    }

    public String getAgentId() {
        return agentId;
    }

    /**
     * Инжектировать ссылку на шину. Вызывается только из MessageBus.register().
     */
    public void setMessageBus(MessageBus messageBus) {
        this.messageBus = messageBus;
    }

    // -----------------------------------------------------------------------
    // Защищённый API для наследников
    // -----------------------------------------------------------------------

    /**
     * Обработать входящее сообщение. Реализуется каждым конкретным агентом.
     *
     * Рекомендуемая структура реализации — switch по message.getType():
     * <pre>
     *   switch (message.getType()) {
     *       case PARAMS_REQUEST -> handleParamsRequest(message);
     *       case PLANNING_REQUEST -> handlePlanningRequest(message);
     *       default -> log.warn("{}: неизвестный тип {}", agentId, message.getType());
     *   }
     * </pre>
     *
     * @param message входящее сообщение (никогда не null)
     */
    protected abstract void dispatch(Message message);

    /**
     * Отправить сообщение другому агенту.
     * Все исходящие сообщения должны идти через этот метод — никогда
     * не вызывать MessageBus.deliver() напрямую из наследников.
     *
     * @param recipientId ID агента-получателя
     * @param type        тип сообщения
     * @param body        тело сообщения (может быть null)
     */
    protected void send(String recipientId, MessageType type, Object body) {
        if (messageBus == null) {
            log.error("{}: попытка отправить сообщение до регистрации в MessageBus!", agentId);
            return;
        }
        Message message = new Message(type, body, agentId);
        messageBus.deliver(recipientId, message);
        log.debug("{} отправил {} → {}", agentId, type, recipientId);
    }

    /**
     * Ответить отправителю входящего сообщения.
     * Удобный shortcut чтобы не извлекать senderId вручную.
     *
     * @param incoming исходное сообщение, на которое отвечаем
     * @param type     тип ответного сообщения
     * @param body     тело ответного сообщения
     */
    protected void reply(Message incoming, MessageType type, Object body) {
        send(incoming.getSenderId(), type, body);
    }

    @Override
    public String toString() {
        return agentId;
    }
}