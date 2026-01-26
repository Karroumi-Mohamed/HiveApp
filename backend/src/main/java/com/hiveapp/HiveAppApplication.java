package com.hiveapp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.modulith.Modulithic;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableCaching
@EnableAsync
@Modulithic(
        systemName = "HiveApp Platform",
        sharedModules = {"shared"}
)
public class HiveAppApplication {

    public static void main(String[] args) {
        SpringApplication.run(HiveAppApplication.class, args);
    }
}
