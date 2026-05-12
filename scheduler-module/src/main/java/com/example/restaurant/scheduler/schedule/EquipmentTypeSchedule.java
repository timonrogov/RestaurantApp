package com.example.restaurant.scheduler.schedule;

import com.example.restaurant.scheduler.messages.dto.PlacementVariant;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Расписание оборудования одного типа (например, всех духовок или всех грилей).
 *
 * Ключевое отличие от CookSchedule: один тип оборудования может обслуживать
 * несколько задач одновременно. Лимит параллельных задач — totalCapacity,
 * который считается как сумма maxParallelTasks всех единиц оборудования этого типа.
 *
 * Пример:
 *   Духовка №1: maxParallelTasks=3
 *   Духовка №2: maxParallelTasks=3
 *   Духовка №3: maxParallelTasks=2
 *   → totalCapacity = 8 (восемь блюд одновременно в духовках)
 *
 * Хранится в памяти агентом EquipmentTypeAgent.
 */
public class EquipmentTypeSchedule {

    /** Строковый код типа, например "OVEN", "GRILL", "FRYER". */
    private final String equipmentType;

    /**
     * Суммарная параллельная ёмкость всех единиц оборудования этого типа.
     * Вычисляется при инициализации: sum(equipment.maxParallelTasks) для всех
     * активных единиц данного типа.
     *
     * Когда единица оборудования ломается (EQUIPMENT_BROKEN), DispatcherAgent
     * вызывает decreaseCapacity(equipment.maxParallelTasks).
     * При починке — increaseCapacity(equipment.maxParallelTasks).
     */
    private int totalCapacity;

    /** Список всех занятых слотов этого типа оборудования. */
    private final List<ScheduleSlot> slots = new ArrayList<>();

    private final int equipmentSlotMaxIters;

    public EquipmentTypeSchedule(String equipmentType, int totalCapacity, int equipmentSlotMaxIters) {
        this.equipmentType = equipmentType;
        this.totalCapacity = totalCapacity;
        this.equipmentSlotMaxIters = equipmentSlotMaxIters;
    }

    // -----------------------------------------------------------------------
    // Проверка доступности
    // -----------------------------------------------------------------------

    /**
     * Доступно ли оборудование данного типа в интервале [from, to)?
     *
     * Подсчитывает число слотов, пересекающихся с запрошенным интервалом.
     * Если это число < totalCapacity — есть свободная «ячейка», возвращает true.
     *
     * @param from начало интервала
     * @param to   конец интервала
     * @return true если есть хотя бы одна свободная ячейка
     */
    public boolean isAvailable(LocalDateTime from, LocalDateTime to) {
        long overlappingCount = slots.stream()
                .filter(slot -> slot.overlapsWith(from, to))
                .count();
        return overlappingCount < totalCapacity;
    }

    /**
     * Найти ближайший момент начала, когда оборудование будет доступно
     * для слота длиной durationMinutes.
     *
     * Алгоритм похож на CookSchedule.findAsapSlot, но вместо «нет ни одного
     * конфликта» нужно «число конфликтов < totalCapacity».
     *
     * Алгоритм:
     *   1. Начать с notBefore
     *   2. Проверить: доступно ли [candidateStart, candidateStart+duration)?
     *   3. Если да — вернуть candidateStart
     *   4. Если нет — найти ближайший момент когда число конфликтов уменьшится
     *      (это endTime одного из перекрывающихся слотов) и повторить с шага 2
     *
     * @param durationMinutes требуемая длительность
     * @param notBefore       самое раннее время начала
     * @return предлагаемое время начала
     */
    public LocalDateTime findAvailableSlot(int durationMinutes, LocalDateTime notBefore) {
        LocalDateTime candidateStart = notBefore;

        // Защита от бесконечного цикла: максимум 1000 итераций
        // (на практике их будет 2-3, это просто страховка)
        for (int i = 0; i < equipmentSlotMaxIters; i++) {
            LocalDateTime candidateEnd = candidateStart.plusMinutes(durationMinutes);

            if (isAvailable(candidateStart, candidateEnd)) {
                return candidateStart; // Нашли!
            }

            // Не нашли. Ищем ближайший endTime среди конфликтующих слотов —
            // именно в этот момент одна «ячейка» освободится.
            LocalDateTime finalCandidateStart = candidateStart;
            LocalDateTime nextRelease = slots.stream()
                    .filter(slot -> slot.overlapsWith(finalCandidateStart, candidateEnd))
                    .map(ScheduleSlot::getEndTime)
                    .min(LocalDateTime::compareTo)
                    .orElse(null);

            if (nextRelease == null) {
                // Не должно случиться (isAvailable вернул false, значит конфликты есть),
                // но на всякий случай возвращаем candidateStart
                return candidateStart;
            }

            candidateStart = nextRelease;
        }

        // Если вышли из цикла — что-то пошло не так, возвращаем лучший известный вариант
        return candidateStart;
    }

    // -----------------------------------------------------------------------
    // Работа с конфликтами
    // -----------------------------------------------------------------------

    /**
     * Найти все слоты, пересекающиеся с интервалом [from, to).
     *
     * @param from начало интервала
     * @param to   конец интервала
     * @return список конфликтующих слотов
     */
    public List<ScheduleSlot> getConflicts(LocalDateTime from, LocalDateTime to) {
        return slots.stream()
                .filter(slot -> slot.overlapsWith(from, to))
                .collect(Collectors.toList());
    }

    // -----------------------------------------------------------------------
    // Управление слотами
    // -----------------------------------------------------------------------

    /**
     * Добавить слот в расписание.
     * Вызывается EquipmentTypeAgent после получения PLANNING_REQUEST.
     * Перед добавлением рекомендуется вызвать isAvailable() для проверки.
     *
     * @param slot слот для добавления
     */
    public void addSlot(ScheduleSlot slot) {
        slots.add(slot);
    }

    /**
     * Удалить слот по ID задачи.
     * Вызывается при вытеснении или отмене заказа.
     *
     * @param taskId ID задачи
     * @return true если слот был найден и удалён
     */
    public boolean removeSlotByTaskId(long taskId) {
        return slots.removeIf(slot -> slot.getTaskId() == taskId);
    }

    /**
     * Удалить все слоты, принадлежащие заданному заказу.
     * Вызывается при отмене заказа (ORDER_CANCELLED) для освобождения
     * всего зарезервированного оборудования.
     *
     * @param orderId ID заказа
     * @return число удалённых слотов
     */
    public int removeSlotsByOrderId(long orderId) {
        int sizeBefore = slots.size();
        slots.removeIf(slot -> slot.getOrderId() == orderId);
        return sizeBefore - slots.size();
    }

    // -----------------------------------------------------------------------
    // Управление ёмкостью (при поломке/починке оборудования)
    // -----------------------------------------------------------------------

    /**
     * Уменьшить суммарную ёмкость.
     * Вызывается DispatcherAgent при получении EQUIPMENT_BROKEN.
     * Если totalCapacity после уменьшения становится <= 0 — устанавливается в 0.
     *
     * @param amount на сколько уменьшить (= maxParallelTasks сломавшейся единицы)
     */
    public void decreaseCapacity(int amount) {
        totalCapacity = Math.max(0, totalCapacity - amount);
    }

    /**
     * Увеличить суммарную ёмкость.
     * Вызывается DispatcherAgent при получении EQUIPMENT_FIXED.
     *
     * @param amount на сколько увеличить (= maxParallelTasks починенной единицы)
     */
    public void increaseCapacity(int amount) {
        totalCapacity += amount;
    }

    // -----------------------------------------------------------------------
    // Геттеры и утилиты
    // -----------------------------------------------------------------------

    public String getEquipmentType() { return equipmentType; }
    public int getTotalCapacity() { return totalCapacity; }
    public List<ScheduleSlot> getSlots() { return slots; }

    /** Создать резервную копию слотов (для отката при вытеснении). */
    public List<ScheduleSlot> copySlots() {
        return new ArrayList<>(slots);
    }

    /** Восстановить слоты из резервной копии. */
    public void restoreSlots(List<ScheduleSlot> backup) {
        slots.clear();
        slots.addAll(backup);
    }

    /**
     * Текущее число одновременно используемых «ячеек» в момент now.
     * Для мониторинга и отладки.
     */
    public long getCurrentLoad() {
        LocalDateTime now = LocalDateTime.now();
        return slots.stream()
                .filter(slot -> slot.overlapsWith(now, now.plusMinutes(1)))
                .count();
    }

    @Override
    public String toString() {
        return "EquipmentTypeSchedule{type='" + equipmentType +
                "', capacity=" + totalCapacity +
                ", slots=" + slots.size() + "}";
    }
}