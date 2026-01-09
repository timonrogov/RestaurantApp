package com.example.restaurant.services;

import com.example.restaurant.enums.OrderStatus;
import com.example.restaurant.models.*;
import com.example.restaurant.repositories.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.UUID;


import java.security.Principal;
import java.util.Optional;


@Service
@RequiredArgsConstructor // Используем Lombok для конструктора
public class CartService {

    private static final String GUEST_COOKIE_NAME = "GUEST_CART_ID";

    // Необходимые зависимости для работы с корзиной
    private final OrderRepository orderRepository;
    private final ClientRepository clientRepository;
    private final AccountRepository accountRepository;
    private final DishRepository dishRepository;
    private final OrderItemRepository orderItemRepository;


    /**
     * Универсальный метод получения корзины.
     * Теперь требует request и response для работы с куками гостя.
     */
    @Transactional
    public Order getCurrentCart(Principal principal, HttpServletRequest request, HttpServletResponse response) {
        if (principal != null) {
            // 1. Логика для авторизованного пользователя
            return getCartForUser(principal.getName());
        } else {
            // 2. Логика для гостя
            return getCartForGuest(request, response);
        }
    }

    private Order getCartForUser(String username) {
        Account account = accountRepository.findByUsername(username)
                .orElseThrow(() -> new RuntimeException("Аккаунт не найден"));
        Client client = clientRepository.findByAccount(account)
                .orElseThrow(() -> new RuntimeException("Клиент не найден"));

        return orderRepository.getCurrentCartForClient(client)
                .orElseGet(() -> createNewOrder(client, null));
    }

    private Order getCartForGuest(HttpServletRequest request, HttpServletResponse response) {
        String token = getOrCreateGuestToken(request, response);

        return orderRepository.getCurrentCartForGuest(token)
                .orElseGet(() -> createNewOrder(null, token));
    }

    // Создание заказа (универсальное)
    private Order createNewOrder(Client client, String token) {
        Order newOrder = new Order();
        newOrder.setClient(client); // Может быть null
        newOrder.setSessionToken(token); // Может быть null
        newOrder.setStatus(OrderStatus.ASSEMBLY);
        return orderRepository.save(newOrder);
    }

    // Работа с куками
    private String getOrCreateGuestToken(HttpServletRequest request, HttpServletResponse response) {
        if (request.getCookies() != null) {
            for (Cookie cookie : request.getCookies()) {
                if (GUEST_COOKIE_NAME.equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }

        // Если куки нет, генерируем новую
        String newToken = UUID.randomUUID().toString();
        Cookie cookie = new Cookie(GUEST_COOKIE_NAME, newToken);
        cookie.setPath("/"); // Доступна на всем сайте
        cookie.setMaxAge(60 * 60 * 24 * 30); // 30 дней
        cookie.setHttpOnly(true); // Безопасность
        response.addCookie(cookie);

        return newToken;
    }


    /**
     * Добавляет блюдо в корзину клиента или обновляет существующую позицию.
     * @param dishId - ID добавляемого блюда.
     * @param quantity - Количество. Если позиция новая, будет установлено это значение. Если существует, будет добавлено.
     * @param comment - Опциональный комментарий к позиции.
     * @param principal - данные аутентифицированного пользователя.
     */
    @Transactional
    public void addItemToCart(Long dishId, int quantity, String comment,
                              Principal principal,
                              HttpServletRequest request,
                              HttpServletResponse response) {
        if (quantity <= 0) {
            // Можно выбросить исключение или просто ничего не делать
            return;
        }

        Order currentCart = getCurrentCart(principal, request, response);
        Dish dish = dishRepository.findById(dishId)
                .orElseThrow(() -> new RuntimeException("Блюдо с ID " + dishId + " не найдено"));

        Optional<OrderItem> existingItemOpt = currentCart.getOrderItems().stream()
                .filter(item -> item.getDish().getId() == dishId)
                .findFirst();

        if (existingItemOpt.isPresent()) {
            // Если позиция уже есть в корзине, обновляем ее
            OrderItem existingItem = existingItemOpt.get();
            existingItem.setQuantity(existingItem.getQuantity() + quantity);
            // Логика для комментария: можно добавить к существующему или перезаписать
            if (comment != null && !comment.isEmpty()) {
                // Например, дописываем новый комментарий к старому
                String newComment = (existingItem.getComment() != null ? existingItem.getComment() + "; " : "") + comment;
                existingItem.setComment(newComment);
            }
            orderItemRepository.save(existingItem);
        } else {
            // Если позиции нет, создаем новую
            OrderItem newItem = new OrderItem();
            newItem.setOrder(currentCart);
            newItem.setDish(dish);
            newItem.setQuantity(quantity);
            newItem.setComment(comment);
            currentCart.getOrderItems().add(newItem);
            orderItemRepository.save(newItem);
            orderRepository.save(currentCart);
        }
    }


    /**
     * Удаляет позицию (блюдо) из корзины.
     * @param orderItemId - ID удаляемой позиции.
     */
    @Transactional
    public void removeOrderItem(Long orderItemId) {
        // Можно добавить проверку, что удаляемый элемент принадлежит
        // текущему пользователю, для повышения безопасности.
        orderItemRepository.deleteById(orderItemId);
    }
}