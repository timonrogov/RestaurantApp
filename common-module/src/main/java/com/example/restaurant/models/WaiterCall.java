package com.example.restaurant.models;

import com.example.restaurant.enums.CallStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "waiter_call")
public class WaiterCall {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_number", nullable = false)
    private String tableNumber;

    @ManyToOne
    @JoinColumn(name = "client_id") // Может быть null (для гостей)
    private Client client;

    @Column(name = "call_time", nullable = false)
    private LocalDateTime callTime;

    @Column(name = "resolved_time")
    private LocalDateTime resolvedTime;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private CallStatus status;
}