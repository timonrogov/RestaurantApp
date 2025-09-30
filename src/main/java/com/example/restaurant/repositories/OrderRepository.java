package com.example.restaurant.repositories;

import com.example.restaurant.models.Client;
import com.example.restaurant.models.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    /**
     * Находит все заказы указанного клиента с определенным статусом
     * @param client объект клиента
     * @param statusName название статуса заказа (например, "Готовится")
     * @return список заказов, соответствующих критериям
     */
    @Query("SELECT o FROM Order o WHERE o.client = :client AND o.status.name = :statusName")
    List<Order> findByClientAndStatusName(
            @Param("client") Client client,
            @Param("statusName") String statusName);

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
    @Query("SELECT o FROM Order o WHERE o.client = :client AND o.status.name = 'Сборка'")
    Optional<Order> getCurrentOrderForClient(@Param("client") Client client);

    /**
     * Находит заказы клиента по его ID и статусу
     * @param clientId идентификатор клиента
     * @param statusName название статуса заказа
     * @return список заказов, соответствующих критериям
     */
    @Query("SELECT o FROM Order o WHERE o.client.id = :clientId AND o.status.name = :statusName")
    List<Order> findByClientIdAndStatusName(
            @Param("clientId") Long clientId,
            @Param("statusName") String statusName);

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
    @Query("SELECT o FROM Order o WHERE o.status.name = :statusName")
    List<Order> findByStatusName(@Param("statusName") String statusName);

    /**
     * Находит заказы для администрирования с фильтрацией по статусу
     * @param statusName название статуса для фильтрации (может быть null)
     * @return список заказов, исключая статус "Сборка", с сортировкой по дате и времени
     */
    @Query("SELECT o FROM Order o WHERE " +
            "( :statusName IS NULL OR o.status.name = :statusName ) " +
            "AND o.status.name != 'Сборка' " +
            "ORDER BY o.timeSlot.day.workDate DESC, o.timeSlot.orderTime DESC")
    List<Order> findOrdersForAdmin(@Param("statusName") String statusName);
}