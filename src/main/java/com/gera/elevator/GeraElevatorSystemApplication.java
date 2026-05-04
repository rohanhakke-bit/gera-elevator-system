package com.gera.elevator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class GeraElevatorSystemApplication {

	public static void main(String[] args) {
		SpringApplication.run(GeraElevatorSystemApplication.class, args);
	}

}
