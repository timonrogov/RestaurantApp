package com.example.restaurant.services;

import com.example.restaurant.models.*;
import com.example.restaurant.repositories.*;
import jakarta.transaction.Transactional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
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
    private final OrderStatusRepository orderStatusRepository;
    private final DishRepository dishRepository;
    private final OrderTimeSlotRepository orderTimeSlotRepository;
    private final DayRepository dayRepository;
    private final DishPromotionService dishPromotionService;

    /**
     * Конструктор для внедрения зависимостей через Spring
     */
    @Autowired
    public OrderService(OrderRepository orderRepository,
                        OrderItemRepository orderItemRepository,
                        OrderStatusRepository orderStatusRepository,
                        DishRepository dishRepository,
                        OrderTimeSlotRepository orderTimeSlotRepository,
                        DayRepository dayRepository,
                        DishPromotionService dishPromotionService) {
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.orderStatusRepository = orderStatusRepository;
        this.dishRepository = dishRepository;
        this.orderTimeSlotRepository = orderTimeSlotRepository;
        this.dayRepository = dayRepository;
        this.dishPromotionService = dishPromotionService;
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
     * @param statusName - название статуса (например, "Сборка")
     */
    public List<Order> getOrdersByClientAndStatus(Client client, String statusName) {
        return orderRepository.findByClientAndStatusName(client, statusName);
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
     * Получить текущий активный заказ клиента или создать новый
     * @param client - объект клиента
     * @Transactional - все операции выполняются в одной транзакции
     */
    @Transactional
    public Order getCurrentOrderForClient(Client client) {
        return orderRepository.getCurrentOrderForClient(client)
                .orElseGet(() -> createNewOrder(client));
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
            newOrder.setStatus(orderStatusRepository
                    .findByName("Сборка"));
            newOrder.setTimeSlot(timeSlot);

            return orderRepository.save(newOrder);
        } catch (Exception e) {
            log.error("Ошибка создания заказа", e);
            throw new RuntimeException
                    ("Не удалось создать заказ: " + e.getMessage());
        }
    }




    /**
     * Добавить блюдо в заказ с учетом:
     * - Поиска существующей позиции
     * - Обновления количества
     * - Применения акционных скидок
     * @Transactional - атомарная операция добавления
     */
    @Transactional
    public void addDishToOrder(Order order,
                               Long dishId,
                               int quantityToAdd,
                               String comment) {
        LocalDate currentDate = LocalDate.now();
        LocalTime currentTime = LocalTime.now();

        // Поиск блюда в базе
        Dish dish = dishRepository.findById(dishId)
                .orElseThrow(() -> new RuntimeException("Блюдо не найдено"));

        // Поиск существующей позиции в заказе
        Optional<OrderItem> existingItem =
                orderItemRepository.findByOrderIdAndDishIdAndComment(
                order.getId(),
                dishId,
                comment != null ? comment : ""
        );

        OrderItem orderItem;
        if (existingItem.isPresent()) {
            // Обновление существующей позиции
            orderItem = existingItem.get();
            orderItem.setQuantity(orderItem.getQuantity() + quantityToAdd);
        } else {
            // Создание новой позиции
            orderItem = new OrderItem();
            orderItem.setOrder(order);
            orderItem.setDish(dish);
            orderItem.setQuantity(quantityToAdd);
            orderItem.setComment(comment);
        }

        // Расчет и применение скидки
        BigDecimal discount = dishPromotionService.getDiscountForDish(
                dish,
                orderItem.getQuantity(), // Учитываем общее количество
                currentDate,
                currentTime
        );
        orderItem.setAppliedDiscount(discount);

        // Сохранение изменений
        orderItemRepository.save(orderItem);
    }




    /**
     * Обновляет статус заказа с дополнительной проверкой номера столика.
     * Только заказы с указанным номером столика могут менять статус
     * (например, на "Готов к выдаче").
     * @Transactional - гарантирует атомарность операции обновления
     * @param orderId ID заказа
     * @param statusName Новый статус заказа
     * @throws RuntimeException если номер столика не указан или заказ не найден
     */
    @Transactional
    public void updateOrderStatus(Long orderId, String statusName) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Заказ не найден"));

        // Проверка номера столика (обязателен для изменения статуса)
        if (order.getTableNumber().equals("0")) {
            throw new RuntimeException("Номер столика не указан");
        }

        OrderStatus newStatus = orderStatusRepository.findByName(statusName);
        order.setStatus(newStatus);
        orderRepository.save(order); // Сохраняем изменения в базе
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
     * Рассчитывает общую сумму заказа БЕЗ учета скидок
     * @param order Заказ для расчета
     * @return double Итоговая сумма
     */
    public double calculateTotal(Order order) {
        List<OrderItem> items = orderItemRepository.findByOrderId(order.getId());
        return items.stream()
                .mapToDouble(item -> item.getQuantity() * item.getDish().getPrice())
                .sum();
    }




    /**
     * Рассчитывает итоговую сумму заказа С УЧЕТОМ всех примененных скидок
     * @param order Заказ для расчета
     * @return double Сумма с учетом скидок
     */
    public double calculateTotalWithDiscount(Order order) {
        return order.getOrderItems().stream()
                .mapToDouble(item ->
                        item.getDish().getPrice() *
                                item.getQuantity() *
                                (1 - item.getAppliedDiscount().doubleValue()))
                .sum();
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

        if (!order.getStatus().getName().equals("Сборка")) {
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
    public List<Order> getOrdersForAdminPanel(String statusFilter) {
        return orderRepository.findOrdersForAdmin(statusFilter).stream()
                .peek(order -> {
                    double total = calculateTotal(order);
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
     * Универсальный метод изменения статуса заказа
     * (без дополнительных проверок, для административных целей)
     * @param orderId ID заказа
     * @param newStatus Новый статус
     * @throws RuntimeException если заказ не найден
     */
    public void changeOrderStatus(Long orderId, String newStatus) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new RuntimeException("Order not found"));
        OrderStatus status = orderStatusRepository.findByName(newStatus);
        order.setStatus(status);
        orderRepository.save(order); // Сохранение нового статуса
    }
}