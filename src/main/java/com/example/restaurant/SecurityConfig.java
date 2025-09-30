package com.example.restaurant;

import com.example.restaurant.services.UserDetailsServiceImpl;
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
    UserDetailsServiceImpl accountDetailsService;

    @Autowired
    public SecurityConfig(UserDetailsServiceImpl accountDetailsService) {
        this.accountDetailsService = accountDetailsService;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers("/api/**") // Отключаем CSRF для API
                )
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/admin/**").hasRole("СОТРУДНИК")
                        .requestMatchers("/admin/orders/**").hasRole("СОТРУДНИК")
                        .requestMatchers("/", "/register", "/login", "/api/**", "/css/**", "/js/**", "/images/**").permitAll()
                        .anyRequest().authenticated()
                )
                .formLogin(form -> form
                        .loginPage("/login")
                        .successHandler((request, response, authentication) -> {
                            // Получаем список ролей пользователя
                            Collection<? extends GrantedAuthority> authorities = authentication.getAuthorities();

                            // Проверяем наличие роли "СОТРУДНИК"
                            boolean isEmployee = authorities.stream()
                                    .anyMatch(a -> a.getAuthority().equals("ROLE_СОТРУДНИК"));

                            // Перенаправляем в зависимости от роли
                            if(isEmployee) {
                                response.sendRedirect("/admin/orders");
                            } else {
                                response.sendRedirect("/menu");
                            }
                        })
                        .permitAll()
                )
                .logout(logout -> logout
                        .logoutUrl("/logout") // URL для выхода
                        .logoutSuccessUrl("/login")
                        .permitAll()
                )
                .userDetailsService(accountDetailsService);
        return http.build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
