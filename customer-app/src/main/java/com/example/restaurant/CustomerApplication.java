package com.example.restaurant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
@EnableScheduling
public class CustomerApplication {

	public static void main(String[] args) {
		System.out.println(new BCryptPasswordEncoder().encode("admin"));
		SpringApplication.run(CustomerApplication.class, args);
	}

}

/*
ToDo:
	- Подправить фронт (шапка, размеры кнопок в админке, цвета, ...).
	- Исправить ошибку с объединением одинаковых вызовов в один.
 */
