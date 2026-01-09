package com.example.restaurant.services;

import com.example.restaurant.enums.CallStatus;
import com.example.restaurant.models.Client;
import com.example.restaurant.models.WaiterCall;
import com.example.restaurant.repositories.WaiterCallRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class WaiterCallService {

    private final WaiterCallRepository waiterCallRepository;

    @Autowired
    public WaiterCallService(WaiterCallRepository waiterCallRepository) {
        this.waiterCallRepository = waiterCallRepository;
    }

    @Transactional
    public void createCall(String tableNumber, Client client) {

        // 1. ПРОВЕРКА НА ДУБЛИКАТ
        // Если такой вызов уже активен, мы просто ничего не делаем (игнорируем запрос).
        // Это предотвращает создание лишних записей.
        boolean alreadyExists = waiterCallRepository.existsByTableNumberAndStatus(
                tableNumber,
                CallStatus.ACTIVE
        );

        if (alreadyExists) {
            // Можно добавить лог, если нужно
            return; // Выходим из метода, не создавая новую запись
        }

        // 2. Создание нового вызова (если дубликата нет)
        WaiterCall call = new WaiterCall();
        call.setTableNumber(tableNumber);
        call.setClient(client);
        call.setCallTime(LocalDateTime.now());
        call.setStatus(CallStatus.ACTIVE);
        waiterCallRepository.save(call);
    }

    public List<WaiterCall> getActiveCalls() {
        return waiterCallRepository.findByStatusOrderByCallTimeAsc(CallStatus.ACTIVE);
    }

    public Long getActiveCallsCount() {
        return waiterCallRepository.countByStatus(CallStatus.ACTIVE);
    }

    @Transactional
    public void resolveCall(Long id) {
        WaiterCall call = waiterCallRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Вызов не найден"));

        call.setStatus(CallStatus.CLOSED);
        call.setResolvedTime(LocalDateTime.now());
        waiterCallRepository.save(call);
    }
}