package com.example.restaurant.controllers.api;

import com.example.restaurant.models.Day;
import com.example.restaurant.services.DayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/days")
public class DayController {
    private final DayService dayService;

    @Autowired
    public DayController(DayService dayService) {
        this.dayService = dayService;
    }

    @GetMapping
    public List<Day> getAllDays() {
        return dayService.getAllDays();
    }

    @GetMapping("/{date}")
    public Day getDayByDate(@PathVariable LocalDate date) {
        return dayService.getDayByDate(date);
    }

    @PostMapping
    public Day createDay(@RequestBody Day day) {
        return dayService.createDay(day);
    }

    @DeleteMapping("/{date}")
    public void deleteDay(@PathVariable LocalDate date) {
        dayService.deleteDay(date);
    }
}