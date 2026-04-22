package com.example.restaurant.scheduler;

import com.example.restaurant.enums.CookSpecialization;
import com.example.restaurant.enums.CookingTaskStatus;
import com.example.restaurant.models.*;
import com.example.restaurant.repositories.*;
import com.example.restaurant.scheduler.agents.*;
import com.example.restaurant.scheduler.dispatcher.DispatcherAgent;
import com.example.restaurant.scheduler.dispatcher.MessageBus;
import com.example.restaurant.scheduler.messages.Message;
import com.example.restaurant.scheduler.messages.MessageType;
import com.example.restaurant.scheduler.schedule.CookSchedule;
import com.example.restaurant.scheduler.schedule.EquipmentTypeSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

    // Фиксированное время для предсказуемых тестов
    private final LocalDateTime NOW = LocalDateTime.of(2025, 1, 1, 12, 0);

    @BeforeEach
    void setUp() {
        // Создаём шину сообщений
        messageBus = new MessageBus();

        // Мокируем все репозитории
        taskRepository       = mock(CookingTaskRepository.class);
        templateRepository   = mock(CookingTaskTemplateRepository.class);
        orderCourseRepository = mock(OrderCourseRepository.class);
        orderRepository      = mock(OrderRepository.class);

        // save() должен возвращать переданный объект с проставленным ID
        when(taskRepository.save(any(CookingTask.class))).thenAnswer(inv -> {
            CookingTask t = inv.getArgument(0);
            if (t.getId() == null) {
                // Имитируем выдачу ID при первом сохранении
                try {
                    var idField = CookingTask.class.getDeclaredField("id");
                    idField.setAccessible(true);
                    idField.set(t, (long)(System.nanoTime() % 10000));
                } catch (Exception ignored) {}
            }
            return t;
        });

        // Курсов по умолчанию нет → OrderAgent создаст один курс автоматически
        when(orderCourseRepository.findByOrderIdOrderByCourseNumberAsc(anyLong()))
                .thenReturn(List.of());

        // Создаём и регистрируем DispatcherAgent
        dispatcher = new DispatcherAgent(messageBus, taskRepository, templateRepository, orderCourseRepository);
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
                templateRepository, orderCourseRepository);
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
        // Повар 1 (занятый): слот 60 минут
        CookProfile cook1 = buildCookProfile(1L, CookSpecialization.UNIVERSAL);
        CookSchedule schedule1 = new CookSchedule(1L);
        schedule1.addSlot(new com.example.restaurant.scheduler.schedule.ScheduleSlot(
                999L, 999L, LocalDateTime.now(), LocalDateTime.now().plusMinutes(60), null));
        CookAgent agent1 = new CookAgent(cook1, schedule1);
        messageBus.register(agent1);

        // Повар 2 (свободный)
        CookProfile cook2 = buildCookProfile(2L, CookSpecialization.UNIVERSAL);
        CookSchedule schedule2 = new CookSchedule(2L);
        CookAgent agent2 = new CookAgent(cook2, schedule2);
        messageBus.register(agent2);

        sceneAgent = new SceneAgent();
        messageBus.register(sceneAgent);
        sceneAgent.registerCookAgent(agent1, schedule1);
        sceneAgent.registerCookAgent(agent2, schedule2);

        CookingTaskTemplate template = buildTemplate(1L, 1, "Приготовление", 15,
                CookSpecialization.UNIVERSAL, null);
        Dish dish = buildDish(1L, "Салат");
        OrderItem orderItem = buildOrderItem(1L, dish, 1);
        Order order = buildOrder(1L, List.of(orderItem));

        when(templateRepository.findByDishIdOrderByStepNumberAsc(1L)).thenReturn(List.of(template));

        OrderAgent orderAgent = new OrderAgent(order, sceneAgent, taskRepository,
                templateRepository, orderCourseRepository);
        messageBus.register(orderAgent);

        dispatcher.initialize(List.of(cook1, cook2), List.of());
        messageBus.deliver(dispatcher.getAgentId(), new Message(MessageType.NEW_ORDER, order, "TEST"));
        messageBus.processAll();

        // Assert: задача назначена свободному повару (cook2, id=2)
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
        CookSchedule cookSchedule = new CookSchedule(1L);
        CookAgent cookAgent = new CookAgent(cook, cookSchedule);
        messageBus.register(cookAgent);

        sceneAgent = new SceneAgent();
        messageBus.register(sceneAgent);
        sceneAgent.registerCookAgent(cookAgent, cookSchedule);

        // Два блюда: курс 1 и курс 2
        CookingTaskTemplate tmpl1 = buildTemplate(1L, 1, "Подача закуски", 10,
                CookSpecialization.UNIVERSAL, null);
        CookingTaskTemplate tmpl2 = buildTemplate(2L, 1, "Подача основного", 15,
                CookSpecialization.UNIVERSAL, null);

        Dish dish1 = buildDish(1L, "Салат Цезарь");
        Dish dish2 = buildDish(2L, "Стейк");

        OrderItem item1 = buildOrderItem(1L, dish1, 1); // курс 1
        OrderItem item2 = buildOrderItem(2L, dish2, 2); // курс 2

        Order order = buildOrder(1L, List.of(item1, item2));

        when(templateRepository.findByDishIdOrderByStepNumberAsc(1L)).thenReturn(List.of(tmpl1));
        when(templateRepository.findByDishIdOrderByStepNumberAsc(2L)).thenReturn(List.of(tmpl2));

        // Настраиваем курсы: gap=15 мин между первым и вторым
        OrderCourse course1 = buildOrderCourse(1L, order, 1, 0);
        OrderCourse course2 = buildOrderCourse(2L, order, 2, 15);
        when(orderCourseRepository.findByOrderIdOrderByCourseNumberAsc(1L))
                .thenReturn(List.of(course1, course2));

        OrderAgent orderAgent = new OrderAgent(order, sceneAgent, taskRepository,
                templateRepository, orderCourseRepository);
        messageBus.register(orderAgent);

        dispatcher.initialize(List.of(cook), List.of());
        messageBus.deliver(dispatcher.getAgentId(), new Message(MessageType.NEW_ORDER, order, "TEST"));
        messageBus.processAll();

        // Собираем все сохранённые задачи
        var savedTasks = org.mockito.ArgumentCaptor.forClass(CookingTask.class);
        verify(taskRepository, atLeast(2)).save(savedTasks.capture());

        List<CookingTask> planned = savedTasks.getAllValues().stream()
                .filter(t -> t.getStatus() == CookingTaskStatus.PLANNED
                        && t.getPlannedStartTime() != null)
                .toList();

        // Задача 1 курса и задача 2 курса должны иметь разные стартовые времена
        assertThat(planned).hasSizeGreaterThanOrEqualTo(2);

        LocalDateTime course1End = planned.stream()
                .filter(t -> t.getOrderItem().getCourseNumber() == 1)
                .map(CookingTask::getPlannedEndTime)
                .max(LocalDateTime::compareTo)
                .orElseThrow();

        LocalDateTime course2Start = planned.stream()
                .filter(t -> t.getOrderItem().getCourseNumber() == 2)
                .map(CookingTask::getPlannedStartTime)
                .min(LocalDateTime::compareTo)
                .orElseThrow();

        // Второй курс должен начаться не раньше чем конец первого + 15 мин
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

        CookSchedule schedule1 = new CookSchedule(1L);
        CookSchedule schedule2 = new CookSchedule(2L);

        CookAgent agent1 = new CookAgent(cook1, schedule1);
        CookAgent agent2 = new CookAgent(cook2, schedule2);

        messageBus.register(agent1);
        messageBus.register(agent2);

        sceneAgent = new SceneAgent();
        messageBus.register(sceneAgent);
        sceneAgent.registerCookAgent(agent1, schedule1);
        sceneAgent.registerCookAgent(agent2, schedule2);

        CookingTaskTemplate template = buildTemplate(1L, 1, "Готовка", 10,
                CookSpecialization.UNIVERSAL, null);
        Dish dish = buildDish(1L, "Суп");
        OrderItem item = buildOrderItem(1L, dish, 1);
        Order order = buildOrder(1L, List.of(item));

        when(templateRepository.findByDishIdOrderByStepNumberAsc(1L)).thenReturn(List.of(template));

        OrderAgent orderAgent = new OrderAgent(order, sceneAgent, taskRepository,
                templateRepository, orderCourseRepository);
        messageBus.register(orderAgent);

        // Планируем заказ
        dispatcher.initialize(List.of(cook1, cook2), List.of());
        messageBus.deliver(dispatcher.getAgentId(), new Message(MessageType.NEW_ORDER, order, "TEST"));
        messageBus.processAll();

        // Повар 1 стал недоступен
        messageBus.deliver(dispatcher.getAgentId(),
                new Message(MessageType.COOK_UNAVAILABLE, 1L, "TEST"));
        messageBus.processAll();

        // Задача должна перепланироваться на повара 2
        verify(taskRepository, atLeastOnce()).save(argThat(task ->
                task.getStatus() == CookingTaskStatus.PLANNED
                        && task.getAssignedCook() != null
                        && task.getAssignedCook().getId() == 2L
        ));
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
        OrderItem item = new OrderItem();
        try {
            var f = OrderItem.class.getDeclaredField("id");
            f.setAccessible(true);
            f.set(item, id);
        } catch (Exception ignored) {}
        item.setDish(dish);
        item.setCourseNumber(courseNumber);
        item.setQuantity(1);
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