package com.example.restaurant.controllers;

import com.example.restaurant.services.WaiterCallService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

@Controller
@RequestMapping("/admin/calls")
@PreAuthorize("hasAnyRole('WAITER', 'ADMIN')") // Поварам это не нужно
public class StaffCallController {

    private final WaiterCallService waiterCallService;

    @Autowired
    public StaffCallController(WaiterCallService waiterCallService) {
        this.waiterCallService = waiterCallService;
    }

    @GetMapping
    public String viewCalls(Model model) {
        model.addAttribute("calls", waiterCallService.getActiveCalls());
        return "admin-calls"; // Этот шаблон создадим на следующем этапе
    }
}