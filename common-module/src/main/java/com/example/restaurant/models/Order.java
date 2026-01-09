package com.example.restaurant.models;

import com.example.restaurant.enums.OrderStatus;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
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
    @JoinColumn(name = "client_id")
    private Client client;

    @ManyToOne(cascade = CascadeType.ALL) // Временной слот заказа
    @JoinColumn(name = "time_slot_id")
    private OrderTimeSlot timeSlot;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private OrderStatus status;

    @Column(name = "table_number", nullable = false) // Номер стола (значение по умолчанию "0")
    private String tableNumber = "0";

    // Состав заказа: каскадное сохранение/удаление
    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

    private double totalPrice = 0; // Итоговая сумма заказа

    @Column(name = "session_token")
    private String sessionToken;
}