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
    public String clientComment;    // из OrderItem.comment, null если нет
    public int    courseNumber;
    public boolean urgent;          // minutesLeft <= 5 и не DONE
    public boolean overdue;         // minutesLeft < 0 и не DONE

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

        if (task.getPlannedStartTime() != null)
            dto.plannedStartTime = task.getPlannedStartTime().format(TIME_FMT);
        if (task.getPlannedEndTime() != null) {
            dto.plannedEndTime = task.getPlannedEndTime().format(TIME_FMT);
            dto.minutesLeft = ChronoUnit.MINUTES.between(LocalDateTime.now(), task.getPlannedEndTime());
        }

        boolean notDone = !"DONE".equals(dto.status) && !"CANCELLED".equals(dto.status);
        dto.overdue = notDone && dto.minutesLeft < 0;
        dto.urgent  = notDone && !dto.overdue && dto.minutesLeft <= 5;
        return dto;
    }
}