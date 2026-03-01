package com.hiveapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class HiveAppApplication {

	public static void main(String[] args) {
		SpringApplication.run(HiveAppApplication.class, args);
	}

}
