package com.example.restaurant.scheduler.schedule;

import com.example.restaurant.scheduler.messages.dto.PlacementVariant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit-тесты для EquipmentTypeSchedule.
 * Ключевой акцент — корректность параллельной ёмкости (maxParallelTasks).
 */
class EquipmentTypeScheduleTest {

    private static final LocalDateTime BASE = LocalDateTime.of(2025, 1, 1, 10, 0);

    // Тестируем духовку с ёмкостью 2 (2 блюда одновременно)
    private EquipmentTypeSchedule schedule;

    @BeforeEach
    void setUp() {
        schedule = new EquipmentTypeSchedule("OVEN", 2);
    }

    // -----------------------------------------------------------------------
    // isAvailable
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("isAvailable: пустое расписание → всегда доступно")
    void isAvailable_empty_alwaysTrue() {
        assertThat(schedule.isAvailable(BASE, BASE.plusMinutes(30))).isTrue();
    }

    @Test
    @DisplayName("isAvailable: один слот, ёмкость 2 → есть место")
    void isAvailable_oneOfTwo_stillAvailable() {
        addSlot(1L, BASE, BASE.plusMinutes(30));
        // Только 1 из 2 ёмкостей занято
        assertThat(schedule.isAvailable(BASE, BASE.plusMinutes(30))).isTrue();
    }

    @Test
    @DisplayName("isAvailable: оба слота заняты → недоступно")
    void isAvailable_bothSlotsTaken_false() {
        addSlot(1L, BASE, BASE.plusMinutes(30));
        addSlot(2L, BASE, BASE.plusMinutes(30));
        // Обе ёмкости заняты
        assertThat(schedule.isAvailable(BASE, BASE.plusMinutes(30))).isFalse();
    }

    @Test
    @DisplayName("isAvailable: полное перекрытие вышло — снова доступно")
    void isAvailable_afterSlotEnd_available() {
        addSlot(1L, BASE, BASE.plusMinutes(30));
        addSlot(2L, BASE, BASE.plusMinutes(30));

        // После 10:30 оба слота закончились
        assertThat(schedule.isAvailable(BASE.plusMinutes(30), BASE.plusMinutes(45))).isTrue();
    }

    @Test
    @DisplayName("isAvailable: смежный слот НЕ считается конфликтом")
    void isAvailable_adjacentSlot_available() {
        addSlot(1L, BASE, BASE.plusMinutes(30));
        addSlot(2L, BASE, BASE.plusMinutes(30));

        // Запрашиваем ровно с 10:30 — оба слота закончились в 10:30
        assertThat(schedule.isAvailable(BASE.plusMinutes(30), BASE.plusMinutes(50))).isTrue();
    }

    // -----------------------------------------------------------------------
    // findAvailableSlot
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("findAvailableSlot: нет слотов → возвращает notBefore")
    void findAvailableSlot_empty_returnsNotBefore() {
        LocalDateTime result = schedule.findAvailableSlot(15, BASE);
        assertThat(result).isEqualTo(BASE);
    }

    @Test
    @DisplayName("findAvailableSlot: ёмкость не исчерпана → возвращает notBefore")
    void findAvailableSlot_capacityNotExhausted_returnsNotBefore() {
        // Только 1 из 2 занято
        addSlot(1L, BASE, BASE.plusMinutes(60));

        LocalDateTime result = schedule.findAvailableSlot(15, BASE);
        // Есть свободная ёмкость → можно начать прямо с BASE
        assertThat(result).isEqualTo(BASE);
    }

    @Test
    @DisplayName("findAvailableSlot: обе ёмкости заняты → ждём освобождения")
    void findAvailableSlot_fullCapacity_waitsForRelease() {
        // Оба слота до 10:30
        addSlot(1L, BASE, BASE.plusMinutes(30));
        addSlot(2L, BASE, BASE.plusMinutes(30));

        LocalDateTime result = schedule.findAvailableSlot(15, BASE);
        // Первый освобождается в 10:30
        assertThat(result).isEqualTo(BASE.plusMinutes(30));
    }

    @Test
    @DisplayName("findAvailableSlot: слоты с разным временем окончания → ждём первого освобождения")
    void findAvailableSlot_differentEndTimes_waitsForEarliest() {
        // Слот 1 до 10:20, слот 2 до 10:40
        addSlot(1L, BASE, BASE.plusMinutes(20));
        addSlot(2L, BASE, BASE.plusMinutes(40));

        LocalDateTime result = schedule.findAvailableSlot(15, BASE);
        // Первый освобождается в 10:20 → с 10:20 можно поставить задачу
        assertThat(result).isEqualTo(BASE.plusMinutes(20));
    }

    // -----------------------------------------------------------------------
    // decreaseCapacity / increaseCapacity
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("decreaseCapacity: ёмкость уменьшается, ниже 0 не падает")
    void decreaseCapacity_noNegative() {
        schedule.decreaseCapacity(5); // было 2, пытаемся уменьшить на 5
        assertThat(schedule.getTotalCapacity()).isEqualTo(0);
    }

    @Test
    @DisplayName("increaseCapacity: ёмкость увеличивается после поломки")
    void increaseCapacity_afterBreakdown() {
        schedule.decreaseCapacity(1);
        assertThat(schedule.getTotalCapacity()).isEqualTo(1);
        schedule.increaseCapacity(1);
        assertThat(schedule.getTotalCapacity()).isEqualTo(2);
    }

    @Test
    @DisplayName("removeSlotsByOrderId: удаляет только слоты нужного заказа")
    void removeSlotsByOrderId_removesOnlyMatchingOrder() {
        ScheduleSlot s1 = new ScheduleSlot(1L, 10L, BASE, BASE.plusMinutes(20), null);
        ScheduleSlot s2 = new ScheduleSlot(2L, 20L, BASE, BASE.plusMinutes(20), null); // другой заказ
        schedule.addSlot(s1);
        schedule.addSlot(s2);

        int removed = schedule.removeSlotsByOrderId(10L);
        assertThat(removed).isEqualTo(1);
        assertThat(schedule.getSlots()).hasSize(1);
        assertThat(schedule.getSlots().get(0).getOrderId()).isEqualTo(20L);
    }

    // -----------------------------------------------------------------------
    // Вспомогательный метод
    // -----------------------------------------------------------------------

    private void addSlot(long taskId, LocalDateTime start, LocalDateTime end) {
        schedule.addSlot(new ScheduleSlot(taskId, 100L, start, end, null));
    }
}