package com.example.restaurant.controllers;

import com.example.restaurant.enums.CookingTaskStatus;
import com.example.restaurant.models.CookProfile;
import com.example.restaurant.models.CookingTask;
import com.example.restaurant.models.Equipment;
import com.example.restaurant.repositories.CookProfileRepository;
import com.example.restaurant.repositories.CookingTaskRepository;
import com.example.restaurant.repositories.DayRepository;
import com.example.restaurant.repositories.EquipmentRepository;
import com.example.restaurant.scheduler.service.SchedulerService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import com.example.restaurant.dto.GanttDataDto;
import com.example.restaurant.dto.GanttCookDto;
import com.example.restaurant.dto.GanttTaskDto;
import com.example.restaurant.enums.CookingTaskStatus;
import com.example.restaurant.models.Day;
import com.example.restaurant.repositories.DayRepository;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Controller
@RequestMapping("/admin/scheduler")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class SchedulerAdminController {

    private final SchedulerService schedulerService;
    private final CookingTaskRepository taskRepository;
    private final DayRepository dayRepository;
    private final CookProfileRepository cookProfileRepository;
    private final EquipmentRepository equipmentRepository;

    @GetMapping
    public String schedulerDashboard(Model model) {
        List<CookingTask> activeTasks = schedulerService.getAllActiveTasksSummary();

        // Группировка задач по заказу
        Map<Long, List<CookingTask>> tasksByOrder = activeTasks.stream()
                .collect(Collectors.groupingBy(t -> t.getOrderItem().getOrder().getId()));

        // Просроченные задачи
        List<CookingTask> overdueTasks = activeTasks.stream()
                .filter(t -> t.getPlannedEndTime() != null
                        && t.getPlannedEndTime().isBefore(LocalDateTime.now())
                        && t.getStatus() != CookingTaskStatus.DONE)
                .toList();

        // Загруженность поваров
        List<CookProfile> activeCooks = cookProfileRepository.findByIsActiveTrue();
        Map<Long, Long> taskCountByCook = activeTasks.stream()
                .filter(t -> t.getAssignedCook() != null)
                .collect(Collectors.groupingBy(t -> t.getAssignedCook().getId(),
                        Collectors.counting()));

        model.addAttribute("activeTasks",    activeTasks);
        model.addAttribute("tasksByOrder",   tasksByOrder);
        model.addAttribute("overdueTasks",   overdueTasks);
        model.addAttribute("activeCooks",    activeCooks);
        model.addAttribute("taskCountByCook", taskCountByCook);
        model.addAttribute("equipment",      equipmentRepository.findAll());
        return "admin-scheduler";
    }

    /** Экстренное: пометить повара недоступным. */
    @PostMapping("/cook/{cookId}/unavailable")
    public String cookUnavailable(@PathVariable Long cookId,
                                  RedirectAttributes redirectAttributes) {
        CookProfile profile = cookProfileRepository.findById(cookId)
                .orElseThrow(() -> new RuntimeException("Повар не найден"));
        profile.setActive(false);
        cookProfileRepository.save(profile);
        schedulerService.markCookUnavailable(cookId);
        redirectAttributes.addFlashAttribute("success",
                "Повар помечен недоступным, задачи перепланируются.");
        return "redirect:/admin/scheduler";
    }

    /** Экстренное: пометить оборудование сломанным. */
    @PostMapping("/equipment/{equipmentId}/broken")
    public String equipmentBroken(@PathVariable Long equipmentId,
                                  RedirectAttributes redirectAttributes) {
        Equipment equipment = equipmentRepository.findById(equipmentId)
                .orElseThrow(() -> new RuntimeException("Оборудование не найдено"));
        equipment.setActive(false);
        equipmentRepository.save(equipment);
        schedulerService.markEquipmentBroken(equipmentId);
        redirectAttributes.addFlashAttribute("success",
                "Оборудование помечено как неисправное, задачи перепланируются.");
        return "redirect:/admin/scheduler";
    }

    /**
     * REST-эндпоинт для AJAX-обновления данных планировщика.
     * Вызывается фронтендом при получении WebSocket-события /topic/scheduler.
     */
    @GetMapping("/api/stats")
    @ResponseBody
    public Map<String, Object> getSchedulerStats() {
        List<CookingTask> activeTasks = schedulerService.getAllActiveTasksSummary();

        long overdueCount = activeTasks.stream()
                .filter(t -> t.getPlannedEndTime() != null
                        && t.getPlannedEndTime().isBefore(LocalDateTime.now())
                        && t.getStatus() != CookingTaskStatus.DONE)
                .count();

        long ordersInWork = activeTasks.stream()
                .map(t -> t.getOrderItem().getOrder().getId())
                .distinct().count();

        return Map.of(
                "activeTasks",  activeTasks.size(),
                "ordersInWork", ordersInWork,
                "overdue",      overdueCount
        );
    }

    /**
     * REST-эндпоинт диаграммы Ганта.
     *
     * Возвращает задачи за указанный день, сгруппированные по поварам.
     * Если дата не передана — используется сегодня.
     *
     * @param date дата в формате YYYY-MM-DD (опционально)
     */
    @GetMapping("/api/gantt")
    @ResponseBody
    public GanttDataDto getGanttData(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {

        if (date == null) date = LocalDate.now();

        final LocalDate finalDate = date;
        boolean isToday = finalDate.equals(LocalDate.now());

        // ─── 1. Определяем рабочее время дня ────────────────────────────
        LocalTime workStart = LocalTime.of(9, 0);
        LocalTime workEnd   = LocalTime.of(22, 0);

        Day day = dayRepository.findById(finalDate).orElse(null);
        if (day != null && day.isWorkingDay()) {
            workStart = day.getStartTime();
            workEnd   = day.getEndTime();
        }

        LocalDateTime dayStart = finalDate.atTime(workStart);
        LocalDateTime dayEnd   = finalDate.atTime(workEnd);

        // ─── 2. Загружаем задачи за этот день ───────────────────────────
        List<CookingTask> tasks = taskRepository.findForGantt(dayStart, dayEnd);

        // ─── 3. Загружаем всех активных поваров ─────────────────────────
        List<CookProfile> cooks = cookProfileRepository.findByIsActiveTrue();

        // ─── 4. Группируем задачи по поварам ────────────────────────────
        Map<Long, List<CookingTask>> tasksByCook = tasks.stream()
                .filter(t -> t.getAssignedCook() != null)
                .collect(Collectors.groupingBy(t -> t.getAssignedCook().getId()));

        // ─── 5. Формируем DTO ────────────────────────────────────────────
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm");

        List<GanttCookDto> cookDtos = cooks.stream().map(cook -> {
            List<GanttTaskDto> taskDtos = tasksByCook
                    .getOrDefault(cook.getId(), List.of())
                    .stream()
                    .map(t -> toGanttTaskDto(t, timeFmt))
                    .toList();

            return new GanttCookDto(
                    cook.getId(),
                    cook.getEmployee().getFullName(),
                    cook.getSpecialization().getDisplayName(),
                    taskDtos
            );
        }).toList();

        String currentTime = isToday
                ? LocalTime.now().format(timeFmt)
                : null;

        return new GanttDataDto(
                finalDate.toString(),
                workStart.format(timeFmt),
                workEnd.format(timeFmt),
                currentTime,
                isToday,
                cookDtos
        );
    }

    /** Конвертирует CookingTask в GanttTaskDto. */
    private GanttTaskDto toGanttTaskDto(CookingTask task, DateTimeFormatter fmt) {
        GanttTaskDto dto = new GanttTaskDto();

        dto.setId(task.getId());
        dto.setOrderId(task.getOrderItem().getOrder().getId());
        dto.setTableNumber(task.getOrderItem().getOrder().getTableNumber());
        dto.setDishName(task.getOrderItem().getDish().getName());
        dto.setDishImagePath(task.getOrderItem().getDish().getImagePath());
        dto.setStepName(task.getTemplate().getStepName());
        dto.setStepNumber(task.getTemplate().getStepNumber());
        dto.setStatus(task.getStatus().name());
        dto.setStatusDisplay(task.getStatus().getDisplayName());
        dto.setEquipmentType(task.getAssignedEquipmentType());
        dto.setDurationMinutes(task.getTemplate().getDurationMinutes());
        dto.setOverdue(task.isOverdue());
        dto.setDelayReason(task.getDelayReason());
        dto.setReplanCount(task.getReplanCount());

        if (task.getPlannedStartTime() != null)
            dto.setPlannedStart(task.getPlannedStartTime().format(fmt));
        if (task.getPlannedEndTime() != null)
            dto.setPlannedEnd(task.getPlannedEndTime().format(fmt));
        if (task.getActualStartTime() != null)
            dto.setActualStart(task.getActualStartTime().format(fmt));
        if (task.getActualEndTime() != null)
            dto.setActualEnd(task.getActualEndTime().format(fmt));

        return dto;
    }
}