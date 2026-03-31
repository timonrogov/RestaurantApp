package com.example.restaurant.repositories;

import com.example.restaurant.models.Equipment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EquipmentRepository extends JpaRepository<Equipment, Long> {

    /**
     * Найти всё активное оборудование нужного типа.
     * Вызывается планировщиком при поиске ресурсов для задачи, требующей оборудование.
     */
    List<Equipment> findByEquipmentTypeAndIsActiveTrue(String equipmentType);

    /**
     * Найти всё работающее оборудование.
     * Используется при инициализации планировщика.
     */
    List<Equipment> findByIsActiveTrue();
}