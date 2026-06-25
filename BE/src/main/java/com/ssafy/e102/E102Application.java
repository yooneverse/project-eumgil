package com.ssafy.e102;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class E102Application {
	public static void main(String[] args) {
		SpringApplication.run(E102Application.class, args);
	}

}
