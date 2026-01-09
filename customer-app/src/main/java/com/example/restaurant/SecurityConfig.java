package com.example.restaurant;

import com.example.restaurant.services.CustomerUserDetailsServiceImpl;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Collection;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    // Внедрение UserDetailsServiceImpl через конструктор (лучшая практика)
    private final CustomerUserDetailsServiceImpl customerUserDetailsService;

    public SecurityConfig(CustomerUserDetailsServiceImpl customerUserDetailsService) {
        this.customerUserDetailsService = customerUserDetailsService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.ignoringRequestMatchers("/api/**")) // CSRF для API можно оставить, если API используется клиентом
                .authorizeHttpRequests(auth -> auth
                        // Разрешаем доступ ко всем статическим ресурсам и страницам регистрации/логина
                        .requestMatchers(
                                "/", "/register", "/login", "/menu", "/api/**",
                                "/css/**", "/js/**", "/images/**", "/img/uploads/**",
                                "/orders/add-item",
                                "/orders/view",
                                "/orders/remove-item/**",
                                "/orders/confirm",
                                "/api/calls/**"
                        ).permitAll()
                        // Все остальные запросы (например, /account, /orders/history) требуют аутентификации
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        // Упрощенный обработчик: всех клиентов перенаправляем в меню
                        .defaultSuccessUrl("/menu", true)
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout")
                        .logoutSuccessUrl("/login")
                        .permitAll()
                )
                .userDetailsService(customerUserDetailsService); // Используем общий сервис из common-module
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
