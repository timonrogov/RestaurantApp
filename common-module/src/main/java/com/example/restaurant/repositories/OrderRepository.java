package com.example.restaurant.repositories;

import com.example.restaurant.models.Client;
import com.example.restaurant.models.Order;
import com.example.restaurant.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Находит все заказы указанного клиента с определенным статусом
     * @param client объект клиента
     * @param status название статуса заказа
     * @return список заказов, соответствующих критериям
     */
    @Query("SELECT o FROM Order o WHERE o.client = :client AND o.status = :status")
    List<Order> findByClientAndStatus(
            @Param("client") Client client,
            @Param("status") OrderStatus status);

    /**
     * Находит заказ по его идентификатору
     * @param id идентификатор заказа
     * @return Optional с заказом или пустой, если не найден
     */
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> getOrderById(@Param("id") Long id);

    /**
     * Находит заказ по ID и проверяет его принадлежность клиенту
     * @param orderId идентификатор заказа
     * @param client объект клиента для проверки
     * @return Optional с заказом, если он принадлежит клиенту
     */
    @Query("SELECT o FROM Order o WHERE o.id = :orderId AND o.client = :client")
    Optional<Order> findByIdAndClient(
            @Param("orderId") Long orderId,
            @Param("client") Client client
    );

    /**
     * Находит текущий "активный" заказ клиента в статусе "Сборка"
     * @param client объект клиента
     * @return Optional с заказом в статусе сборки
     */
    @Query("SELECT o FROM Order o WHERE o.client = :client AND o.status = 'ASSEMBLY'")
    Optional<Order> getCurrentCart(@Param("client") Client client);

    /**
     * Находит заказы клиента по его ID и статусу
     * @param clientId идентификатор клиента
     * @param status название статуса заказа
     * @return список заказов, соответствующих критериям
     */
    @Query("SELECT o FROM Order o WHERE o.client.id = :clientId AND o.status = :status")
    List<Order> findByClientIdAndStatus(
            @Param("clientId") Long clientId,
            @Param("status") OrderStatus status);

    /**
     * Находит все заказы указанного клиента
     * @param client объект клиента
     * @return список всех заказов клиента
     */
    @Query("SELECT o FROM Order o WHERE o.client = :client")
    List<Order> findByClient(@Param("client") Client client);

    /**
     * Находит все заказы с указанным статусом
     * @param statusName название статуса заказа
     * @return список заказов с заданным статусом
     */
    List<Order> findByStatus(@Param("statusName") String statusName);

    /**
     * Находит заказы для администрирования с фильтрацией по статусу
     * @param status название статуса для фильтрации (может быть null)
     * @return список заказов, исключая статус "Сборка", с сортировкой по дате и времени
     */
    @Query("SELECT o FROM Order o WHERE " +
            "( :status IS NULL OR o.status = :status ) " +
            "AND o.status != 'ASSEMBLY' " +
            "ORDER BY o.timeSlot.day.workDate DESC, o.timeSlot.orderTime DESC")
    List<Order> findOrdersForAdmin(@Param("status") OrderStatus status);

    /**
     * Находит заказы для административной панели только за указанный день.
     * Используется для отображения заказов текущей смены.
     *
     * @param status фильтр по статусу (null = все статусы)
     * @param today  дата рабочего дня
     */
    @Query("SELECT o FROM Order o WHERE " +
            "( :status IS NULL OR o.status = :status ) " +
            "AND o.status != 'ASSEMBLY' " +
            "AND o.timeSlot.day.workDate = :today " +
            "ORDER BY o.timeSlot.orderTime DESC")
    List<Order> findOrdersForAdminToday(@Param("status") OrderStatus status,
                                        @Param("today") LocalDate today);


    // Поиск корзины для авторизованного (как было, но с учетом null sessionToken для чистоты)
    @Query("SELECT o FROM Order o WHERE o.client = :client " +
            "AND o.status = com.example.restaurant.enums.OrderStatus.ASSEMBLY")
    Optional<Order> getCurrentCartForClient(@Param("client") Client client);

    // Поиск корзины для гостя по токену
    @Query("SELECT o FROM Order o WHERE o.sessionToken = :token " +
            "AND o.status = com.example.restaurant.enums.OrderStatus.ASSEMBLY")
    Optional<Order> getCurrentCartForGuest(@Param("token") String token);

    @Query("SELECT o FROM Order o WHERE o.client = :client AND o.status != " +
            "com.example.restaurant.enums.OrderStatus.ASSEMBLY ORDER BY o.id DESC")
    List<Order> findHistoryByClient(@Param("client") Client client);

    // Ищет заказы, статус которых входит в переданный список (IN)
    List<Order> findByClientAndStatusIn(Client client, List<OrderStatus> statuses);

    /**
     * Загрузить заказ вместе со всеми OrderItem и их блюдами одним запросом.
     * Используется планировщиком: агентам нужны эти данные вне Hibernate-сессии.
     */
    @Query("""
                SELECT DISTINCT o FROM Order o
                JOIN FETCH o.orderItems oi
                JOIN FETCH oi.dish
                WHERE o.id = :orderId
            """)
    Optional<Order> findByIdWithItems(@Param("orderId") Long orderId);

    List<Order> findByStatusNotIn(List<OrderStatus> statuses);
}