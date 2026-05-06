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
import com.example.restaurant.scheduler.messages.dto.InitTaskPayload;
import com.example.restaurant.scheduler.messages.dto.TaskPlannedBody;
import com.example.restaurant.scheduler.messages.dto.TaskDelayBody;
import com.example.restaurant.scheduler.messages.dto.CancelAndReplanBody;

import java.time.LocalDateTime;
import java.util.*;

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
            case TASK_DELAY_EVENT -> handleTaskDelayEvent(message);
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

    private static class CourseTimeParams {
        final LocalDateTime notBefore;
        LocalDateTime targetEndTime; // Не final, так как можем корректировать
        LocalDateTime deadline;

        CourseTimeParams(LocalDateTime notBefore, LocalDateTime targetEndTime, LocalDateTime deadline) {
            this.notBefore = notBefore;
            this.targetEndTime = targetEndTime;
            this.deadline = deadline;
        }
    }

    private CourseTimeParams calculateCourseTimes(int courseIndex) {
        CourseState current = courseStates.get(courseIndex);
        List<CookingTask> tasks = taskRepository.findAllById(current.taskIds);
        int maxDuration = tasks.stream().mapToInt(t -> t.getTemplate().getDurationMinutes()).max().orElse(0);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime notBefore;
        LocalDateTime targetEndTime;

        // 1. Актуализируем время текущего курса (вдруг кто-то уже начал работу)
        recalculateLatestPlannedEnd(current);

        if (courseIndex == 0) {
            // --- ДЛЯ ПЕРВОГО КУРСА ---
            if (current.latestPlannedEnd != null && current.latestPlannedEnd.isAfter(now)) {
                // ИСПРАВЛЕНИЕ: Если кто-то УЖЕ начал готовить первый курс,
                // весь курс выравнивается по нему! (никаких слепых +1 минута)
                targetEndTime = current.latestPlannedEnd;
                notBefore = targetEndTime.minusMinutes(maxDuration);
                if (notBefore.isBefore(now)) notBefore = now;
            } else {
                // Никто еще не начал - планируем от текущего момента
                notBefore = now.plusMinutes(1);
                targetEndTime = now.plusMinutes(1 + maxDuration);
            }
        } else {
            // --- ДЛЯ ОСТАЛЬНЫХ КУРСОВ ---
            CourseState previous = courseStates.get(courseIndex - 1);
            recalculateLatestPlannedEnd(previous);
            LocalDateTime prevEnd = previous.latestPlannedEnd != null ? previous.latestPlannedEnd : now;

            // Базовый расчет от предыдущего курса
            targetEndTime = prevEnd.plusMinutes(current.syncGapMinutes);
            notBefore = targetEndTime.minusMinutes(maxDuration);

            // Защита от "прошлого"
            if (notBefore.isBefore(now)) {
                notBefore = now;
                targetEndTime = now.plusMinutes(maxDuration);
            }

            // ИСПРАВЛЕНИЕ: Если мы перепланируем курс, в котором УЖЕ есть начатые задачи,
            // их время окончания приоритетнее, чем время, рассчитанное от предыдущего курса!
            if (current.latestPlannedEnd != null && current.latestPlannedEnd.isAfter(targetEndTime)) {
                targetEndTime = current.latestPlannedEnd;
                notBefore = targetEndTime.minusMinutes(maxDuration);
                if (notBefore.isBefore(now)) notBefore = now;
            }
        }

        LocalDateTime deadline = targetEndTime.plusMinutes(PLANNING_HORIZON_MINUTES);
        return new CourseTimeParams(notBefore, targetEndTime, deadline);
    }

    private void planCurrentCourse() {
        if (currentCourseIndex >= courseStates.size()) return;
        CourseState current = courseStates.get(currentCourseIndex);

        log.info("{}: запуск планирования курса {} ({} задач)", agentId, current.courseNumber, current.taskIds.size());

        List<CookingTask> tasks = taskRepository.findAllById(current.taskIds);

        // Берем только те задачи, которые нуждаются в планировании
        List<CookingTask> tasksToPlan = tasks.stream()
                .filter(t -> t.getStatus() == CookingTaskStatus.PENDING || t.getStatus() == CookingTaskStatus.FAILED)
                .sorted(Comparator.comparingInt((CookingTask t) -> t.getTemplate().getDurationMinutes()).reversed())
                .toList();

        current.sortedTaskIdsToPlan = new ArrayList<>(tasksToPlan.stream().map(CookingTask::getId).toList());
        current.currentTaskIndex = 0;

        // Актуализируем список уже запланированных задач (если это перепланирование)
        current.plannedTaskIds.clear();
        for (CookingTask t : tasks) {
            if (t.getStatus() != CookingTaskStatus.PENDING && t.getStatus() != CookingTaskStatus.FAILED && t.getStatus() != CookingTaskStatus.CANCELLED) {
                current.plannedTaskIds.add(t.getId());
            }
        }
        recalculateLatestPlannedEnd(current);

        if (current.sortedTaskIdsToPlan.isEmpty()) {
            checkCourseCompletion(current);
            return;
        }

        // Регистрируем новых агентов (если они еще не созданы)
        for (CookingTask task : tasksToPlan) {
            if (!taskIdToAgentId.containsKey(task.getId())) {
                TaskAgent taskAgent = new TaskAgent(task, agentId, sceneAgent, taskRepository);
                messageBus.register(taskAgent);
                taskIdToAgentId.put(task.getId(), taskAgent.getAgentId());
            }
        }

        planNextTaskInCurrentCourse(current);
    }

    /**
     * Запускает следующую по очереди задачу в текущем курсе.
     * Вызывается на старте курса и после завершения планирования каждой задачи.
     */
    private void planNextTaskInCurrentCourse(CourseState course) {
        if (course.currentTaskIndex < course.sortedTaskIdsToPlan.size()) {
            long nextTaskId = course.sortedTaskIdsToPlan.get(course.currentTaskIndex);
            course.currentTaskIndex++;

            CourseTimeParams times = calculateCourseTimes(courseStates.indexOf(course));

            // МАГИЯ JIT: Если в курсе уже есть начатые задачи (IN_PROGRESS),
            // мы должны ориентироваться на них.
            recalculateLatestPlannedEnd(course);

            // Если кто-то в этом курсе УЖЕ готовится, то targetEndTime для
            // остальных задач курса должен быть ТАКИМ ЖЕ, как у него.
            if (course.latestPlannedEnd != null) {
                times.targetEndTime = course.latestPlannedEnd;
            }

            InitTaskPayload payload = new InitTaskPayload(times.notBefore, times.targetEndTime, times.deadline);
            String taskAgentId = taskIdToAgentId.get(nextTaskId);

            log.debug("{}: отправлен INIT агенту {} (target={})", agentId, taskAgentId, times.targetEndTime);
            send(taskAgentId, MessageType.INIT, payload);
        } else {
            checkCourseCompletion(course);
        }
    }

    // -----------------------------------------------------------------------
    // Фаза 3: отслеживание результатов
    // -----------------------------------------------------------------------

    private void handleTaskPlanned(Message message) {
        TaskPlannedBody body = (TaskPlannedBody) message.getBody();
        long taskId = body.getTaskId();
        LocalDateTime confirmedEnd = body.getConfirmedEnd();

        CourseState courseState = findCourseStateByTaskId(taskId);
        if (courseState == null) return;

        // Фиксируем задачу
        courseState.markPlanned(taskId, confirmedEnd);

        // Получаем актуальные рамки (calculateCourseTimes уже учел confirmedEnd как новый максимум)
        CourseTimeParams times = calculateCourseTimes(courseStates.indexOf(courseState));

        // ЖЕЛЕЗОБЕТОННОЕ ВЫРАВНИВАНИЕ JIT:
        // Ищем в курсе задачи, которые УЖЕ запланированы, но их конец РАНЬШЕ, чем новая цель!
        // Это значит, что они приготовятся слишком рано и остынут. Их нужно сдвинуть вправо.
        List<Long> tasksToReplan = new ArrayList<>();
        for (Long plannedId : courseState.plannedTaskIds) {
            if (plannedId != taskId) {
                CookingTask pt = taskRepository.findById(plannedId).orElse(null);
                // Если задача заканчивается раньше цели хотя бы на 30 секунд
                if (pt != null && pt.getPlannedEndTime() != null &&
                        pt.getPlannedEndTime().isBefore(times.targetEndTime.minusSeconds(30))) {
                    tasksToReplan.add(plannedId);
                }
            }
        }

        if (!tasksToReplan.isEmpty()) {
            log.info("{}: задача {} задала новый дедлайн {}. Выравниваем {} старых задач.",
                    agentId, taskId, times.targetEndTime, tasksToReplan.size());

            for (Long idToReplan : tasksToReplan) {
                String taId = taskIdToAgentId.get(idToReplan);
                if (taId != null) {
                    CancelAndReplanBody payload = new CancelAndReplanBody(
                            idToReplan, times.notBefore, times.targetEndTime, times.deadline
                    );
                    send(taId, MessageType.CANCEL_AND_REPLAN, payload);
                }

                // Сбрасываем в БД
                CookingTask pt = taskRepository.findById(idToReplan).orElseThrow();
                pt.setStatus(CookingTaskStatus.PENDING);
                pt.setPlannedStartTime(null);
                pt.setPlannedEndTime(null);
                pt.setAssignedCook(null);
                pt.setAssignedEquipmentType(null);
                taskRepository.save(pt);

                // Возвращаем в очередь планирования
                courseState.plannedTaskIds.remove(idToReplan);
                if (!courseState.sortedTaskIdsToPlan.contains(idToReplan)) {
                    courseState.sortedTaskIdsToPlan.add(idToReplan);
                }
            }
        }

        if (!checkCourseCompletion(courseState)) {
            planNextTaskInCurrentCourse(courseState);
        }
    }

    private void handleTaskFailed(Message message) {
        long taskId = (Long) message.getBody();
        CourseState courseState = findCourseStateByTaskId(taskId);
        if (courseState == null) return;

        courseState.markFailed(taskId);
        if (!checkCourseCompletion(courseState)) {
            planNextTaskInCurrentCourse(courseState);
        }
    }

    private void handleTaskReplanning(Message message) {
        // Мы переписали механизм перепланирования (handleTaskDelayEvent),
        // поэтому TaskAgent больше не присылает TASK_REPLANNING.
        // Оставляем пустой метод или логируем для отладки.
    }


    // -----------------------------------------------------------------------
    // Завершение курса
    // -----------------------------------------------------------------------

    private boolean checkCourseCompletion(CourseState courseState) {
        int resolved = courseState.plannedTaskIds.size() + courseState.failedTaskIds.size();
        if (resolved >= courseState.taskIds.size()) {
            onCourseCompleted();
            return true;
        }
        return false;
    }

    private void onCourseCompleted() {
        currentCourseIndex++;
        if (currentCourseIndex < courseStates.size()) {
            planCurrentCourse();
        } else {
            notifyAllTasksPlanned();
        }
    }

    private void notifyAllTasksPlanned() {
        log.info("{}: все курсы заказа #{} запланированы.", agentId, order.getId());
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
     * Надежный пересчет времени окончания курса с учетом всех актуальных задач.
     */
    private void recalculateLatestPlannedEnd(CourseState courseState) {
        List<CookingTask> tasks = taskRepository.findAllById(courseState.taskIds);
        LocalDateTime maxEnd = null;

        for (CookingTask t : tasks) {
            // Игнорируем только PENDING (они сейчас перепланируются и не имеют времени),
            // а также отмененные и проваленные.
            // PLANNED, IN_PROGRESS и DONE обязательно учитываем!
            if (t.getStatus() == CookingTaskStatus.PENDING ||
                    t.getStatus() == CookingTaskStatus.FAILED ||
                    t.getStatus() == CookingTaskStatus.CANCELLED) {
                continue;
            }

            LocalDateTime end = t.getActualEndTime() != null ? t.getActualEndTime() : t.getPlannedEndTime();
            if (end != null) {
                if (maxEnd == null || end.isAfter(maxEnd)) {
                    maxEnd = end;
                }
            }
        }
        courseState.latestPlannedEnd = maxEnd;
    }

    // -----------------------------------------------------------------------
    // Обработка задержек и системное перепланирование
    // -----------------------------------------------------------------------

    /**
     * Реакция на задержку или досрочное завершение задачи.
     * OrderAgent пересчитывает время для этого курса и заставляет все
     * зависимые задачи (в этом и следующих курсах) провести новые торги.
     */
    private void handleTaskDelayEvent(Message message) {
        TaskDelayBody body = (TaskDelayBody) message.getBody();
        long taskId = body.getTaskId();

        CourseState affectedCourse = findCourseStateByTaskId(taskId);
        if (affectedCourse == null) return;

        int startIndex = courseStates.indexOf(affectedCourse);
        log.info("{}: сдвиг в курсе {}. Сбрасываем старые планы.", agentId, affectedCourse.courseNumber);

        if (startIndex < currentCourseIndex) {
            currentCourseIndex = startIndex;
        }

        // ПРИНУДИТЕЛЬНО очищаем статусы в БД прямо здесь!
        for (int i = startIndex; i < courseStates.size(); i++) {
            CourseState cs = courseStates.get(i);
            List<CookingTask> tasks = taskRepository.findAllById(cs.taskIds);

            for (CookingTask t : tasks) {
                // Сбрасываем только те, что еще не начали готовиться
                if (t.getStatus() == CookingTaskStatus.PLANNED || t.getStatus() == CookingTaskStatus.PENDING) {

                    // Уведомляем агента, чтобы он очистил память (RAM) у поваров
                    String taId = taskIdToAgentId.get(t.getId());
                    if (taId != null) {
                        send(taId, MessageType.CANCEL_AND_REPLAN, null);
                    }

                    // ЖЕСТКО меняем статус в БД, чтобы planCurrentCourse их увидел
                    t.setStatus(CookingTaskStatus.PENDING);
                    t.setPlannedStartTime(null);
                    t.setPlannedEndTime(null);
                    t.setAssignedCook(null);
                    t.setAssignedEquipmentType(null);
                    taskRepository.save(t);
                }
            }
        }

        // Теперь вызываем планирование — теперь фильтр сработает правильно!
        planCurrentCourse();
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

        // === ДОБАВЛЯЕМ ПОЛЯ ДЛЯ ПОСЛЕДОВАТЕЛЬНОГО ЗАПУСКА ===
        List<Long> sortedTaskIdsToPlan = new ArrayList<>();
        int currentTaskIndex = 0;
        // ====================================================

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