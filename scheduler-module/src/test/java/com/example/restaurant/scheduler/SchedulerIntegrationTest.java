package com.example.restaurant.scheduler;

import com.example.restaurant.enums.CookSpecialization;
import com.example.restaurant.enums.CookingTaskStatus;
import com.example.restaurant.models.*;
import com.example.restaurant.repositories.*;
import com.example.restaurant.scheduler.agents.*;
import com.example.restaurant.scheduler.config.SchedulerProperties;
import com.example.restaurant.scheduler.agents.DispatcherAgent;
import com.example.restaurant.scheduler.messages.MessageBus;
import com.example.restaurant.scheduler.messages.NegotiationFileLogger;
import com.example.restaurant.scheduler.messages.Message;
import com.example.restaurant.scheduler.messages.MessageType;
import com.example.restaurant.scheduler.schedule.CookSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Интеграционные тесты полного цикла переговоров планировщика.
 *
 * Используем Mockito для репозиториев (не нужна реальная БД).
 * Система агентов работает полностью в памяти через MessageBus.
 *
 * Для запуска нужна зависимость: mockito-core в pom.xml scheduler-module.
 */
class SchedulerIntegrationTest {

    // -----------------------------------------------------------------------
    // Инфраструктура
    // -----------------------------------------------------------------------

    private MessageBus messageBus;
    private SceneAgent sceneAgent;
    private DispatcherAgent dispatcher;

    private CookingTaskRepository taskRepository;
    private CookingTaskTemplateRepository templateRepository;
    private OrderCourseRepository orderCourseRepository;
    private OrderRepository orderRepository;
    private NegotiationFileLogger fileLogger;

    // Фиксированное время для предсказуемых тестов
    private final LocalDateTime NOW = LocalDateTime.of(2025, 1, 1, 12, 0);

    private Map<Long, CookingTask> fakeTaskDb;
    private long taskIdCounter;

    private SchedulerProperties props;

    @BeforeEach
    void setUp() {
        // 1. Создаём шину сообщений
        props = SchedulerProperties.defaults();
        messageBus = new MessageBus(fileLogger, props);

        // 2. Мокируем все репозитории (Создаем их!)
        taskRepository       = mock(CookingTaskRepository.class);
        templateRepository   = mock(CookingTaskTemplateRepository.class);
        orderCourseRepository = mock(OrderCourseRepository.class);
        orderRepository      = mock(OrderRepository.class);

        // 3. Инициализируем нашу фейковую БД
        fakeTaskDb = new HashMap<>();
        taskIdCounter = 1L;

        // 4. Учим taskRepository сохранять в фейковую БД
        when(taskRepository.save(any(CookingTask.class))).thenAnswer(invocation -> {
            CookingTask task = invocation.getArgument(0);

            // Если это новая задача (без ID), выдаем ей ID
            if (task.getId() == null) {
                try {
                    var idField = CookingTask.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(task, taskIdCounter++);
                } catch (Exception ignored) {}
            }

            // Сохраняем в нашу фейковую мапу
            fakeTaskDb.put(task.getId(), task);
            return task;
        });

        // 5. Учим taskRepository искать по списку ID
        when(taskRepository.findAllById(any())).thenAnswer(invocation -> {
            Iterable<Long> ids = invocation.getArgument(0);
            List<CookingTask> result = new ArrayList<>();
            for (Long id : ids) {
                if (fakeTaskDb.containsKey(id)) {
                    result.add(fakeTaskDb.get(id));
                }
            }
            return result;
        });

        // 6. Учим taskRepository отдавать всё (на всякий случай)
        when(taskRepository.findAll()).thenAnswer(invocation ->
                new ArrayList<>(fakeTaskDb.values())
        );

        // 7. Остальные базовые настройки
        when(orderCourseRepository.findByOrderIdOrderByCourseNumberAsc(anyLong()))
                .thenReturn(List.of());

        // 8. Создаём и регистрируем DispatcherAgent
        dispatcher = new DispatcherAgent(messageBus, taskRepository, templateRepository,
                orderCourseRepository, orderRepository, props);
        messageBus.register(dispatcher);
    }

    // -----------------------------------------------------------------------
    // Сценарий 1: счастливый путь
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Сценарий 1: 1 повар + 1 задача → задача запланирована")
    void scenario1_happyPath_taskIsPlanned() {
        // Arrange: 1 повар UNIVERSAL
        CookProfile cookProfile = buildCookProfile(1L, CookSpecialization.UNIVERSAL);
        CookSchedule cookSchedule = new CookSchedule(1L);
        CookAgent cookAgent = new CookAgent(cookProfile, cookSchedule);
        messageBus.register(cookAgent);

        sceneAgent = new SceneAgent();
        messageBus.register(sceneAgent);
        sceneAgent.registerCookAgent(cookAgent, cookSchedule);

        // 1 шаблон без оборудования, 10 минут
        CookingTaskTemplate template = buildTemplate(1L, 1, "Приготовление", 10,
                CookSpecialization.UNIVERSAL, null);
        Dish dish = buildDish(1L, "Борщ");
        OrderItem orderItem = buildOrderItem(1L, dish, 1);
        Order order = buildOrder(1L, List.of(orderItem));

        when(templateRepository.findByDishIdOrderByStepNumberAsc(1L))
                .thenReturn(List.of(template));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        // Act
        OrderAgent orderAgent = new OrderAgent(order, sceneAgent, taskRepository,
                templateRepository, orderCourseRepository, props);
        messageBus.register(orderAgent);

        dispatcher.initialize(List.of(cookProfile), List.of());
        messageBus.deliver(dispatcher.getAgentId(), new Message(MessageType.NEW_ORDER, order, "TEST"));
        messageBus.processAll();

        // Assert: задача сохранена со статусом PLANNED
        verify(taskRepository, atLeastOnce()).save(argThat(task ->
                task.getStatus() == CookingTaskStatus.PLANNED
                        && task.getAssignedCook() != null
                        && task.getPlannedStartTime() != null
        ));
    }

    // -----------------------------------------------------------------------
    // Сценарий 2: предпочтение менее загруженного повара
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Сценарий 2: 2 повара, один занят → задача идёт к свободному")
    void scenario2_preferLessLoadedCook() {
        CookProfile cook1 = buildCookProfile(1L, CookSpecialization.UNIVERSAL);
        CookProfile cook2 = buildCookProfile(2L, CookSpecialization.UNIVERSAL);

        // 1. Пусть Диспетчер сам всё создаст
        dispatcher.initialize(List.of(cook1, cook2), List.of());

        // 2. Достаем расписание 1-го повара из Сцены и "забиваем" его на час вперед
        CookSchedule schedule1 = dispatcher.getSceneAgent().getCookSchedule(1L);
        schedule1.addSlot(new com.example.restaurant.scheduler.schedule.ScheduleSlot(
                999L, 999L, LocalDateTime.now(), LocalDateTime.now().plusMinutes(60), null));

        CookingTaskTemplate template = buildTemplate(1L, 1, "Приготовление", 15, CookSpecialization.UNIVERSAL, null);
        Dish dish = buildDish(1L, "Салат");
        OrderItem orderItem = buildOrderItem(1L, dish, 1);
        Order order = buildOrder(1L, List.of(orderItem));

        when(templateRepository.findByDishIdOrderByStepNumberAsc(1L)).thenReturn(List.of(template));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        // 3. Act: Просто кидаем NEW_ORDER в шину (Диспетчер сам создаст OrderAgent)
        messageBus.deliver(DispatcherAgent.AGENT_ID, new Message(MessageType.NEW_ORDER, order, "TEST"));
        messageBus.processAll();

        // Assert: задача назначена свободному повару (cook2, id=2L)
        verify(taskRepository, atLeastOnce()).save(argThat(task ->
                task.getStatus() == CookingTaskStatus.PLANNED
                        && task.getAssignedCook() != null
                        && task.getAssignedCook().getId() == 2L
        ));
    }

    // -----------------------------------------------------------------------
    // Сценарий 3: синхронизация курсов
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Сценарий 3: 2 курса с syncGap=15 мин → второй курс начинается позже")
    void scenario3_courseSync_secondCourseStartsAfterGap() {
        CookProfile cook = buildCookProfile(1L, CookSpecialization.UNIVERSAL);
        dispatcher.initialize(List.of(cook), List.of());

        CookingTaskTemplate tmpl1 = buildTemplate(1L, 1, "Подача закуски", 10, CookSpecialization.UNIVERSAL, null);
        CookingTaskTemplate tmpl2 = buildTemplate(2L, 1, "Подача основного", 15, CookSpecialization.UNIVERSAL, null);

        Dish dish1 = buildDish(1L, "Салат Цезарь");
        Dish dish2 = buildDish(2L, "Стейк");

        OrderItem item1 = buildOrderItem(1L, dish1, 1); // курс 1
        OrderItem item2 = buildOrderItem(2L, dish2, 2); // курс 2

        Order order = buildOrder(1L, List.of(item1, item2));

        when(templateRepository.findByDishIdOrderByStepNumberAsc(1L)).thenReturn(List.of(tmpl1));
        when(templateRepository.findByDishIdOrderByStepNumberAsc(2L)).thenReturn(List.of(tmpl2));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order)); // ВАЖНО: не забываем мок для заказа

        OrderCourse course1 = buildOrderCourse(1L, order, 1, 0);
        OrderCourse course2 = buildOrderCourse(2L, order, 2, 15);
        when(orderCourseRepository.findByOrderIdOrderByCourseNumberAsc(1L))
                .thenReturn(List.of(course1, course2));

        // Act
        messageBus.deliver(DispatcherAgent.AGENT_ID, new Message(MessageType.NEW_ORDER, order, "TEST"));
        messageBus.processAll();

        // Assert
        var savedTasks = org.mockito.ArgumentCaptor.forClass(CookingTask.class);
        verify(taskRepository, atLeast(2)).save(savedTasks.capture());

        List<CookingTask> planned = savedTasks.getAllValues().stream()
                .filter(t -> t.getStatus() == CookingTaskStatus.PLANNED && t.getPlannedStartTime() != null)
                .toList();

        assertThat(planned).hasSizeGreaterThanOrEqualTo(2);

        LocalDateTime course1End = planned.stream()
                .filter(t -> t.getOrderItem().getCourseNumber() == 1)
                .map(CookingTask::getPlannedEndTime)
                .max(LocalDateTime::compareTo).orElseThrow();

        LocalDateTime course2Start = planned.stream()
                .filter(t -> t.getOrderItem().getCourseNumber() == 2)
                .map(CookingTask::getPlannedStartTime)
                .min(LocalDateTime::compareTo).orElseThrow();

        assertThat(course2Start).isAfterOrEqualTo(course1End.plusMinutes(15));
    }

    // -----------------------------------------------------------------------
    // Сценарий 4: адаптация — повар стал недоступен
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("Сценарий 4: повар недоступен → задача перепланируется на другого")
    void scenario4_cookUnavailable_taskReplanned() {
        CookProfile cook1 = buildCookProfile(1L, CookSpecialization.UNIVERSAL);
        CookProfile cook2 = buildCookProfile(2L, CookSpecialization.UNIVERSAL);

        dispatcher.initialize(List.of(cook1, cook2), List.of());

        CookingTaskTemplate template = buildTemplate(1L, 1, "Готовка", 10, CookSpecialization.UNIVERSAL, null);
        Dish dish = buildDish(1L, "Суп");
        OrderItem item = buildOrderItem(1L, dish, 1);
        Order order = buildOrder(1L, List.of(item));

        when(templateRepository.findByDishIdOrderByStepNumberAsc(1L)).thenReturn(List.of(template));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        // Act 1: Планируем заказ
        messageBus.deliver(DispatcherAgent.AGENT_ID, new Message(MessageType.NEW_ORDER, order, "TEST"));
        messageBus.processAll();

        // Узнаем, кому назначилась задача (cook1 или cook2)
        var captor = org.mockito.ArgumentCaptor.forClass(CookingTask.class);
        verify(taskRepository, atLeastOnce()).save(captor.capture());

        long assignedCookId = captor.getValue().getAssignedCook().getId();
        long otherCookId = (assignedCookId == 1L) ? 2L : 1L; // Это тот повар, который останется

        // Act 2: Повар, взявший задачу, уходит домой (имитируем сигнал от админа)
        messageBus.deliver(DispatcherAgent.AGENT_ID, new Message(MessageType.COOK_UNAVAILABLE, assignedCookId, "TEST"));
        messageBus.processAll();

        // Assert: задача перепланировалась на оставшегося повара
        verify(taskRepository, atLeastOnce()).save(argThat(task ->
                task.getStatus() == CookingTaskStatus.PLANNED
                        && task.getAssignedCook() != null
                        && task.getAssignedCook().getId() == otherCookId
        ));
    }

    @Test
    @DisplayName("Сценарий 5: quantity=5, portionsPerSlot=2 → 3 задачи, все запланированы")
    void scenario5_portionsPerSlot_createsCorrectBatchCount() {
        // Arrange: 2 повара (чтобы 3 задачи могли распределиться)
        CookProfile cook1 = buildCookProfile(1L, CookSpecialization.UNIVERSAL);
        CookProfile cook2 = buildCookProfile(2L, CookSpecialization.UNIVERSAL);
        dispatcher.initialize(List.of(cook1, cook2), List.of());

        // Шаблон: 2 порции за раз (portionsPerSlot=2), 15 мин
        CookingTaskTemplate template = buildTemplate(
                1L, 1, "Жарка стейков", 15,
                CookSpecialization.UNIVERSAL, null,
                2  // portionsPerSlot=2
        );

        Dish dish = buildDish(1L, "Стейк рибай");
        // Заказано 5 стейков
        OrderItem item = buildOrderItem(1L, dish, 1, 5);
        Order order = buildOrder(1L, List.of(item));

        when(templateRepository.findByDishIdOrderByStepNumberAsc(1L))
                .thenReturn(List.of(template));
        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        // Act
        messageBus.deliver(DispatcherAgent.AGENT_ID,
                new Message(MessageType.NEW_ORDER, order, "TEST"));
        messageBus.processAll();

        // Assert 1: создано ровно 3 задачи (ceil(5/2) = 3)
        List<CookingTask> allTasks = new ArrayList<>(fakeTaskDb.values());
        assertThat(allTasks).hasSize(3);

        // Assert 2: portionCount корректен: [2, 2, 1]
        List<Integer> portionCounts = allTasks.stream()
                .map(CookingTask::getPortionCount)
                .sorted()
                .toList();
        assertThat(portionCounts).containsExactly(1, 2, 2);

        // Assert 3: все задачи успешно запланированы
        long plannedCount = allTasks.stream()
                .filter(t -> t.getStatus() == CookingTaskStatus.PLANNED)
                .count();
        assertThat(plannedCount).isEqualTo(3);

        // Assert 4: все задачи назначены на поваров
        boolean allAssigned = allTasks.stream()
                .allMatch(t -> t.getAssignedCook() != null);
        assertThat(allAssigned).isTrue();
    }

    // -----------------------------------------------------------------------
    // Вспомогательные фабричные методы
    // -----------------------------------------------------------------------

    private CookProfile buildCookProfile(Long id, CookSpecialization spec) {
        Employee employee = new Employee();
        try {
            var idField = Employee.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(employee, id);
        } catch (Exception ignored) {}

        CookProfile profile = new CookProfile();
        try {
            var idField = CookProfile.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(profile, id);
        } catch (Exception ignored) {}
        profile.setEmployee(employee);
        profile.setSpecialization(spec);
        profile.setActive(true);
        return profile;
    }

    private CookingTaskTemplate buildTemplate(Long id, int step, String name,
                                              int duration, CookSpecialization spec,
                                              String equipType) {
        return buildTemplate(id, step, name, duration, spec, equipType, 1);
    }

    private CookingTaskTemplate buildTemplate(Long id, int step, String name,
                                              int duration, CookSpecialization spec,
                                              String equipType, int portionsPerSlot) {
        CookingTaskTemplate t = new CookingTaskTemplate();
        try {
            var f = CookingTaskTemplate.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(t, id);
        } catch (Exception ignored) {}
        t.setStepNumber(step);
        t.setStepName(name);
        t.setDurationMinutes(duration);
        t.setRequiredSpecialization(spec);
        t.setRequiredEquipmentType(equipType);
        t.setPortionsPerSlot(portionsPerSlot);
        return t;
    }

    private Dish buildDish(Long id, String name) {
        Dish dish = new Dish();
        try {
            var f = Dish.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(dish, id);
        } catch (Exception ignored) {}
        dish.setName(name);
        return dish;
    }

    private OrderItem buildOrderItem(Long id, Dish dish, int courseNumber) {
        return buildOrderItem(id, dish, courseNumber, 1);  // дефолт: 1 порция
    }

    private OrderItem buildOrderItem(Long id, Dish dish, int courseNumber, int quantity) {
        OrderItem item = new OrderItem();
        try {
            var f = OrderItem.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(item, id);
        } catch (Exception ignored) {}
        item.setDish(dish);
        item.setCourseNumber(courseNumber);
        item.setQuantity(quantity);
        return item;
    }

    private Order buildOrder(Long id, List<OrderItem> items) {
        Order order = new Order();
        try {
            var f = Order.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(order, id);
        } catch (Exception ignored) {}
        order.setTableNumber("5");
        order.setStatus(com.example.restaurant.enums.OrderStatus.COOKING);
        // Связываем OrderItem с Order
        items.forEach(item -> item.setOrder(order));
        order.setOrderItems(new java.util.ArrayList<>(items));
        return order;
    }

    private OrderCourse buildOrderCourse(Long id, Order order, int courseNumber, int syncGap) {
        OrderCourse course = new OrderCourse();
        try {
            var f = OrderCourse.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(course, id);
        } catch (Exception ignored) {}
        course.setOrder(order);
        course.setCourseNumber(courseNumber);
        course.setSyncGapMinutes(syncGap);
        return course;
    }
}