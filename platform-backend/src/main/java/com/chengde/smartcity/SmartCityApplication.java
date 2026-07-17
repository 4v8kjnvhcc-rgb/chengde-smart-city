package com.chengde.smartcity;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SmartCityApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartCityApplication.class, args);
    }
}
