package com.example.restaurant.services;

import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

@Service
public class ImageService {

    // Папка для хранения загрузок в корне проекта
    // System.getProperty("user.dir") указывает на текущую рабочую директорию
    private final String UPLOAD_DIR = System.getProperty("user.dir") + "/uploads/dishes";

    /**
     * Сохраняет файл на диск и возвращает путь для доступа через веб.
     * @param file Загружаемый файл
     * @return Относительный URL картинки (например, "/img/uploads/uuid.jpg") или null
     */
    public String saveImage(MultipartFile file) throws IOException {
        if (file == null || file.isEmpty()) {
            return null;
        }

        // 1. Создаем директорию, если её нет
        Path uploadPath = Paths.get(UPLOAD_DIR);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        // 2. Генерируем уникальное имя файла
        // originalFileName может содержать пробелы или спецсимволы, лучше взять только расширение
        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }

        // Генерируем UUID (например, "550e8400-e29b-41d4-a716-446655440000.jpg")
        String uniqueFilename = UUID.randomUUID().toString() + extension;

        // 3. Сохраняем файл
        Path filePath = uploadPath.resolve(uniqueFilename);
        file.transferTo(filePath.toFile());

        // 4. Возвращаем путь, по которому файл будет доступен из браузера
        // Префикс /img/uploads/ мы настроим в WebMvcConfig на 5 этапе
        return "/img/uploads/" + uniqueFilename;
    }

    /**
     * Удаляет файл с диска (полезно при удалении блюда)
     */
    public void deleteImage(String imagePath) {
        if (imagePath == null || !imagePath.startsWith("/img/uploads/")) {
            return; // Это либо заглушка, либо внешний ресурс, не удаляем
        }

        try {
            // Извлекаем имя файла из URL
            String filename = imagePath.substring("/img/uploads/".length());
            Path filePath = Paths.get(UPLOAD_DIR).resolve(filename);
            Files.deleteIfExists(filePath);
        } catch (IOException e) {
            // Логируем ошибку, но не прерываем выполнение
            System.err.println("Не удалось удалить файл: " + imagePath);
        }
    }
}