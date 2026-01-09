package com.example.restaurant.services;

import com.example.restaurant.models.Day;
import com.example.restaurant.repositories.DayRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class RestaurantScheduleService {

    private final DayRepository dayRepository;

    /**
     * Определяет детальный статус работы ресторана на текущий момент.
     * Возвращает Map с ключами: "status", "message" и опционально "openingTime".
     */
    public Map<String, Object> getRestaurantStatus() {
        LocalDate currentDate = LocalDate.now();
        LocalTime currentTime = LocalTime.now();

        Optional<Day> dayOpt = dayRepository.findByWorkDate(currentDate);

        // Сценарий 1: День не найден в расписании или помечен как выходной
        if (dayOpt.isEmpty() || !dayOpt.get().isWorkingDay()) {
            return Map.of(
                    "status", "closed_today",
                    "message", "Извините, сегодня ресторан не работает"
            );
        }

        Day day = dayOpt.get();

        // Сценарий 2: Ресторан еще не открылся
        if (currentTime.isBefore(day.getStartTime())) {
            return Map.of(
                    "status", "not_opened_yet",
                    "message", "Ресторан еще закрыт, он откроется в " +
                            day.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm")),
                    "openingTime", day.getStartTime()
            );
        }

        // Сценарий 3: Ресторан уже закрылся
        if (currentTime.isAfter(day.getEndTime())) {
            return Map.of(
                    "status", "already_closed",
                    "message", "Извините, ресторан уже закрыт"
            );
        }

        // Сценарий 4: Ресторан работает
        return Map.of(
                "status", "open",
                "message", ""
        );
    }
}