package com.example.restaurant.scheduler.agents;

import com.example.restaurant.enums.CookSpecialization;
import com.example.restaurant.scheduler.messages.Message;
import com.example.restaurant.scheduler.messages.MessageType;
import com.example.restaurant.scheduler.schedule.CookSchedule;
import com.example.restaurant.scheduler.schedule.EquipmentTypeSchedule;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Агент сцены — центральный реестр всех ресурсов планировщика.
 *
 * Хранит ссылки на всех агентов-поваров и агентов-оборудования,
 * а также их расписания. Отвечает на запросы TaskAgent-ов о том,
 * какие ресурсы подходят для выполнения конкретной задачи.
 *
 * Пассивный агент: никогда не инициирует сообщения самостоятельно.
 * Обрабатывает только два типа входящих сообщений:
 *   GET_AVAILABLE_COOKS      → AVAILABLE_COOKS_RESPONSE
 *   GET_AVAILABLE_EQUIPMENT  → AVAILABLE_EQUIPMENT_RESPONSE
 *
 * Регистрируется в MessageBus с ID "SCENE" — единственный экземпляр в системе.
 * TaskAgent знает этот ID как константу и обращается напрямую.
 *
 * Публичные методы (registerCookAgent, markCookInactive и т.д.) вызываются
 * не через систему сообщений, а напрямую из DispatcherAgent — это допустимо,
 * так как DispatcherAgent создаёт SceneAgent и держит на него прямую ссылку.
 */
public class SceneAgent extends BaseAgent {

    /** Фиксированный ID — TaskAgent-ы обращаются к сцене по этой константе. */
    public static final String AGENT_ID = "SCENE";

    // -----------------------------------------------------------------------
    // Реестры агентов-ресурсов
    // -----------------------------------------------------------------------

    /**
     * cookProfileId → CookAgent.
     * Ключ — ID записи CookProfile в БД, не ID сотрудника (Employee.id).
     */
    private final Map<Long, CookAgent> cookAgents = new HashMap<>();

    /**
     * equipmentType → EquipmentTypeAgent.
     * Ключ — строковый код типа: "OVEN", "GRILL" и т.д.
     * Один агент на тип — именно поэтому ключ String, а не Long.
     */
    private final Map<String, EquipmentTypeAgent> equipmentTypeAgents = new HashMap<>();

    // -----------------------------------------------------------------------
    // Реестры расписаний (SceneAgent хранит их здесь для доступа из TaskAgent
    // через метод getCookSchedule — нужно при расчёте loadScore)
    // -----------------------------------------------------------------------

    /**
     * cookProfileId → расписание повара.
     * TaskAgent запрашивает расписание чтобы вычислить loadScore варианта:
     * {@code schedule.getOccupancyRate(60)}
     */
    private final Map<Long, CookSchedule> cookSchedules = new HashMap<>();

    /**
     * equipmentType → расписание типа оборудования.
     */
    private final Map<String, EquipmentTypeSchedule> equipmentTypeSchedules = new HashMap<>();

    // -----------------------------------------------------------------------
    // Конструктор
    // -----------------------------------------------------------------------

    public SceneAgent() {
        super(AGENT_ID);
    }

    // -----------------------------------------------------------------------
    // Обработка входящих сообщений
    // -----------------------------------------------------------------------

    @Override
    protected void dispatch(Message message) {
        switch (message.getType()) {
            case GET_AVAILABLE_COOKS     -> handleGetAvailableCooks(message);
            case GET_AVAILABLE_EQUIPMENT -> handleGetAvailableEquipment(message);
            default -> log.warn("{}: получено неожиданное сообщение типа {}",
                    agentId, message.getType());
        }
    }

    /**
     * Обработать запрос о доступных поварах.
     *
     * Тело входящего сообщения: {@link CookSpecialization}.
     *
     * Логика:
     *   1. Прочитать требуемую специализацию из тела сообщения
     *   2. Найти всех активных поваров подходящей специализации
     *      (включая UNIVERSAL — такие повара подходят для любой задачи)
     *   3. Собрать список их agentId
     *   4. Отправить AVAILABLE_COOKS_RESPONSE обратно TaskAgent-у
     *
     * Тело ответного сообщения: {@code List<String>} — список agentId.
     * Если подходящих поваров нет — отправляем пустой список (не null).
     */
    private void handleGetAvailableCooks(Message message) {
        CookSpecialization required = (CookSpecialization) message.getBody();

        List<String> availableAgentIds = new ArrayList<>();
        for (Map.Entry<Long, CookAgent> entry : cookAgents.entrySet()) {
            CookAgent cookAgent = entry.getValue();
            CookSpecialization spec = cookAgent.getSpecialization();

            // Повар подходит если его специализация совпадает с требуемой,
            // или если он UNIVERSAL (работает в любом цехе)
            boolean specMatches = spec == required || spec == CookSpecialization.UNIVERSAL;
            boolean isActive = cookAgent.isActive();

            if (specMatches && isActive) {
                availableAgentIds.add(cookAgent.getAgentId());
            }
        }

        log.debug("{}: для специализации {} найдено {} поваров",
                agentId, required, availableAgentIds.size());

        reply(message, MessageType.AVAILABLE_COOKS_RESPONSE, availableAgentIds);
    }

    /**
     * Обработать запрос об агенте оборудования нужного типа.
     *
     * Тело входящего сообщения: {@code String equipmentType}.
     *
     * Логика:
     *   1. Найти EquipmentTypeAgent для запрошенного типа
     *   2. Проверить что он активен (totalCapacity > 0)
     *   3. Отправить его agentId обратно (или null если нет такого типа)
     *
     * Тело ответного сообщения: {@code String equipmentTypeAgentId} или null.
     * null означает «оборудования такого типа нет или оно недоступно» —
     * TaskAgent должен считать этот вариант повара невозможным.
     */
    private void handleGetAvailableEquipment(Message message) {
        String equipmentType = (String) message.getBody();

        EquipmentTypeAgent agent = equipmentTypeAgents.get(equipmentType);

        if (agent == null || !agent.isActive()) {
            log.debug("{}: оборудование типа '{}' не найдено или недоступно",
                    agentId, equipmentType);
            reply(message, MessageType.AVAILABLE_EQUIPMENT_RESPONSE, null);
            return;
        }

        log.debug("{}: для типа оборудования '{}' найден агент {}",
                agentId, equipmentType, agent.getAgentId());

        reply(message, MessageType.AVAILABLE_EQUIPMENT_RESPONSE, agent.getAgentId());
    }

    // -----------------------------------------------------------------------
    // Регистрация ресурсов (вызывается из DispatcherAgent при инициализации)
    // -----------------------------------------------------------------------

    /**
     * Зарегистрировать агента повара и его расписание.
     * Вызывается DispatcherAgent при старте системы для каждого активного повара,
     * а также когда повар «возвращается» после недоступности (COOK_AVAILABLE).
     *
     * @param agent    агент повара
     * @param schedule его расписание (обычно пустое при регистрации)
     */
    public void registerCookAgent(CookAgent agent, CookSchedule schedule) {
        long cookId = agent.getCookId();
        cookAgents.put(cookId, agent);
        cookSchedules.put(cookId, schedule);
        log.info("{}: зарегистрирован повар {}, специализация {}",
                agentId, agent.getAgentId(), agent.getSpecialization());
    }

    /**
     * Зарегистрировать агента типа оборудования и его расписание.
     * Вызывается DispatcherAgent при старте системы.
     *
     * Если агент для данного типа уже существует (например, добавили новую
     * единицу оборудования того же типа) — просто обновляем ёмкость через
     * EquipmentTypeSchedule.increaseCapacity(), а не перезаписываем агента.
     *
     * @param agent    агент типа оборудования
     * @param schedule его расписание
     */
    public void registerEquipmentTypeAgent(EquipmentTypeAgent agent,
                                           EquipmentTypeSchedule schedule) {
        String type = agent.getEquipmentType();
        equipmentTypeAgents.put(type, agent);
        equipmentTypeSchedules.put(type, schedule);
        log.info("{}: зарегистрировано оборудование типа '{}', ёмкость {}",
                agentId, type, schedule.getTotalCapacity());
    }

    // -----------------------------------------------------------------------
    // Пометка ресурсов как неактивных
    // -----------------------------------------------------------------------

    /**
     * Пометить повара как недоступного.
     * Вызывается DispatcherAgent при получении COOK_UNAVAILABLE.
     * После этого SceneAgent перестаёт включать этого повара в ответы на GET_AVAILABLE_COOKS.
     *
     * Расписание повара при этом не очищается — это делает сам CookAgent
     * (он отправляет REMOVE_TASK всем своим задачам).
     *
     * @param cookId ID записи CookProfile
     */
    public void markCookInactive(long cookId) {
        CookAgent agent = cookAgents.get(cookId);
        if (agent != null) {
            agent.setActive(false);
            log.info("{}: повар {} помечен как недоступный", agentId, agent.getAgentId());
        } else {
            log.warn("{}: попытка пометить несуществующего повара (cookId={})", agentId, cookId);
        }
    }

    /**
     * Пометить повара как снова доступного.
     * Вызывается DispatcherAgent при COOK_AVAILABLE.
     *
     * @param cookId ID записи CookProfile
     */
    public void markCookActive(long cookId) {
        CookAgent agent = cookAgents.get(cookId);
        if (agent != null) {
            agent.setActive(true);
            log.info("{}: повар {} снова доступен", agentId, agent.getAgentId());
        }
    }

    /**
     * Уменьшить ёмкость типа оборудования (при поломке одной единицы).
     * Вызывается DispatcherAgent при EQUIPMENT_BROKEN.
     * Если ёмкость становится 0 — агент остаётся в реестре, но isActive() вернёт false.
     *
     * @param equipmentType тип оборудования
     * @param amount        на сколько уменьшить (= maxParallelTasks сломавшейся единицы)
     */
    public void decreaseEquipmentCapacity(String equipmentType, int amount) {
        EquipmentTypeSchedule schedule = equipmentTypeSchedules.get(equipmentType);
        if (schedule != null) {
            schedule.decreaseCapacity(amount);
            log.info("{}: ёмкость типа '{}' уменьшена на {}, теперь {}",
                    agentId, equipmentType, amount, schedule.getTotalCapacity());
        }
    }

    /**
     * Увеличить ёмкость типа оборудования (при починке единицы).
     * Вызывается DispatcherAgent при EQUIPMENT_FIXED.
     *
     * @param equipmentType тип оборудования
     * @param amount        на сколько увеличить
     */
    public void increaseEquipmentCapacity(String equipmentType, int amount) {
        EquipmentTypeSchedule schedule = equipmentTypeSchedules.get(equipmentType);
        if (schedule != null) {
            schedule.increaseCapacity(amount);
            log.info("{}: ёмкость типа '{}' увеличена на {}, теперь {}",
                    agentId, equipmentType, amount, schedule.getTotalCapacity());
        }
    }

    // -----------------------------------------------------------------------
    // Геттеры расписаний (используются TaskAgent-ом для расчёта loadScore)
    // -----------------------------------------------------------------------

    /**
     * Получить расписание повара.
     * TaskAgent вызывает этот метод через SceneAgent чтобы рассчитать loadScore:
     * {@code sceneAgent.getCookSchedule(cookId).getOccupancyRate(60)}
     *
     * Почему через SceneAgent, а не через CookAgent напрямую?
     * Потому что TaskAgent взаимодействует с CookAgent только через сообщения.
     * Прямые вызовы методов между агентами нарушают архитектуру.
     * SceneAgent — единственное место, где такой прямой доступ к расписаниям допустим.
     *
     * @param cookId ID записи CookProfile
     * @return расписание или null если такого повара нет
     */
    public CookSchedule getCookSchedule(long cookId) {
        return cookSchedules.get(cookId);
    }

    /**
     * Получить расписание типа оборудования.
     *
     * @param equipmentType тип оборудования
     * @return расписание или null если такого типа нет
     */
    public EquipmentTypeSchedule getEquipmentTypeSchedule(String equipmentType) {
        return equipmentTypeSchedules.get(equipmentType);
    }

    // -----------------------------------------------------------------------
    // Утилиты для DispatcherAgent и тестов
    // -----------------------------------------------------------------------

    /** Число зарегистрированных активных поваров (для мониторинга). */
    public long getActiveCookCount() {
        return cookAgents.values().stream().filter(CookAgent::isActive).count();
    }

    /** Все зарегистрированные agentId поваров (для отладки). */
    public List<String> getAllCookAgentIds() {
        return cookAgents.values().stream()
                .map(CookAgent::getAgentId)
                .toList();
    }

    /** Все зарегистрированные типы оборудования (для отладки). */
    public List<String> getAllEquipmentTypes() {
        return new ArrayList<>(equipmentTypeAgents.keySet());
    }
}