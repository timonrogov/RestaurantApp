package com.example.restaurant.services;
import com.example.restaurant.models.Day;
import com.example.restaurant.models.Dish;
import com.example.restaurant.models.DishCategory;
import com.example.restaurant.models.DishPromotion;
import com.example.restaurant.repositories.DayRepository;
import com.example.restaurant.repositories.DishCategoryRepository;
import com.example.restaurant.repositories.DishRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DishService {
    private final DishRepository dishRepository;
    private final PromotionTimeSlotService promotionTimeSlotService;
    private final DishCategoryRepository dishCategoryRepository;
    private final ImageService imageService;

    @Autowired
    public DishService(DishRepository dishRepository,
                       PromotionTimeSlotService promotionTimeSlotService,
                       DishCategoryRepository dishCategoryRepository,
                       ImageService imageService) {
        this.dishRepository = dishRepository;
        this.promotionTimeSlotService = promotionTimeSlotService;
        this.dishCategoryRepository = dishCategoryRepository;
        this.imageService = imageService;
    }

    public List<Dish> getAllDishes() {
        return dishRepository.findAll();
    }

    public Dish getDishById(Long id) {
        return dishRepository.findById(id).orElse(null);
    }

    public Dish createDish(Dish dish) {
        return dishRepository.save(dish);
    }

    public void deleteDish(Long id) {
        dishRepository.deleteById(id);
    }

    public List<Dish> getAllDishesWithActivePromotions(LocalDate date, LocalTime time) {
        List<Dish> dishes = dishRepository.findAll();
        dishes.forEach(dish -> {
            List<DishPromotion> activePromos = dish.getPromotions().stream()
                    .filter(promo -> promotionTimeSlotService.isPromotionActive(promo.getPromotionTimeSlot(), date, time))
                    .collect(Collectors.toList());
            dish.setActivePromotions(activePromos);
        });
        return dishes;
    }

    public List<Dish> getDishesByCategory(DishCategory category) {
        return dishRepository.findByCategoryAndIsAvailableTrue(category);
    }

    public List<Dish> getAllAvailableDishes(LocalDate date, LocalTime time) {
        List<Dish> dishes = dishRepository.findByIsAvailableTrue();

        // Для каждого блюда вычисляем активные акции и статистику
        dishes.forEach(dish -> {
            // Логика для акций (у тебя она уже есть, просто оставляем)
            List<DishPromotion> activePromos = dish.getPromotions().stream()
                    .filter(promo -> promotionTimeSlotService.isPromotionActive(promo.getPromotionTimeSlot(), date, time))
                    .collect(Collectors.toList());
            dish.setActivePromotions(activePromos);

            // Логика для статистики (у тебя она в контроллере, переносим сюда)
            // Map<String, Object> stats = dishReviewService.getDishReviewStats(dish.getId());
            // dish.setStats(stats);
        });

        // Конвертируем список Dish в список DishDto
        return dishes;
    }

    public void toggleAvailability(Long id) {
        Dish dish = dishRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Dish not found"));
        dish.setAvailable(!dish.isAvailable());
        dishRepository.save(dish);
    }

    public List<Dish> getDishesWithoutActivePromotions() {
        LocalDate currentDate = LocalDate.now();
        List<Dish> allDishes = dishRepository.findAll();
        return allDishes.stream()
                .filter(dish -> {
                    List<DishPromotion> promotions = dish.getPromotions();
                    return promotions.stream()
                            .noneMatch(promo -> promo.getPromotionTimeSlot().getEndDate().getWorkDate().isAfter(currentDate));
                })
                .collect(Collectors.toList());
    }

    /**
     * Сохраняет новое или обновляет существующее блюдо с картинкой.
     */
    @Transactional
    public void saveDish(Dish dish, MultipartFile imageFile, Long categoryId) throws IOException {

        // 1. Устанавливаем категорию
        DishCategory category = dishCategoryRepository.findById(categoryId)
                .orElseThrow(() -> new RuntimeException("Категория не найдена"));
        dish.setCategory(category);

        // 2. Проверяем, редактирование это или создание
        if (dish.getId() != null) {
            // --- РЕДАКТИРОВАНИЕ ---
            Dish existingDish = dishRepository.findById(dish.getId())
                    .orElseThrow(() -> new RuntimeException("Блюдо не найдено"));

            if (imageFile != null && !imageFile.isEmpty()) {
                // Если загрузили новую картинку — сохраняем её
                String imagePath = imageService.saveImage(imageFile);
                dish.setImagePath(imagePath);
            } else {
                // Если картинку НЕ меняли — берем старую из БД
                // Важно: метод getImagePath() может возвращать путь с префиксом.
                // При сохранении это нормально, так как наш геттер умеет работать с полными путями.
                dish.setImagePath(existingDish.getImagePath());
            }

            // Сохраняем статус доступности (чтобы он не сбросился в false, если нет чекбокса в форме)
            dish.setAvailable(existingDish.isAvailable());

        } else {
            // --- СОЗДАНИЕ НОВОГО ---
            if (imageFile != null && !imageFile.isEmpty()) {
                String imagePath = imageService.saveImage(imageFile);
                dish.setImagePath(imagePath);
            }
            // Новое блюдо по умолчанию доступно
            dish.setAvailable(true);
        }

        // 3. Сохраняем в БД
        dishRepository.save(dish);
    }
}
