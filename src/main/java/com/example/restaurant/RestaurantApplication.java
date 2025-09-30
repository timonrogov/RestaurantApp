package com.example.restaurant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
public class RestaurantApplication {

	public static void main(String[] args) {
		System.out.println(new BCryptPasswordEncoder().encode("1"));
		SpringApplication.run(RestaurantApplication.class, args);
	}

}

/*
ToDo:
	- Скидки к блюдам (визуализация скидки для блюд с минимальным количеством)
	- Сообщение о том, что ресторан не работает
	- Настроить отображение даты и времени заказа на странице истории заказов
 */
