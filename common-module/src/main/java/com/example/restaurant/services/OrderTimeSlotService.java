package com.example.restaurant.services;

import com.example.restaurant.models.Day;
import com.example.restaurant.models.OrderTimeSlot;
import com.example.restaurant.repositories.DayRepository;
import com.example.restaurant.repositories.OrderTimeSlotRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;

@Service
public class OrderTimeSlotService {
    private final OrderTimeSlotRepository orderTimeSlotRepository;
    private final DayRepository dayRepository;

    @Autowired
    public OrderTimeSlotService(OrderTimeSlotRepository orderTimeSlotRepository,
                                DayRepository dayRepository) {
        this.orderTimeSlotRepository = orderTimeSlotRepository;
        this.dayRepository = dayRepository;
    }

    public List<OrderTimeSlot> getTimeSlotsByDate(LocalDate date) {
        return orderTimeSlotRepository.findByDayWorkDate(date);
    }

    public OrderTimeSlot createTimeSlot(OrderTimeSlot timeSlot) {
        return orderTimeSlotRepository.save(timeSlot);
    }

    public void deleteTimeSlot(Long id) {
        orderTimeSlotRepository.deleteById(id);
    }


    /**
     * Создает новый временной слот, разделяя текущие дату и время
     * по соответствующим полям.
     * @return Новая, еще не сохраненная сущность OrderTimeSlot.
     */
    public OrderTimeSlot createCurrentOrderTimeSlot() {
        OrderTimeSlot newTimeSlot = new OrderTimeSlot();
        LocalDateTime now = LocalDateTime.now();

        // 1. Извлекаем ДАТУ из LocalDateTime и устанавливаем ее в поле workDate
        newTimeSlot.setDay(dayRepository.findByWorkDate(now.toLocalDate())
                .orElseThrow(() -> new RuntimeException("Текущий день не найден")));

        // 2. Извлекаем ВРЕМЯ из LocalDateTime и устанавливаем его в поле orderTime
        newTimeSlot.setOrderTime(now.toLocalTime());

        // Как и раньше, мы не сохраняем его здесь.
        // Сохранение произойдет каскадно вместе с заказом.
        return newTimeSlot;
    }

}
