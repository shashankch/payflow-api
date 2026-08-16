package com.payflow;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class PayflowApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(PayflowApiApplication.class, args);
	}
}
