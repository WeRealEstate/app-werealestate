package com.werealestate.backend;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {

	public static void main(String[] args) {
		// Toda la operación de la empresa es en Oaxaca; sin esto el VPS corre en UTC
		// y LocalDateTime.now() (usado en toda la app) queda 6 horas adelantado.
		TimeZone.setDefault(TimeZone.getTimeZone("America/Mexico_City"));
		SpringApplication.run(BackendApplication.class, args);
	}

}
