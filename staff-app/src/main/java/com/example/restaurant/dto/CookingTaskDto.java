package com.example.restaurant.dto;

import com.example.restaurant.models.CookingTask;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Облегчённый объект задачи для передачи на KDS-фронтенд.
 * Содержит только то, что нужно JavaScript — без JPA-ссылок и лишних полей.
 */
public class CookingTaskDto {

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    public long   id;
    public String status;           // "PLANNED", "IN_PROGRESS", "DONE"
    public long   orderId;
    public String tableNumber;
    public String dishName;
    public String stepName;
    public int    stepNumber;
    public String plannedStartTime; // "14:30"
    public String plannedEndTime;   // "14:45"
    public long   minutesLeft;      // отрицательно если просрочена
    public int totalDelayMinutes; // Сдвиг от изначального плана
    public String clientComment;    // из OrderItem.comment, null если нет
    public int    courseNumber;
    public boolean urgent;          // minutesLeft <= 5 и не DONE
    public boolean overdue;         // minutesLeft < 0 и не DONE
    public int portionCount;     // сколько порций в этой партии (1 = одна порция)

    public static CookingTaskDto from(CookingTask task) {
        CookingTaskDto dto = new CookingTaskDto();
        dto.id     = task.getId();
        dto.status = task.getStatus().name();
        dto.orderId       = task.getOrderItem().getOrder().getId();
        dto.tableNumber   = task.getOrderItem().getOrder().getTableNumber();
        dto.dishName      = task.getOrderItem().getDish().getName();
        dto.stepName      = task.getTemplate().getStepName();
        dto.stepNumber    = task.getTemplate().getStepNumber();
        dto.clientComment = task.getOrderItem().getComment();
        dto.courseNumber  = task.getOrderItem().getCourseNumber();
        dto.portionCount  = task.getPortionCount();

        if (task.getPlannedStartTime() != null)
            dto.plannedStartTime = task.getPlannedStartTime().format(TIME_FMT);
        if (task.getPlannedEndTime() != null) {
            dto.plannedEndTime = task.getPlannedEndTime().format(TIME_FMT);
            dto.minutesLeft = ChronoUnit.MINUTES.between(LocalDateTime.now(), task.getPlannedEndTime());
        }

        // Если сдвинулся старт, то сдвинется и конец.
        // Если старт не сдвинулся (задача выполняется), но затянулась готовка — конец тоже сдвинется.
        // Поэтому достаточно сравнивать только время окончания!
        if (task.getInitialPlannedEndTime() != null && task.getPlannedEndTime() != null) {
            dto.totalDelayMinutes = (int) ChronoUnit.MINUTES.between(
                    task.getInitialPlannedEndTime(),
                    task.getPlannedEndTime()
            );
        }

        boolean notDone = !"DONE".equals(dto.status) && !"CANCELLED".equals(dto.status) && !"FAILED".equals(dto.status);

        if (notDone) {
            LocalDateTime now = LocalDateTime.now();
            boolean isCurrentlyOverdue = false;

            // Мгновенная проверка (работает до того, как сработает фоновый крон)
            if ("PLANNED".equals(dto.status) || "PENDING".equals(dto.status)) {
                isCurrentlyOverdue = task.getPlannedStartTime() != null && now.isAfter(task.getPlannedStartTime());
            } else if ("IN_PROGRESS".equals(dto.status)) {
                isCurrentlyOverdue = task.getPlannedEndTime() != null && now.isAfter(task.getPlannedEndTime());
            }

            // Задача просрочена, если текущее время прямо сейчас больше планового
            // ИЛИ если планировщик уже успел сдвинуть время и поставил флаг вины повара
            dto.overdue = task.isLocalOverdue() || isCurrentlyOverdue;
        } else {
            dto.overdue = false;
        }

        // Если задача просрочена, она уже не считается просто "срочной"
        dto.urgent = notDone && !dto.overdue && dto.minutesLeft <= 5 && dto.minutesLeft >= 0;

        return dto;
    }
}