package com.example.restaurant.repositories;

import com.example.restaurant.models.Day;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

@Repository
public interface DayRepository extends JpaRepository<Day, LocalDate> {
    Optional<Day> findByWorkDate(LocalDate date);
}
