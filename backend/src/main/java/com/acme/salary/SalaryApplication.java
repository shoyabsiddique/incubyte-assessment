package com.acme.salary;

import java.util.TimeZone;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class SalaryApplication {

	public static void main(String[] args) {
		// Pin the JVM to UTC before anything reads dates, so results never depend on the host machine.
		TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
		SpringApplication.run(SalaryApplication.class, args);
	}

}
