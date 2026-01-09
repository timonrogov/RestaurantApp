package com.example.restaurant.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MvcConfig implements WebMvcConfigurer {

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Логика маппинга:
        // Если приходит запрос на адрес, начинающийся с "/img/uploads/..."
        // Spring будет искать файл в папке "uploads/dishes/" в корне проекта.

        registry.addResourceHandler("/img/uploads/**")
                .addResourceLocations("file:./uploads/dishes/");
    }
}