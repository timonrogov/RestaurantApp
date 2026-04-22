package com.example.restaurant.controllers;

import com.example.restaurant.dto.CookingTaskDto;
import com.example.restaurant.repositories.CookProfileRepository;
import com.example.restaurant.repositories.EmployeeRepository;
import com.example.restaurant.scheduler.service.SchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Controller
@RequestMapping("/kds")
@RequiredArgsConstructor
public class KdsController {

    private final SchedulerService schedulerService;
    private final EmployeeRepository employeeRepository;
    private final CookProfileRepository cookProfileRepository;

    @GetMapping
    public String kdsPage(Model model, Principal principal) {
        Long cookProfileId = resolveCookProfileId(principal);
        if (cookProfileId == null) return "redirect:/admin/orders";

        cookProfileRepository.findById(cookProfileId).ifPresent(profile -> {
            model.addAttribute("cookName",        profile.getEmployee().getFullName());
            model.addAttribute("specialization",  profile.getSpecialization().getDisplayName());
            model.addAttribute("cookProfileId",   cookProfileId);
        });
        return "kds";
    }

    @GetMapping("/api/tasks")
    @ResponseBody
    public List<CookingTaskDto> getTasks(Principal principal) {
        Long id = resolveCookProfileId(principal);
        if (id == null) return List.of();
        return schedulerService.getTasksForCook(id).stream().map(CookingTaskDto::from).toList();
    }

    @PostMapping("/api/tasks/{taskId}/start")
    @ResponseBody
    public ResponseEntity<?> startTask(@PathVariable Long taskId) {
        try { schedulerService.markTaskStarted(taskId); return ResponseEntity.ok(Map.of("success", true)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    @PostMapping("/api/tasks/{taskId}/done")
    @ResponseBody
    public ResponseEntity<?> completeTask(@PathVariable Long taskId) {
        try { schedulerService.markTaskDone(taskId); return ResponseEntity.ok(Map.of("success", true)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    @PostMapping("/api/tasks/{taskId}/delay")
    @ResponseBody
    public ResponseEntity<?> reportDelay(@PathVariable Long taskId,
                                         @RequestParam int delayMinutes,
                                         @RequestParam(required = false) String reason) {
        try { schedulerService.reportDelay(taskId, delayMinutes, reason); return ResponseEntity.ok(Map.of("success", true)); }
        catch (Exception e) { return ResponseEntity.badRequest().body(Map.of("error", e.getMessage())); }
    }

    private Long resolveCookProfileId(Principal principal) {
        if (principal == null) return null;
        return employeeRepository.findByAccountUsername(principal.getName())
                .flatMap(emp -> cookProfileRepository.findByEmployeeId(emp.getId()))
                .map(p -> p.getId())
                .orElse(null);
    }
}