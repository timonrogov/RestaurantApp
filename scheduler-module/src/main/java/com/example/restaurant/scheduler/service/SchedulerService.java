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
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

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

    private final DispatcherAgent dispatcher;
    private final MessageBus messageBus;
    private final CookProfileRepository cookProfileRepository;
    private final EquipmentRepository equipmentRepository;
    private final CookingTaskRepository cookingTaskRepository;
    private final OrderRepository orderRepository;

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

        List<CookProfile> activeCooks = cookProfileRepository.findByIsActiveTrue();
        List<Equipment> activeEquipment = equipmentRepository.findByIsActiveTrue();

        dispatcher.initialize(activeCooks, activeEquipment);

        log.info("SchedulerService: система готова. Поваров: {}, единиц оборудования: {}.",
                activeCooks.size(), activeEquipment.size());
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
        task.setStatus(CookingTaskStatus.IN_PROGRESS);
        task.setActualStartTime(LocalDateTime.now());
        cookingTaskRepository.save(task);
        log.debug("SchedulerService: задача #{} → IN_PROGRESS", taskId);
    }

    /**
     * Повар нажал «Готово» на KDS.
     * Переводит задачу в DONE и фиксирует actualEndTime.
     * Отправляет TASK_DONE_EVENT — OrderAgent может пересчитать notBefore
     * следующего курса если фактическое время сильно отличается от планового.
     *
     * @param taskId ID задачи CookingTask
     */
    @Transactional
    public void markTaskDone(Long taskId) {
        CookingTask task = getTaskOrThrow(taskId);
        task.setStatus(CookingTaskStatus.DONE);
        task.setActualEndTime(LocalDateTime.now());
        cookingTaskRepository.save(task);
        log.info("SchedulerService: задача #{} → DONE (фактически в {})",
                taskId, task.getActualEndTime());

        dispatchAndProcess(MessageType.TASK_DONE_EVENT, taskId);
    }

    /**
     * Повар сообщил о задержке через KDS.
     * Сохраняет причину и уведомляет OrderAgent для пересчёта зависимых задач.
     *
     * @param taskId       ID задачи CookingTask
     * @param delayMinutes на сколько минут задерживается
     * @param reason       причина (может быть null)
     */
    @Transactional
    public void reportDelay(Long taskId, int delayMinutes, String reason) {
        CookingTask task = getTaskOrThrow(taskId);
        task.setDelayReason(reason);
        cookingTaskRepository.save(task);
        log.info("SchedulerService: задача #{} задерживается на {} мин. Причина: {}",
                taskId, delayMinutes, reason);

        TaskDelayBody body = new TaskDelayBody(taskId, delayMinutes, reason);
        dispatchAndProcess(MessageType.TASK_DELAY_EVENT, body);
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
        List<CookingTask> tasks = cookingTaskRepository.findByAssignedCookIdAndStatusIn(
                cookProfileId,
                // ИСПРАВЛЕНИЕ: Добавили CookingTaskStatus.DONE в список
                List.of(CookingTaskStatus.PLANNED, CookingTaskStatus.IN_PROGRESS, CookingTaskStatus.DONE)
        );

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
}