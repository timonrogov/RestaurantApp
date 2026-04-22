package com.example.restaurant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
@EnableScheduling
public class StaffApplication {

	public static void main(String[] args) {
		System.out.println(new BCryptPasswordEncoder().encode("1"));
		SpringApplication.run(StaffApplication.class, args);
	}

}
