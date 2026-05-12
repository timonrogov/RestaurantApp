package com.example.restaurant.scheduler.dispatcher;

import com.example.restaurant.scheduler.config.SchedulerProperties;
import com.example.restaurant.scheduler.messages.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Специальный логгер для записи истории переговоров агентов в файл.
 * Отличный инструмент для демонстрации работы МАС в рамках дипломной работы.
 */
@Component
public class NegotiationFileLogger {

    private static final Logger log = LoggerFactory.getLogger(NegotiationFileLogger.class);

    // Файл будет создан в корне твоего проекта
    private Path logFilePath;
    private final DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss.SSS");

    private final SchedulerProperties props;

    public NegotiationFileLogger(SchedulerProperties props) {  // ← ДОБАВИТЬ параметр
        this.props = props;
        if (!props.getLogger().isEnabled()) {
            return;   // логгер выключен — ничего не создаём
        }

        // Путь из конфигурации (не хардкод)
        this.logFilePath = Paths.get(props.getLogger().getFilePath());

        try {
            if (props.getLogger().isClearOnStart()) {
                Files.deleteIfExists(logFilePath);
                Files.createFile(logFilePath);
            } else if (!Files.exists(logFilePath)) {
                Files.createFile(logFilePath);
            }
            writeLine("=== СТАРТ СИСТЕМЫ ПЛАНИРОВАНИЯ: " + LocalDateTime.now() + " ===\n");
        } catch (IOException e) {
            log.error("Не удалось создать файл лога переговоров", e);
        }
    }

    /**
     * Записывает одно сообщение в файл.
     */
    public void logCommunication(Message message, String recipientId) {
        if (!props.getLogger().isEnabled()) return;
        String timestamp = LocalDateTime.now().format(timeFormatter);

        // Форматируем тело сообщения
        String bodyString = message.getBody() != null ? message.getBody().toString() : "null";

        // Формируем красивую строку: [Время] ОТПРАВИТЕЛЬ -> ПОЛУЧАТЕЛЬ : ТИП_СООБЩЕНИЯ | Данные
        String logLine = String.format("[%s] %-20s -> %-20s : %-25s | Payload: %s%n",
                timestamp,
                message.getSenderId(),
                recipientId,
                message.getType().name(),
                bodyString);

        writeLine(logLine);
    }

    private void writeLine(String line) {
        try {
            Files.writeString(logFilePath, line, StandardOpenOption.APPEND);
        } catch (IOException e) {
            log.error("Ошибка при записи в файл переговоров", e);
        }
    }
}