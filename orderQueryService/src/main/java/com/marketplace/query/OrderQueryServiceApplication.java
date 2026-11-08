package com.marketplace.query;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

// Point d'entrée du service Query (suivi client, CQRS)
// @EnableScheduling active la lecture périodique de la file d'événements
@SpringBootApplication
@EnableScheduling
public class OrderQueryServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderQueryServiceApplication.class, args);
    }
}
