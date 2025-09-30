package com.example.restaurant.models;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data // Lombok: автоматически генерирует геттеры и сеттеры
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


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getComposition() {
        return composition;
    }

    public void setComposition(String composition) {
        this.composition = composition;
    }

    public Integer getWeight() {
        return weight;
    }

    public void setWeight(Integer weight) {
        this.weight = weight;
    }

    public DishCategory getCategory() {
        return category;
    }

    public void setCategory(DishCategory category) {
        this.category = category;
    }

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }

    public List<DishPromotion> getPromotions() {
        return promotions;
    }

    public void setPromotions(List<DishPromotion> promotions) {
        this.promotions = promotions;
    }

    public List<DishPromotion> getActivePromotions() {
        return activePromotions;
    }

    public void setActivePromotions(List<DishPromotion> activePromotions) {
        this.activePromotions = activePromotions;
    }

    public String getImagePath() {
        return "/images/dishes/" + imagePath;
    }

    public void setImagePath(String imagePath) { this.imagePath = imagePath; }

    public Map<String, Object> getStats() {
        return stats;
    }

    public void setStats(Map<String, Object> stats) {
        this.stats = stats;
    }

    public List<DishReview> getReviews() {
        return reviews;
    }

    public void setReviews(List<DishReview> reviews) {
        this.reviews = reviews;
    }

    public boolean isAvailable() {
        return isAvailable;
    }

    public void setAvailable(boolean available) {
        isAvailable = available;
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
