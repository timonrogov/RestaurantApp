package com.example.restaurant.scheduler.schedule;

import com.example.restaurant.scheduler.messages.dto.PlacementVariant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты для CookSchedule.
 * Проверяют математику поиска слотов изолированно — без Spring-контекста.
 */
class CookScheduleTest {

    private CookSchedule schedule;

    // Фиксированная точка отсчёта для всех тестов
    private static final LocalDateTime BASE = LocalDateTime.of(2025, 1, 1, 10, 0);

    @BeforeEach
    void setUp() {
        schedule = new CookSchedule(1L);
    }

    // -----------------------------------------------------------------------
    // findAsapSlot
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("findAsapSlot: пустое расписание → возвращает notBefore")
    void findAsapSlot_emptySchedule_returnsNotBefore() {
        LocalDateTime result = schedule.findAsapSlot(15, BASE);
        assertThat(result).isEqualTo(BASE);
    }

    @Test
    @DisplayName("findAsapSlot: один слот посередине → возвращает время после него")
    void findAsapSlot_oneSlotInMiddle_returnsAfterSlot() {
        // Слот занимает [10:20 → 10:40]
        addSlot(BASE.plusMinutes(20), BASE.plusMinutes(40));

        // Запрашиваем 15 минут с 10:00
        // Перед слотом есть [10:00 → 10:20] = 20 мин ≥ 15 → должен вернуть 10:00
        LocalDateTime result = schedule.findAsapSlot(15, BASE);
        assertThat(result).isEqualTo(BASE);
    }

    @Test
    @DisplayName("findAsapSlot: промежуток между двумя слотами слишком мал → возвращает время после второго")
    void findAsapSlot_gapTooSmall_returnsAfterSecondSlot() {
        // [10:00 → 10:20] и [10:25 → 10:50]
        // Промежуток [10:20 → 10:25] = 5 мин < 15
        addSlot(BASE, BASE.plusMinutes(20));
        addSlot(BASE.plusMinutes(25), BASE.plusMinutes(50));

        LocalDateTime result = schedule.findAsapSlot(15, BASE);
        // После обоих слотов: 10:50
        assertThat(result).isEqualTo(BASE.plusMinutes(50));
    }

    @Test
    @DisplayName("findAsapSlot: notBefore находится внутри занятого слота → начинаем с конца слота")
    void findAsapSlot_notBeforeInsideSlot_returnsAfterSlot() {
        // Слот [10:00 → 10:30]
        addSlot(BASE, BASE.plusMinutes(30));

        // notBefore = 10:15 (внутри слота)
        LocalDateTime result = schedule.findAsapSlot(15, BASE.plusMinutes(15));
        // Должен начаться с 10:30 (конец слота)
        assertThat(result).isEqualTo(BASE.plusMinutes(30));
    }

    @Test
    @DisplayName("findAsapSlot: несколько слотов подряд → возвращает первый свободный промежуток")
    void findAsapSlot_multipleConsecutiveSlots_returnsFirstFreeGap() {
        // [10:00 → 10:15], [10:15 → 10:30], [10:30 → 10:50]
        addSlot(BASE, BASE.plusMinutes(15));
        addSlot(BASE.plusMinutes(15), BASE.plusMinutes(30));
        addSlot(BASE.plusMinutes(30), BASE.plusMinutes(50));

        // Нужно 20 мин, первый свободный — после 10:50
        LocalDateTime result = schedule.findAsapSlot(20, BASE);
        assertThat(result).isEqualTo(BASE.plusMinutes(50));
    }

    // -----------------------------------------------------------------------
    // findJitSlot
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("findJitSlot: свободный интервал → idealStart = deadline - duration")
    void findJitSlot_freeSlot_returnsIdealStart() {
        LocalDateTime deadline = BASE.plusMinutes(60);
        // Нужно 15 мин → idealStart = 10:45
        LocalDateTime result = schedule.findJitSlot(15, deadline, BASE);
        assertThat(result).isEqualTo(BASE.plusMinutes(45));
    }

    @Test
    @DisplayName("findJitSlot: idealStart раньше notBefore → возвращает null")
    void findJitSlot_idealStartBeforeNotBefore_returnsNull() {
        LocalDateTime notBefore = BASE.plusMinutes(50);
        LocalDateTime deadline  = BASE.plusMinutes(60);
        // idealStart = deadline - 30 = 10:30 < notBefore(10:50) → null
        LocalDateTime result = schedule.findJitSlot(30, deadline, notBefore);
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("findJitSlot: конфликт с существующим слотом → возвращает null")
    void findJitSlot_conflictsWithExistingSlot_returnsNull() {
        // Слот [10:40 → 10:55]
        addSlot(BASE.plusMinutes(40), BASE.plusMinutes(55));

        LocalDateTime deadline = BASE.plusMinutes(60);
        // idealStart = 10:45, интервал [10:45 → 11:00] конфликтует со [10:40 → 10:55]
        LocalDateTime result = schedule.findJitSlot(15, deadline, BASE);
        assertThat(result).isNull();
    }

    // -----------------------------------------------------------------------
    // getConflicts
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getConflicts: смежные слоты НЕ считаются конфликтом")
    void getConflicts_adjacentSlots_noConflict() {
        // Слот заканчивается в 10:30, запрос начинается в 10:30
        addSlot(BASE, BASE.plusMinutes(30));

        List<ScheduleSlot> conflicts = schedule.getConflicts(BASE.plusMinutes(30), BASE.plusMinutes(45));
        assertThat(conflicts).isEmpty();
    }

    @Test
    @DisplayName("getConflicts: частичное перекрытие → конфликт")
    void getConflicts_partialOverlap_conflict() {
        // Слот [10:20 → 10:50]
        addSlot(BASE.plusMinutes(20), BASE.plusMinutes(50));

        // Запрос [10:10 → 10:30] перекрывается
        List<ScheduleSlot> conflicts = schedule.getConflicts(BASE.plusMinutes(10), BASE.plusMinutes(30));
        assertThat(conflicts).hasSize(1);
    }

    @Test
    @DisplayName("getConflicts: слот целиком внутри запроса → конфликт")
    void getConflicts_slotInsideRequest_conflict() {
        // Слот [10:15 → 10:25] внутри запроса [10:10 → 10:30]
        addSlot(BASE.plusMinutes(15), BASE.plusMinutes(25));

        List<ScheduleSlot> conflicts = schedule.getConflicts(BASE.plusMinutes(10), BASE.plusMinutes(30));
        assertThat(conflicts).hasSize(1);
    }

    @Test
    @DisplayName("getConflicts: несколько слотов, только часть конфликтует")
    void getConflicts_multipleSlots_onlyConflictingReturned() {
        addSlot(BASE, BASE.plusMinutes(20));               // [10:00 → 10:20] — до запроса
        addSlot(BASE.plusMinutes(25), BASE.plusMinutes(35)); // [10:25 → 10:35] — конфликт
        addSlot(BASE.plusMinutes(50), BASE.plusMinutes(60)); // [10:50 → 11:00] — после запроса

        List<ScheduleSlot> conflicts = schedule.getConflicts(BASE.plusMinutes(20), BASE.plusMinutes(40));
        assertThat(conflicts).hasSize(1);
        assertThat(conflicts.get(0).getStartTime()).isEqualTo(BASE.plusMinutes(25));
    }

    // -----------------------------------------------------------------------
    // getOccupancyRate
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("getOccupancyRate: нет задач → 0.0")
    void getOccupancyRate_empty_returnsZero() {
        assertThat(schedule.getOccupancyRate(60)).isEqualTo(0.0);
    }

    @Test
    @DisplayName("getOccupancyRate: полностью занятое окно → 1.0")
    void getOccupancyRate_fullyOccupied_returnsOne() {
        // Слот на 60 минут начиная с now
        LocalDateTime now = LocalDateTime.now();
        ScheduleSlot slot = new ScheduleSlot(99L, 99L, now, now.plusMinutes(60), null);
        schedule.addSlot(slot);

        double rate = schedule.getOccupancyRate(60);
        assertThat(rate).isCloseTo(1.0, org.assertj.core.data.Offset.offset(0.05));
    }

    @Test
    @DisplayName("getOccupancyRate: половина окна занята → ~0.5")
    void getOccupancyRate_halfOccupied_returnsHalf() {
        LocalDateTime now = LocalDateTime.now();
        // Занимаем 30 минут из 60
        ScheduleSlot slot = new ScheduleSlot(99L, 99L, now, now.plusMinutes(30), null);
        schedule.addSlot(slot);

        double rate = schedule.getOccupancyRate(60);
        assertThat(rate).isBetween(0.4, 0.6);
    }

    // -----------------------------------------------------------------------
    // copySlots / restoreSlots (откат при вытеснении)
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("copySlots + restoreSlots: откат корректен")
    void copyAndRestore_rollbackWorks() {
        addSlot(BASE, BASE.plusMinutes(30));

        List<ScheduleSlot> backup = schedule.copySlots();
        assertThat(backup).hasSize(1);

        // Добавляем ещё один и удаляем первый
        addSlot(BASE.plusMinutes(40), BASE.plusMinutes(55));
        schedule.removeSlotByTaskId(1L);

        // Откатываем
        schedule.restoreSlots(backup);
        assertThat(schedule.getSlots()).hasSize(1);
        assertThat(schedule.getSlots().get(0).getTaskId()).isEqualTo(1L);
    }

    // -----------------------------------------------------------------------
    // Вспомогательный метод
    // -----------------------------------------------------------------------

    private void addSlot(LocalDateTime start, LocalDateTime end) {
        long taskId = schedule.getSlots().size() + 1L;
        ScheduleSlot slot = new ScheduleSlot(taskId, 100L, start, end,
                new PlacementVariant("COOK_1", "asap", start, end, null));
        schedule.addSlot(slot);
    }
}