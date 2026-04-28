package com.example.restaurant.scheduler.dispatcher;

import com.example.restaurant.enums.CookingTaskStatus;
import com.example.restaurant.models.CookProfile;
import com.example.restaurant.models.CookingTask;
import com.example.restaurant.models.Equipment;
import com.example.restaurant.models.Order;
import com.example.restaurant.repositories.CookingTaskRepository;
import com.example.restaurant.repositories.CookingTaskTemplateRepository;
import com.example.restaurant.repositories.OrderCourseRepository;
import com.example.restaurant.repositories.OrderRepository;
import com.example.restaurant.scheduler.agents.BaseAgent;
import com.example.restaurant.scheduler.agents.CookAgent;
import com.example.restaurant.scheduler.agents.EquipmentTypeAgent;
import com.example.restaurant.scheduler.agents.OrderAgent;
import com.example.restaurant.scheduler.agents.SceneAgent;
import com.example.restaurant.scheduler.messages.Message;
import com.example.restaurant.scheduler.messages.MessageType;
import com.example.restaurant.scheduler.schedule.CookSchedule;
import com.example.restaurant.scheduler.schedule.EquipmentTypeSchedule;
import com.example.restaurant.scheduler.schedule.ScheduleSlot;
import com.example.restaurant.repositories.OrderRepository;
import com.example.restaurant.scheduler.messages.dto.TaskDelayBody;
import com.example.restaurant.scheduler.schedule.ScheduleSlot;
import java.time.LocalDateTime;

import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Агент-диспетчер — точка входа всей мультиагентной системы.
 *
 * Отвечает за:
 *   1. Инициализацию системы: создание SceneAgent, CookAgent-ов и EquipmentTypeAgent-ов.
 *   2. Маршрутизацию внешних событий (NEW_ORDER, COOK_UNAVAILABLE и т.д.)
 *      от SchedulerService к нужным агентам.
 *   3. Управление жизненным циклом OrderAgent-ов.
 *   4. Освобождение ресурсов при отмене заказа.
 *
 * Хранит прямые ссылки на все созданные агенты для быстрого доступа
 * без поиска в MessageBus.
 *
 * ID агента: "DISPATCHER" — единственный экземпляр в системе.
 */
public class DispatcherAgent extends BaseAgent {

    public static final String AGENT_ID = "DISPATCHER";

    // -----------------------------------------------------------------------
    // Прямые ссылки на компоненты системы
    // -----------------------------------------------------------------------

    private final MessageBus messageBus;
    private SceneAgent sceneAgent;

    /** cookProfileId → CookAgent */
    private final Map<Long, CookAgent> cookAgents = new HashMap<>();

    /** equipmentType → EquipmentTypeAgent */
    private final Map<String, EquipmentTypeAgent> equipmentTypeAgents = new HashMap<>();

    /** orderId → OrderAgent */
    private final Map<Long, OrderAgent> orderAgents = new HashMap<>();

    // -----------------------------------------------------------------------
    // Репозитории — передаются в OrderAgent при его создании
    // -----------------------------------------------------------------------

    private final CookingTaskRepository taskRepository;
    private final CookingTaskTemplateRepository templateRepository;
    private final OrderCourseRepository orderCourseRepository;
    private final OrderRepository orderRepository;

    // -----------------------------------------------------------------------
    // Конструктор
    // -----------------------------------------------------------------------

    public DispatcherAgent(MessageBus messageBus,
                           CookingTaskRepository taskRepository,
                           CookingTaskTemplateRepository templateRepository,
                           OrderCourseRepository orderCourseRepository,
                           OrderRepository orderRepository) {
        super(AGENT_ID);
        this.messageBus = messageBus;
        this.taskRepository = taskRepository;
        this.templateRepository = templateRepository;
        this.orderCourseRepository = orderCourseRepository;
        this.orderRepository = orderRepository;
    }

    // -----------------------------------------------------------------------
    // Диспетчеризация входящих сообщений
    // -----------------------------------------------------------------------

    @Override
    protected void dispatch(Message message) {
        switch (message.getType()) {
            case NEW_ORDER          -> handleNewOrder(message);
            case ORDER_CANCELLED    -> handleOrderCancelled(message);
            case COOK_UNAVAILABLE   -> handleCookUnavailable(message);
            case COOK_AVAILABLE     -> handleCookAvailable(message);
            case EQUIPMENT_BROKEN   -> handleEquipmentBroken(message);
            case EQUIPMENT_FIXED    -> handleEquipmentFixed(message);
            case ALL_TASKS_PLANNED  -> handleAllTasksPlanned(message);
            case TASK_DONE_EVENT    -> handleTaskDone(message);
            case TASK_DELAY_EVENT    -> handleTaskDelayEvent(message);
            case COOK_CREATED       -> handleCookCreated(message);
            case EQUIPMENT_CREATED  -> handleEquipmentCreated(message);
            default -> log.warn("{}: получено неожиданное сообщение типа {}",
                    agentId, message.getType());
        }
    }

    // -----------------------------------------------------------------------
    // Инициализация системы
    // -----------------------------------------------------------------------

    /**
     * Инициализировать всю мультиагентную систему.
     *
     * Вызывается из SchedulerService при старте приложения (@PostConstruct).
     * Должна вызываться ровно один раз перед первым заказом.
     *
     * Последовательность:
     *   1. Создать и зарегистрировать сам DispatcherAgent.
     *   2. Создать и зарегистрировать SceneAgent.
     *   3. Для каждого активного повара создать CookAgent + CookSchedule.
     *   4. Для каждого типа оборудования создать EquipmentTypeAgent + EquipmentTypeSchedule.
     *      (Суммируем maxParallelTasks всех единиц одного типа.)
     *
     * @param cooks      список активных профилей поваров из БД
     * @param equipments список всего активного оборудования из БД
     */
    public void initialize(List<CookProfile> cooks, List<Equipment> equipments) {
        log.info("{}: инициализация системы. Поваров: {}, единиц оборудования: {}",
                agentId, cooks.size(), equipments.size());

        // Регистрируем самого себя
        messageBus.register(this);

        // Создаём SceneAgent
        sceneAgent = new SceneAgent();
        messageBus.register(sceneAgent);
        log.debug("{}: SceneAgent зарегистрирован", agentId);

        // Создаём агентов поваров
        for (CookProfile profile : cooks) {
            CookSchedule schedule = new CookSchedule(profile.getId());
            CookAgent agent = new CookAgent(profile, schedule);
            messageBus.register(agent);
            sceneAgent.registerCookAgent(agent, schedule);
            cookAgents.put(profile.getId(), agent);
        }
        log.info("{}: зарегистрировано {} агентов поваров", agentId, cookAgents.size());

        // Создаём агентов типов оборудования.
        // Группируем Equipment по equipmentType и суммируем maxParallelTasks.
        Map<String, Integer> capacityByType = new HashMap<>();
        for (Equipment equipment : equipments) {
            capacityByType.merge(
                    equipment.getEquipmentType(),
                    equipment.getMaxParallelTasks(),
                    Integer::sum
            );
        }

        for (Map.Entry<String, Integer> entry : capacityByType.entrySet()) {
            String type = entry.getKey();
            int totalCapacity = entry.getValue();

            EquipmentTypeSchedule schedule = new EquipmentTypeSchedule(type, totalCapacity);
            EquipmentTypeAgent agent = new EquipmentTypeAgent(type, schedule);
            messageBus.register(agent);
            sceneAgent.registerEquipmentTypeAgent(agent, schedule);
            equipmentTypeAgents.put(type, agent);

            log.debug("{}: тип оборудования '{}' зарегистрирован, суммарная ёмкость={}",
                    agentId, type, totalCapacity);
        }
        log.info("{}: зарегистрировано {} типов оборудования", agentId, equipmentTypeAgents.size());

        log.info("{}: инициализация завершена. Система готова к планированию.", agentId);
        restoreSchedules();
    }

    // -----------------------------------------------------------------------
    // Обработка событий заказов
    // -----------------------------------------------------------------------

    /**
     * Новый заказ — создать OrderAgent и запустить планирование.
     *
     * Тело сообщения: {@link Order} (JPA-сущность).
     */
    private void handleNewOrder(Message message) {
        Order order = (Order) message.getBody();
        log.info("{}: новый заказ #{}, стол {}", agentId, order.getId(), order.getTableNumber());

        OrderAgent orderAgent = new OrderAgent(
                order,
                sceneAgent,
                taskRepository,
                templateRepository,
                orderCourseRepository
        );

        messageBus.register(orderAgent);
        orderAgents.put(order.getId(), orderAgent);

        // Запускаем планирование через INIT
        send(orderAgent.getAgentId(), MessageType.INIT, null);
    }

    /**
     * Заказ отменён — освободить все его ресурсы.
     *
     * Тело сообщения: {@code Long orderId}.
     *
     * Логика:
     *   1. Найти OrderAgent и снять с регистрации.
     *   2. Удалить все слоты этого заказа из расписаний поваров.
     *   3. Удалить все слоты этого заказа из расписаний оборудования.
     *   TaskAgent-ы заказа уже сняты с регистрации самим OrderAgent-ом
     *   при завершении курсов (если они завершились). Если не завершились —
     *   они попытаются отправить сообщения в несуществующий OrderAgent,
     *   MessageBus залогирует предупреждение и пропустит.
     */
    private void handleOrderCancelled(Message message) {
        long orderId = (Long) message.getBody();
        log.info("{}: отмена заказа #{}", agentId, orderId);

        // Снимаем OrderAgent с регистрации
        OrderAgent orderAgent = orderAgents.remove(orderId);
        if (orderAgent != null) {
            messageBus.unregister(orderAgent.getAgentId());
            log.debug("{}: OrderAgent {} снят с регистрации", agentId, orderAgent.getAgentId());
        } else {
            log.warn("{}: OrderAgent для заказа #{} не найден", agentId, orderId);
        }

        // Освобождаем слоты этого заказа во всех расписаниях поваров
        int freedCookSlots = 0;
        for (CookAgent cookAgent : cookAgents.values()) {
            if (cookAgent.getSchedule().removeSlotByOrderId(orderId)) {
                freedCookSlots++;
            }
        }

        // Освобождаем слоты этого заказа во всех расписаниях оборудования
        int freedEquipSlots = 0;
        for (EquipmentTypeAgent equipAgent : equipmentTypeAgents.values()) {
            freedEquipSlots += equipAgent.getSchedule().removeSlotsByOrderId(orderId);
        }

        log.info("{}: заказ #{} отменён. Освобождено слотов поваров: {}, оборудования: {}",
                agentId, orderId, freedCookSlots, freedEquipSlots);
    }

    /**
     * Все задачи заказа запланированы — снять OrderAgent с регистрации.
     *
     * Тело сообщения: {@code Long orderId}.
     * Отправляется OrderAgent-ом после завершения всех курсов.
     */
    private void handleAllTasksPlanned(Message message) {
        long orderId = (Long) message.getBody();
        log.info("{}: заказ #{} полностью запланирован", agentId, orderId);

        /*OrderAgent orderAgent = orderAgents.remove(orderId);
        if (orderAgent != null) {
            messageBus.unregister(orderAgent.getAgentId());
            log.debug("{}: OrderAgent {} завершил работу и снят с регистрации",
                    agentId, orderAgent.getAgentId());
        }*/
    }

    /**
     * Задача фактически завершена поваром (нажата кнопка на KDS).
     * Освобождаем временные слоты у повара и оборудования,
     * чтобы они могли взять новые задачи досрочно.
     */
    private void handleTaskDone(Message message) {
        long taskId = (Long) message.getBody();
        log.info("{}: задача #{} выполнена фактически, освобождаем ресурсы в RAM", agentId, taskId);

        // 1. Ищем и удаляем слот из расписания поваров
        for (CookAgent cookAgent : cookAgents.values()) {
            if (cookAgent.getSchedule().removeSlotByTaskId(taskId)) {
                log.debug("{}: слот задачи #{} удален у повара {}", agentId, taskId, cookAgent.getAgentId());
            }
        }

        // 2. Ищем и удаляем слот из расписания оборудования
        for (EquipmentTypeAgent equipAgent : equipmentTypeAgents.values()) {
            if (equipAgent.getSchedule().removeSlotByTaskId(taskId)) {
                log.debug("{}: слот задачи #{} удален у оборудования {}", agentId, taskId, equipAgent.getAgentId());
            }
        }

        // Теперь задача физически завершена, убиваем её агента!
        messageBus.unregister("TASK_" + taskId);
        log.debug("{}: TaskAgent TASK_{} завершил миссию и снят с регистрации", agentId, taskId);
    }

    // -----------------------------------------------------------------------
    // Обработка событий поваров
    // -----------------------------------------------------------------------

    /**
     * Повар стал недоступен (заболел, ушёл раньше).
     *
     * Тело сообщения: {@code Long cookProfileId}.
     *
     * Логика:
     *   1. Найти CookAgent.
     *   2. Вызвать handleCookUnavailable() — он сам разошлёт REMOVE_TASK
     *      всем своим задачам и очистит расписание.
     *   3. Пометить повара неактивным в SceneAgent — новые задачи
     *      не будут ему назначаться.
     */
    private void handleCookUnavailable(Message message) {
        long cookId = (Long) message.getBody();
        log.info("{}: повар COOK_{} недоступен", agentId, cookId);

        CookAgent cookAgent = cookAgents.get(cookId);
        if (cookAgent == null) {
            log.warn("{}: CookAgent для cookId={} не найден", agentId, cookId);
            return;
        }

        // CookAgent сам разошлёт REMOVE_TASK своим задачам
        cookAgent.handleCookUnavailable();

        // Помечаем в SceneAgent — новые запросы вариантов его не получат
        sceneAgent.markCookInactive(cookId);
    }

    /**
     * Повар снова доступен.
     *
     * Тело сообщения: {@code Long cookProfileId}.
     */
    private void handleCookAvailable(Message message) {
        long cookId = (Long) message.getBody();
        log.info("{}: повар COOK_{} снова доступен", agentId, cookId);

        CookAgent cookAgent = cookAgents.get(cookId);
        if (cookAgent == null) {
            log.warn("{}: CookAgent для cookId={} не найден", agentId, cookId);
            return;
        }

        sceneAgent.markCookActive(cookId);
        log.info("{}: COOK_{} помечен активным, теперь принимает новые задачи", agentId, cookId);
    }

    // -----------------------------------------------------------------------
    // Обработка событий оборудования
    // -----------------------------------------------------------------------

    /**
     * Единица оборудования сломана.
     *
     * Тело сообщения: {@link Equipment} (JPA-сущность сломавшейся единицы).
     *
     * Логика:
     *   1. Найти EquipmentTypeAgent для типа сломавшегося оборудования.
     *   2. Вызвать handleEquipmentBroken(brokenCapacity) — агент сам
     *      уменьшит ёмкость и при необходимости разошлёт REMOVE_TASK задачам.
     *   3. Обновить ёмкость в SceneAgent.
     */
    private void handleEquipmentBroken(Message message) {
        Equipment equipment = (Equipment) message.getBody();
        String type = equipment.getEquipmentType();
        int brokenCapacity = equipment.getMaxParallelTasks();

        log.info("{}: оборудование '{}' типа '{}' сломано (ёмкость: -{})",
                agentId, equipment.getName(), type, brokenCapacity);

        EquipmentTypeAgent agent = equipmentTypeAgents.get(type);
        if (agent == null) {
            log.warn("{}: EquipmentTypeAgent для типа '{}' не найден", agentId, type);
            return;
        }

        // Агент сам вытеснит лишние задачи если ёмкость стала меньше загрузки
        agent.handleEquipmentBroken(brokenCapacity);

        // Обновляем ёмкость в SceneAgent для корректной маршрутизации
        // (эта строка вычитала емкость второй раз)
        //sceneAgent.decreaseEquipmentCapacity(type, brokenCapacity);
    }

    /**
     * Единица оборудования починена.
     *
     * Тело сообщения: {@link Equipment} (JPA-сущность починенной единицы).
     */
    private void handleEquipmentFixed(Message message) {
        Equipment equipment = (Equipment) message.getBody();
        String type = equipment.getEquipmentType();
        int restoredCapacity = equipment.getMaxParallelTasks();

        log.info("{}: оборудование '{}' типа '{}' починено (ёмкость: +{})",
                agentId, equipment.getName(), type, restoredCapacity);

        EquipmentTypeAgent agent = equipmentTypeAgents.get(type);
        if (agent == null) {
            log.warn("{}: EquipmentTypeAgent для типа '{}' не найден", agentId, type);
            return;
        }

        agent.handleEquipmentFixed(restoredCapacity);
        // (эта строка прибавляла емкость второй раз)
        //sceneAgent.increaseEquipmentCapacity(type, restoredCapacity);
    }


    // -----------------------------------------------------------------------
    // Обработка создания нового повара и оборудования
    // -----------------------------------------------------------------------


    private void handleCookCreated(Message message) {
        CookProfile profile = (CookProfile) message.getBody();
        long cookId = profile.getId();

        if (cookAgents.containsKey(cookId)) {
            // Повар уже есть в памяти (например, ему поменяли специализацию)
            cookAgents.get(cookId).setCookProfile(profile);
            log.info("{}: профиль повара COOK_{} обновлен в памяти", agentId, cookId);
        } else {
            // Рождаем нового агента!
            CookSchedule schedule = new CookSchedule(cookId);
            CookAgent agent = new CookAgent(profile, schedule);
            messageBus.register(agent);
            sceneAgent.registerCookAgent(agent, schedule);
            cookAgents.put(cookId, agent);
            log.info("{}: НОВЫЙ повар COOK_{} зарегистрирован и готов к работе!", agentId, cookId);
        }
    }

    private void handleEquipmentCreated(Message message) {
        Equipment equipment = (Equipment) message.getBody();
        String type = equipment.getEquipmentType();
        int capacity = equipment.getMaxParallelTasks();

        EquipmentTypeAgent existingAgent = equipmentTypeAgents.get(type);
        if (existingAgent != null) {
            // Такой тип уже есть, просто плюсуем ёмкость
            existingAgent.getSchedule().increaseCapacity(capacity);
            log.info("{}: ёмкость оборудования '{}' увеличена на {} (куплено новое)",
                    agentId, type, capacity);
        } else {
            // Абсолютно новый тип оборудования
            EquipmentTypeSchedule schedule = new EquipmentTypeSchedule(type, capacity);
            EquipmentTypeAgent agent = new EquipmentTypeAgent(type, schedule);
            messageBus.register(agent);
            sceneAgent.registerEquipmentTypeAgent(agent, schedule);
            equipmentTypeAgents.put(type, agent);
            log.info("{}: зарегистрирован НОВЫЙ тип оборудования '{}'", agentId, type);
        }
    }


    // -----------------------------------------------------------------------
// Сдвиг расписания при задержке / досрочном завершении
// -----------------------------------------------------------------------

    /**
     * Обработать задержку задачи — сдвинуть последующие задачи.
     *
     * Тело сообщения: {@link TaskDelayBody}.
     * delayMinutes > 0 → сдвиг вперёд (задержка).
     * delayMinutes < 0 → сдвиг назад (досрочное завершение).
     */
    private void handleTaskDelayEvent(Message message) {
        TaskDelayBody body = (TaskDelayBody) message.getBody();
        long taskId = body.getTaskId();
        int delayMinutes = body.getDelayMinutes();

        if (delayMinutes == 0) return;

        taskRepository.findById(taskId).ifPresentOrElse(
                task -> shiftSubsequentTasks(task, delayMinutes),
                () -> log.warn("{}: TASK_DELAY_EVENT для задачи #{} — задача не найдена в БД",
                        agentId, taskId)
        );
    }

    /**
     * Сдвинуть последующие PLANNED-задачи при задержке или досрочном завершении.
     *
     * Алгоритм:
     *   1. Сдвинуть in-memory слот задержанной задачи (чтобы расписание было актуальным)
     *   2. Найти все PLANNED-задачи того же повара с startTime > plannedEndTime задержанной
     *   3. Сдвинуть их время в БД и in-memory расписании
     *   4. Найти PLANNED-задачи следующих курсов того же заказа
     *   5. Сдвинуть их аналогично
     *   6. Не сдвигать задачи в прошлое (минимум — now())
     *
     * @param task         задержанная задача
     * @param delayMinutes на сколько минут сдвинуть (может быть отрицательным)
     */
    private void shiftSubsequentTasks(CookingTask task, int delayMinutes) {
        if (task.getAssignedCook() == null || task.getPlannedEndTime() == null) {
            log.debug("{}: задача #{} не имеет назначенного повара или планового времени — пропускаем",
                    agentId, task.getId());
            return;
        }

        long cookId = task.getAssignedCook().getId();
        LocalDateTime taskEnd = task.getPlannedEndTime();
        LocalDateTime now = LocalDateTime.now();
        long orderId = task.getOrderItem().getOrder().getId();
        int courseNumber = task.getOrderItem().getCourseNumber();

        // === ИСПРАВЛЕНИЕ ===
        // В БД уже лежит новое время. Чтобы найти задачи, идущие "встык",
        // нам нужно откатиться к старому плановому времени окончания.
        LocalDateTime oldTaskEnd;
        if (delayMinutes > 0) {
            oldTaskEnd = task.getPlannedEndTime().minusMinutes(delayMinutes);
        } else {
            oldTaskEnd = task.getPlannedEndTime(); // При досрочном завершении (отрицательный delay) время в БД еще старое
        }

        log.info("{}: сдвиг расписания из-за задачи #{} на {} мин. Повар COOK_{}, заказ #{}",
                agentId, task.getId(), delayMinutes, cookId, orderId);

        // 1. Сдвиг задачи повара в in-memory расписании
        CookAgent cookAgent = cookAgents.get(cookId);
        if (cookAgent != null) {
            cookAgent.getSchedule().findByTaskId(task.getId()).ifPresent(slot -> {
                LocalDateTime newEnd = slot.getEndTime().plusMinutes(delayMinutes);
                if (newEnd.isBefore(now)) newEnd = now;
                slot.setEndTime(newEnd);
            });
        }

        // 2. ИСПОЛЬЗУЕМ oldTaskEnd для поиска следующих задач
        List<CookingTask> cookTasks = taskRepository
                .findByAssignedCookIdAndStatusAndPlannedStartTimeGreaterThanEqual(
                        cookId, CookingTaskStatus.PLANNED, oldTaskEnd);

        for (CookingTask t : cookTasks) {
            shiftSingleTask(t, delayMinutes, now, cookAgent);
        }

        // 3. Найти PLANNED-задачи следующих курсов того же заказа
        List<CookingTask> nextCourseTasks = taskRepository
                .findNextCourseTasks(orderId, courseNumber);

        for (CookingTask t : nextCourseTasks) {
            // Не дублировать: если задача уже сдвинута в шаге 2 — пропустить
            boolean alreadyShifted = cookTasks.stream()
                    .anyMatch(ct -> ct.getId().equals(t.getId()));
            if (!alreadyShifted) {
                Long assignedCookId = t.getAssignedCook() != null ? t.getAssignedCook().getId() : null;
                CookAgent assignedAgent = assignedCookId != null ? cookAgents.get(assignedCookId) : null;
                shiftSingleTask(t, delayMinutes, now, assignedAgent);
            }
        }

        log.info("{}: сдвиг завершён. Затронуто задач повара: {}, следующих курсов: {}",
                agentId, cookTasks.size(), nextCourseTasks.size());
    }

    /**
     * Сдвинуть время одной задачи в БД и в in-memory расписании её повара.
     *
     * @param task       задача для сдвига
     * @param minutes    на сколько минут (знак определяет направление)
     * @param now        текущее время (нижняя граница — не сдвигаем в прошлое)
     * @param cookAgent  агент повара, или null если не известен
     */
    private void shiftSingleTask(CookingTask task, int minutes, LocalDateTime now, CookAgent cookAgent) {
        // 1. Сначала вычисляем новое предполагаемое время
        LocalDateTime newStart = task.getPlannedStartTime().plusMinutes(minutes);
        LocalDateTime newEnd = task.getPlannedEndTime().plusMinutes(minutes);

        // 2. Не сдвигаем задачу в прошлое
        if (newStart.isBefore(now)) {
            // Если пытаемся сдвинуть в прошлое, то начинаем прямо СЕЙЧАС (now)
            // А продолжительность задачи (duration) оставляем прежней.
            long duration = ChronoUnit.MINUTES.between(task.getPlannedStartTime(), task.getPlannedEndTime());
            newStart = now;
            newEnd = now.plusMinutes(duration);
        }

        // Обновляем БД
        task.setPlannedStartTime(newStart);
        task.setPlannedEndTime(newEnd);
        taskRepository.save(task);

        // Обновляем in-memory расписание повара
        if (cookAgent != null) {
            LocalDateTime finalNewStart = newStart;
            LocalDateTime finalNewEnd = newEnd;
            cookAgent.getSchedule().findByTaskId(task.getId()).ifPresent(slot -> {
                slot.setStartTime(finalNewStart);
                slot.setEndTime(finalNewEnd);
            });
        }

        log.debug("{}: задача #{} сдвинута → [{} → {}]",
                agentId, task.getId(), newStart, newEnd);
    }

// -----------------------------------------------------------------------
// Восстановление расписания после перезапуска
// -----------------------------------------------------------------------

    /**
     * Восстановить in-memory расписания поваров и оборудования из БД.
     *
     * Вызывается в конце initialize(). При перезапуске приложения агенты
     * начинают с пустыми расписаниями, но в БД могут быть задачи
     * в статусах PLANNED и IN_PROGRESS. Без восстановления планировщик
     * не знает что время уже занято и назначает новые задачи на те же слоты.
     */
    private void restoreSchedules() {
        List<CookingTask> activeTasks = taskRepository.findByStatusIn(
                List.of(CookingTaskStatus.PLANNED, CookingTaskStatus.IN_PROGRESS));

        if (activeTasks.isEmpty()) {
            log.info("{}: нет активных задач для восстановления расписания", agentId);
            return;
        }

        int restoredCook = 0;
        int restoredEquip = 0;

        for (CookingTask task : activeTasks) {
            if (task.getPlannedStartTime() == null || task.getPlannedEndTime() == null) continue;

            // Восстановить слот в расписании повара
            if (task.getAssignedCook() != null) {
                long cookId = task.getAssignedCook().getId();
                CookAgent cookAgent = cookAgents.get(cookId);
                if (cookAgent != null) {
                    ScheduleSlot slot = new ScheduleSlot(
                            task.getId(),
                            task.getOrderItem().getOrder().getId(),
                            task.getPlannedStartTime(),
                            task.getPlannedEndTime(),
                            null  // PlacementVariant не нужен для восстановления
                    );
                    cookAgent.getSchedule().addSlot(slot);
                    restoredCook++;
                }
            }

            // Восстановить слот в расписании оборудования
            if (task.getAssignedEquipmentType() != null) {
                EquipmentTypeAgent equipAgent =
                        equipmentTypeAgents.get(task.getAssignedEquipmentType());
                if (equipAgent != null) {
                    ScheduleSlot slot = new ScheduleSlot(
                            task.getId(),
                            task.getOrderItem().getOrder().getId(),
                            task.getPlannedStartTime(),
                            task.getPlannedEndTime(),
                            null
                    );
                    equipAgent.getSchedule().addSlot(slot);
                    restoredEquip++;
                }
            }
        }

        log.info("{}: расписание восстановлено: {} слотов поваров, {} слотов оборудования " +
                "из {} активных задач", agentId, restoredCook, restoredEquip, activeTasks.size());
    }

    // -----------------------------------------------------------------------
    // Утилиты для SchedulerService
    // -----------------------------------------------------------------------

    /**
     * Проверить инициализирован ли диспетчер.
     * SchedulerService вызывает этот метод перед первым использованием.
     */
    public boolean isInitialized() {
        return sceneAgent != null;
    }

    /**
     * Число активных OrderAgent-ов (заказов в процессе планирования).
     * Используется для мониторинга и тестов.
     */
    public int getActiveOrderCount() {
        return orderAgents.size();
    }

    /** Получить SceneAgent — нужен TaskAgent-у как прямая ссылка. */
    public SceneAgent getSceneAgent() {
        return sceneAgent;
    }
}