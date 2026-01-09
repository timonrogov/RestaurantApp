package com.example.restaurant.controllers.api;

import com.example.restaurant.services.WaiterCallService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/calls")
@PreAuthorize("hasAnyRole('WAITER', 'ADMIN')")
public class StaffCallApiController {

    private final WaiterCallService waiterCallService;

    @Autowired
    public StaffCallApiController(WaiterCallService waiterCallService) {
        this.waiterCallService = waiterCallService;
    }

    // Метод для получения количества активных вызовов (для бейджика)
    @GetMapping("/count")
    public ResponseEntity<Long> getActiveCallsCount() {
        return ResponseEntity.ok(waiterCallService.getActiveCallsCount());
    }

    // Метод для завершения вызова (AJAX)
    @PostMapping("/{id}/close")
    public ResponseEntity<String> closeCall(@PathVariable Long id) {
        waiterCallService.resolveCall(id);
        return ResponseEntity.ok("Вызов закрыт");
    }
}