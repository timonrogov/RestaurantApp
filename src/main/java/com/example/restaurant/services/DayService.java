package com.example.restaurant.services;

import com.example.restaurant.models.Day;
import com.example.restaurant.repositories.DayRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class DayService {
    private final DayRepository dayRepository;

    @Autowired
    public DayService(DayRepository dayRepository) {
        this.dayRepository = dayRepository;
    }

    public List<Day> getAllDays() {
        return dayRepository.findAll();
    }

    public Day getDayByDate(LocalDate date) {
        return dayRepository.findById(date).orElse(null);
    }

    public Optional<Day> getDayById(LocalDate date) {
        return dayRepository.findById(date);
    }

    public Day createDay(Day day) {
        return dayRepository.save(day);
    }

    public void deleteDay(LocalDate date) {
        dayRepository.deleteById(date);
    }

    public Optional<Day> findById(LocalDate workDate) {
        return dayRepository.findById(workDate);
    }
}
