package com.marketplace.stock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Point d'entrée du service Stock
// @EnableScheduling active la lecture périodique de la file d'événements
@SpringBootApplication
@EnableScheduling
public class StockServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(StockServiceApplication.class, args);
    }
}
