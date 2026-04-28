package com.example.restaurant.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Getter
@Setter
@NoArgsConstructor // Lombok: конструктор без аргументов
@AllArgsConstructor // Lombok: конструктор со всеми полями
@Entity // JPA: сущность отображается на таблицу в БД
@Table(name = "dish") // JPA: имя таблицы в БД
public class Dish {
    @Id // JPA: первичный ключ
    @GeneratedValue(strategy = GenerationType.IDENTITY) // Автоинкрементный ID
    @Column(name = "id") // JPA: имя столбца
    private Long id;

    @Column(name = "dish_name", nullable = false) // Обязательное поле
    private String name;

    @Column(name = "description", columnDefinition = "TEXT") // Текстовый тип в БД
    private String description;

    @Column(name = "composition", columnDefinition = "TEXT") // Состав блюда (текст)
    private String composition;

    @ManyToOne // JPA: связь "многие к одному" с категорией блюда
    @JoinColumn(name = "category_id", nullable = false) // Внешний ключ
    private DishCategory category;

    @Column(name = "weight") // Вес блюда в граммах
    private Integer weight;

    @Column(name = "price", nullable = false) // Цена (обязательное поле)
    private Double price;

    @Column(name = "image_path") // Путь к изображению блюда
    private String imagePath = "/images/dishes/placeholder.jpg";

    @Column(name = "is_available", nullable = false) // Доступность блюда для заказа
    private boolean isAvailable = true; // По умолчанию доступно

    /**
     * Курс подачи блюда по умолчанию.
     * Определяет, в рамках какой подачи блюдо попадёт в заказ.
     *
     * Стандартная шкала:
     *   1 — Аперитив / Напитки  (подаются сразу, без паузы)
     *   2 — Закуска / Салаты    (через ~5 мин после напитков)
     *   3 — Первое блюдо (суп)  (через ~10 мин после закусок)
     *   4 — Основное блюдо      (через ~15 мин после первого)
     *   5 — Десерт              (через ~20 мин после основного)
     *
     * Администратор может изменить курс конкретного блюда
     * независимо от курса, принятого по умолчанию для его категории.
     *
     * columnDefinition гарантирует, что при ALTER TABLE существующие
     * строки получат значение 1, а не вызовут ошибку NOT NULL.
     */
    @Column(name = "default_course", nullable = false, columnDefinition = "integer default 1")
    private int defaultCourse = 1;

    // Связь с акциями: каскадное обновление/удаление
    @OneToMany(mappedBy = "dish", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DishPromotion> promotions = new ArrayList<>();

    @Transient // Поле не сохраняется в БД (для временных данных)
    private List<DishPromotion> activePromotions = new ArrayList<>();

    @Transient // Поле для статистики (например, средний рейтинг)
    private Map<String, Object> stats;

    // Связь с отзывами: каскадное обновление/удаление
    @OneToMany(mappedBy = "dish", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<DishReview> reviews = new ArrayList<>();


    public String getImagePath() {
        if (imagePath == null || imagePath.isEmpty()) {
            return "/images/dishes/placeholder.jpg"; // Заглушка, если ничего нет
        }
        // Если путь уже полный (начинается со слэша), возвращаем его
        // Это сработает для новых загрузок: "/img/uploads/..."
        if (imagePath.startsWith("/")) {
            return imagePath;
        }
        // Иначе это старая картинка, добавляем префикс
        return "/images/dishes/" + imagePath;
    }


    public double getAverageRating(){
        if(reviews.size() == 0){
            return 0;
        }
        double ratingSumm = 0;
        for (DishReview review: reviews) {
            ratingSumm += review.getRating();
        }
        double average = ratingSumm / reviews.size();
        return average;
    }
}
