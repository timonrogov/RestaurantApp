package com.example.restaurant.scheduler.service;

import com.example.restaurant.enums.CookingTaskStatus;
import com.example.restaurant.enums.OrderStatus;
import com.example.restaurant.events.OrderStatusChangedEvent;
import com.example.restaurant.models.CookProfile;
import com.example.restaurant.models.CookingTask;
import com.example.restaurant.models.Equipment;
import com.example.restaurant.models.Order;
import com.example.restaurant.repositories.CookProfileRepository;
import com.example.restaurant.repositories.CookingTaskRepository;
import com.example.restaurant.repositories.EquipmentRepository;
import com.example.restaurant.repositories.OrderRepository;
import com.example.restaurant.scheduler.dispatcher.DispatcherAgent;
import com.example.restaurant.scheduler.dispatcher.MessageBus;
import com.example.restaurant.scheduler.messages.Message;
import com.example.restaurant.scheduler.messages.MessageType;
import com.example.restaurant.scheduler.messages.dto.TaskDelayBody;
import com.example.restaurant.services.OrderService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.restaurant.events.CookingTaskUpdatedEvent;
import org.springframework.context.ApplicationEventPublisher;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Spring-сервис — единственная точка входа в мультиагентную систему планировщика.
 *
 * Выполняет три роли:
 *   1. Инициализация: при старте приложения загружает поваров и оборудование
 *      из БД и передаёт в DispatcherAgent.
 *   2. Мост между Spring-событиями и агентной системой: слушает
 *      OrderStatusChangedEvent и запускает планирование нужных заказов.
 *   3. API для KDS-экрана и административных контроллеров.
 *
 * Паттерн вызова для любого внешнего события:
 *   1. Создать сообщение и положить в очередь через messageBus.deliver()
 *   2. Вызвать messageBus.processAll() — обработать всю очередь до конца
 *   3. Результаты уже в БД (TaskAgent сохранил CookingTask при финализации)
 */
@Service
@RequiredArgsConstructor
public class SchedulerService {

    private static final Logger log = LoggerFactory.getLogger(SchedulerService.class);

    /**
     * Если повар закончил задачу раньше чем на это количество минут — считаем
     * это значимым досрочным завершением и сдвигаем последующие задачи назад.
     * Значение 5 даёт небольшой буфер: мелкое расхождение не вызывает лишних сдвигов.
     */
    private static final int EARLY_FINISH_THRESHOLD_MINUTES = 0;

    private final DispatcherAgent dispatcher;
    private final MessageBus messageBus;
    private final CookProfileRepository cookProfileRepository;
    private final EquipmentRepository equipmentRepository;
    private final CookingTaskRepository cookingTaskRepository;
    private final OrderRepository orderRepository;
    private final OrderService orderService;
    private final ApplicationEventPublisher eventPublisher;

    // -----------------------------------------------------------------------
    // Инициализация при старте
    // -----------------------------------------------------------------------

    /**
     * Инициализировать мультиагентную систему при старте приложения.
     *
     * Загружает всех активных поваров и всё активное оборудование из БД
     * и передаёт в DispatcherAgent для создания агентов.
     *
     * @PostConstruct гарантирует вызов после инжекции всех зависимостей.
     */
    @PostConstruct
    public void init() {
        log.info("SchedulerService: инициализация мультиагентной системы...");

        // ИСПРАВЛЕНИЕ: Грузим ВСЕХ поваров и ВСЁ оборудование.
        // Неактивные агенты создадутся, но будут спать.
        List<CookProfile> allCooks = cookProfileRepository.findAll();
        List<Equipment> allEquipment = equipmentRepository.findAll();

        dispatcher.initialize(allCooks, allEquipment);

        log.info("SchedulerService: система готова. Поваров: {}, единиц оборудования: {}.",
                allCooks.size(), allEquipment.size());
    }

    // -----------------------------------------------------------------------
    // Слушатель Spring-событий
    // -----------------------------------------------------------------------

    /**
     * Реагировать на изменение статуса заказа.
     *
     * Интересуют два случая:
     *   * → COOKING  : заказ принят, запустить планирование
     *   * → CANCELED : заказ отменён, освободить ресурсы
     *
     * @EventListener вызывается Spring синхронно в той же транзакции что
     * и OrderService.confirmOrder(). К этому моменту заказ уже сохранён в БД.
     */
    @EventListener
    public void onOrderStatusChanged(OrderStatusChangedEvent event) {
        if (event.getNewStatus() == OrderStatus.COOKING) {
            log.info("SchedulerService: заказ #{} → COOKING, запускаем планирование",
                    event.getOrderId());
            scheduleOrder(event.getOrderId());

        } else if (event.getNewStatus() == OrderStatus.CANCELED) {
            log.info("SchedulerService: заказ #{} → CANCELED, освобождаем ресурсы",
                    event.getOrderId());
            cancelOrder(event.getOrderId());
        }
    }

    // -----------------------------------------------------------------------
    // Управление заказами
    // -----------------------------------------------------------------------

    /**
     * Запустить планирование заказа.
     *
     * @param orderId ID заказа со статусом COOKING
     */
    @Transactional(readOnly = true)
    public void scheduleOrder(Long orderId) {
        // Загружаем заказ с уже подтянутыми orderItems и dish.
        // JOIN FETCH гарантирует что все данные в памяти до закрытия сессии.
        Order order = orderRepository.findByIdWithItems(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ #" + orderId + " не найден"));

        dispatchAndProcess(MessageType.NEW_ORDER, order);
    }

    /**
     * Отменить заказ — освободить все зарезервированные ресурсы.
     *
     * @param orderId ID отменяемого заказа
     */
    public void cancelOrder(Long orderId) {
        dispatchAndProcess(MessageType.ORDER_CANCELLED, orderId);
    }

    // -----------------------------------------------------------------------
    // Управление поварами
    // -----------------------------------------------------------------------

    /**
     * Пометить повара как недоступного.
     * Его задачи будут перепланированы на других поваров.
     *
     * @param cookProfileId ID записи CookProfile
     */
    public void markCookUnavailable(Long cookProfileId) {
        log.info("SchedulerService: повар {} недоступен", cookProfileId);
        dispatchAndProcess(MessageType.COOK_UNAVAILABLE, cookProfileId);
    }

    /**
     * Пометить повара как снова доступного.
     *
     * @param cookProfileId ID записи CookProfile
     */
    public void markCookAvailable(Long cookProfileId) {
        log.info("SchedulerService: повар {} снова доступен", cookProfileId);
        dispatchAndProcess(MessageType.COOK_AVAILABLE, cookProfileId);
    }

    public void addOrUpdateCook(CookProfile profile) {
        log.info("SchedulerService: добавлен/обновлен повар {}", profile.getId());
        dispatchAndProcess(MessageType.COOK_CREATED, profile);
    }

    // -----------------------------------------------------------------------
    // Управление оборудованием
    // -----------------------------------------------------------------------

    /**
     * Зарегистрировать поломку единицы оборудования.
     *
     * @param equipmentId ID записи Equipment
     */
    public void markEquipmentBroken(Long equipmentId) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new RuntimeException("Оборудование #" + equipmentId + " не найдено"));
        log.info("SchedulerService: оборудование '{}' (тип '{}') сломано",
                equipment.getName(), equipment.getEquipmentType());
        dispatchAndProcess(MessageType.EQUIPMENT_BROKEN, equipment);
    }

    /**
     * Зарегистрировать починку единицы оборудования.
     *
     * @param equipmentId ID записи Equipment
     */
    public void markEquipmentFixed(Long equipmentId) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new RuntimeException("Оборудование #" + equipmentId + " не найдено"));
        log.info("SchedulerService: оборудование '{}' (тип '{}') починено",
                equipment.getName(), equipment.getEquipmentType());
        dispatchAndProcess(MessageType.EQUIPMENT_FIXED, equipment);
    }

    public void addEquipment(Equipment equipment) {
        log.info("SchedulerService: добавлено новое оборудование {}", equipment.getName());
        dispatchAndProcess(MessageType.EQUIPMENT_CREATED, equipment);
    }

    // -----------------------------------------------------------------------
    // Действия повара на KDS-экране
    // -----------------------------------------------------------------------

    /**
     * Повар нажал «Начать» на KDS.
     * Переводит задачу в IN_PROGRESS и фиксирует actualStartTime.
     * Не требует processAll() — это простое обновление в БД.
     *
     * @param taskId ID задачи CookingTask
     */
    @Transactional
    public void markTaskStarted(Long taskId) {
        CookingTask task = getTaskOrThrow(taskId);
        LocalDateTime now = LocalDateTime.now();
        int duration = task.getTemplate().getDurationMinutes();
        int shiftMinutes = (int) ChronoUnit.MINUTES.between(task.getPlannedStartTime(), now);

        task.setStatus(CookingTaskStatus.IN_PROGRESS);
        task.setActualStartTime(now);
        task.setLocalOverdue(false);
        task.setPlannedStartTime(now);
        task.setPlannedEndTime(now.plusMinutes(duration));
        cookingTaskRepository.save(task);

        // ИСПРАВЛЕНИЕ П2: синхронизируем ScheduleSlot в памяти с новым plannedEndTime
        if (task.getAssignedCook() != null) {
            dispatcher.updateCookSlotEndTime(taskId,
                    task.getAssignedCook().getId(),
                    task.getPlannedEndTime());
        }

        // ИСПРАВЛЕНИЕ П2: публикуем TASK_DELAY_EVENT при ЛЮБОМ ненулевом сдвиге
        // (включая отрицательный — ранний старт)
        if (shiftMinutes != 0) {
            log.info("SchedulerService: задача #{} начата с {} мин ({}). Пересчёт расписания.",
                    taskId, Math.abs(shiftMinutes), shiftMinutes > 0 ? "опоздание" : "досрочно");
            TaskDelayBody delayBody = new TaskDelayBody(taskId, shiftMinutes, "Начало приготовления");
            dispatchAndProcess(MessageType.TASK_DELAY_EVENT, delayBody);
        }

        eventPublisher.publishEvent(new CookingTaskUpdatedEvent(this, task.getAssignedCook().getId()));
    }

    /**
     * Повар нажал «Готово» на KDS.
     *
     * Логика:
     *   1. Перевести задачу в DONE, зафиксировать actualEndTime.
     *   2. Освободить ресурсы (через TASK_DONE_EVENT).
     *   3. Если повар закончил значительно раньше плана — сдвинуть последующие
     *      задачи на более раннее время (через TASK_DELAY_EVENT с отрицательным числом).
     *   4. Проверить: все ли задачи заказа выполнены? Если да — перевести заказ в READY.
     */
    @Transactional
    public void markTaskDone(Long taskId) {
        CookingTask task = getTaskOrThrow(taskId);
        LocalDateTime actualEnd = LocalDateTime.now();

        task.setStatus(CookingTaskStatus.DONE);
        task.setActualEndTime(actualEnd);
        task.setLocalOverdue(false);
        cookingTaskRepository.save(task);

        log.info("SchedulerService: задача #{} → DONE (фактически в {})", taskId, actualEnd);

        // Шаг 2: освободить ресурсы
        dispatchAndProcess(MessageType.TASK_DONE_EVENT, taskId);

        // Шаг 3: если закончил значительно раньше — сдвинуть следующие задачи
        if (task.getPlannedEndTime() != null) {
            long earlyMinutes = ChronoUnit.MINUTES.between(actualEnd, task.getPlannedEndTime());
            if (earlyMinutes > EARLY_FINISH_THRESHOLD_MINUTES) {
                log.info("SchedulerService: задача #{} завершена на {} мин раньше — " +
                        "сдвигаем последующие задачи", taskId, earlyMinutes);
                // Отрицательное значение = сдвиг назад
                TaskDelayBody earlyBody = new TaskDelayBody(taskId, (int) -earlyMinutes, null);
                dispatchAndProcess(MessageType.TASK_DELAY_EVENT, earlyBody);
            }
        }

        // Шаг 4: проверить завершённость заказа
        Long orderId = task.getOrderItem().getOrder().getId();
        checkAndMarkOrderReady(orderId);
        eventPublisher.publishEvent(new CookingTaskUpdatedEvent(this, null)); // null = затронуты все повара
    }

    /**
     * Повар сообщил о задержке через KDS (ручное сообщение).
     * Сохраняет причину в БД и запускает сдвиг расписания.
     */
    @Transactional
    public void reportDelay(Long taskId, int delayMinutes, String reason) {
        CookingTask task = getTaskOrThrow(taskId);
        if (reason != null && !reason.isBlank()) {
            task.setDelayReason(reason);
        }
        if (task.getPlannedEndTime() != null) {
            task.setPlannedEndTime(task.getPlannedEndTime().plusMinutes(delayMinutes));
            task.setLocalOverdue(false);
        }
        cookingTaskRepository.save(task);
        log.info("SchedulerService: задача #{} задерживается на {} мин. Причина: {}",
                taskId, delayMinutes, reason);

        TaskDelayBody body = new TaskDelayBody(taskId, delayMinutes, reason);
        dispatchAndProcess(MessageType.TASK_DELAY_EVENT, body);
        eventPublisher.publishEvent(new CookingTaskUpdatedEvent(this, null));
    }

    // -----------------------------------------------------------------------
    // Чтение данных для KDS и администратора
    // -----------------------------------------------------------------------

    /**
     * Получить задачи конкретного повара для KDS-экрана.
     * Возвращает PLANNED + IN_PROGRESS, отсортированные:
     * IN_PROGRESS первее, затем по plannedStartTime по возрастанию.
     *
     * @param cookProfileId ID профиля повара
     */
    public List<CookingTask> getTasksForCook(Long cookProfileId) {
        // Определяем начало сегодняшнего дня (00:00:00)
        LocalDateTime startOfDay = LocalDateTime.now().with(LocalTime.MIN);

        // Используем наш новый метод репозитория
        List<CookingTask> tasks = cookingTaskRepository.findActiveAndRecentlyDoneTasks(
                cookProfileId,
                startOfDay
        );

        // Сортировка остается прежней: сначала то, что в работе, потом по времени
        return tasks.stream()
                .sorted(Comparator
                        .comparingInt((CookingTask t) ->
                                t.getStatus() == CookingTaskStatus.IN_PROGRESS ? 0 : 1)
                        .thenComparing(t ->
                                t.getPlannedStartTime() != null
                                        ? t.getPlannedStartTime()
                                        : LocalDateTime.MAX))
                .toList();
    }

    /**
     * Получить все активные задачи — для сводного экрана администратора.
     */
    public List<CookingTask> getAllActiveTasksSummary() {
        List<CookingTask> planned  = cookingTaskRepository.findByStatus(CookingTaskStatus.PLANNED);
        List<CookingTask> inProgress = cookingTaskRepository.findByStatus(CookingTaskStatus.IN_PROGRESS);

        return java.util.stream.Stream.concat(inProgress.stream(), planned.stream()).toList();
    }

    /**
     * Автоматическое обнаружение задержек — строго в начале каждой минуты.
     * Крон только фиксирует факт задержки в БД и публикует событие.
     * Всю волну перепланирований берет на себя мультиагентная система.
     */
    @Scheduled(cron = "0 * * * * *")
    @Transactional
    public void detectAndReportDelays() {
        LocalDateTime now = LocalDateTime.now();
        boolean hasChanges = false;

        // 1. Задержки выполнения: IN_PROGRESS с просроченным endTime
        List<CookingTask> overdueExecution = cookingTaskRepository.findOverdueInProgressTasks(now);
        if (!overdueExecution.isEmpty()) {
            log.info("SchedulerService: {} задач задерживают выполнение", overdueExecution.size());

            // ИСПРАВЛЕНО БАГ-04: группируем по заказу, берём максимальную задержку
            Map<Long, CookingTask> worstTaskByOrder = new java.util.HashMap<>();
            Map<Long, Integer> maxDelayByOrder = new java.util.HashMap<>();

            for (CookingTask task : overdueExecution) {
                try {
                    LocalDateTime oldEndTime = task.getPlannedEndTime();
                    int delayMinutes = (int) ChronoUnit.MINUTES.between(oldEndTime, now.plusMinutes(1));
                    if (delayMinutes <= 0) delayMinutes = 1;

                    task.setLocalOverdue(true);
                    task.setPlannedEndTime(oldEndTime.plusMinutes(delayMinutes));
                    cookingTaskRepository.save(task);
                    hasChanges = true;

                    // Группируем по orderId
                    long orderId = task.getOrderItem().getOrder().getId();
                    int currentMax = maxDelayByOrder.getOrDefault(orderId, 0);
                    if (delayMinutes > currentMax) {
                        maxDelayByOrder.put(orderId, delayMinutes);
                        worstTaskByOrder.put(orderId, task);
                    }
                } catch (Exception e) {
                    log.error("SchedulerService: ошибка при обработке задержки выполнения задачи #{}: {}",
                            task.getId(), e.getMessage(), e);
                }
            }

            // Публикуем ОДНО событие на заказ
            for (Map.Entry<Long, CookingTask> entry : worstTaskByOrder.entrySet()) {
                int delay = maxDelayByOrder.get(entry.getKey());
                CookingTask worstTask = entry.getValue();
                TaskDelayBody body = new TaskDelayBody(
                        worstTask.getId(), delay,
                        "auto: задача просрочена на " + delay + " мин.");
                dispatchAndProcess(MessageType.TASK_DELAY_EVENT, body);
                log.info("SchedulerService: заказ #{} — публикуем одно TASK_DELAY_EVENT (задача #{}, задержка {} мин)",
                        entry.getKey(), worstTask.getId(), delay);
            }
        }

        // 2. Задержки начала: PLANNED с просроченным startTime
        List<CookingTask> overdueStart = cookingTaskRepository.findOverduePlannedTasks(now);
        if (!overdueStart.isEmpty()) {
            log.info("SchedulerService: {} задач задерживают начало", overdueStart.size());

            // ИСПРАВЛЕНО БАГ-04: та же группировка по заказу
            Map<Long, CookingTask> worstTaskByOrder = new java.util.HashMap<>();
            Map<Long, Integer> maxDelayByOrder = new java.util.HashMap<>();

            for (CookingTask task : overdueStart) {
                try {
                    LocalDateTime oldStartTime = task.getPlannedStartTime();
                    int delayMinutes = (int) ChronoUnit.MINUTES.between(oldStartTime, now.plusMinutes(1));
                    if (delayMinutes <= 0) delayMinutes = 1;

                    task.setLocalOverdue(true);
                    task.setPlannedStartTime(oldStartTime.plusMinutes(delayMinutes));
                    task.setPlannedEndTime(task.getPlannedEndTime().plusMinutes(delayMinutes));
                    cookingTaskRepository.save(task);
                    hasChanges = true;

                    long orderId = task.getOrderItem().getOrder().getId();
                    int currentMax = maxDelayByOrder.getOrDefault(orderId, 0);
                    if (delayMinutes > currentMax) {
                        maxDelayByOrder.put(orderId, delayMinutes);
                        worstTaskByOrder.put(orderId, task);
                    }
                } catch (Exception e) {
                    log.error("SchedulerService: ошибка при обработке задержки начала задачи #{}: {}",
                            task.getId(), e.getMessage(), e);
                }
            }

            // Публикуем ОДНО событие на заказ
            for (Map.Entry<Long, CookingTask> entry : worstTaskByOrder.entrySet()) {
                int delay = maxDelayByOrder.get(entry.getKey());
                CookingTask worstTask = entry.getValue();
                TaskDelayBody body = new TaskDelayBody(
                        worstTask.getId(), delay,
                        "auto: начало просрочено на " + delay + " мин.");
                dispatchAndProcess(MessageType.TASK_DELAY_EVENT, body);
                log.info("SchedulerService: заказ #{} — публикуем одно TASK_DELAY_EVENT (задача #{}, задержка {} мин)",
                        entry.getKey(), worstTask.getId(), delay);
            }
        }

        if (hasChanges) {
            eventPublisher.publishEvent(new CookingTaskUpdatedEvent(this, null));
        }
    }

    // -----------------------------------------------------------------------
    // Вспомогательные методы
    // -----------------------------------------------------------------------

    /**
     * Положить сообщение в очередь и обработать всю очередь до конца.
     * Единый паттерн для всех методов этого класса.
     */
    private void dispatchAndProcess(MessageType type, Object body) {
        Message message = new Message(type, body, "SCHEDULER_SERVICE");
        messageBus.deliver(DispatcherAgent.AGENT_ID, message);
        messageBus.processAll();
    }

    private CookingTask getTaskOrThrow(Long taskId) {
        return cookingTaskRepository.findById(taskId)
                .orElseThrow(() -> new RuntimeException("Задача #" + taskId + " не найдена"));
    }

    /**
     * Периодически проверяет БД на наличие заказов в статусе COOKING,
     * для которых ещё не запущено планирование.
     *
     * Это необходимо потому что customer-app и staff-app — разные Spring-контексты.
     * Событие, опубликованное в customer-app, не достигает слушателей в staff-app.
     * Опрос БД решает эту проблему без межпроцессного взаимодействия.
     *
     * fixedDelay = 10 секунд: следующая проверка начинается через 10 сек ПОСЛЕ
     * окончания предыдущей, что исключает параллельный запуск.
     */
    @Scheduled(fixedDelay = 10_000)
    public void schedulePendingOrders() {
        List<Long> unscheduledOrderIds = cookingTaskRepository.findCookingOrderIdsWithoutTasks();

        if (!unscheduledOrderIds.isEmpty()) {
            log.info("SchedulerService: обнаружено {} незапланированных заказов: {}",
                    unscheduledOrderIds.size(), unscheduledOrderIds);
            for (Long orderId : unscheduledOrderIds) {
                try {
                    scheduleOrder(orderId);
                } catch (Exception e) {
                    log.error("SchedulerService: ошибка планирования заказа #{}: {}", orderId, e.getMessage(), e);
                }
            }
        }
    }

    /**
     * Проверить: все ли задачи заказа завершены?
     * Если да — перевести заказ из COOKING в READY.
     *
     * Вызывается после каждого нажатия «Готово» на KDS.
     */
    private void checkAndMarkOrderReady(Long orderId) {
        long unfinished = cookingTaskRepository.countUnfinishedTasksByOrderId(orderId);
        if (unfinished == 0) {
            orderRepository.findById(orderId).ifPresent(order -> {
                if (order.getStatus() == OrderStatus.COOKING) {
                    orderService.changeOrderStatus(orderId, "READY");
                    log.info("SchedulerService: заказ #{} → READY (все задачи выполнены)", orderId);
                }
            });
        }
    }
}