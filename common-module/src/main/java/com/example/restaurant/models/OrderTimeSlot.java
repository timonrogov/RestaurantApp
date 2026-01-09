package com.example.restaurant.models;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "order_time_slot")
public class OrderTimeSlot {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne
    @JoinColumn(name = "work_date", nullable = false)
    private Day day;

    @Column(name = "order_time", nullable = false)
    private LocalTime orderTime;
}
