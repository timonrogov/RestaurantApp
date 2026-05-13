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
import com.example.restaurant.scheduler.config.SchedulerProperties;
import com.example.restaurant.scheduler.messages.Message;
import com.example.restaurant.scheduler.messages.MessageType;
import com.example.restaurant.scheduler.messages.dto.InitTaskPayload;
import com.example.restaurant.scheduler.messages.dto.TaskPlannedBody;
import com.example.restaurant.scheduler.messages.dto.TaskDelayBody;
import com.example.restaurant.scheduler.messages.dto.CancelAndReplanBody;
import com.example.restaurant.scheduler.schedule.CookSchedule;
import com.example.restaurant.scheduler.schedule.EquipmentTypeSchedule;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;

import static com.example.restaurant.enums.CookingTaskStatus.*;

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

    private final SchedulerProperties props;

    // -----------------------------------------------------------------------
    // Конструктор
    // -----------------------------------------------------------------------

    public OrderAgent(Order order,
                      SceneAgent sceneAgent,
                      CookingTaskRepository taskRepository,
                      CookingTaskTemplateRepository templateRepository,
                      OrderCourseRepository orderCourseRepository,
                      SchedulerProperties props) {
        super("ORDER_" + order.getId());
        this.order = order;
        this.sceneAgent = sceneAgent;
        this.taskRepository = taskRepository;
        this.templateRepository = templateRepository;
        this.orderCourseRepository = orderCourseRepository;
        this.props = props;
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
     * ИЗМЕНЕНИЕ: теперь учитывается OrderItem.quantity и template.portionsPerSlot.
     * Для каждого шаблона создаётся ceil(quantity / portionsPerSlot) задач-партий.
     *
     * Каждая партия — отдельная CookingTask с полем portionCount:
     *   полная партия:    portionCount = portionsPerSlot
     *   последняя (неполная): portionCount = quantity mod portionsPerSlot
     *
     * Планировщик не знает о partitionCount — он работает с задачами как обычно,
     * каждая занимает 1 capacity у оборудования и 1 слот у повара.
     *
     * Граничный случай: quantity = 0 → задачи не создаются (корректно).
     */
    private void createCookingTasksForAllItems() {
        for (OrderItem item : order.getOrderItems()) {
            int courseNumber = item.getCourseNumber();
            CourseState courseState = findCourseState(courseNumber);

            if (courseState == null) {
                log.warn("{}: OrderItem {} ссылается на курс {}, которого нет. Используем курс 1.",
                        agentId, item.getId(), courseNumber);
                courseState = courseStates.get(0);
            }

            List<CookingTaskTemplate> templates = templateRepository
                    .findByDishIdOrderByStepNumberAsc(item.getDish().getId());

            if (templates.isEmpty()) {
                log.warn("{}: блюдо '{}' не имеет шаблонов этапов. Позиция {} пропущена.",
                        agentId, item.getDish().getName(), item.getId());
                continue;
            }

            int quantity = item.getQuantity();

            for (CookingTaskTemplate template : templates) {
                int pps = template.getPortionsPerSlot();  // portions per slot

                // ceil(quantity / pps): количество партий для этого шаблона
                int batchCount = (pps <= 0 || quantity <= 0)
                        ? 0
                        : (int) Math.ceil((double) quantity / pps);

                for (int b = 0; b < batchCount; b++) {
                    // Последняя партия может быть неполной
                    int portionCount = (b == batchCount - 1)
                            ? quantity - b * pps   // остаток
                            : pps;                 // полная партия

                    CookingTask task = new CookingTask();
                    task.setOrderItem(item);
                    task.setTemplate(template);
                    task.setStatus(PENDING);
                    task.setPortionCount(portionCount);

                    CookingTask saved = taskRepository.save(task);
                    courseState.addTaskId(saved.getId());

                    log.debug("{}: создана задача #{} для блюда '{}', шаг {} ({}), " +
                                    "партия {}/{}, порций: {}",
                            agentId, saved.getId(), item.getDish().getName(),
                            template.getStepNumber(), template.getStepName(),
                            b + 1, batchCount, portionCount);
                }
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

        // Максимальная длительность задачи в курсе — используется для вычисления
        // notBefore: чтобы самая долгая задача успела завершиться к targetEndTime,
        // нужно начать не позже чем targetEndTime - maxDuration.
        int maxDuration = tasks.stream()
                .mapToInt(t -> t.getTemplate().getDurationMinutes())
                .max()
                .orElse(0);

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime notBefore;
        LocalDateTime targetEndTime;

        // Пересчитываем latestPlannedEnd текущего курса из актуальных данных БД.
        // Это нужно потому что между вызовами calculateCourseTimes() статусы задач
        // могут измениться (IN_PROGRESS → DONE и т.д.).
        recalculateLatestPlannedEnd(current);

        if (courseIndex == 0) {
            // ---------------------------------------------------------------
            // ПЕРВЫЙ КУРС
            // ---------------------------------------------------------------

            if (current.latestPlannedEnd != null && current.latestPlannedEnd.isAfter(now)) {
                // Ветка А: кто-то из поваров уже готовит (IN_PROGRESS) или запланирован
                // (PLANNED) и его плановый конец ещё в будущем.
                //
                // В этом случае весь курс «якорится» по этому повару: остальные задачи
                // должны закончиться одновременно с ним. Буфер здесь не нужен —
                // реальное время уже зафиксировано фактом начала работы.
                //
                // Пример: COOK_1 начал напиток в 14:14, закончит в 14:24.
                //   targetEndTime = 14:24
                //   notBefore     = 14:24 - 10 = 14:14, но не раньше now(14:17) → 14:17
                targetEndTime = current.latestPlannedEnd;
                notBefore = targetEndTime.minusMinutes(maxDuration);
                if (notBefore.isBefore(now)) notBefore = now;

            } else {
                // Ветка Б: никто ещё не начал (все задачи курса в PENDING/FAILED
                // или latestPlannedEnd уже в прошлом — курс «просрочен»).
                //
                // П4-ИЗМЕНЕНИЕ: буфер +1 мин добавляется ТОЛЬКО при первом
                // планировании (firstPlanningDone == false).
                //
                // Зачем буфер при первом планировании?
                // Между моментом когда OrderAgent вычисляет notBefore и моментом
                // когда повар фактически видит задачу на KDS проходит некоторое время
                // (обработка очереди MessageBus, запись в БД, WebSocket-пуш).
                // Без буфера задача с notBefore = now рискует сразу попасть в «просрочку»
                // и спровоцировать лишнее перепланирование ещё до того, как повар
                // успел её взять.
                //
                // Зачем убирать буфер при перепланировании?
                // При перепланировании ситуация уже активная: например, повар закончил
                // задачу на 2 минуты раньше и мы хотим сдвинуть следующие задачи назад.
                // Если добавить +1 мин, мы потеряем часть выигрыша. Здесь нужна точность.
                if (!current.firstPlanningDone) {
                    // Первое планирование — с буфером.
                    int buf = props.getPlanning().getFirstCourseBufferMinutes();
                    notBefore = now.plusMinutes(buf);
                    targetEndTime = now.plusMinutes(buf + maxDuration);
                } else {
                    // Перепланирование — без буфера.
                    notBefore = now;
                    targetEndTime = now.plusMinutes(maxDuration);
                }
            }

        } else {
            // ---------------------------------------------------------------
            // КУРС N > 0 (второй, третий и т.д.)
            // ---------------------------------------------------------------

            CourseState previous = courseStates.get(courseIndex - 1);
            recalculateLatestPlannedEnd(previous);
            LocalDateTime prevEnd = previous.latestPlannedEnd != null
                    ? previous.latestPlannedEnd
                    : now;

            // Базовый расчёт: этот курс должен закончиться через syncGapMinutes
            // после окончания предыдущего.
            //
            // syncGapMinutes — это минимальная пауза между подачей предыдущего блюда
            // и подачей этого. Например, пауза между закусками и основным = 15 мин.
            //
            //   prevEnd = 14:30 (напитки закончились)
            //   syncGapMinutes = 5
            //   targetEndTime = 14:35 (салаты должны быть готовы к 14:35)
            //   notBefore = 14:35 - 10 = 14:25 (самая долгая задача курса = 10 мин)
            targetEndTime = prevEnd.plusMinutes(current.syncGapMinutes);
            notBefore = targetEndTime.minusMinutes(maxDuration);

            // НП1-ИСПРАВЛЕНИЕ: разделяем два случая.
            //
            // Случай А: targetEndTime уже в прошлом (курс сильно задержан).
            // Планируем от текущего момента — единственный разумный вариант.
            if (targetEndTime.isBefore(now)) {
                targetEndTime = now.plusMinutes(maxDuration);
            }
            // Случай Б: notBefore в прошлом, но targetEndTime в будущем.
            // Это нормальная ситуация при задержках в предыдущем курсе.
            //
            // targetEndTime не трогаем — он остаётся prevEnd + syncGap (правильным).
            // notBefore обрезаем до now: findAsapSlot() требует значение не в прошлом,
            // иначе при пустом расписании вернёт время из прошлого.
            //
            // Пример: prevEnd = 17:58, syncGap = 5, maxDuration = 15, now = 17:59.
            //   Было: notBefore → 17:59, targetEndTime → 18:14 (терялся syncGap!)
            //   Стало: notBefore → 17:59, targetEndTime → 18:03 (syncGap сохранён)
            //
            // Примечание: ранние повара (свободные до now) всё равно предложат
            // ASAP = now, а не своё реальное время освобождения. Это ограничение
            // устраняется отдельно через механизм lastTaskEnd в CookSchedule.
            if (notBefore.isBefore(now)) {
                notBefore = now;
            }
            // Если в текущем курсе уже есть начатые (IN_PROGRESS) или запланированные
            // задачи, их плановый конец важнее «теоретического» расчёта.
            //
            // Пример: курс салатов уже планируется, первая задача назначена на COOK_2
            // с окончанием 14:38. Расчёт от предыдущего курса даёт targetEndTime = 14:35.
            // Нужно взять 14:38, иначе мы будем «тянуть назад» уже запланированную задачу.
            if (current.latestPlannedEnd != null
                    && current.latestPlannedEnd.isAfter(targetEndTime)) {
                targetEndTime = current.latestPlannedEnd;
                notBefore = targetEndTime.minusMinutes(maxDuration);
                if (notBefore.isBefore(now)) notBefore = now;
            }
        }

        LocalDateTime deadline = targetEndTime.plusMinutes(props.getPlanning().getHorizonMinutes());
        return new CourseTimeParams(notBefore, targetEndTime, deadline);
    }

    private void planCurrentCourse() {
        if (currentCourseIndex >= courseStates.size()) return;
        CourseState current = courseStates.get(currentCourseIndex);

        log.info("{}: запуск планирования курса {} ({} задач)",
                agentId, current.courseNumber, current.taskIds.size());

        List<CookingTask> tasks = taskRepository.findAllById(current.taskIds);

        // Сортировка по убыванию длительности (LPT — Longest Processing Time First).
        //
        // MessageBus — однопоточная FIFO-очередь: задача, получившая INIT первой,
        // завершает переговоры первой и бронирует лучшего повара первой.
        // Чтобы самая длинная задача попала к наиболее раннему повару
        // (что минимизирует makespan курса), её переговоры должны идти первыми.
        //
        // Это классический алгоритм LPT, адаптированный к мультиагентной архитектуре:
        // каждый агент грамотно выбирает лучшего из оставшихся поваров,
        // а порядок переговоров гарантирует, что «длинные» задачи имеют приоритет.
        List<CookingTask> tasksToPlan = tasks.stream()
                .filter(t -> t.getStatus() == PENDING
                        || t.getStatus() == FAILED)
                .sorted(Comparator.comparingInt(
                        (CookingTask t) -> t.getTemplate().getDurationMinutes()
                ).reversed())   // LPT: длинные задачи ведут переговоры первыми
                .toList();

        // Актуализируем список уже запланированных задач (при перепланировании
        // некоторые могут быть уже IN_PROGRESS или DONE — их не трогаем).
        current.plannedTaskIds.clear();
        for (CookingTask t : tasks) {
            if (t.getStatus() != PENDING
                    && t.getStatus() != FAILED
                    && t.getStatus() != CookingTaskStatus.CANCELLED) {
                current.plannedTaskIds.add(t.getId());
            }
        }
        recalculateLatestPlannedEnd(current);

        if (tasksToPlan.isEmpty()) {
            // Все задачи курса уже в нужном статусе — сразу проверяем завершение.
            checkCourseCompletion(current);
            return;
        }

        // Временны́е параметры вычисляются ОДИН РАЗ для всего курса.
        //
        // Раньше calculateCourseTimes() вызывался внутри planNextTaskInCurrentCourse()
        // отдельно для каждой задачи. Между вызовами могло накапливаться состояние
        // (latestPlannedEnd обновлялся по ходу), поэтому разные задачи получали
        // разные targetEndTime — что само по себе некорректно: все задачи курса
        // должны стремиться к одному целевому времени окончания.
        CourseTimeParams times = calculateCourseTimes(currentCourseIndex);

        // Фиксируем, что первое планирование этого курса состоялось.
        // При следующих вызовах calculateCourseTimes() для этого курса
        // буфер +1 мин добавляться не будет.
        current.firstPlanningDone = true;

        current.stableTargetEndTime = times.targetEndTime;

        log.debug("{}: параметры курса {}: notBefore={}, targetEnd={}, deadline={}",
                agentId, current.courseNumber,
                times.notBefore, times.targetEndTime, times.deadline);

        // Регистрируем и запускаем ВСЕ задачи курса одновременно.
        //
        // Каждая задача получает одинаковые notBefore, targetEndTime, deadline.
        // После этого все TaskAgent-ы параллельно (в рамках однопоточной очереди
        // MessageBus) рассылают PARAMS_REQUEST поварам. Поскольку расписания поваров
        // ещё не заняты задачами этого курса, каждый TaskAgent видит честную картину
        // и выбирает оптимальный вариант для себя.
        //
        // Когда несколько TaskAgent-ов попытаются забронировать одного повара,
        // первый успешно зарезервирует слот (PLANNING_REQUEST → success=true),
        // остальные получат отказ (conflicts) и перейдут к следующему варианту.
        // Это и есть мультиагентный торг в действии.
        for (CookingTask task : tasksToPlan) {
            // Создаём TaskAgent если ещё не существует (первый запуск),
            // или переиспользуем существующий (перепланирование).
            if (!taskIdToAgentId.containsKey(task.getId())) {
                TaskAgent taskAgent = new TaskAgent(
                        task, agentId, sceneAgent, taskRepository, props);
                messageBus.register(taskAgent);
                taskIdToAgentId.put(task.getId(), taskAgent.getAgentId());
            }

            InitTaskPayload payload = new InitTaskPayload(
                    times.notBefore, times.targetEndTime, times.deadline);
            String taskAgentId = taskIdToAgentId.get(task.getId());

            log.debug("{}: отправлен INIT → {} (target={})",
                    agentId, taskAgentId, times.targetEndTime);
            send(taskAgentId, MessageType.INIT, payload);
        }

        // После этого метод завершается. Дальнейшее управление —
        // через входящие сообщения TASK_PLANNED и TASK_FAILED.
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

        courseState.markPlanned(taskId, confirmedEnd);

        // Выравнивание срабатывает ТОЛЬКО если задача вернулась позже
        // текущей стабильной цели (реальный сдвиг, не ASAP-переполнение).
        // ASAP-задачи (confirmedEnd <= stableTarget) цель не двигают.
        boolean genuinelyLater = confirmedEnd != null
                && courseState.stableTargetEndTime != null
                && confirmedEnd.isAfter(courseState.stableTargetEndTime.plusMinutes(1));

        if (!genuinelyLater) {
            // Цель не изменилась — просто проверяем завершение курса
            checkCourseCompletion(courseState);
            return;
        }

        // Реальный сдвиг: обновляем стабильную цель и выравниваем других
        courseState.stableTargetEndTime = confirmedEnd;

        // JIT-выравнивание: если только что запланированная задача задала новый
        // (более поздний) targetEndTime, нужно перепланировать те задачи курса,
        // которые заканчиваются значительно раньше — они «висят» с лишним разрывом.
        CourseTimeParams times = calculateCourseTimes(courseStates.indexOf(courseState));

        List<Long> tasksToReplan = new ArrayList<>();
        /*for (Long plannedId : courseState.plannedTaskIds) {
            if (plannedId == taskId) continue;

            CookingTask pt = taskRepository.findById(plannedId).orElse(null);
            if (pt == null) continue;

            // Не трогаем задачи, которые уже выполняются или выполнены.
            if (pt.getStatus() == CookingTaskStatus.IN_PROGRESS
                    || pt.getStatus() == CookingTaskStatus.DONE) {
                log.debug("{}: JIT-выравнивание: пропускаем задачу #{} в статусе {}",
                        agentId, plannedId, pt.getStatus());
                continue;
            }

            // Перепланируем только если задача заканчивается заметно раньше цели
            // (30-секундный буфер исключает бесконечные микроперепланирования).
            if (pt.getPlannedEndTime() != null
                    && pt.getPlannedEndTime().isBefore(times.targetEndTime.minusSeconds(
                    props.getPlanning().getJitAlignmentBufferSeconds()))) {
                tasksToReplan.add(plannedId);
            }
        }*/

        // Проверяем ВСЕ запланированные задачи курса, включая только что добавленную
        for (Long plannedId : courseState.plannedTaskIds) {
            if (plannedId == taskId) continue;
            CookingTask pt = taskRepository.findById(plannedId).orElse(null);
            if (pt == null) continue;
            if (pt.getStatus() == IN_PROGRESS || pt.getStatus() == DONE) continue;

            if (pt.getPlannedEndTime() != null) {
                long diffMinutes = ChronoUnit.MINUTES.between(
                        pt.getPlannedEndTime(), courseState.stableTargetEndTime);
                if (diffMinutes >= 1) {
                    tasksToReplan.add(plannedId);
                }
            }
        }

        if (!tasksToReplan.isEmpty()) {
            log.info("{}: задача #{} задала новый дедлайн {}. " +
                            "Выравниваем {} ранее запланированных задач.",
                    agentId, taskId, times.targetEndTime, tasksToReplan.size());

            for (Long idToReplan : tasksToReplan) {
                String taId = taskIdToAgentId.get(idToReplan);
                if (taId != null) {
                    // Шаг 1: отправляем CANCEL_AND_REPLAN — TaskAgent освобождает
                    // слоты у повара и оборудования, сбрасывает своё состояние
                    // и ждёт нового INIT.
                    CancelAndReplanBody cancelPayload = new CancelAndReplanBody(
                            idToReplan,
                            times.notBefore,
                            times.targetEndTime,
                            times.deadline
                    );
                    send(taId, MessageType.CANCEL_AND_REPLAN, cancelPayload);

                    // П1-ИЗМЕНЕНИЕ: сразу отправляем INIT вместо добавления в очередь.
                    //
                    // Раньше задача добавлялась в sortedTaskIdsToPlan и получала INIT
                    // только когда до неё «доходила очередь» в planNextTaskInCurrentCourse().
                    // Теперь INIT отправляется немедленно вслед за CANCEL_AND_REPLAN.
                    //
                    // Порядок в MessageBus гарантирует корректность: CANCEL_AND_REPLAN
                    // будет обработан первым (TaskAgent освободит слоты), и только
                    // затем INIT запустит новые переговоры.
                    InitTaskPayload initPayload = new InitTaskPayload(
                            times.notBefore,
                            times.targetEndTime,
                            times.deadline
                    );
                    send(taId, MessageType.INIT, initPayload);
                }

                // Сбрасываем задачу в БД.
                CookingTask pt = taskRepository.findById(idToReplan).orElseThrow();
                pt.setStatus(PENDING);
                pt.setPlannedStartTime(null);
                pt.setPlannedEndTime(null);
                pt.setAssignedCook(null);
                pt.setAssignedEquipmentType(null);
                taskRepository.save(pt);

                // Убираем из запланированных — она снова в процессе торга.
                courseState.plannedTaskIds.remove(idToReplan);

                log.debug("{}: задача #{} отправлена на JIT-перепланирование",
                        agentId, idToReplan);
            }
        }

        // П1-ИЗМЕНЕНИЕ: убран вызов planNextTaskInCurrentCourse().
        //
        // Раньше здесь запускалась следующая задача из очереди. Теперь очереди нет:
        // все задачи уже запущены из planCurrentCourse(). Просто проверяем,
        // завершён ли курс (все задачи получили TASK_PLANNED или TASK_FAILED).
        checkCourseCompletion(courseState);
    }

    private void handleTaskFailed(Message message) {
        long taskId = (Long) message.getBody();

        CourseState courseState = findCourseStateByTaskId(taskId);
        if (courseState == null) return;

        courseState.markFailed(taskId);

        // П1-ИЗМЕНЕНИЕ: убран вызов planNextTaskInCurrentCourse().
        //
        // Провал одной задачи не должен влиять на остальные — они уже ведут
        // переговоры параллельно. Просто фиксируем провал и проверяем завершение курса.
        checkCourseCompletion(courseState);
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
     * Найти индекс первого курса, в котором есть хотя бы одна задача,
     * требующая планирования (PENDING, PLANNED или FAILED).
     *
     * IN_PROGRESS и DONE задачи не считаются: они уже выполняются или выполнены,
     * их слоты зафиксированы.
     *
     * @return индекс первого такого курса, или courseStates.size() если все завершены
     */
    private int findFirstIndexWithPendingTasks() {
        for (int i = 0; i < courseStates.size(); i++) {
            CourseState cs = courseStates.get(i);
            List<CookingTask> tasks = taskRepository.findAllById(cs.taskIds);
            boolean hasPending = tasks.stream().anyMatch(t ->
                    t.getStatus() == PENDING
                            || t.getStatus() == CookingTaskStatus.PLANNED
                            || t.getStatus() == FAILED);
            if (hasPending) return i;
        }
        return courseStates.size(); // все курсы завершены
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
            if (t.getStatus() == PENDING ||
                    t.getStatus() == FAILED ||
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
     *
     * Запускает полное перепланирование всех незавершённых задач заказа,
     * начиная с наиболее раннего курса, который затронут событием или
     * содержит незавершённые задачи.
     *
     * Логика выбора стартового курса:
     *   affectedIndex   — курс задачи, которая вызвала событие
     *   firstPendingIndex — первый курс с незавершёнными задачами
     *   startIndex = min(affectedIndex, firstPendingIndex)
     *
     * Пример: второй курс (основные блюда) уже PLANNED, событие от задачи
     * первого курса (salads, affectedIndex=0). firstPendingIndex=0 или 1.
     * min(0, 0) = 0 → сбрасываем оба курса. Если бы брали только affectedIndex,
     * второй курс остался бы нетронутым с устаревшим расписанием.
     */
    private void handleTaskDelayEvent(Message message) {
        TaskDelayBody body = (TaskDelayBody) message.getBody();
        long taskId = body.getTaskId();

        CourseState affectedCourse = findCourseStateByTaskId(taskId);
        if (affectedCourse == null) {
            log.warn("{}: TASK_DELAY_EVENT для задачи #{} — курс не найден", agentId, taskId);
            return;
        }

        int affectedIndex = courseStates.indexOf(affectedCourse);
        int firstPendingIndex = findFirstIndexWithPendingTasks();

        if (firstPendingIndex >= courseStates.size()) {
            log.debug("{}: TASK_DELAY_EVENT — все задачи завершены, перепланирование не нужно",
                    agentId);
            return;
        }

        int startIndex = Math.min(affectedIndex, firstPendingIndex);

        log.info("{}: сдвиг в курсе {} (задача #{}). Полный сброс с курса {}.",
                agentId, affectedCourse.courseNumber, taskId,
                courseStates.get(startIndex).courseNumber);

        triggerFullReschedule(startIndex);
    }

    /**
     * Сбросить все незавершённые задачи начиная с курса {@code startIndex}
     * и запустить полное перепланирование.
     *
     * Алгоритм:
     *   1. Для каждого курса от startIndex до конца:
     *      — IN_PROGRESS и DONE задачи: фиксируем в plannedTaskIds, не трогаем.
     *      — PLANNED, PENDING, FAILED задачи: отправляем CANCEL_AND_REPLAN,
     *        сбрасываем в PENDING, очищаем время и назначение.
     *   2. Запускаем planCurrentCourse() — дальше система работает в штатном режиме:
     *      задачи планируются, TASK_PLANNED каскадирует к следующему курсу.
     *
     * Почему сбрасываем failedTaskIds:
     *   Провалившаяся задача мешала завершению курса (учитывалась в resolved).
     *   После сброса она получает новый шанс на планирование — ситуация могла
     *   измениться (повар освободился, оборудование починили).
     *
     * @param startIndex индекс курса, с которого начинаем сброс
     */
    private void triggerFullReschedule(int startIndex) {
        if (startIndex >= courseStates.size()) {
            log.debug("{}: triggerFullReschedule: все курсы завершены, ничего не делаем", agentId);
            return;
        }

        // Откатываем currentCourseIndex если нужно
        if (startIndex < currentCourseIndex) {
            currentCourseIndex = startIndex;
        }

        log.info("{}: полный сброс и перепланирование с курса {} (currentCourseIndex={})",
                agentId, courseStates.get(startIndex).courseNumber, currentCourseIndex);

        for (int i = startIndex; i < courseStates.size(); i++) {
            CourseState cs = courseStates.get(i);
            List<CookingTask> tasks = taskRepository.findAllById(cs.taskIds);

            cs.plannedTaskIds.clear();
            cs.failedTaskIds.clear();

            for (CookingTask t : tasks) {
                if (t.getStatus() == IN_PROGRESS || t.getStatus() == DONE) {
                    cs.plannedTaskIds.add(t.getId());
                    continue;
                }
                if (t.getStatus() == PLANNED || t.getStatus() == PENDING || t.getStatus() == FAILED) {

                    // ← Синхронно снимаем слот ДО того, как CANCEL_AND_REPLAN уйдёт в очередь.
                    // Это исключает гонку: старый слот гарантированно снят,
                    // когда новый PLANNING_REQUEST дойдёт до повара.
                    if (t.getAssignedCook() != null) {
                        CookSchedule cookSchedule = sceneAgent.getCookSchedule(
                                t.getAssignedCook().getId());
                        if (cookSchedule != null) {
                            cookSchedule.removeSlotByTaskId(t.getId());
                            log.debug("{}: слот задачи #{} синхронно снят с COOK_{}",
                                    agentId, t.getId(), t.getAssignedCook().getId());
                        }
                    }
                    // Аналогично для оборудования:
                    if (t.getAssignedEquipmentType() != null) {
                        EquipmentTypeSchedule equipSchedule = sceneAgent.getEquipmentTypeSchedule(
                                t.getAssignedEquipmentType());
                        if (equipSchedule != null) {
                            equipSchedule.removeSlotByTaskId(t.getId());
                        }
                    }

                    String taId = taskIdToAgentId.get(t.getId());
                    if (taId != null) {
                        send(taId, MessageType.CANCEL_AND_REPLAN, null);
                    }

                    t.setStatus(PENDING);
                    t.setPlannedStartTime(null);
                    t.setPlannedEndTime(null);
                    t.setAssignedCook(null);
                    t.setAssignedEquipmentType(null);
                    taskRepository.save(t);
                }
            }
        }

        // Запускаем планирование с currentCourseIndex.
        // Если в этом курсе остались только IN_PROGRESS/DONE задачи (tasksToPlan пуст),
        // planCurrentCourse() немедленно каскадирует к следующему курсу через
        // checkCourseCompletion() → onCourseCompleted().
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

        /**
         * Флаг первого планирования этого курса.
         *
         * false → курс планируется впервые. В calculateCourseTimes() добавим
         *         буфер +1 мин, чтобы задачи не оказались «в прошлом» к тому
         *         моменту, когда повара фактически получат их на KDS.
         *
         * true  → курс уже планировался хотя бы раз (сейчас идёт перепланирование
         *         из-за задержки или досрочного завершения). Буфер не нужен —
         *         важна максимальная точность, а не запас.
         *
         * Флаг устанавливается в planCurrentCourse() перед первой рассылкой INIT.
         * Сбрасывается — никогда: при всех последующих перепланированиях этого
         * курса он остаётся true.
         */
        boolean firstPlanningDone = false;

        /** Стабильный целевой дедлайн курса. Обновляется только если задача
         *  подтвердила время позже текущей цели. ASAP-задачи его не двигают. */
        LocalDateTime stableTargetEndTime = null;

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