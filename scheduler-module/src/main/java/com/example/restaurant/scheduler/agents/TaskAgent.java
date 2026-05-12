package com.example.restaurant.scheduler.agents;

import com.example.restaurant.enums.CookingTaskStatus;
import com.example.restaurant.models.CookingTask;
import com.example.restaurant.repositories.CookingTaskRepository;
import com.example.restaurant.scheduler.config.SchedulerProperties;
import com.example.restaurant.scheduler.messages.Message;
import com.example.restaurant.scheduler.messages.MessageType;
import com.example.restaurant.scheduler.messages.dto.*;
import com.example.restaurant.scheduler.schedule.CookSchedule;
import com.example.restaurant.enums.CookingTaskStatus;
import com.example.restaurant.scheduler.schedule.ScheduleSlot;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Агент задачи — активный участник переговоров.
 *
 * Представляет один этап приготовления блюда (CookingTask). При создании
 * немедленно инициирует поиск подходящего повара (и оборудования, если нужно).
 * Добивается резервирования ресурсов и сохранения результата в БД.
 *
 * Является единственным активным агентом системы: сам инициирует сообщения,
 * не ждёт когда к нему обратятся.
 *
 * ID агента: "TASK_{cookingTask.id}", например "TASK_42".
 */
public class TaskAgent extends BaseAgent {

    // -----------------------------------------------------------------------
    // Конфигурация задачи
    // -----------------------------------------------------------------------

    private final CookingTask task;
    private final String orderAgentId;
    private final SceneAgent sceneAgent; // прямая ссылка для чтения расписаний
    private final CookingTaskRepository taskRepository;
    private LocalDateTime notBefore;
    private LocalDateTime targetEndTime;
    private LocalDateTime deadline;

    /** Нужно ли оборудование для этой задачи (кэшируем из template). */
    private final boolean equipmentNeeded;

    // -----------------------------------------------------------------------
    // Состояние переговоров
    // -----------------------------------------------------------------------

    /** Все варианты, накопленные от поваров в текущем цикле переговоров. */
    private final List<PlacementVariant> collectedVariants = new ArrayList<>();

    /**
     * Отсортированный и оценённый список вариантов после evaluateAndPlan().
     * TaskAgent перебирает их по порядку начиная с currentVariantIndex.
     */
    private List<PlacementVariant> evaluatedVariants = new ArrayList<>();

    /** Индекс текущего варианта, который мы пытаемся зарезервировать. */
    private int currentVariantIndex = 0;

    /**
     * Счётчик ожидаемых PARAMS_RESPONSE.
     * Устанавливается равным числу поваров, которым разосланы запросы.
     * Декрементируется при каждом ответе. Когда достигает 0 — все ответы
     * получены и можно запускать оценку.
     */
    private int pendingParamsResponses = 0;

    /**
     * Выбранный вариант повара для текущей попытки.
     * Используется при двухфазном резервировании (повар + оборудование):
     * после подтверждения оборудования отправляем PLANNING_REQUEST повару
     * с этим вариантом.
     */
    private PlacementVariant chosenCookVariant = null;

    /** Счётчик перепланирований (увеличивается при REMOVE_TASK). */
    private int replanCount = 0;

    // ДОБАВИТЬ:
    private final SchedulerProperties props;

    // -----------------------------------------------------------------------
    // Конструктор
    // -----------------------------------------------------------------------

    public TaskAgent(CookingTask task,
                     String orderAgentId,
                     SceneAgent sceneAgent,
                     CookingTaskRepository taskRepository,
                     SchedulerProperties props) {
        super("TASK_" + task.getId());
        this.task = task;
        this.orderAgentId = orderAgentId;
        this.sceneAgent = sceneAgent;
        this.taskRepository = taskRepository;
        this.equipmentNeeded = task.getTemplate().getRequiredEquipmentType() != null;
        this.props = props;
    }

    // -----------------------------------------------------------------------
    // Диспетчеризация входящих сообщений
    // -----------------------------------------------------------------------

    @Override
    protected void dispatch(Message message) {
        switch (message.getType()) {
            case INIT                        -> handleInit(message);
            case AVAILABLE_COOKS_RESPONSE    -> handleAvailableCooksResponse(message);
            case PARAMS_RESPONSE             -> handleParamsResponse(message);
            case EQUIPMENT_RESPONSE          -> handleEquipmentResponse(message);
            case PLANNING_RESPONSE           -> handlePlanningResponse(message);
            case REMOVE_TASK                 -> handleRemoveTask(message);
            case CANCEL_AND_REPLAN           -> handleCancelAndReplan(message);
            default -> log.warn("{}: получено неожиданное сообщение типа {}",
                    agentId, message.getType());
        }
    }

    // -----------------------------------------------------------------------
    // Фаза 1: запуск переговоров
    // -----------------------------------------------------------------------

    /**
     * Начать (или перезапустить) цикл переговоров.
     * Вызывается при инициализации, а также при перепланировании (REMOVE_TASK).
     *
     * Сбрасывает все накопленные варианты и запрашивает у SceneAgent
     * список доступных поваров нужной специализации.
     */
    private void startNegotiations() {
        log.info("{}: начало переговоров (replanCount={})", agentId, replanCount);
        collectedVariants.clear();
        evaluatedVariants.clear();
        currentVariantIndex = 0;
        chosenCookVariant = null;
        pendingParamsResponses = 0;

        send(SceneAgent.AGENT_ID, MessageType.GET_AVAILABLE_COOKS,
                task.getTemplate().getRequiredSpecialization());
    }

    private void handleInit(Message message) {
        InitTaskPayload payload = (InitTaskPayload) message.getBody();
        this.notBefore = payload.getNotBefore();
        this.targetEndTime = payload.getTargetEndTime();
        this.deadline = payload.getDeadline();
        startNegotiations();
    }

    // -----------------------------------------------------------------------
    // Фаза 2: получение списка поваров
    // -----------------------------------------------------------------------

    /**
     * Обработать список доступных поваров от SceneAgent.
     * Если поваров нет — задача провалена немедленно.
     * Если есть — рассылаем запросы вариантов всем поварам.
     *
     * @param message ответ SceneAgent с телом {@code List<String>} (agentId поваров)
     */
    @SuppressWarnings("unchecked")
    private void handleAvailableCooksResponse(Message message) {
        List<String> cookAgentIds = (List<String>) message.getBody();

        if (cookAgentIds == null || cookAgentIds.isEmpty()) {
            log.warn("{}: поваров нужной специализации {} нет в системе",
                    agentId, task.getTemplate().getRequiredSpecialization());
            failTask();
            return;
        }

        log.debug("{}: найдено {} поваров, рассылаем PARAMS_REQUEST", agentId, cookAgentIds.size());

        pendingParamsResponses = cookAgentIds.size();

        ParamsRequestBody requestBody = new ParamsRequestBody(
                task.getId(),
                task.getTemplate().getDurationMinutes(),
                task.getTemplate().getRequiredSpecialization(),
                notBefore,
                deadline,
                task.getOrderItem().getOrder().getId(),
                targetEndTime
        );

        for (String cookAgentId : cookAgentIds) {
            send(cookAgentId, MessageType.PARAMS_REQUEST, requestBody);
        }
    }

    // -----------------------------------------------------------------------
    // Фаза 3: сбор вариантов от поваров
    // -----------------------------------------------------------------------

    /**
     * Получить варианты от одного повара.
     * Накапливаем варианты, декрементируем счётчик.
     * Когда все повара ответили — запускаем оценку.
     *
     * @param message ответ CookAgent с телом {@link ParamsResponseBody}
     */
    private void handleParamsResponse(Message message) {
        ParamsResponseBody response = (ParamsResponseBody) message.getBody();
        List<PlacementVariant> newVariants = response.getVariants();

        if (newVariants != null && !newVariants.isEmpty()) {
            collectedVariants.addAll(newVariants);
            log.debug("{}: получено {} вариантов от повара {} (итого: {})",
                    agentId, newVariants.size(), response.getCookId(), collectedVariants.size());
        }

        pendingParamsResponses--;

        if (pendingParamsResponses == 0) {
            evaluateAndPlan();
        }
    }

    // -----------------------------------------------------------------------
    // Фаза 4: оценка и выбор лучшего варианта
    // -----------------------------------------------------------------------

    /**
     * Оценить все собранные варианты и выбрать лучший.
     *
     * Алгоритм оценки:
     *   1. Фильтруем варианты asap/jit у которых endTime > deadline.
     *      Варианты conflict не фильтруем — они по определению в дедлайне.
     *   2. Если нет ни одного подходящего и MAX_REPLAN превышен — TASK_FAILED.
     *   3. Для каждого варианта считаем три показателя и сворачиваем.
     *   4. Сортируем по убыванию totalScore.
     *   5. Начинаем с лучшего: tryVariantAtIndex(0).
     */
    private void evaluateAndPlan() {
        log.debug("{}: оценка {} вариантов", agentId, collectedVariants.size());

        if (collectedVariants.isEmpty()) {
            log.warn("{}: ни один повар не предложил вариантов", agentId);
            if (replanCount >= props.getPlanning().getMaxReplan()) {
                failTask();
            } else {
                startNegotiations();
            }
            return;
        }

        List<PlacementVariant> viable = collectedVariants.stream()
                .filter(v -> "conflict".equals(v.getVariantName())
                        || !v.getEndTime().isAfter(deadline))
                .toList();

        if (viable.isEmpty()) {
            log.warn("{}: после фильтрации по дедлайну вариантов нет", agentId);
            if (replanCount >= props.getPlanning().getMaxReplan()) {
                failTask();
                return;
            }
            startNegotiations();
            return;
        }

        long windowMinutes = props.getScoring().getWindowMinutes();;

        for (PlacementVariant variant : viable) {
            double syncScore  = computeSyncScore(variant.getEndTime());
            double speedScore = computeSpeedScore(variant.getStartTime(), windowMinutes);
            double loadScore  = computeLoadScore(variant.getResourceAgentId());

            double total = props.getScoring().getWeightSync()  * syncScore
                    + props.getScoring().getWeightSpeed() * speedScore
                    + props.getScoring().getWeightLoad()  * loadScore;

            variant.setUrgencyScore(syncScore);
            variant.setSpeedScore(speedScore);
            variant.setLoadScore(loadScore);
            variant.setTotalScore(total);

            // ИСПРАВЛЕНИЕ БАГ-07: корректный формат для SLF4J (не Python {:.2f})
            log.debug("{}: вариант {} повар {} → urgency={} speed={} load={} total={}",
                    agentId, variant.getVariantName(), variant.getResourceAgentId(),
                    String.format("%.2f", syncScore),
                    String.format("%.2f", speedScore),
                    String.format("%.2f", loadScore),
                    String.format("%.2f", total));
        }

        // Старый способ давал рассинхрон курсов при равных score у ASAP и JIT вариантов.
        /*evaluatedVariants = viable.stream()
                .sorted(Comparator
                        .comparingInt((PlacementVariant v) ->
                                "conflict".equals(v.getVariantName()) ? 1 : 0)
                        .thenComparingDouble(PlacementVariant::getTotalScore).reversed())
                .collect(java.util.stream.Collectors.toList());*/

        evaluatedVariants = viable.stream()
                .sorted(Comparator
                        .comparingInt((PlacementVariant v) ->
                                "conflict".equals(v.getVariantName()) ? 1 : 0)
                        .thenComparingDouble(PlacementVariant::getTotalScore).reversed()
                        // При равном score JIT предпочтительнее ASAP:
                        // он ближе к targetEndTime, ASAP — как можно раньше
                        .thenComparingInt((PlacementVariant v) ->
                                "jit".equals(v.getVariantName()) ? 0 : 1))
                .collect(java.util.stream.Collectors.toList());

        currentVariantIndex = 0;
        tryVariantAtIndex();
    }

    /**
     * Оценка синхронизации: насколько точно задача заканчивается к targetEndTime.
     * 1.0 — заканчивается идеально секунда в секунду.
     * Чем больше отклонение в любую сторону, тем ближе к 0.0.
     */
    private double computeSyncScore(LocalDateTime endTime) {
        long diffMinutes = Math.abs(ChronoUnit.MINUTES.between(endTime, targetEndTime));
        return Math.max(0.0, 1.0 - (double) diffMinutes
                / props.getScoring().getSyncSensitivityMinutes());
    }

    /**
     * Оценка скорости: насколько рано начинается задача.
     * 1.0 — начинается ровно в notBefore.
     * 0.0 — начинается в конце временного окна.
     */
    private double computeSpeedScore(LocalDateTime startTime, long windowMinutes) {
        long minutesDelay = ChronoUnit.MINUTES.between(notBefore, startTime);
        if (minutesDelay < 0) return 1.0; // раньше notBefore — отлично
        return Math.max(0.0, 1.0 - (double) minutesDelay / windowMinutes);
    }

    /**
     * Оценка нагрузки повара: предпочитаем менее занятых.
     * 1.0 — повар свободен.
     * 0.0 — повар полностью загружен.
     *
     * Читаем напрямую из SceneAgent (не через сообщения) для синхронного доступа.
     * cookAgentId имеет формат "COOK_{id}" — извлекаем числовой ID.
     */
    private double computeLoadScore(String cookAgentId) {
        double windowMinutesForNormalize = props.getScoring().getLoadWindowMinutes();
        try {
            long cookId = Long.parseLong(cookAgentId.replace("COOK_", ""));
            CookSchedule schedule = sceneAgent.getCookSchedule(cookId);
            if (schedule == null) return 0.5;

            /*// Когда повар свободен в следующий раз (начиная с notBefore)?
            LocalDateTime asapFree = schedule.findAsapSlot(0, notBefore);
            // Чем раньше освобождается — тем лучше (нормализуем по окну 60 мин)
            long minutesUntilFree = ChronoUnit.MINUTES.between(notBefore, asapFree);*/

            // Исправление 1:
            // Используем ТЕКУЩИЙ момент, а не notBefore
            // Это показывает реальную «занятость» повара прямо сейчас
            /*LocalDateTime now = LocalDateTime.now();
            LocalDateTime asapFree = schedule.findAsapSlot(0, now);
            long minutesUntilFree = ChronoUnit.MINUTES.between(now, asapFree);*/

            // Исправление 2:
            // Конец последнего запланированного слота повара.
            // Это реальный момент, когда повар освободится полностью.
            // findAsapSlot(0, now) давал ложный результат: если задача
            // ещё не началась, он возвращал now (зазор до старта задачи),
            // показывая занятого повара как свободного.
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime lastSlotEnd = schedule.getSlots().stream()
                    .map(ScheduleSlot::getEndTime)
                    .filter(end -> end.isAfter(now))
                    .max(LocalDateTime::compareTo)
                    .orElse(now);

            long minutesUntilFree = ChronoUnit.MINUTES.between(now, lastSlotEnd);
            if (minutesUntilFree < 0) return 1.0;
            return Math.max(0.0, 1.0 - (double) minutesUntilFree / windowMinutesForNormalize);
        } catch (NumberFormatException e) {
            return 0.5;
        }
    }

    // -----------------------------------------------------------------------
    // Фаза 5: попытка зарезервировать вариант
    // -----------------------------------------------------------------------

    /**
     * Попытаться зарезервировать вариант с текущим индексом.
     * Если варианты кончились — запустить новый цикл переговоров.
     */
    private void tryVariantAtIndex() {
        if (currentVariantIndex >= evaluatedVariants.size()) {
            log.warn("{}: все {} варианта исчерпаны", agentId, evaluatedVariants.size());
            if (replanCount >= props.getPlanning().getMaxReplan()) {
                failTask();
            } else {
                startNegotiations();
            }
            return;
        }

        chosenCookVariant = evaluatedVariants.get(currentVariantIndex);
        log.debug("{}: пробуем вариант {} #{} повара {} [{} → {}]",
                agentId, chosenCookVariant.getVariantName(), currentVariantIndex,
                chosenCookVariant.getResourceAgentId(),
                chosenCookVariant.getStartTime(), chosenCookVariant.getEndTime());

        if (equipmentNeeded) {
            // Сначала проверяем оборудование — оно дополнительное ограничение
            sendEquipmentRequest(chosenCookVariant);
        } else {
            // Оборудование не нужно — сразу бронируем повара
            sendCookPlanningRequest(chosenCookVariant);
        }
    }

    /**
     * Отправить запрос проверки оборудования для данного варианта.
     */
    private void sendEquipmentRequest(PlacementVariant variant) {
        String equipmentType = task.getTemplate().getRequiredEquipmentType();

        // Узнать agentId агента оборудования через SceneAgent
        //send(SceneAgent.AGENT_ID, MessageType.GET_AVAILABLE_EQUIPMENT, equipmentType);

        // Мы ждём сначала AVAILABLE_EQUIPMENT_RESPONSE, потом пошлём EQUIPMENT_REQUEST.
        // Но это лишний round-trip. Проще: мы знаем ID агента оборудования напрямую.
        // EquipmentTypeAgent.agentId = "EQUIPMENT_TYPE_{type}"
        String equipmentAgentId = "EQUIPMENT_TYPE_" + equipmentType;

        EquipmentRequestBody body = new EquipmentRequestBody(
                task.getId(),
                equipmentType,
                variant.getStartTime(),
                variant.getEndTime()
        );
        send(equipmentAgentId, MessageType.EQUIPMENT_REQUEST, body);
    }

    /**
     * Отправить запрос резервирования повару.
     */
    private void sendCookPlanningRequest(PlacementVariant variant) {
        PlanningRequestBody body = new PlanningRequestBody(
                task.getId(),
                variant,
                task.getOrderItem().getOrder().getId()
        );
        send(variant.getResourceAgentId(), MessageType.PLANNING_REQUEST, body);
    }

    /**
     * Отправить запрос резервирования оборудования.
     * Вызывается после того, как повар уже подтвердил бронирование.
     */
    private void sendEquipmentPlanningRequest(PlacementVariant cookVariant) {
        String equipmentType = task.getTemplate().getRequiredEquipmentType();
        String equipmentAgentId = "EQUIPMENT_TYPE_" + equipmentType;

        PlanningRequestBody body = new PlanningRequestBody(
                task.getId(),
                cookVariant,  // время берётся из варианта повара
                task.getOrderItem().getOrder().getId()
        );
        send(equipmentAgentId, MessageType.PLANNING_REQUEST, body);
    }

    // -----------------------------------------------------------------------
    // Фаза 6а: ответ об оборудовании
    // -----------------------------------------------------------------------

    /**
     * Обработать ответ о доступности оборудования.
     *
     * Если оборудование доступно в нужное время — резервируем повара.
     * Если нет — переходим к следующему варианту (у другого повара может
     * быть другое время, и оборудование там окажется свободным).
     *
     * @param message ответ EquipmentTypeAgent с телом {@link EquipmentResponseBody}
     */
    private void handleEquipmentResponse(Message message) {
        EquipmentResponseBody response = (EquipmentResponseBody) message.getBody();

        log.debug("{}: EQUIPMENT_RESPONSE для задачи {}: available={}",
                agentId, response.getTaskId(), response.isAvailable());

        if (response.isAvailable()) {
            // Оборудование свободно — бронируем повара
            sendCookPlanningRequest(chosenCookVariant);
        } else {
            // Оборудование занято — следующий вариант
            log.debug("{}: оборудование типа {} занято в [{} → {}], пробуем следующий вариант",
                    agentId, response.getEquipmentType(),
                    chosenCookVariant.getStartTime(), chosenCookVariant.getEndTime());
            currentVariantIndex++;
            tryVariantAtIndex();
        }
    }

    // -----------------------------------------------------------------------
    // Фаза 6б / 7: ответ на резервирование
    // -----------------------------------------------------------------------

    /**
     * Обработать ответ на PLANNING_REQUEST.
     *
     * Этот метод вызывается и на ответ повара, и на ответ агента оборудования.
     * Различаем их по senderId:
     *   senderId начинается с "COOK_"           → ответ повара
     *   senderId начинается с "EQUIPMENT_TYPE_" → ответ оборудования
     *
     * @param message ответ ресурс-агента с телом {@link PlanningResponseBody}
     */
    private void handlePlanningResponse(Message message) {
        PlanningResponseBody response = (PlanningResponseBody) message.getBody();
        String sender = message.getSenderId();

        if (sender.startsWith("COOK_")) {
            handleCookPlanningResponse(response);
        } else if (sender.startsWith("EQUIPMENT_TYPE_")) {
            handleEquipmentPlanningResponse(response);
        } else {
            log.warn("{}: PLANNING_RESPONSE от неизвестного агента {}", agentId, sender);
        }
    }

    /**
     * Повар ответил на запрос резервирования.
     *
     * success=false → этот вариант занят, пробуем следующий.
     * success=true  → если нужно оборудование — резервируем его;
     *                 если нет — финализируем.
     */
    private void handleCookPlanningResponse(PlanningResponseBody response) {
        log.debug("{}: ответ повара на PLANNING_REQUEST: success={}", agentId, response.isSuccess());

        if (!response.isSuccess()) {
            currentVariantIndex++;
            tryVariantAtIndex();
            return;
        }

        // Повар забронирован
        if (equipmentNeeded) {
            // Второй шаг: бронируем оборудование
            sendEquipmentPlanningRequest(chosenCookVariant);
        } else {
            // Всё готово — сохраняем результат
            finalizeTask(response.getConfirmedStart(), response.getConfirmedEnd(), null);
        }
    }

    /**
     * Агент оборудования ответил на запрос резервирования.
     *
     * success=false → оборудование между EQUIPMENT_REQUEST и PLANNING_REQUEST
     *                 стало занятым (редкий случай в однопоточной модели).
     *                 Повар уже забронирован — его слот останется в расписании
     *                 как «сирота» до следующего перепланирования или освобождения.
     *                 Запускаем startNegotiations() заново — при следующей попытке
     *                 ASAP-вариант этого же повара будет предложен с новым временем.
     *
     * success=true  → всё зарезервировано — финализируем.
     */
    private void handleEquipmentPlanningResponse(PlanningResponseBody response) {
        log.debug("{}: ответ оборудования на PLANNING_REQUEST: success={}", agentId, response.isSuccess());

        if (!response.isSuccess()) {
            log.warn("{}: оборудование отказало после подтверждения повара. " +
                            "Слот повара останется до перепланирования. Начинаем заново.",
                    agentId);
            startNegotiations();
            return;
        }

        // Оборудование тоже забронировано
        finalizeTask(response.getConfirmedStart(), response.getConfirmedEnd(),
                task.getTemplate().getRequiredEquipmentType());
    }

    // -----------------------------------------------------------------------
    // Финализация: сохранение результата в БД
    // -----------------------------------------------------------------------

    /**
     * Зафиксировать успешное планирование задачи.
     *
     * Обновляет JPA-сущность CookingTask и сохраняет её в БД.
     * Отправляет TASK_PLANNED в OrderAgent.
     *
     * @param confirmedStart    подтверждённое время начала
     * @param confirmedEnd      подтверждённое время окончания
     * @param equipmentType     тип оборудования или null если не нужно
     */
    private void finalizeTask(LocalDateTime confirmedStart,
                              LocalDateTime confirmedEnd,
                              String equipmentType) {
        // Извлекаем cookId из agentId повара ("COOK_3" → 3)
        long cookId = Long.parseLong(chosenCookVariant.getResourceAgentId().replace("COOK_", ""));

        // Обновляем JPA-сущность
        task.setStatus(CookingTaskStatus.PLANNED);
        task.setPlannedStartTime(confirmedStart);
        task.setPlannedEndTime(confirmedEnd);
        if (task.getInitialPlannedStartTime() == null) {
            task.setInitialPlannedStartTime(confirmedStart);
            task.setInitialPlannedEndTime(confirmedEnd);
        }

        // assignedCook: находим CookProfile через sceneAgent
        CookSchedule cookSchedule = sceneAgent.getCookSchedule(cookId);
        // Используем CookAgent напрямую из SceneAgent для получения CookProfile
        // SceneAgent хранит CookAgent-ов — берём через него
        // Простой вариант: CookProfile лежит в CookAgent, а CookAgent доступен через MessageBus
        // Для сохранения в БД достаточно установить ID через прокси-сеттер (Hibernate proxy)
        task.getAssignedCook(); // не null — будет установлен ниже

        // Устанавливаем CookProfile через специальный метод (см. ниже)
        setCookProfileOnTask(cookId);

        if (equipmentType != null) {
            task.setAssignedEquipmentType(equipmentType);
        }

        // Сохраняем в БД
        taskRepository.save(task);

        log.info("{}: задача {} запланирована. Повар: COOK_{}, время: [{} → {}]",
                agentId, task.getId(), cookId, confirmedStart, confirmedEnd);

        // Уведомляем OrderAgent
        TaskPlannedBody body = new TaskPlannedBody(task.getId(), confirmedEnd);
        send(orderAgentId, MessageType.TASK_PLANNED, body);
    }

    /**
     * Установить CookProfile на задачу перед сохранением в БД.
     *
     * Нам нужна ссылка на JPA-сущность CookProfile, но в TaskAgent её нет напрямую.
     * Решение: используем SceneAgent → CookAgent → CookProfile.
     *
     * Это единственное место где TaskAgent использует внутренние данные агентов
     * через SceneAgent — для финального сохранения в БД.
     */
    private void setCookProfileOnTask(long cookId) {
        messageBus.getAgent("COOK_" + cookId).ifPresent(agent -> {
            if (agent instanceof CookAgent cookAgent) {
                // CookAgent предоставляет CookProfile через геттер
                // (добавим его в CookAgent ниже)
                task.setAssignedCook(cookAgent.getCookProfile());
            }
        });
    }

    // -----------------------------------------------------------------------
    // Обработка вытеснения
    // -----------------------------------------------------------------------

    /**
     * Обработать вытеснение из расписания повара.
     *
     * CookAgent-резервировавший другую более срочную задачу удалил наш слот
     * и отправил нам REMOVE_TASK. Нужно перепланироваться.
     *
     * @param message сообщение с телом Long (taskId — для верификации)
     */
    private void handleRemoveTask(Message message) {
        // ИСПРАВЛЕНО БАГ-06: защита IN_PROGRESS и DONE задач
        if (task.getStatus() == CookingTaskStatus.IN_PROGRESS
                || task.getStatus() == CookingTaskStatus.DONE) {
            log.warn("{}: REMOVE_TASK проигнорирован — задача уже в статусе {}. " +
                    "Нельзя вытеснить задачу, которая выполняется или выполнена.", agentId, task.getStatus());
            return;
        }

        replanCount++;
        log.info("{}: получен REMOVE_TASK (replanCount={}/{})", agentId, replanCount, props.getPlanning().getMaxReplan());

        task.setStatus(CookingTaskStatus.PENDING);
        task.setPlannedStartTime(null);
        task.setPlannedEndTime(null);
        task.setAssignedCook(null);
        task.setAssignedEquipmentType(null);
        task.setReplanCount(replanCount);
        task.setLocalOverdue(false);
        taskRepository.save(task);

        if (replanCount > props.getPlanning().getMaxReplan()) {
            log.warn("{}: превышен лимит перепланирований ({})", agentId, props.getPlanning().getMaxReplan());
            failTask();
            return;
        }

        send(orderAgentId, MessageType.TASK_REPLANNING, task.getId());
        startNegotiations();
    }

    /**
     * Обработать приказ на перепланирование от OrderAgent.
     * Это системный сдвиг (из-за задержек соседей), поэтому мы снимаем
     * старые брони, обновляем рамки и запускаем торги с чистого листа.
     */
    private void handleCancelAndReplan(Message message) {
        // ИСПРАВЛЕНО БАГ-06: критическая защита от перезаписи IN_PROGRESS и DONE задач
        if (task.getStatus() == CookingTaskStatus.IN_PROGRESS
                || task.getStatus() == CookingTaskStatus.DONE) {
            log.warn("{}: CANCEL_AND_REPLAN проигнорирован — задача уже в статусе {}. " +
                    "Задача выполняется или выполнена, брони не освобождаются.", agentId, task.getStatus());
            return;
        }

        log.info("{}: получен CANCEL_AND_REPLAN. Снимаем брони.", agentId);

        // Освобождаем слот у повара (если был назначен)
        if (task.getAssignedCook() != null) {
            send("COOK_" + task.getAssignedCook().getId(), MessageType.FREE_SLOT, task.getId());
        }
        // Освобождаем слот у оборудования (если было назначено)
        if (task.getAssignedEquipmentType() != null) {
            send("EQUIPMENT_TYPE_" + task.getAssignedEquipmentType(), MessageType.FREE_SLOT, task.getId());
        }

        // Сброс внутреннего состояния агента
        task.setStatus(CookingTaskStatus.PENDING);
        task.setPlannedStartTime(null);
        task.setPlannedEndTime(null);
        task.setAssignedCook(null);
        task.setAssignedEquipmentType(null);
        task.setLocalOverdue(false);
        this.replanCount = 0;
        task.setReplanCount(0);

        // ВАЖНО: НЕ вызываем startNegotiations() здесь.
        // OrderAgent сам пришлёт новый INIT, когда придёт наша очередь.
    }

    // -----------------------------------------------------------------------
    // Провал задачи
    // -----------------------------------------------------------------------

    /**
     * Зафиксировать провал планирования.
     * Обновить статус в БД и уведомить OrderAgent.
     */
    private void failTask() {
        log.warn("{}: задача {} не смогла запланироваться", agentId, task.getId());
        task.setStatus(CookingTaskStatus.FAILED);
        taskRepository.save(task);
        send(orderAgentId, MessageType.TASK_FAILED, task.getId());
    }
}