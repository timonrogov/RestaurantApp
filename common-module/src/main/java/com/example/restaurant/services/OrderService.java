package com.example.restaurant.services;

import com.example.restaurant.enums.OrderStatus;
import com.example.restaurant.events.OrderStatusChangedEvent;
import com.example.restaurant.models.*;
import com.example.restaurant.repositories.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.transaction.Transactional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
public class OrderService {
    // Логгер для записи событий и ошибок
    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    // Репозитории и сервисы, используемые для работы с данными
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final DishRepository dishRepository;
    private final OrderTimeSlotRepository orderTimeSlotRepository;
    private final DayRepository dayRepository;
    private final OrderTimeSlotRepository timeSlotRepository;
    private final DishPromotionService dishPromotionService;
    private final CartService cartService;
    private final OrderTimeSlotService orderTimeSlotService;
    private final PricingService pricingService;
    private final ApplicationEventPublisher eventPublisher;
    private final OrderCourseRepository orderCourseRepository;

    /**
     * Конструктор для внедрения зависимостей через Spring
     */
    @Autowired
    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        DishRepository dishRepository,
                        OrderTimeSlotRepository orderTimeSlotRepository,
                        DayRepository dayRepository,
                        OrderTimeSlotRepository timeSlotRepository,
                        DishPromotionService dishPromotionService,
                        CartService cartService,
                        OrderTimeSlotService orderTimeSlotService,
                        PricingService pricingService,
                        ApplicationEventPublisher eventPublisher,
                        OrderCourseRepository orderCourseRepository) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.dishRepository = dishRepository;
        this.orderTimeSlotRepository = orderTimeSlotRepository;
        this.dayRepository = dayRepository;
        this.timeSlotRepository = timeSlotRepository;
        this.dishPromotionService = dishPromotionService;
        this.cartService = cartService;
        this.orderTimeSlotService = orderTimeSlotService;
        this.pricingService = pricingService;
        this.eventPublisher = eventPublisher;
        this.orderCourseRepository = orderCourseRepository;
    }




    /**
     * Получить все заказы в системе
     */
    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }




    /**
     * Получить заказы конкретного клиента
     * @param client - объект клиента
     */
    public List<Order> getOrdersByClient(Client client) {
        return orderRepository.findByClient(client);
    }




    /**
     * Получить заказы клиента с определенным статусом
     * @param client - объект клиента
     * @param statusName - название статуса
     */
    public List<Order> getOrdersByClientAndStatus(Client client, OrderStatus statusName) {
        return orderRepository.findByClientAndStatus(client, statusName);
    }




    /**
     * Создать новый заказ
     * @param order - объект заказа для сохранения
     */
    public Order createOrder(Order order) {
        return orderRepository.save(order);
    }




    /**
     * Удалить заказ по ID
     * @param id - идентификатор заказа
     */
    public void deleteOrder(Long id) {
        orderRepository.deleteById(id);
    }




    /**
     * Получить все позиции в заказе
     * @param orderId - ID заказа
     */
    public List<OrderItem> getOrderItemsByOrderId(Long orderId) {
        return orderItemRepository.findByOrderId(orderId);
    }




    /**
     * Создание нового заказа с базовыми настройками:
     * - Привязка к текущей дате
     * - Статус "Сборка"
     * - Создание временного слота
     */
    private Order createNewOrder(Client client) {
        try {
            // Получаем текущий день работы ресторана
            Day currentDay = dayRepository.findById(LocalDate.now())
                    .orElseThrow(() ->
                            new RuntimeException("День работы ресторана не найден"));

            // Создаем и сохраняем временной слот для заказа
            OrderTimeSlot timeSlot = new OrderTimeSlot();
            timeSlot.setDay(currentDay);
            timeSlot.setOrderTime(LocalTime.now());
            timeSlot = orderTimeSlotRepository.save(timeSlot);

            // Создаем новый заказ с базовыми параметрами
            Order newOrder = new Order();
            newOrder.setClient(client);
            newOrder.setStatus(OrderStatus.ASSEMBLY);
            newOrder.setTimeSlot(timeSlot);

            return orderRepository.save(newOrder);
        } catch (Exception e) {
            log.error("Ошибка создания заказа", e);
            throw new RuntimeException
                    ("Не удалось создать заказ: " + e.getMessage());
        }
    }




    @Transactional
    public void confirmOrder(Principal principal, String tableNumber,
                             HttpServletRequest request, HttpServletResponse response) {

        // 1. Получаем корзину
        Order cart = cartService.getCurrentCart(principal, request, response);

        if (cart.getOrderItems() == null || cart.getOrderItems().isEmpty()) {
            throw new RuntimeException("Невозможно подтвердить пустой заказ.");
        }

        // 2. Создаём и сохраняем временной слот
        OrderTimeSlot timeSlot = orderTimeSlotService.createCurrentOrderTimeSlot();
        timeSlot = orderTimeSlotRepository.save(timeSlot);

        // 3. Обновляем поля заказа
        cart.setTimeSlot(timeSlot);
        cart.setTableNumber(tableNumber);
        cart.setStatus(OrderStatus.COOKING);

        double totalWithDiscount = pricingService.calculateTotalWithDiscount(cart);
        cart.setTotalPrice(totalWithDiscount);

        // 4. ═══════════════════════════════════════════════════════
        //    НОВАЯ ЛОГИКА: Проставляем курс каждому OrderItem
        //    из defaultCourse блюда
        // ═══════════════════════════════════════════════════════════
        cart.getOrderItems().forEach(item -> {
            int course = item.getDish().getDefaultCourse();
            item.setCourseNumber(course);
        });

        // 5. Сохраняем заказ (items сохраняются каскадно)
        orderRepository.save(cart);

        // 6. ═══════════════════════════════════════════════════════
        //    НОВАЯ ЛОГИКА: Создаём OrderCourse записи для каждого
        //    уникального курса, встреченного в заказе
        // ═══════════════════════════════════════════════════════════
        rebuildOrderCourses(cart);

        // 7. Публикуем событие изменения статуса
        eventPublisher.publishEvent(
                new OrderStatusChangedEvent(this, cart.getId(), OrderStatus.COOKING, OrderStatus.ASSEMBLY)
        );
    }

    /**
     * Пересоздаёт записи OrderCourse для заказа на основе курсов его позиций.
     *
     * Метод:
     *  1. Удаляет старые записи (идемпотентность — можно вызвать повторно)
     *  2. Находит уникальные номера курсов среди OrderItem заказа
     *  3. Для каждого курса создаёт OrderCourse с нужным syncGapMinutes
     *
     * Если в заказе только блюда одного курса (например, только основные) —
     * создаётся одна запись с courseNumber=тот_курс и syncGapMinutes=0,
     * так как нет предыдущего курса для синхронизации.
     */
    private void rebuildOrderCourses(Order order) {
        // Удаляем старые (для идемпотентности)
        orderCourseRepository.deleteByOrderId(order.getId());

        // Собираем уникальные номера курсов, сортируем по возрастанию
        List<Integer> courseNumbers = order.getOrderItems().stream()
                .map(item -> item.getDish().getDefaultCourse())
                .distinct()
                .sorted()
                .toList();

        for (int i = 0; i < courseNumbers.size(); i++) {
            int courseNumber = courseNumbers.get(i);

            OrderCourse course = new OrderCourse();
            course.setOrder(order);
            course.setCourseNumber(courseNumber);

            // Первый курс в ЭТОМ заказе всегда начинается без паузы.
            // Последующие получают стандартную паузу по своему номеру.
            if (i == 0) {
                course.setSyncGapMinutes(0);
            } else {
                course.setSyncGapMinutes(defaultSyncGap(courseNumber));
            }

            orderCourseRepository.save(course);
        }
    }

    /**
     * Стандартная пауза (в минутах) перед началом готовки курса N,
     * отсчитываемая от момента готовности предыдущего курса.
     *
     * Значения основаны на реальной ресторанной практике:
     * пауза нужна, чтобы гости успели съесть предыдущую подачу
     * прежде, чем принесут следующую.
     */
    private int defaultSyncGap(int courseNumber) {
        // Для тестирования поставил все курсы 5 минут. Комментарии показывают старые значения.
        return switch (courseNumber) {
            case 1 -> 0;   // Аперитив — сразу
            case 2 -> 5;   // Закуска — через 5 мин
            case 3 -> 5;  // Первое — через 10 мин
            case 4 -> 5;  // Основное — через 15 мин
            case 5 -> 5;  // Десерт — через 20 мин
            default -> 5; // Нестандартный курс — разумный дефолт
        };
    }




    /**
     * Получает заказ по ID с проверкой существования
     * @param orderId ID заказа
     * @return Order найденный заказ
     * @throws RuntimeException если заказ не существует
     */
    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ не найден"));
    }




    /**
     * Поиск заказа по ID с проверкой принадлежности клиенту
     * (защита от несанкционированного доступа)
     * @param orderId ID заказа
     * @param client Клиент для проверки
     * @return Optional<Order> заказ, если принадлежит клиенту
     */
    public Optional<Order> findByIdAndClient(Long orderId, Client client) {
        return orderRepository.findByIdAndClient(orderId, client);
    }




    /**
     * Отмена заказа. Доступна только для статуса "Сборка".
     * Удаляет все позиции заказа из системы.
     * @Transactional - гарантирует атомарность операции удаления
     * @param orderId ID заказа
     * @throws RuntimeException если заказ нельзя отменить
     */
    @Transactional
    public void cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ не найден"));

        if (!order.getStatus().equals(OrderStatus.ASSEMBLY)) {
            throw new RuntimeException("Заказ нельзя отменить в текущем статусе");
        }

        // Удаление всех связанных позиций заказа
        orderItemRepository.deleteByOrderId(orderId);
    }




    /**
     * Получение заказов для админ-панели с дополнительными расчетами:
     * - Общая сумма заказа
     * - Сумма с учетом скидок для каждой позиции
     * @param statusFilter Фильтр по статусу (опционально)
     * @return List<Order> обработанные заказы
     */
    public List<Order> getOrdersForAdminPanel(OrderStatus statusFilter) {
        OrderStatus status = null;
        if (statusFilter != null) {
            try {
                status = statusFilter; // Ожидает "COOKING", "SERVED"
            } catch (IllegalArgumentException e) {
                // Если пришла ерунда, можно игнорировать или кидать ошибку
            }
        }

        return orderRepository.findOrdersForAdmin(status).stream()
                .peek(order -> {
                    double total = pricingService.calculateTotal(order);
                    // Расчет суммы скидки для каждой позиции
                    order.getOrderItems().forEach(item -> item.setTotalPrice(
                            item.getQuantity() * item.getDish().getPrice() *
                                    (1 - item.getAppliedDiscount().doubleValue())
                    ));
                    order.setTotalPrice(total);
                })
                .collect(Collectors.toList());
    }

    /**
     * Получение заказов для административной панели только за текущий день.
     *
     * @param statusFilter Фильтр по статусу (опционально)
     * @return List<Order> заказы за сегодня с рассчитанными ценами
     */
    public List<Order> getOrdersForAdminPanelToday(OrderStatus statusFilter) {
        LocalDate today = LocalDate.now();
        return orderRepository.findOrdersForAdminToday(statusFilter, today).stream()
                .peek(order -> {
                    double total = pricingService.calculateTotal(order);
                    order.getOrderItems().forEach(item -> item.setTotalPrice(
                            item.getQuantity() * item.getDish().getPrice() *
                                    (1 - item.getAppliedDiscount().doubleValue())
                    ));
                    order.setTotalPrice(total);
                })
                .collect(Collectors.toList());
    }




    /**
     * Универсальный метод изменения статуса заказа
     * (без дополнительных проверок, для административных целей)
     * @param orderId ID заказа
     * @param newStatusName Новый статус
     * @throws RuntimeException если заказ не найден
     */
    public void changeOrderStatus(Long orderId, String newStatusName) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));

        OrderStatus previousStatus = order.getStatus(); // ← ДОБАВИТЬ эту строку
        OrderStatus newStatus = OrderStatus.valueOf(newStatusName);
        order.setStatus(newStatus);
        orderRepository.save(order);

        // ← ДОБАВИТЬ публикацию события:
        eventPublisher.publishEvent(
                new OrderStatusChangedEvent(this, orderId, newStatus, previousStatus)
        );
    }


    public List<Order> getOrderHistory(Client client) {
        return orderRepository.findHistoryByClient(client);
    }


    public List<Order> getOrdersByClientAndStatuses(Client client, List<OrderStatus> statuses) {
        return orderRepository.findByClientAndStatusIn(client, statuses);
    }
}