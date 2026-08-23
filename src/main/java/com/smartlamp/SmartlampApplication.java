package com.smartlamp;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartlampApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartlampApplication.class, args);
    }
}