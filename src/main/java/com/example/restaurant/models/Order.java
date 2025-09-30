package com.example.restaurant.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "orders") // Имя таблицы в БД
public class Order {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne // Связь с клиентом
    @JoinColumn(name = "client_id", nullable = false)
    private Client client;

    @ManyToOne // Временной слот заказа
    @JoinColumn(name = "time_slot_id", nullable = false)
    private OrderTimeSlot timeSlot;

    @ManyToOne // Статус заказа (например, "Готовится", "Завершен")
    @JoinColumn(name = "status_id", nullable = false)
    private OrderStatus status;

    @Column(name = "table_number", nullable = false) // Номер стола (значение по умолчанию "0")
    private String tableNumber = "0";

    // Состав заказа: каскадное сохранение/удаление
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    private double totalPrice = 0; // Итоговая сумма заказа

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Client getClient() {
        return client;
    }

    public void setClient(Client client) {
        this.client = client;
    }

    public OrderTimeSlot getTimeSlot() {
        return timeSlot;
    }

    public void setTimeSlot(OrderTimeSlot timeSlot) {
        this.timeSlot = timeSlot;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public List<OrderItem> getOrderItems() {
        return orderItems;
    }

    public void setOrderItems(List<OrderItem> orderItems) {
        this.orderItems = orderItems;
    }

    public String getTableNumber() {
        return tableNumber;
    }

    public void setTableNumber(String tableNumber) {
        this.tableNumber = tableNumber;
    }

    public double getTotalPrice() {
        return totalPrice;
    }

    public void setTotalPrice(double totalPrice) {
        this.totalPrice = totalPrice;
    }
}