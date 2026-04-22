package com.example.restaurant.scheduler.agents;

import com.example.restaurant.enums.CookSpecialization;
import com.example.restaurant.models.CookProfile;
import com.example.restaurant.scheduler.messages.Message;
import com.example.restaurant.scheduler.messages.MessageType;
import com.example.restaurant.scheduler.messages.dto.ParamsRequestBody;
import com.example.restaurant.scheduler.messages.dto.ParamsResponseBody;
import com.example.restaurant.scheduler.messages.dto.PlacementVariant;
import com.example.restaurant.scheduler.messages.dto.PlanningRequestBody;
import com.example.restaurant.scheduler.messages.dto.PlanningResponseBody;
import com.example.restaurant.scheduler.schedule.CookSchedule;
import com.example.restaurant.scheduler.schedule.ScheduleSlot;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Агент повара — пассивный агент-ресурс.
 *
 * Представляет одного конкретного повара и управляет его расписанием.
 * Не инициирует переговоры самостоятельно — только отвечает на запросы TaskAgent-ов.
 *
 * Обрабатывает три типа входящих сообщений:
 *   PARAMS_REQUEST   — TaskAgent спрашивает варианты размещения
 *   PLANNING_REQUEST — TaskAgent бронирует выбранный вариант
 *
 * Метод handleCookUnavailable() вызывается напрямую из DispatcherAgent
 * (не через MessageBus) — это допустимо, так как DispatcherAgent держит
 * прямую ссылку на этот агент.
 *
 * ID агента: "COOK_{cookProfile.id}", например "COOK_3".
 */
public class CookAgent extends BaseAgent {

    private final CookProfile cookProfile;
    private final CookSchedule schedule;

    /** Флаг доступности. SceneAgent проверяет его перед включением в список кандидатов. */
    private boolean active;

    public CookAgent(CookProfile cookProfile, CookSchedule schedule) {
        super("COOK_" + cookProfile.getId());
        this.cookProfile = cookProfile;
        this.schedule = schedule;
        this.active = cookProfile.isActive();
    }

    // -----------------------------------------------------------------------
    // Диспетчеризация входящих сообщений
    // -----------------------------------------------------------------------

    @Override
    protected void dispatch(Message message) {
        switch (message.getType()) {
            case PARAMS_REQUEST   -> handleParamsRequest(message);
            case PLANNING_REQUEST -> handlePlanningRequest(message);
            default -> log.warn("{}: получено неожиданное сообщение типа {}",
                    agentId, message.getType());
        }
    }

    // -----------------------------------------------------------------------
    // Обработка PARAMS_REQUEST
    // -----------------------------------------------------------------------

    /**
     * Ответить TaskAgent-у списком вариантов размещения задачи в своём расписании.
     *
     * @param message входящее сообщение с телом ParamsRequestBody
     */
    private void handleParamsRequest(Message message) {
        ParamsRequestBody request = (ParamsRequestBody) message.getBody();
        log.debug("{}: получен PARAMS_REQUEST для задачи {}", agentId, request.getTaskId());

        List<PlacementVariant> variants = buildVariants(request);

        ParamsResponseBody response = new ParamsResponseBody(
                request.getTaskId(),
                cookProfile.getId(),
                variants
        );

        reply(message, MessageType.PARAMS_RESPONSE, response);

        log.debug("{}: отправлен PARAMS_RESPONSE с {} вариантами для задачи {}",
                agentId, variants.size(), request.getTaskId());
    }

    /**
     * Построить список вариантов размещения задачи.
     *
     * Метод пробует три стратегии по убыванию предпочтительности:
     *
     *   1. ASAP — как можно раньше, первый свободный слот начиная с notBefore.
     *      Добавляется если задача успевает завершиться до deadline.
     *
     *   2. JIT — just-in-time, задача заканчивается ровно к deadline.
     *      Добавляется если JIT-слот свободен И отличается от ASAP
     *      (нет смысла добавлять дублирующий вариант).
     *
     *   3. CONFLICT — вытеснение. Добавляется только если ни ASAP, ни JIT
     *      не укладываются в deadline. Ищет единственную конкурирующую задачу
     *      из другого заказа с наибольшим запасом времени (наиболее «вытесняемую»).
     *
     * @param request тело запроса от TaskAgent
     * @return список вариантов (может быть пустым)
     */
    private List<PlacementVariant> buildVariants(ParamsRequestBody request) {
        List<PlacementVariant> variants = new ArrayList<>();

        int duration = request.getDurationMinutes();
        LocalDateTime notBefore = request.getNotBefore();
        LocalDateTime deadline = request.getDeadline();

        // -------------------------------------------------------------------
        // Шаг 1: ASAP-вариант
        // -------------------------------------------------------------------
        LocalDateTime asapStart = schedule.findAsapSlot(duration, notBefore);
        LocalDateTime asapEnd = asapStart.plusMinutes(duration);
        boolean asapFitsDeadline = !asapEnd.isAfter(deadline);

        if (asapFitsDeadline) {
            PlacementVariant asap = new PlacementVariant(
                    agentId, "asap", asapStart, asapEnd, null
            );
            variants.add(asap);
            log.debug("{}: ASAP-вариант для задачи {}: [{} → {}]",
                    agentId, request.getTaskId(), asapStart, asapEnd);
        }

        // -------------------------------------------------------------------
        // Шаг 2: JIT-вариант
        // -------------------------------------------------------------------
        LocalDateTime jitStart = schedule.findJitSlot(duration, request.getTargetEndTime(), notBefore);

        if (jitStart != null) {
            LocalDateTime jitEnd = jitStart.plusMinutes(duration);
            // Добавляем JIT только если он отличается от ASAP
            // (если они совпали — два одинаковых варианта не нужны)
            boolean isDifferentFromAsap = !jitStart.equals(asapStart);
            if (isDifferentFromAsap) {
                PlacementVariant jit = new PlacementVariant(
                        agentId, "jit", jitStart, jitEnd, null
                );
                variants.add(jit);
                log.debug("{}: JIT-вариант для задачи {}: [{} → {}]",
                        agentId, request.getTaskId(), jitStart, jitEnd);
            }
        }

        // -------------------------------------------------------------------
        // Шаг 3: CONFLICT-вариант (только если ни ASAP ни JIT не уложились)
        // -------------------------------------------------------------------
        if (variants.isEmpty()) {
            PlacementVariant conflictVariant = buildConflictVariant(request, duration, notBefore, deadline);
            if (conflictVariant != null) {
                variants.add(conflictVariant);
            }
        }

        return variants;
    }

    /**
     * Попытаться построить вариант с вытеснением конкурирующей задачи.
     *
     * Алгоритм:
     *   1. Вычислить идеальный интервал [jitStart, deadline) для новой задачи.
     *   2. Найти все слоты, конфликтующие с этим интервалом.
     *   3. Отфильтровать слоты своего заказа (своих вытеснять нельзя).
     *   4. Если остался ровно один кандидат — предложить вытеснение.
     *      Если кандидатов несколько — не вытесняем (слишком сложный случай).
     *   5. Выбрать кандидата с наибольшим endTime (наименее срочный).
     *
     * @return вариант с вытеснением или null если не удалось построить
     */
    private PlacementVariant buildConflictVariant(ParamsRequestBody request,
                                                  int duration,
                                                  LocalDateTime notBefore,
                                                  LocalDateTime deadline) {
        // Идеальный интервал для новой задачи — в JIT-позиции
        LocalDateTime jitStart = deadline.minusMinutes(duration);

        // Не можем начать раньше notBefore
        if (jitStart.isBefore(notBefore)) {
            jitStart = notBefore;
        }

        LocalDateTime jitEnd = jitStart.plusMinutes(duration);

        // Ищем конфликтующие слоты в этом интервале
        List<ScheduleSlot> conflicts = schedule.getConflicts(jitStart, jitEnd);

        if (conflicts.isEmpty()) {
            // Нет конфликтов, но мы сюда попали потому что ASAP/JIT не подошли.
            // Это не должно происходить, логируем и выходим.
            log.warn("{}: конфликт-вариант: конфликтов нет, но стандартные варианты не подошли. " +
                            "Задача {}, интервал [{} → {}]",
                    agentId, request.getTaskId(), jitStart, jitEnd);
            return null;
        }

        // Фильтруем — не трогаем задачи того же заказа
        List<ScheduleSlot> evictableCandidates = conflicts.stream()
                .filter(slot -> slot.getOrderId() != request.getOrderIdForConflictCheck())
                .toList();

        if (evictableCandidates.isEmpty()) {
            log.debug("{}: конфликт-вариант для задачи {}: все конфликтующие задачи из того же заказа",
                    agentId, request.getTaskId());
            return null;
        }

        if (evictableCandidates.size() > 1) {
            // Несколько кандидатов — слишком сложный случай, не вытесняем
            log.debug("{}: конфликт-вариант для задачи {}: {} кандидатов на вытеснение, пропускаем",
                    agentId, request.getTaskId(), evictableCandidates.size());
            return null;
        }

        // Ровно один кандидат — предлагаем его вытеснить
        ScheduleSlot victim = evictableCandidates.get(0);

        log.debug("{}: конфликт-вариант для задачи {}: кандидат на вытеснение — задача {} (заказ {})",
                agentId, request.getTaskId(), victim.getTaskId(), victim.getOrderId());

        return new PlacementVariant(
                agentId,
                "conflict",
                jitStart,
                jitEnd,
                victim.getTaskId()   // ID задачи-кандидата на вытеснение
        );
    }

    // -----------------------------------------------------------------------
    // Обработка PLANNING_REQUEST
    // -----------------------------------------------------------------------

    /**
     * Зарезервировать временной слот для задачи.
     *
     * Три сценария в зависимости от variantName:
     *
     *   "asap" / "jit" — проверить что слот всё ещё свободен, добавить.
     *   "conflict"     — выполнить вытеснение: удалить конкурирующий слот,
     *                    добавить новый, отправить REMOVE_TASK вытесненному TaskAgent-у.
     *
     * Почему вытеснение — оптимистичная операция без отката?
     * В однопоточной модели откат сложно реализовать «синхронно»: CookAgent
     * отправляет REMOVE_TASK и немедленно продолжает обработку очереди.
     * Вытесненный TaskAgent сам разберётся — найдёт новый ресурс или сообщит
     * о провале OrderAgent-у. Это «простейшая версия» разрешения конфликтов
     * из методички — достаточная для учебного проекта.
     *
     * @param message входящее сообщение с телом PlanningRequestBody
     */
    private void handlePlanningRequest(Message message) {
        PlanningRequestBody request = (PlanningRequestBody) message.getBody();
        PlacementVariant chosen = request.getChosenVariant();

        log.debug("{}: получен PLANNING_REQUEST для задачи {}, вариант '{}'",
                agentId, request.getTaskId(), chosen.getVariantName());

        if ("conflict".equals(chosen.getVariantName())) {
            handleConflictPlanning(message, request, chosen);
        } else {
            handleRegularPlanning(message, request, chosen);
        }
    }

    /**
     * Зарезервировать обычный слот (asap или jit).
     *
     * Проверяем что слот по-прежнему свободен: пока TaskAgent собирал ответы
     * от всех поваров и выбирал лучший вариант, этот повар мог уже занять
     * время другой задачей. В однопоточной модели это не может произойти
     * (TaskAgent обрабатывает ответы последовательно, а не параллельно),
     * но проверка остаётся как защита на будущее.
     */
    private void handleRegularPlanning(Message message,
                                       PlanningRequestBody request,
                                       PlacementVariant chosen) {
        // Проверяем что интервал свободен
        List<ScheduleSlot> conflicts = schedule.getConflicts(
                chosen.getStartTime(), chosen.getEndTime()
        );

        if (!conflicts.isEmpty()) {
            // Слот занят — отказываем
            log.warn("{}: слот [{} → {}] занят, отказываем задаче {}",
                    agentId, chosen.getStartTime(), chosen.getEndTime(), request.getTaskId());
            reply(message, MessageType.PLANNING_RESPONSE,
                    PlanningResponseBody.failure(request.getTaskId()));
            return;
        }

        // Добавляем слот
        // orderId нам нужен для будущих проверок вытеснения — берём из варианта.
        // Проблема: PlacementVariant не хранит orderId. Используем 0 как заглушку.
        // TaskAgent при создании PlanningRequestBody может передать orderId в теле —
        // но в текущей реализации PlanningRequestBody содержит только taskId и вариант.
        // Решение: извлекаем orderId позднее из CookingTask при необходимости,
        // а здесь используем специальное значение -1 (будет исправлено при интеграции
        // с TaskAgent в этапе 8, который передаст orderId в PlanningRequestBody).
        // Пока что orderId в слоте будет заполняться через расширение PlanningRequestBody.
        ScheduleSlot slot = new ScheduleSlot(
                request.getTaskId(),
                request.getOrderId(),
                chosen.getStartTime(),
                chosen.getEndTime(),
                chosen
        );
        schedule.addSlot(slot);

        log.info("{}: задача {} запланирована [{} → {}]",
                agentId, request.getTaskId(), chosen.getStartTime(), chosen.getEndTime());

        reply(message, MessageType.PLANNING_RESPONSE,
                new PlanningResponseBody(
                        true,
                        request.getTaskId(),
                        chosen.getStartTime(),
                        chosen.getEndTime()
                ));
    }

    /**
     * Выполнить вытеснение конкурирующей задачи.
     *
     * Последовательность:
     *   1. Убедиться что конкурирующий слот ещё существует (не был удалён)
     *   2. Удалить конкурирующий слот из расписания
     *   3. Добавить новый слот
     *   4. Отправить REMOVE_TASK вытесненному TaskAgent-у
     *   5. Подтвердить бронирование новому TaskAgent-у
     */
    private void handleConflictPlanning(Message message,
                                        PlanningRequestBody request,
                                        PlacementVariant chosen) {
        Long victimTaskId = chosen.getConflictingTaskId();

        if (victimTaskId == null) {
            log.error("{}: conflict-вариант не содержит conflictingTaskId для задачи {}",
                    agentId, request.getTaskId());
            reply(message, MessageType.PLANNING_RESPONSE,
                    PlanningResponseBody.failure(request.getTaskId()));
            return;
        }

        // Проверяем что жертва всё ещё в расписании
        boolean victimExists = schedule.findByTaskId(victimTaskId).isPresent();
        if (!victimExists) {
            // Кто-то уже вытеснил жертву — слот занят нами или другим образом изменился.
            // Откажем — TaskAgent попробует следующий вариант.
            log.warn("{}: задача-жертва {} уже не в расписании, отказываем задаче {}",
                    agentId, victimTaskId, request.getTaskId());
            reply(message, MessageType.PLANNING_RESPONSE,
                    PlanningResponseBody.failure(request.getTaskId()));
            return;
        }

        // Удаляем жертву из расписания
        schedule.removeSlotByTaskId(victimTaskId);
        log.info("{}: задача {} вытеснена из расписания", agentId, victimTaskId);

        // Добавляем новую задачу
        ScheduleSlot newSlot = new ScheduleSlot(
                request.getTaskId(),
                request.getOrderId(),
                chosen.getStartTime(),
                chosen.getEndTime(),
                chosen
        );
        schedule.addSlot(newSlot);

        // Отправляем REMOVE_TASK вытесненному TaskAgent-у
        // Он должен начать перепланирование заново
        String victimAgentId = "TASK_" + victimTaskId;
        send(victimAgentId, MessageType.REMOVE_TASK, victimTaskId);
        log.info("{}: отправлен REMOVE_TASK агенту {}", agentId, victimAgentId);

        // Подтверждаем бронирование новому TaskAgent-у
        log.info("{}: задача {} запланирована с вытеснением [{} → {}]",
                agentId, request.getTaskId(), chosen.getStartTime(), chosen.getEndTime());

        reply(message, MessageType.PLANNING_RESPONSE,
                new PlanningResponseBody(
                        true,
                        request.getTaskId(),
                        chosen.getStartTime(),
                        chosen.getEndTime()
                ));
    }

    // -----------------------------------------------------------------------
    // Обработка недоступности повара (прямой вызов из DispatcherAgent)
    // -----------------------------------------------------------------------

    /**
     * Обработать недоступность повара (заболел, ушёл раньше и т.д.).
     *
     * Вызывается напрямую из DispatcherAgent при получении COOK_UNAVAILABLE.
     * Не через систему сообщений — это допустимо, DispatcherAgent держит
     * прямую ссылку на этот агент.
     *
     * Логика:
     *   1. Пометить повара как недоступного
     *   2. Для каждой задачи в расписании отправить REMOVE_TASK соответствующему TaskAgent-у
     *   3. Очистить расписание
     */
    public void handleCookUnavailable() {
        log.info("{}: повар становится недоступным, задач в расписании: {}",
                agentId, schedule.getSlots().size());

        this.active = false;

        // Копируем список перед очисткой — не можем итерировать и модифицировать одновременно
        List<ScheduleSlot> currentSlots = schedule.copySlots();

        // Очищаем расписание
        schedule.restoreSlots(new ArrayList<>());

        // Уведомляем все задачи о необходимости перепланирования
        for (ScheduleSlot slot : currentSlots) {
            String taskAgentId = "TASK_" + slot.getTaskId();
            send(taskAgentId, MessageType.REMOVE_TASK, slot.getTaskId());
            log.debug("{}: отправлен REMOVE_TASK агенту {} (недоступность повара)",
                    agentId, taskAgentId);
        }

        log.info("{}: расписание очищено, {} задач уведомлены о перепланировании",
                agentId, currentSlots.size());
    }

    // -----------------------------------------------------------------------
    // Геттеры
    // -----------------------------------------------------------------------

    public long getCookId() {
        return cookProfile.getId();
    }

    public CookSpecialization getSpecialization() {
        return cookProfile.getSpecialization();
    }

    public CookSchedule getSchedule() {
        return schedule;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public CookProfile getCookProfile() {
        return cookProfile;
    }
}