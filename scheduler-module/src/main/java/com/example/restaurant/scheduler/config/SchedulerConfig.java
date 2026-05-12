package com.example.restaurant.scheduler.config;

import com.example.restaurant.repositories.CookingTaskRepository;
import com.example.restaurant.repositories.CookingTaskTemplateRepository;
import com.example.restaurant.repositories.OrderCourseRepository;
import com.example.restaurant.repositories.OrderRepository;
import com.example.restaurant.scheduler.dispatcher.DispatcherAgent;
import com.example.restaurant.scheduler.dispatcher.MessageBus;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.PropertySource;

/**
 * Spring-конфигурация планировщика.
 *
 * Объявляет DispatcherAgent как Spring-бин. MessageBus уже является
 * @Component — Spring создаёт его автоматически и инжектирует сюда.
 */
@Configuration
@PropertySource("classpath:scheduler.properties")
//@EnableConfigurationProperties(SchedulerProperties.class)
public class SchedulerConfig {

    @Bean
    public DispatcherAgent dispatcherAgent(MessageBus messageBus,
                                           CookingTaskRepository taskRepository,
                                           CookingTaskTemplateRepository templateRepository,
                                           OrderCourseRepository orderCourseRepository,
                                           OrderRepository orderRepository,
                                           SchedulerProperties schedulerProperties) {
        return new DispatcherAgent(
                messageBus,
                taskRepository,
                templateRepository,
                orderCourseRepository,
                orderRepository,
                schedulerProperties
        );
    }
}