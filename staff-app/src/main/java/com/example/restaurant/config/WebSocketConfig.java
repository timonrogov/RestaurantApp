package com.example.restaurant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

/**
 * Конфигурация WebSocket с STOMP-протоколом для staff-app.
 *
 * Регистрирует эндпоинт /ws — точку подключения браузера.
 * Настраивает встроенный брокер для рассылки сообщений по топикам /topic/*.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Встроенный брокер сообщений для топиков /topic/*
        config.enableSimpleBroker("/topic");
        // Префикс для сообщений от клиента к серверу (если понадобится)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Эндпоинт WebSocket — браузер подключается по адресу /ws
        // withSockJS() — фолбэк для браузеров/прокси без поддержки WS
        registry.addEndpoint("/ws").withSockJS();
    }
}