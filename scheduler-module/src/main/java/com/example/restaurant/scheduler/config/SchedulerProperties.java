package com.example.restaurant.scheduler.config;

import jakarta.annotation.PostConstruct;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Типобезопасная конфигурация планировщика.
 *
 * Значения загружаются из scheduler.properties (или application.properties)
 * по префиксу «scheduler». Вложенные группы параметров оформлены как
 * статические inner-классы — Spring заполняет их рекурсивно.
 *
 * Использование в агентах:
 *   private final SchedulerProperties props;
 *   // через конструктор, props передаётся из DispatcherAgent
 *   double w = props.getScoring().getWeightSync();
 */
@Component
@ConfigurationProperties(prefix = "scheduler")
public class SchedulerProperties {

    private Scoring scoring = new Scoring();
    private Planning planning = new Planning();
    private Adaptive adaptive = new Adaptive();
    private MessageBusConfig messageBus = new MessageBusConfig();
    private LoggerConfig logger = new LoggerConfig();

    // -----------------------------------------------------------------------
    // Валидация при старте
    // -----------------------------------------------------------------------

    /**
     * Проверить что сумма весов score-функций равна 1.0.
     * Если нет — выбрасываем исключение при старте, чтобы ошибка была заметна сразу.
     */
    @PostConstruct
    public void validate() {
        double sum = scoring.weightSync + scoring.weightSpeed + scoring.weightLoad;
        if (Math.abs(sum - 1.0) > 0.001) {
            throw new IllegalStateException(
                    "Конфигурация планировщика: сумма весов score-функций должна быть 1.0, " +
                            "но получено " + sum +
                            " (sync=" + scoring.weightSync +
                            ", speed=" + scoring.weightSpeed +
                            ", load=" + scoring.weightLoad + ")"
            );
        }
    }

    // -----------------------------------------------------------------------
    // Геттеры групп
    // -----------------------------------------------------------------------

    public Scoring getScoring() { return scoring; }
    public Planning getPlanning() { return planning; }
    public Adaptive getAdaptive() { return adaptive; }
    public MessageBusConfig getMessageBus() { return messageBus; }
    public LoggerConfig getLogger() { return logger; }

    public void setScoring(Scoring scoring) { this.scoring = scoring; }
    public void setPlanning(Planning planning) { this.planning = planning; }
    public void setAdaptive(Adaptive adaptive) { this.adaptive = adaptive; }
    public void setMessageBus(MessageBusConfig messageBus) { this.messageBus = messageBus; }
    public void setLogger(LoggerConfig logger) { this.logger = logger; }

    // -----------------------------------------------------------------------
    // Группа: веса и параметры score-функций
    // -----------------------------------------------------------------------

    public static class Scoring {
        /** Вес оценки синхронизации (попадание в targetEndTime). */
        private double weightSync = 0.6;
        /** Вес оценки скорости начала задачи. */
        private double weightSpeed = 0.2;
        /** Вес оценки нагрузки повара. */
        private double weightLoad = 0.2;
        /** Окно нормализации speedScore (минуты). */
        private int windowMinutes = 120;
        /** Окно чувствительности syncScore (минуты). */
        private int syncSensitivityMinutes = 60;
        /** Горизонт расчёта нагрузки повара в computeLoadScore (минуты). */
        private int loadWindowMinutes = 60;

        public double getWeightSync() { return weightSync; }
        public double getWeightSpeed() { return weightSpeed; }
        public double getWeightLoad() { return weightLoad; }
        public int getWindowMinutes() { return windowMinutes; }
        public int getSyncSensitivityMinutes() { return syncSensitivityMinutes; }
        public int getLoadWindowMinutes() { return loadWindowMinutes; }

        public void setWeightSync(double v) { this.weightSync = v; }
        public void setWeightSpeed(double v) { this.weightSpeed = v; }
        public void setWeightLoad(double v) { this.weightLoad = v; }
        public void setWindowMinutes(int v) { this.windowMinutes = v; }
        public void setSyncSensitivityMinutes(int v) { this.syncSensitivityMinutes = v; }
        public void setLoadWindowMinutes(int v) { this.loadWindowMinutes = v; }
    }

    // -----------------------------------------------------------------------
    // Группа: параметры планирования
    // -----------------------------------------------------------------------

    public static class Planning {
        /** Максимальное число перепланирований до FAILED. */
        private int maxReplan = 3;
        /** Дедлайн = targetEndTime + horizonMinutes. */
        private int horizonMinutes = 120;
        /** Буфер notBefore при первом планировании курса (мин). */
        private int firstCourseBufferMinutes = 1;
        /** Порог JIT-выравнивания (секунды). */
        private int jitAlignmentBufferSeconds = 30;

        public int getMaxReplan() { return maxReplan; }
        public int getHorizonMinutes() { return horizonMinutes; }
        public int getFirstCourseBufferMinutes() { return firstCourseBufferMinutes; }
        public int getJitAlignmentBufferSeconds() { return jitAlignmentBufferSeconds; }

        public void setMaxReplan(int v) { this.maxReplan = v; }
        public void setHorizonMinutes(int v) { this.horizonMinutes = v; }
        public void setFirstCourseBufferMinutes(int v) { this.firstCourseBufferMinutes = v; }
        public void setJitAlignmentBufferSeconds(int v) { this.jitAlignmentBufferSeconds = v; }
    }

    // -----------------------------------------------------------------------
    // Группа: адаптивные события
    // -----------------------------------------------------------------------

    public static class Adaptive {
        /** Порог досрочного завершения (мин). */
        private int earlyFinishThresholdMinutes = 0;
        /** Cron обнаружения задержек. */
        private String delayDetectCron = "0 * * * * *";
        /** Интервал опроса незапланированных заказов (мс). */
        private long pendingOrdersPollMs = 10_000L;
        /** Сдвиг при автообнаружении задержки (мин). */
        private int autoDelayAheadMinutes = 1;

        public int getEarlyFinishThresholdMinutes() { return earlyFinishThresholdMinutes; }
        public String getDelayDetectCron() { return delayDetectCron; }
        public long getPendingOrdersPollMs() { return pendingOrdersPollMs; }
        public int getAutoDelayAheadMinutes() { return autoDelayAheadMinutes; }

        public void setEarlyFinishThresholdMinutes(int v) { this.earlyFinishThresholdMinutes = v; }
        public void setDelayDetectCron(String v) { this.delayDetectCron = v; }
        public void setPendingOrdersPollMs(long v) { this.pendingOrdersPollMs = v; }
        public void setAutoDelayAheadMinutes(int v) { this.autoDelayAheadMinutes = v; }
    }

    // -----------------------------------------------------------------------
    // Группа: MessageBus
    // -----------------------------------------------------------------------

    public static class MessageBusConfig {
        /** Лимит итераций processAll(). */
        private int maxIterations = 10_000;
        /** Лимит итераций findAvailableSlot() в EquipmentTypeSchedule. */
        private int equipmentSlotMaxIters = 1_000;

        public int getMaxIterations() { return maxIterations; }
        public int getEquipmentSlotMaxIters() { return equipmentSlotMaxIters; }

        public void setMaxIterations(int v) { this.maxIterations = v; }
        public void setEquipmentSlotMaxIters(int v) { this.equipmentSlotMaxIters = v; }
    }

    // -----------------------------------------------------------------------
    // Группа: логгер переговоров
    // -----------------------------------------------------------------------

    public static class LoggerConfig {
        /** Включить запись в файл. */
        private boolean enabled = true;
        /** Путь к файлу. */
        private String filePath = "negotiations_log.txt";
        /** Очищать при старте. */
        private boolean clearOnStart = true;

        public boolean isEnabled() { return enabled; }
        public String getFilePath() { return filePath; }
        public boolean isClearOnStart() { return clearOnStart; }

        public void setEnabled(boolean v) { this.enabled = v; }
        public void setFilePath(String v) { this.filePath = v; }
        public void setClearOnStart(boolean v) { this.clearOnStart = v; }
    }

    // -----------------------------------------------------------------------
    // Фабричный метод для тестов
    // -----------------------------------------------------------------------

    /**
     * Создать экземпляр с дефолтными значениями без Spring-контекста.
     * Используется в SchedulerIntegrationTest.
     */
    public static SchedulerProperties defaults() {
        return new SchedulerProperties();
    }
}