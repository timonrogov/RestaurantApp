package com.example.restaurant.scheduler.agents;

import com.example.restaurant.scheduler.messages.Message;
import com.example.restaurant.scheduler.messages.MessageType;
import com.example.restaurant.scheduler.messages.dto.EquipmentRequestBody;
import com.example.restaurant.scheduler.messages.dto.EquipmentResponseBody;
import com.example.restaurant.scheduler.messages.dto.PlanningRequestBody;
import com.example.restaurant.scheduler.messages.dto.PlanningResponseBody;
import com.example.restaurant.scheduler.schedule.EquipmentTypeSchedule;
import com.example.restaurant.scheduler.schedule.ScheduleSlot;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Агент типа оборудования — пассивный агент-ресурс.
 *
 * Представляет всё оборудование одного типа (например, все духовки или все грили)
 * как единый ресурс с суммарной параллельной ёмкостью.
 *
 * Один агент на тип оборудования, не на единицу. Это сокращает число агентов
 * и количество сообщений: TaskAgent обращается к одному агенту «Духовки»,
 * а не к трём отдельным агентам «Духовка №1», «Духовка №2», «Духовка №3».
 *
 * Обрабатывает два типа входящих сообщений:
 *   EQUIPMENT_REQUEST — TaskAgent проверяет доступность в нужный интервал
 *   PLANNING_REQUEST  — TaskAgent резервирует слот после согласования с поваром
 *
 * Метод handleEquipmentBroken() вызывается напрямую из DispatcherAgent.
 *
 * ID агента: "EQUIPMENT_TYPE_{equipmentType}", например "EQUIPMENT_TYPE_OVEN".
 */
public class EquipmentTypeAgent extends BaseAgent {

    private final String equipmentType;
    private final EquipmentTypeSchedule schedule;

    public EquipmentTypeAgent(String equipmentType, EquipmentTypeSchedule schedule) {
        super("EQUIPMENT_TYPE_" + equipmentType);
        this.equipmentType = equipmentType;
        this.schedule = schedule;
    }

    // -----------------------------------------------------------------------
    // Диспетчеризация входящих сообщений
    // -----------------------------------------------------------------------

    @Override
    protected void dispatch(Message message) {
        switch (message.getType()) {
            case EQUIPMENT_REQUEST -> handleEquipmentRequest(message);
            case PLANNING_REQUEST  -> handlePlanningRequest(message);
            case FREE_SLOT         -> handleFreeSlot(message);
            default -> log.warn("{}: получено неожиданное сообщение типа {}",
                    agentId, message.getType());
        }
    }

    // -----------------------------------------------------------------------
    // Обработка EQUIPMENT_REQUEST
    // -----------------------------------------------------------------------

    /**
     * Ответить TaskAgent-у о доступности оборудования.
     *
     * Логика:
     *   1. Проверить: доступно ли оборудование именно в запрошенный интервал
     *      [desiredStart, desiredEnd)?
     *   2. Если да — подтвердить этот интервал.
     *   3. Если нет — найти ближайший доступный момент начала и предложить его.
     *      TaskAgent сам решит, подходит ли смещённое время (возможно, оно
     *      выходит за дедлайн или конфликтует с выбранным поваром — тогда
     *      TaskAgent попробует другой вариант).
     *
     * Почему возвращаем ближайший доступный, а не просто "недоступно"?
     * TaskAgent выбирает варианты повара, у которых фиксировано время.
     * Знание ближайшего свободного момента позволяет TaskAgent пропустить
     * неподходящие варианты быстро, не делая лишних запросов.
     *
     * @param message входящее сообщение с телом EquipmentRequestBody
     */
    private void handleEquipmentRequest(Message message) {
        EquipmentRequestBody request = (EquipmentRequestBody) message.getBody();

        log.debug("{}: EQUIPMENT_REQUEST для задачи {}, интервал [{} → {}]",
                agentId, request.getTaskId(),
                request.getDesiredStart(), request.getDesiredEnd());

        int duration = (int) ChronoUnit.MINUTES.between(
                request.getDesiredStart(), request.getDesiredEnd()
        );

        boolean available = schedule.isAvailable(
                request.getDesiredStart(), request.getDesiredEnd()
        );

        if (available) {
            // Запрошенный интервал свободен — подтверждаем его
            log.debug("{}: оборудование доступно в [{} → {}] для задачи {}",
                    agentId, request.getDesiredStart(), request.getDesiredEnd(),
                    request.getTaskId());

            reply(message, MessageType.EQUIPMENT_RESPONSE,
                    new EquipmentResponseBody(
                            true,
                            request.getTaskId(),
                            equipmentType,
                            request.getDesiredStart(),
                            request.getDesiredEnd()
                    ));
        } else {
            // Запрошенный интервал занят — ищем ближайший свободный
            LocalDateTime nextAvailable = schedule.findAvailableSlot(
                    duration, request.getDesiredStart()
            );
            LocalDateTime nextEnd = nextAvailable.plusMinutes(duration);

            log.debug("{}: оборудование занято, ближайший свободный [{} → {}] для задачи {}",
                    agentId, nextAvailable, nextEnd, request.getTaskId());

            // Возвращаем available=false, но с подсказкой о ближайшем времени.
            // TaskAgent проверит: укладывается ли nextAvailable в его дедлайн.
            reply(message, MessageType.EQUIPMENT_RESPONSE,
                    new EquipmentResponseBody(
                            false,
                            request.getTaskId(),
                            equipmentType,
                            nextAvailable,
                            nextEnd
                    ));
        }
    }

    // -----------------------------------------------------------------------
    // Обработка PLANNING_REQUEST
    // -----------------------------------------------------------------------

    /**
     * Зарезервировать слот оборудования для задачи.
     *
     * Вызывается TaskAgent-ом после того, как он уже зарезервировал время
     * у повара и хочет дополнительно закрепить оборудование.
     *
     * Финальная проверка доступности обязательна: между EQUIPMENT_REQUEST
     * и PLANNING_REQUEST теоретически могла вклиниться другая задача
     * (в однопоточной модели это невозможно, но проверка остаётся как
     * документированная гарантия корректности).
     *
     * @param message входящее сообщение с телом PlanningRequestBody
     */
    private void handlePlanningRequest(Message message) {
        PlanningRequestBody request = (PlanningRequestBody) message.getBody();
        LocalDateTime start = request.getChosenVariant().getStartTime();
        LocalDateTime end = request.getChosenVariant().getEndTime();

        log.debug("{}: PLANNING_REQUEST для задачи {}, интервал [{} → {}]",
                agentId, request.getTaskId(), start, end);

        // Финальная проверка
        if (!schedule.isAvailable(start, end)) {
            log.warn("{}: оборудование занято в [{} → {}], отказываем задаче {}",
                    agentId, start, end, request.getTaskId());
            reply(message, MessageType.PLANNING_RESPONSE,
                    PlanningResponseBody.failure(request.getTaskId()));
            return;
        }

        // Резервируем слот
        ScheduleSlot slot = new ScheduleSlot(
                request.getTaskId(),
                request.getOrderId(),
                start,
                end,
                request.getChosenVariant()
        );
        schedule.addSlot(slot);

        log.info("{}: задача {} зарезервировала оборудование [{} → {}]",
                agentId, request.getTaskId(), start, end);

        reply(message, MessageType.PLANNING_RESPONSE,
                new PlanningResponseBody(true, request.getTaskId(), start, end));
    }

    // -----------------------------------------------------------------------
    // Обработка поломки оборудования (прямой вызов из DispatcherAgent)
    // -----------------------------------------------------------------------

    /**
     * Обработать поломку одной единицы оборудования данного типа.
     *
     * Вызывается напрямую из DispatcherAgent при получении EQUIPMENT_BROKEN.
     * Уменьшает суммарную ёмкость на maxParallelTasks сломавшейся единицы.
     *
     * Если после уменьшения ёмкости текущая загрузка превышает новую ёмкость
     * (т.е. одновременно используется больше «ячеек» чем доступно) — нужно
     * вытеснить часть задач. Мы выбираем задачи с наиболее поздним дедлайном
     * (наименее срочные) и отправляем им REMOVE_TASK.
     *
     * @param brokenCapacity на сколько уменьшить ёмкость (= maxParallelTasks единицы)
     */
    public void handleEquipmentBroken(int brokenCapacity) {
        int capacityBefore = schedule.getTotalCapacity();
        schedule.decreaseCapacity(brokenCapacity);
        int capacityAfter = schedule.getTotalCapacity();

        log.info("{}: оборудование частично вышло из строя. Ёмкость: {} → {}",
                agentId, capacityBefore, capacityAfter);

        if (capacityAfter <= 0) {
            // Всё оборудование данного типа недоступно — вытесняем все задачи
            log.warn("{}: ёмкость = 0, все задачи будут перепланированы", agentId);
            evictAllTasks();
        } else {
            // Проверяем: не превышает ли текущая загрузка новую ёмкость
            evictExcessTasks(capacityAfter);
        }
    }

    /**
     * Обработать починку одной единицы оборудования.
     *
     * Вызывается напрямую из DispatcherAgent при получении EQUIPMENT_FIXED.
     *
     * @param restoredCapacity на сколько увеличить ёмкость
     */
    public void handleEquipmentFixed(int restoredCapacity) {
        int capacityBefore = schedule.getTotalCapacity();
        schedule.increaseCapacity(restoredCapacity);
        log.info("{}: оборудование починено. Ёмкость: {} → {}",
                agentId, capacityBefore, schedule.getTotalCapacity());
    }

    /**
     * Вытеснить все задачи из расписания (при полной недоступности типа).
     */
    private void evictAllTasks() {
        List<ScheduleSlot> allSlots = schedule.copySlots();
        schedule.restoreSlots(new ArrayList<>());

        for (ScheduleSlot slot : allSlots) {
            String taskAgentId = "TASK_" + slot.getTaskId();
            send(taskAgentId, MessageType.REMOVE_TASK, slot.getTaskId());
            log.debug("{}: отправлен REMOVE_TASK агенту {} (оборудование недоступно)",
                    agentId, taskAgentId);
        }

        log.info("{}: вытеснено {} задач", agentId, allSlots.size());
    }

    /**
     * Вытеснить лишние задачи если текущая загрузка превышает новую ёмкость.
     *
     * Проверяем каждый момент времени в расписании: если число одновременных
     * слотов > newCapacity — находим задачи с наиболее поздним endTime
     * (наименее срочные) и вытесняем их до тех пор пока загрузка не уложится.
     *
     * @param newCapacity новая суммарная ёмкость
     */
    private void evictExcessTasks(int newCapacity) {
        // Собираем все «критические моменты» — начала и концы слотов
        // В каждый такой момент число одновременных задач может измениться
        List<ScheduleSlot> currentSlots = new ArrayList<>(schedule.getSlots());
        List<Long> toEvict = new ArrayList<>();

        for (ScheduleSlot slot : currentSlots) {
            // Сколько задач одновременно с этим слотом?
            long concurrent = currentSlots.stream()
                    .filter(s -> s.overlapsWith(slot.getStartTime(), slot.getEndTime()))
                    .filter(s -> !toEvict.contains(s.getTaskId()))
                    .count();

            if (concurrent > newCapacity) {
                // Этот слот — кандидат на вытеснение (выбираем с наиболее поздним endTime)
                currentSlots.stream()
                        .filter(s -> s.overlapsWith(slot.getStartTime(), slot.getEndTime()))
                        .filter(s -> !toEvict.contains(s.getTaskId()))
                        .max((a, b) -> a.getEndTime().compareTo(b.getEndTime()))
                        .ifPresent(victim -> toEvict.add(victim.getTaskId()));
            }
        }

        // Вытесняем найденных кандидатов
        for (Long taskId : toEvict) {
            schedule.removeSlotByTaskId(taskId);
            String taskAgentId = "TASK_" + taskId;
            send(taskAgentId, MessageType.REMOVE_TASK, taskId);
            log.info("{}: задача {} вытеснена из-за уменьшения ёмкости", agentId, taskId);
        }
    }

    /**
     * Обработать запрос добровольного освобождения слота оборудования.
     */
    private void handleFreeSlot(Message message) {
        Long taskId = (Long) message.getBody();
        if (schedule.removeSlotByTaskId(taskId)) {
            log.info("{}: слот задачи {} освобожден по запросу FREE_SLOT", agentId, taskId);
        }
    }

    // -----------------------------------------------------------------------
    // Геттеры
    // -----------------------------------------------------------------------

    public String getEquipmentType() {
        return equipmentType;
    }

    public EquipmentTypeSchedule getSchedule() {
        return schedule;
    }

    /**
     * Агент активен если суммарная ёмкость > 0.
     * SceneAgent проверяет это перед включением агента в ответ на GET_AVAILABLE_EQUIPMENT.
     */
    public boolean isActive() {
        return schedule.getTotalCapacity() > 0;
    }
}