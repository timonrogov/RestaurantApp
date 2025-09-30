package com.example.restaurant.controllers;

import com.example.restaurant.models.*;
import com.example.restaurant.repositories.DayRepository;
import com.example.restaurant.services.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.security.Principal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.*;

@Controller
public class MenuController {
    // Внедрение зависимостей через Spring (сервисы для работы с данными)
    @Autowired
    private DishService dishService; // Сервис для работы с блюдами
    @Autowired
    private DayService dayService; // Сервис для работы с днями работы ресторана
    @Autowired
    private DishCategoryService dishCategoryService; // Сервис для категорий блюд
    @Autowired
    private DishPromotionService dishPromotionService; // Сервис для акций
    @Autowired
    private DishReviewService dishReviewService;     // Сервис для отзывов о блюдах
    @Autowired
    private ClientService clientService;             // Сервис для клиентов
    @Autowired
    private OrderService orderService;               // Сервис для заказов




    /**
     * Обрабатывает GET-запрос на страницу меню.
     *
     * @param model     Объект для передачи данных в шаблон Thymeleaf.
     * @param principal Текущий аутентифицированный пользователь (может быть null).
     * @return Имя HTML-шаблона для рендеринга.
     */
    @GetMapping("/menu")
    public String getMenu(Model model, Principal principal) {
        // Текущие дата и время для проверки акций и работы ресторана
        LocalDate currentDate = LocalDate.now();
        LocalTime currentTime = LocalTime.now();

        // 1. Проверка статуса работы ресторана
        Map<String, Object> restaurantStatus =
                dishService.getRestaurantStatus(currentDate, currentTime);
        model.addAttribute("restaurantStatus", restaurantStatus);

        // Если ресторан закрыт или еще не открылся:
        if (!restaurantStatus.get("status").equals("open")) {
            // Если ресторан "еще не открылся" - добавляем время открытия/закрытия
            if (restaurantStatus.get("status").equals("not_opened_yet")) {
                Day today = dayService.getDayByDate(currentDate);
                model.addAttribute("openingTime", today.getStartTime());
                model.addAttribute("closingTime", today.getEndTime());
            }
            return "menu"; // Возвращаем шаблон без данных о меню
        }

        // 2. Загрузка данных для открытого ресторана:
        // Получаем все доступные блюда (с учетом времени работы и акций)
        List<Dish> dishes =
                dishService.getAllAvailableDishes(currentDate, currentTime);

        // Активные акции на текущее время
        List<DishPromotion> activePromotions =
                dishPromotionService.getActivePromotions(currentDate, currentTime);

        // Все категории блюд
        List<DishCategory> categories = dishCategoryService.getAllCategories();

        // Группировка блюд по категориям для отображения в меню
        Map<DishCategory, List<Dish>> dishesByCategory = new LinkedHashMap<>();

        // Статистика отзывов для каждого блюда (рейтинг, количество)
        Map<Long, Map<String, Object>> dishStats = new HashMap<>();

        // 3. Формирование структуры данных для шаблона:
        for (DishCategory category : categories) {
            // Блюда в текущей категории
            List<Dish> dishesInCategory = dishService.getDishesByCategory(category);
            dishesByCategory.put(category, dishesInCategory);

            // Для каждого блюда добавляем статистику отзывов
            dishes.forEach(dish -> {
                Map<String, Object> stats =
                        dishReviewService.getDishReviewStats(dish.getId());
                dish.setStats(stats);
                // Например: {"averageRating": 4.5, "reviewCount": 10}
            });
        }

        // 4. Информация о текущем заказе пользователя (если авторизован)
        double totalPrice = 0;
        double totalDiscounted = 0;
        boolean hasDiscount = false;
        boolean hasItems = false;

        if (principal != null) {
            // Получаем клиента по имени из сессии
            Client client = clientService
                    .getClientByUsername(principal.getName()).orElse(null);
            if (client != null) {
                // Активный заказ клиента (статус "В корзине")
                Order currentOrder = orderService.getCurrentOrderForClient(client);
                if (currentOrder != null
                        && !currentOrder.getOrderItems().isEmpty()) {
                    // Есть блюда в заказе
                    hasItems = true;
                    // Общая сумма без скидок
                    totalPrice = orderService.calculateTotal(currentOrder);
                    // Сумма со скидками
                    totalDiscounted =
                            orderService.calculateTotalWithDiscount(currentOrder);
                    // Есть ли примененные скидки
                    hasDiscount = totalDiscounted < totalPrice;
                }
            }
        }

        // 5. Передача данных в шаблон:
        // Все блюда
        model.addAttribute("dishes", dishes);
        // Текущая дата
        model.addAttribute("currentDate", currentDate);
        // Текущее время
        model.addAttribute("currentTime", currentTime);
        // Активные акции
        model.addAttribute("activePromotions", activePromotions);
        // Блюда по категориям
        model.addAttribute("dishesByCategory", dishesByCategory);
        // Статистика отзывов
        model.addAttribute("dishStats", dishStats);
        // Флаг наличия блюд в заказе
        model.addAttribute("hasItems", hasItems);
        // Сумма заказа без скидок
        model.addAttribute("totalPrice", totalPrice);
        // Сумма заказа со скидками
        model.addAttribute("totalDiscounted", totalDiscounted);
        // Флаг применения скидок
        model.addAttribute("hasDiscount", hasDiscount);

        return "menu"; // Рендеринг шаблона src/main/resources/templates/menu.html
    }




    @PostMapping("/add-item") // Обрабатывает POST-запросы по URL /add-item
    @ResponseBody // Возвращает данные в формате JSON (не HTML)
    public ResponseEntity<Map<String, Object>> addOrderItem(
            @RequestParam Long dishId,          // ID блюда из параметров запроса
            @RequestParam int quantity,         // Количество блюд
            // Опциональный комментарий
            @RequestParam(required = false) String comment,
            Principal principal) {  // Текущий аутентифицированный пользователь

        Map<String, Object> response = new HashMap<>(); // Ответ для клиента

        try {
            // 1. Получение клиента
            Client client = clientService.getClientByUsername(principal.getName())
                    .orElseThrow(() -> new RuntimeException("Клиент не найден"));

            // 2. Получение или создание текущего заказа
            Order currentOrder = orderService.getCurrentOrderForClient(client);

            // 3. Добавление блюда в заказ
            orderService.addDishToOrder(currentOrder, dishId, quantity, comment);

            // 4. Обновление данных заказа
            currentOrder = orderService.getCurrentOrderForClient(client);

            // 5. Проверка доступности блюда
            Dish dish = dishService.getDishById(dishId);
            // Если блюдо недоступно (например, нет в наличии)
            if (!dish.isAvailable()) {
                response.put("success", false);
                response.put("error", "Блюдо недоступно для заказа");
                // 400 Bad Request
                return ResponseEntity
                        .status(HttpStatus.BAD_REQUEST)
                        .body(response);
            }

            // 6. Расчет сумм заказа
            // Сумма без скидок
            double total = orderService.calculateTotal(currentOrder);
            // Сумма со скидками
            double totalDiscounted =
                    orderService.calculateTotalWithDiscount(currentOrder);
            // Есть ли позиции в заказе
            boolean hasItems = !currentOrder.getOrderItems().isEmpty();
            // Применены ли скидки
            boolean hasDiscount = totalDiscounted < total;

            // 7. Формирование успешного ответа
            response.put("success", true);
            response.put("totalPrice", total); // Общая сумма без скидок
            response.put("totalDiscounted", totalDiscounted); // Сумма со скидками
            response.put("hasItems", hasItems); // Флаг наличия позиций
            response.put("hasDiscount", hasDiscount); // Флаг скидок

            return ResponseEntity.ok(response); // 200 OK

        } catch (Exception e) {
            // 8. Обработка ошибок (например, сетевые сбои, некорректные данные)
            response.put("success", false);
            response.put("error", e.getMessage());
            // 500 Internal Server Error
            return ResponseEntity
                    .status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(response);
        }
    }
}
