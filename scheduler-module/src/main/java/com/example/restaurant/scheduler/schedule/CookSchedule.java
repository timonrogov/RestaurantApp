package com.example.restaurant.scheduler.schedule;

import com.example.restaurant.scheduler.messages.dto.PlacementVariant;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Расписание одного повара — список занятых временных слотов.
 *
 * Хранится в памяти агентом CookAgent. Не является JPA-сущностью.
 * Отражает только PLANNED и IN_PROGRESS задачи — выполненные (DONE) из
 * расписания можно не удалять сразу, они просто не мешают новым слотам
 * (их endTime уже в прошлом).
 *
 * Все методы работают с временны́м окном в будущем. Слоты из прошлого
 * автоматически игнорируются при поиске свободных мест (они за пределами
 * notBefore).
 */
public class CookSchedule {

    private final long cookId;

    /** Список занятых слотов. Порядок не гарантирован — сортируем при необходимости. */
    private final List<ScheduleSlot> slots = new ArrayList<>();

    public CookSchedule(long cookId) {
        this.cookId = cookId;
    }

    // -----------------------------------------------------------------------
    // Поиск вариантов размещения
    // -----------------------------------------------------------------------

    /**
     * Найти первый свободный слот длиной durationMinutes, начиная не раньше notBefore.
     * Стратегия ASAP (as soon as possible).
     *
     * Алгоритм:
     *   1. Взять все слоты, чей endTime > notBefore (актуальные)
     *   2. Отсортировать по startTime
     *   3. Перебирать промежутки:
     *      - до первого слота: [notBefore, slot[0].start)
     *      - между слотами: [slot[i].end, slot[i+1].start)
     *      - после последнего: [slot[last].end, +∞)
     *   4. Если промежуток >= durationMinutes — вернуть его начало
     *
     * @param durationMinutes требуемая длительность задачи
     * @param notBefore       самое раннее возможное время начала
     * @return предлагаемое время начала или null если свободного места нет в обозримом будущем
     */
    public LocalDateTime findAsapSlot(int durationMinutes, LocalDateTime notBefore) {
        // Берём слоты, которые ещё актуальны (не завершились до notBefore)
        List<ScheduleSlot> relevantSlots = slots.stream()
                .filter(s -> s.getEndTime().isAfter(notBefore))
                .sorted(Comparator.comparing(ScheduleSlot::getStartTime))
                .collect(Collectors.toList());

        // Пробуем поставить задачу в самом начале — до первого слота
        LocalDateTime candidateStart = notBefore;

        for (ScheduleSlot slot : relevantSlots) {
            // Если candidateStart находится внутри текущего слота — сдвигаем
            // candidateStart на конец этого слота
            if (!candidateStart.isBefore(slot.getStartTime())) {
                // Слот начинается раньше или в момент candidateStart — он нам мешает
                if (slot.getEndTime().isAfter(candidateStart)) {
                    candidateStart = slot.getEndTime();
                }
                continue;
            }

            // candidateStart < slot.startTime — есть промежуток перед этим слотом.
            // Проверяем: влезает ли задача в промежуток [candidateStart, slot.startTime)?
            long gapMinutes = ChronoUnit.MINUTES.between(candidateStart, slot.getStartTime());
            if (gapMinutes >= durationMinutes) {
                return candidateStart; // Нашли место!
            }

            // Промежуток слишком мал — сдвигаемся за конец текущего слота
            candidateStart = slot.getEndTime();
        }

        // После всех слотов — возвращаем candidateStart (место после последнего слота)
        return candidateStart;
    }

    /**
     * Найти JIT-слот: такое время начала, чтобы задача завершилась ровно к deadline.
     * Стратегия JIT (just-in-time) — задача начинается как можно позже.
     *
     * Вычисляет idealStart = deadline - durationMinutes.
     * Проверяет, что интервал [idealStart, deadline) не пересекается ни с одним слотом.
     * Также проверяет что idealStart >= notBefore.
     *
     * @param durationMinutes требуемая длительность
     * @param deadline        крайний срок — задача должна завершиться не позже
     * @param notBefore       самое раннее время начала
     * @return предлагаемое время начала (= deadline - duration) или null если не подходит
     */
    public LocalDateTime findJitSlot(int durationMinutes, LocalDateTime deadline,
                                     LocalDateTime notBefore) {
        LocalDateTime idealStart = deadline.minusMinutes(durationMinutes);

        // JIT недостижим если idealStart раньше notBefore
        if (idealStart.isBefore(notBefore)) {
            return null;
        }

        // Проверяем что этот интервал не конфликтует ни с кем
        List<ScheduleSlot> conflicts = getConflicts(idealStart, deadline);
        if (conflicts.isEmpty()) {
            return idealStart;
        }

        return null;
    }

    // -----------------------------------------------------------------------
    // Работа с конфликтами
    // -----------------------------------------------------------------------

    /**
     * Найти все слоты, пересекающиеся с интервалом [from, to).
     * Используется при поиске конфликтного варианта размещения (вытеснение).
     *
     * @param from начало интервала (включительно)
     * @param to   конец интервала (не включая)
     * @return список конфликтующих слотов (может быть пустым)
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
     * Добавить занятый слот в расписание.
     * Вызывается CookAgent после получения PLANNING_REQUEST и принятия решения
     * о резервировании (до отправки PLANNING_RESPONSE).
     *
     * @param slot слот для добавления
     */
    public void addSlot(ScheduleSlot slot) {
        slots.add(slot);
    }

    /**
     * Удалить слот по ID задачи.
     * Вызывается при вытеснении: CookAgent удаляет слот конкурирующей задачи,
     * добавляет новый слот и отправляет REMOVE_TASK вытесненному TaskAgent-у.
     *
     * @param taskId ID задачи, чей слот нужно удалить
     * @return true если слот был найден и удалён, false если не найден
     */
    public boolean removeSlotByTaskId(long taskId) {
        return slots.removeIf(slot -> slot.getTaskId() == taskId);
    }

    /**
     * Найти слот по ID задачи.
     * Используется CookAgent для получения деталей конкурирующей задачи
     * перед принятием решения о вытеснении (нужно знать orderId конкурента).
     *
     * @param taskId ID задачи
     * @return слот или пустой Optional если не найден
     */
    public Optional<ScheduleSlot> findByTaskId(long taskId) {
        return slots.stream()
                .filter(slot -> slot.getTaskId() == taskId)
                .findFirst();
    }

    // -----------------------------------------------------------------------
    // Оценка нагрузки
    // -----------------------------------------------------------------------

    /**
     * Доля занятого времени за ближайшие windowMinutes минут.
     * Используется TaskAgent при расчёте loadScore для выбора менее загруженного повара.
     *
     * Формула: суммарное занятое время в окне / размер окна.
     * Результат от 0.0 (повар полностью свободен) до 1.0 (полностью занят).
     *
     * @param windowMinutes размер временного окна в минутах (обычно 60)
     * @return значение от 0.0 до 1.0
     */
    public double getOccupancyRate(int windowMinutes) {
        if (windowMinutes <= 0) return 0.0;

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime windowEnd = now.plusMinutes(windowMinutes);

        // Считаем суммарное занятое время в окне [now, windowEnd)
        long occupiedMinutes = slots.stream()
                .filter(slot -> slot.overlapsWith(now, windowEnd))
                .mapToLong(slot -> {
                    // Обрезаем слот по границам окна
                    LocalDateTime effectiveStart = slot.getStartTime().isBefore(now)
                            ? now : slot.getStartTime();
                    LocalDateTime effectiveEnd = slot.getEndTime().isAfter(windowEnd)
                            ? windowEnd : slot.getEndTime();
                    return ChronoUnit.MINUTES.between(effectiveStart, effectiveEnd);
                })
                .sum();

        return Math.min(1.0, (double) occupiedMinutes / windowMinutes);
    }

    // -----------------------------------------------------------------------
    // Геттеры и утилиты
    // -----------------------------------------------------------------------

    public long getCookId() { return cookId; }

    /** Все текущие слоты (для создания резервной копии при вытеснении). */
    public List<ScheduleSlot> getSlots() { return slots; }

    /**
     * Создать копию текущего списка слотов.
     * CookAgent использует её перед вытеснением для возможного отката:
     * {@code List<ScheduleSlot> backup = schedule.copySlots();}
     * Если вытеснение провалилось — {@code schedule.restoreSlots(backup)}.
     */
    public List<ScheduleSlot> copySlots() {
        return new ArrayList<>(slots);
    }

    /**
     * Восстановить список слотов из резервной копии.
     * Вызывается при откате неудавшегося вытеснения.
     *
     * @param backup копия слотов, созданная через copySlots()
     */
    public void restoreSlots(List<ScheduleSlot> backup) {
        slots.clear();
        slots.addAll(backup);
    }

    /**
     * Удалить все слоты, принадлежащие заданному заказу.
     * Вызывается DispatcherAgent при отмене заказа.
     *
     * @param orderId ID заказа
     * @return true если хотя бы один слот был удалён
     */
    public boolean removeSlotByOrderId(long orderId) {
        int sizeBefore = slots.size();
        slots.removeIf(slot -> slot.getOrderId() == orderId);
        return slots.size() < sizeBefore;
    }

    @Override
    public String toString() {
        return "CookSchedule{cookId=" + cookId + ", slots=" + slots.size() + "}";
    }
}