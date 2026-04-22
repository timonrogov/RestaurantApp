package com.example.restaurant.scheduler.agents;

import com.example.restaurant.enums.CookingTaskStatus;
import com.example.restaurant.models.CookingTask;
import com.example.restaurant.models.Order;
import com.example.restaurant.models.OrderCourse;
import com.example.restaurant.models.OrderItem;
import com.example.restaurant.models.CookingTaskTemplate;
import com.example.restaurant.repositories.CookingTaskRepository;
import com.example.restaurant.repositories.CookingTaskTemplateRepository;
import com.example.restaurant.repositories.OrderCourseRepository;
import com.example.restaurant.scheduler.messages.Message;
import com.example.restaurant.scheduler.messages.MessageType;
import com.example.restaurant.scheduler.messages.dto.TaskPlannedBody;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Агент заказа — координатор планирования всего заказа.
 *
 * Отвечает за:
 *   1. Создание CookingTask для каждого этапа каждого блюда заказа.
 *   2. Создание TaskAgent-ов и запуск переговоров курс за курсом.
 *   3. Соблюдение временных зависимостей между курсами (syncGapMinutes).
 *   4. Отслеживание завершения каждого курса и запуск следующего.
 *   5. Уведомление DispatcherAgent о полном завершении планирования.
 *
 * Полуактивный агент: не инициирует переговоры сам, но запускает TaskAgent-ов,
 * которые их ведут. Реагирует на их результаты (TASK_PLANNED, TASK_FAILED,
 * TASK_REPLANNING) и принимает решение о следующем шаге.
 *
 * ID агента: "ORDER_{order.id}", например "ORDER_15".
 */
public class OrderAgent extends BaseAgent {

    // -----------------------------------------------------------------------
    // Зависимости
    // -----------------------------------------------------------------------

    private final Order order;
    private final SceneAgent sceneAgent;
    private final CookingTaskRepository taskRepository;
    private final CookingTaskTemplateRepository templateRepository;
    private final OrderCourseRepository orderCourseRepository;

    /** ID диспетчера — уведомляем его когда все курсы завершены. */
    private static final String DISPATCHER_AGENT_ID = "DISPATCHER";

    /**
     * Дедлайн для задач: как далеко вперёд планируем.
     * Для учебного проекта используем фиксированное значение — 2 часа.
     * В реальной системе дедлайн считался бы из ожиданий гостя.
     */
    private static final int PLANNING_HORIZON_MINUTES = 120;

    // -----------------------------------------------------------------------
    // Состояние планирования
    // -----------------------------------------------------------------------

    /**
     * Список состояний всех курсов заказа.
     * Упорядочен по courseNumber: courseStates.get(0) — первый курс,
     * courseStates.get(1) — второй и т.д.
     */
    private List<CourseState> courseStates;

    /**
     * Индекс курса, который планируется прямо сейчас.
     * Увеличивается на 1 каждый раз когда текущий курс завершается.
     */
    private int currentCourseIndex = 0;

    /**
     * taskId → agentId соответствующего TaskAgent.
     * Используется для снятия агентов с регистрации после завершения.
     */
    private final Map<Long, String> taskIdToAgentId = new HashMap<>();

    // -----------------------------------------------------------------------
    // Конструктор
    // -----------------------------------------------------------------------

    public OrderAgent(Order order,
                      SceneAgent sceneAgent,
                      CookingTaskRepository taskRepository,
                      CookingTaskTemplateRepository templateRepository,
                      OrderCourseRepository orderCourseRepository) {
        super("ORDER_" + order.getId());
        this.order = order;
        this.sceneAgent = sceneAgent;
        this.taskRepository = taskRepository;
        this.templateRepository = templateRepository;
        this.orderCourseRepository = orderCourseRepository;
    }

    // -----------------------------------------------------------------------
    // Диспетчеризация входящих сообщений
    // -----------------------------------------------------------------------

    @Override
    protected void dispatch(Message message) {
        switch (message.getType()) {
            case INIT             -> handleInit(message);
            case TASK_PLANNED     -> handleTaskPlanned(message);
            case TASK_FAILED      -> handleTaskFailed(message);
            case TASK_REPLANNING  -> handleTaskReplanning(message);
            default -> log.warn("{}: получено неожиданное сообщение типа {}",
                    agentId, message.getType());
        }
    }

    // -----------------------------------------------------------------------
    // Фаза 1: инициализация
    // -----------------------------------------------------------------------

    /**
     * Инициализировать агент заказа.
     *
     * Алгоритм:
     *   1. Загрузить курсы заказа из БД (OrderCourse).
     *      Если курсов нет — создать один курс по умолчанию (все блюда = курс 1).
     *   2. Для каждого курса создать CourseState.
     *   3. Для каждой OrderItem заказа:
     *      a. Загрузить шаблоны (CookingTaskTemplate) блюда.
     *      b. Создать CookingTask для каждого шаблона, сохранить в БД.
     *      c. Добавить taskId в соответствующий CourseState.
     *   4. Запустить планирование первого курса.
     */
    private void handleInit(Message message) {
        log.info("{}: инициализация. Заказ #{}, стол {}",
                agentId, order.getId(), order.getTableNumber());

        initializeCourseStates();
        createCookingTasksForAllItems();

        if (courseStates.isEmpty()) {
            log.warn("{}: у заказа нет курсов и нет позиций. Завершаем.", agentId);
            notifyAllTasksPlanned();
            return;
        }

        planCurrentCourse();
    }

    /**
     * Загрузить или создать курсы заказа.
     * Если в таблице order_course нет записей для этого заказа —
     * считаем что все блюда в одном курсе (courseNumber=1, syncGap=0).
     */
    private void initializeCourseStates() {
        List<OrderCourse> courses = orderCourseRepository
                .findByOrderIdOrderByCourseNumberAsc(order.getId());

        if (courses.isEmpty()) {
            // Нет явных курсов — создаём один по умолчанию
            CourseState defaultCourse = new CourseState(1, 0);
            courseStates = List.of(defaultCourse);
            log.debug("{}: курсы не заданы, создан курс по умолчанию", agentId);
        } else {
            courseStates = courses.stream()
                    .map(c -> new CourseState(c.getCourseNumber(), c.getSyncGapMinutes()))
                    .toList();
            log.debug("{}: загружено {} курсов", agentId, courseStates.size());
        }
    }

    /**
     * Создать CookingTask-объекты для всех позиций заказа.
     *
     * Для каждой OrderItem берём все CookingTaskTemplate блюда,
     * создаём по одному CookingTask на каждый шаблон и добавляем
     * ID задачи в CourseState соответствующего курса.
     *
     * Задачи многоэтапных блюд (stepNumber > 1) не запускаются сразу —
     * TaskAgent для шага N создаётся только после завершения шага N-1.
     * Поэтому в CourseState.taskIds мы пока добавляем только задачи
     * первого шага (stepNumber == 1). Задачи следующих шагов будут
     * добавлены динамически в handleStepCompleted() — но для упрощения
     * в текущей реализации все этапы одного блюда считаются независимыми
     * и планируются параллельно (stepNumber игнорируется).
     *
     * Примечание: последовательное планирование этапов (шаг N после шага N-1)
     * — это расширение, которое можно добавить позднее.
     */
    private void createCookingTasksForAllItems() {
        for (OrderItem item : order.getOrderItems()) {
            int courseNumber = item.getCourseNumber();
            CourseState courseState = findCourseState(courseNumber);

            if (courseState == null) {
                // Позиция ссылается на несуществующий курс — используем первый
                log.warn("{}: OrderItem {} ссылается на курс {}, которого нет. Используем курс 1.",
                        agentId, item.getId(), courseNumber);
                courseState = courseStates.get(0);
            }

            // Загружаем шаблоны этапов блюда
            List<CookingTaskTemplate> templates = templateRepository
                    .findByDishIdOrderByStepNumberAsc(item.getDish().getId());

            if (templates.isEmpty()) {
                log.warn("{}: блюдо '{}' не имеет шаблонов этапов. Позиция {} пропущена.",
                        agentId, item.getDish().getName(), item.getId());
                continue;
            }

            // Создаём CookingTask для каждого шаблона
            for (CookingTaskTemplate template : templates) {
                CookingTask task = new CookingTask();
                task.setOrderItem(item);
                task.setTemplate(template);
                task.setStatus(CookingTaskStatus.PENDING);

                CookingTask saved = taskRepository.save(task);
                courseState.addTaskId(saved.getId());

                log.debug("{}: создана задача #{} для блюда '{}', шаг {} ({})",
                        agentId, saved.getId(), item.getDish().getName(),
                        template.getStepNumber(), template.getStepName());
            }
        }
    }

    // -----------------------------------------------------------------------
    // Фаза 2: планирование текущего курса
    // -----------------------------------------------------------------------

    /**
     * Запустить планирование текущего курса.
     *
     * Вычисляет notBefore для задач курса:
     *   - курс 1: прямо сейчас
     *   - курс N: конец курса N-1 + syncGapMinutes
     *
     * Создаёт TaskAgent для каждой задачи курса и отправляет им INIT.
     */
    private void planCurrentCourse() {
        CourseState current = courseStates.get(currentCourseIndex);
        log.info("{}: запуск планирования курса {} ({} задач)",
                agentId, current.courseNumber, current.taskIds.size());

        if (current.taskIds.isEmpty()) {
            log.warn("{}: курс {} не содержит задач, переходим к следующему", agentId, current.courseNumber);
            onCourseCompleted();
            return;
        }

        // Вычисляем notBefore для задач этого курса
        LocalDateTime notBefore = computeNotBefore(current);
        LocalDateTime deadline = notBefore.plusMinutes(PLANNING_HORIZON_MINUTES);

        log.debug("{}: курс {}, notBefore={}, deadline={}", agentId, current.courseNumber, notBefore, deadline);

        // Загружаем все задачи курса из БД и создаём для каждой TaskAgent
        List<CookingTask> tasks = taskRepository.findAll().stream()
                .filter(t -> current.taskIds.contains(t.getId()))
                .toList();

        // === ИСПРАВЛЕНИЕ: ВЫЧИСЛЯЕМ УМНЫЙ ДЕДЛАЙН (targetEndTime) ===
        int maxDuration = tasks.stream()
                .mapToInt(t -> t.getTemplate().getDurationMinutes())
                .max()
                .orElse(0);

        // Целевое время синхронизации — это время окончания самого долгого блюда
        LocalDateTime targetEndTime = notBefore.plusMinutes(maxDuration);
        log.debug("{}: курс {}, notBefore={}, targetEndTime={}, deadline={}",
                agentId, current.courseNumber, notBefore, targetEndTime, deadline);

        for (CookingTask task : tasks) {
            TaskAgent taskAgent = new TaskAgent(
                    task,
                    agentId,
                    sceneAgent,
                    taskRepository,
                    notBefore,
                    targetEndTime,
                    deadline
            );

            messageBus.register(taskAgent);
            taskIdToAgentId.put(task.getId(), taskAgent.getAgentId());

            // Запускаем переговоры через INIT
            send(taskAgent.getAgentId(), MessageType.INIT, null);

            log.debug("{}: создан и запущен TaskAgent {} для задачи #{}",
                    agentId, taskAgent.getAgentId(), task.getId());
        }
    }

    /**
     * Вычислить notBefore для задач курса.
     *
     * Для первого курса — текущее время.
     * Для остальных — конец предыдущего курса + пауза.
     * Если предыдущий курс ещё не завершился (нет latestPlannedEnd) — тоже текущее время.
     */
    private LocalDateTime computeNotBefore(CourseState current) {
        if (currentCourseIndex == 0) {
            return LocalDateTime.now();
        }

        CourseState previous = courseStates.get(currentCourseIndex - 1);
        if (previous.latestPlannedEnd == null) {
            log.warn("{}: предыдущий курс не имеет latestPlannedEnd, используем now()", agentId);
            return LocalDateTime.now();
        }

        return previous.latestPlannedEnd.plusMinutes(current.syncGapMinutes);
    }

    // -----------------------------------------------------------------------
    // Фаза 3: отслеживание результатов
    // -----------------------------------------------------------------------

    /**
     * Задача успешно запланирована.
     *
     * Обновляет latestPlannedEnd курса (берём максимум — самая поздняя задача
     * определяет когда весь курс будет готов). Проверяет завершённость курса.
     *
     * @param message тело: {@link TaskPlannedBody}
     */
    private void handleTaskPlanned(Message message) {
        TaskPlannedBody body = (TaskPlannedBody) message.getBody();
        long taskId = body.getTaskId();
        LocalDateTime confirmedEnd = body.getConfirmedEnd();

        CourseState courseState = findCourseStateByTaskId(taskId);
        if (courseState == null) {
            log.warn("{}: TASK_PLANNED для задачи #{}, но курс не найден", agentId, taskId);
            return;
        }

        courseState.markPlanned(taskId, confirmedEnd);

        log.info("{}: задача #{} запланирована до {}. Курс {}: {}/{} задач завершено.",
                agentId, taskId, confirmedEnd,
                courseState.courseNumber,
                courseState.plannedTaskIds.size() + courseState.failedTaskIds.size(),
                courseState.taskIds.size());

        checkCourseCompletion(courseState);
    }

    /**
     * Задача провалила планирование.
     *
     * Логируем предупреждение. Провалившаяся задача не блокирует весь заказ:
     * остальные блюда будут приготовлены, просто это блюдо (или его этап) — нет.
     * Проверяем завершённость курса.
     *
     * @param message тело: {@code Long taskId}
     */
    private void handleTaskFailed(Message message) {
        long taskId = (Long) message.getBody();

        CourseState courseState = findCourseStateByTaskId(taskId);
        if (courseState == null) {
            log.warn("{}: TASK_FAILED для задачи #{}, но курс не найден", agentId, taskId);
            return;
        }

        courseState.markFailed(taskId);

        log.warn("{}: задача #{} провалила планирование! Курс {}: {}/{} задач завершено.",
                agentId, taskId,
                courseState.courseNumber,
                courseState.plannedTaskIds.size() + courseState.failedTaskIds.size(),
                courseState.taskIds.size());

        checkCourseCompletion(courseState);
    }

    /**
     * Задача перепланируется из-за вытеснения.
     *
     * Убираем задачу из plannedTaskIds если она там была
     * (задача могла быть запланирована, потом вытеснена).
     * latestPlannedEnd будет пересчитан при следующем TASK_PLANNED.
     *
     * @param message тело: {@code Long taskId}
     */
    private void handleTaskReplanning(Message message) {
        long taskId = (Long) message.getBody();

        CourseState courseState = findCourseStateByTaskId(taskId);
        if (courseState == null) {
            log.warn("{}: TASK_REPLANNING для задачи #{}, но курс не найден", agentId, taskId);
            return;
        }

        boolean wasPlanned = courseState.plannedTaskIds.remove(taskId);

        if (wasPlanned) {
            // Задача была запланирована, теперь перепланируется.
            // latestPlannedEnd мог опираться на эту задачу — пересчитываем.
            recalculateLatestPlannedEnd(courseState);
            log.info("{}: задача #{} убрана из планов курса {}, пересчитываем latestPlannedEnd={}",
                    agentId, taskId, courseState.courseNumber, courseState.latestPlannedEnd);
        } else {
            log.debug("{}: задача #{} перепланируется (ещё не была в planned)", agentId, taskId);
        }
    }

    // -----------------------------------------------------------------------
    // Завершение курса
    // -----------------------------------------------------------------------

    /**
     * Проверить: завершён ли текущий курс?
     * Курс считается завершённым когда каждая его задача либо запланирована,
     * либо провалилась (т.е. ответ получен от всех).
     */
    private void checkCourseCompletion(CourseState courseState) {
        int resolved = courseState.plannedTaskIds.size() + courseState.failedTaskIds.size();
        int total = courseState.taskIds.size();

        if (resolved >= total) {
            log.info("{}: курс {} завершён. Запланировано: {}, провалено: {}.",
                    agentId,
                    courseState.courseNumber,
                    courseState.plannedTaskIds.size(),
                    courseState.failedTaskIds.size());
            onCourseCompleted();
        }
    }

    /**
     * Текущий курс завершён — запустить следующий или уведомить о полном завершении.
     */
    private void onCourseCompleted() {
        // Снимаем с регистрации TaskAgent-ов завершённого курса
        CourseState completedCourse = courseStates.get(currentCourseIndex);
        unregisterTaskAgentsForCourse(completedCourse);

        currentCourseIndex++;

        if (currentCourseIndex < courseStates.size()) {
            // Есть ещё курсы — запускаем следующий
            log.info("{}: переход к курсу {}", agentId, currentCourseIndex + 1);
            planCurrentCourse();
        } else {
            // Все курсы пройдены
            notifyAllTasksPlanned();
        }
    }

    /**
     * Уведомить DispatcherAgent что планирование заказа полностью завершено.
     */
    private void notifyAllTasksPlanned() {
        log.info("{}: все курсы заказа #{} запланированы. Уведомляем диспетчера.", agentId, order.getId());
        send(DISPATCHER_AGENT_ID, MessageType.ALL_TASKS_PLANNED, order.getId());
    }

    /**
     * Снять с регистрации TaskAgent-ов завершённого курса.
     * Освобождаем память — завершённые агенты больше не нужны.
     */
    private void unregisterTaskAgentsForCourse(CourseState courseState) {
        for (Long taskId : courseState.taskIds) {
            String taskAgentId = taskIdToAgentId.get(taskId);
            if (taskAgentId != null) {
                messageBus.unregister(taskAgentId);
                log.debug("{}: снят с регистрации {}", agentId, taskAgentId);
            }
        }
    }

    // -----------------------------------------------------------------------
    // Вспомогательные методы
    // -----------------------------------------------------------------------

    /**
     * Найти CourseState по номеру курса.
     * Возвращает null если курс не найден.
     */
    private CourseState findCourseState(int courseNumber) {
        return courseStates.stream()
                .filter(cs -> cs.courseNumber == courseNumber)
                .findFirst()
                .orElse(null);
    }

    /**
     * Найти CourseState по ID задачи.
     * Перебирает все курсы и ищет тот, в чьём taskIds есть нужный ID.
     */
    private CourseState findCourseStateByTaskId(long taskId) {
        return courseStates.stream()
                .filter(cs -> cs.taskIds.contains(taskId))
                .findFirst()
                .orElse(null);
    }

    /**
     * Пересчитать latestPlannedEnd курса после перепланирования задачи.
     * Берём максимум confirmedEnd среди всех оставшихся запланированных задач курса.
     */
    private void recalculateLatestPlannedEnd(CourseState courseState) {
        if (courseState.plannedTaskIds.isEmpty()) {
            courseState.latestPlannedEnd = null;
            return;
        }

        // Загружаем запланированные задачи и берём максимум plannedEndTime
        courseState.latestPlannedEnd = taskRepository.findAll().stream()
                .filter(t -> courseState.plannedTaskIds.contains(t.getId()))
                .map(CookingTask::getPlannedEndTime)
                .filter(t -> t != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    // -----------------------------------------------------------------------
    // Вложенный класс: состояние одного курса
    // -----------------------------------------------------------------------

    /**
     * Состояние курса блюд в заказе.
     * Хранит прогресс планирования: какие задачи уже запланированы,
     * какие провалились, и когда заканчивается самая поздняя из запланированных.
     */
    private static class CourseState {

        /** Номер курса (1, 2, 3...). */
        final int courseNumber;

        /**
         * Пауза в минутах между окончанием предыдущего курса и notBefore этого.
         * Для первого курса = 0.
         */
        final int syncGapMinutes;

        /** ID всех CookingTask этого курса. */
        final Set<Long> taskIds = new HashSet<>();

        /** ID задач, которые успешно запланированы (получили TASK_PLANNED). */
        final Set<Long> plannedTaskIds = new HashSet<>();

        /** ID задач, которые провалились (получили TASK_FAILED). */
        final Set<Long> failedTaskIds = new HashSet<>();

        /**
         * Максимальное время окончания среди всех запланированных задач курса.
         * Используется для вычисления notBefore следующего курса:
         *   nextCourse.notBefore = latestPlannedEnd + nextCourse.syncGapMinutes
         *
         * null если ни одна задача ещё не запланирована.
         */
        LocalDateTime latestPlannedEnd = null;

        CourseState(int courseNumber, int syncGapMinutes) {
            this.courseNumber = courseNumber;
            this.syncGapMinutes = syncGapMinutes;
        }

        void addTaskId(long taskId) {
            taskIds.add(taskId);
        }

        /**
         * Отметить задачу как запланированную и обновить latestPlannedEnd.
         * latestPlannedEnd — это max из всех confirmedEnd запланированных задач.
         */
        void markPlanned(long taskId, LocalDateTime confirmedEnd) {
            plannedTaskIds.add(taskId);
            if (confirmedEnd != null) {
                if (latestPlannedEnd == null || confirmedEnd.isAfter(latestPlannedEnd)) {
                    latestPlannedEnd = confirmedEnd;
                }
            }
        }

        void markFailed(long taskId) {
            failedTaskIds.add(taskId);
        }
    }
}